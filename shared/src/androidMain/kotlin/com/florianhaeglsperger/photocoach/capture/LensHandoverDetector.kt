package com.florianhaeglsperger.photocoach.capture

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.os.SystemClock
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.florianhaeglsperger.photocoach.capture.saliency.LumaGrid
import com.florianhaeglsperger.photocoach.capture.saliency.SpectralResidualSaliency
import com.florianhaeglsperger.photocoach.ui.LensHandover

/** Wie viele Bild-Metadaten vorgehalten werden, bis das passende Analyse-Bild ankommt. */
private const val MAX_RECENT_RESULTS = 16

/**
 * Erkennt, wann eine logische Mehrfachkamera das physische Objektiv wechselt (Galaxy S24:
 * Hauptkamera ↔ Tele bei ~2,9×), und misst dabei, wie weit das Bild springt.
 *
 * Den Wechsel waehlt der Kameratreiber selbst; die App sieht ihn nur im Ergebnis-Feld
 * `LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID` jedes Bildes. Dieses Feld kommt ueber einen
 * Camera2-Callback, die Bilder ueber die Analyse — verbunden werden beide ueber den
 * Sensor-Zeitstempel (CameraX setzt `ImageInfo.timestamp` auf genau diesen Wert).
 *
 * Gemessen wird per [ImageRegistration] zwischen dem letzten Bild der alten und dem ersten
 * der neuen Kamera, auf demselben 64x64-Helligkeitsbild, das auch die Saliency nimmt. Das
 * kostet pro Bild eine Abtastung (~4000 Pixel) und nur beim Wechsel selbst drei FFTs.
 *
 * Threads: der Callback laeuft auf dem Camera2-Thread, [onFrame] auf dem Analyse-Thread,
 * [bindTo] auf dem Main-Thread.
 */
@OptIn(ExperimentalCamera2Interop::class)
internal class LensHandoverDetector {

    private class FrameMeta(val timestampNs: Long, val physicalId: String?, val zoomRatio: Float)

    /** Nur logische Mehrfachkameras wechseln das Objektiv — sonst ist alles hier aus. */
    @Volatile private var enabled = false

    /** Sensor-Zeitstempel in `elapsedRealtime`-Zeit? Nur dann laesst sich die Latenz angeben. */
    @Volatile private var realtimeTimestamps = false

    /** Zaehlt bei jedem Binden hoch; der Analyse-Thread verwirft dann sein altes Bild. */
    @Volatile private var generation = 0

    private val recent = ArrayDeque<FrameMeta>()

    // Nur auf dem Analyse-Thread.
    private var seenGeneration = -1
    private var lastLuma: LumaGrid? = null
    private var lastMeta: FrameMeta? = null

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
            val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
            val physicalId = result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
            val zoom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                result.get(CaptureResult.CONTROL_ZOOM_RATIO) ?: 1f
            } else {
                1f
            }
            synchronized(recent) {
                recent.addLast(FrameMeta(timestamp, physicalId, zoom))
                while (recent.size > MAX_RECENT_RESULTS) recent.removeFirst()
            }
        }
    }

    /**
     * Haengt den Callback an die Analyse. Vor `build()` aufrufen. CameraX buendelt die
     * Callbacks aller Use-Cases, der von [ManualCameraControl] an der Vorschau bleibt also
     * unberuehrt.
     */
    fun attachTo(builder: ImageAnalysis.Builder) {
        Camera2Interop.Extender(builder).setSessionCaptureCallback(captureCallback)
    }

    /** Bei jedem Binden aufrufen: neue Kamera, neue Faehigkeiten, kein altes Vergleichsbild. */
    fun bindTo(camera: Camera) {
        val info = Camera2CameraInfo.from(camera.cameraInfo)
        val capabilities = info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?: IntArray(0)
        synchronized(recent) { recent.clear() }
        generation++
        realtimeTimestamps = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) ==
            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME
        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in capabilities
    }

    /**
     * Fuer jedes Analyse-Bild aufrufen (nicht nur im 10-Hz-Takt: je aelter das Vergleichsbild,
     * desto mehr hat sich zwischendurch die Kamera bewegt). Liefert einen [LensHandover],
     * wenn mit diesem Bild das Objektiv gewechselt hat, sonst `null`.
     *
     * Nur waehrend `analyze()` gueltig, wie jedes [ImageProxy].
     */
    fun onFrame(image: ImageProxy, mirrored: Boolean): LensHandover? {
        if (!enabled) return null
        if (seenGeneration != generation) {
            seenGeneration = generation
            lastLuma = null
            lastMeta = null
        }
        val meta = lookup(image.imageInfo.timestamp) ?: return null
        val luma = image.uprightLuma(SpectralResidualSaliency.INPUT_SIZE)
        val previousLuma = lastLuma
        val previous = lastMeta
        lastLuma = luma
        lastMeta = meta

        val from = previous?.physicalId ?: return null
        val to = meta.physicalId ?: return null
        if (previousLuma == null || from == to) return null

        val start = SystemClock.elapsedRealtimeNanos()
        val shift = ImageRegistration.estimateShift(previousLuma, luma, meta.zoomRatio / previous.zoomRatio)
            // Die Vorschau der Frontkamera ist gespiegelt, das Analyse-Bild nicht.
            ?.let { if (mirrored) it.copy(dx = -it.dx) else it }
        val end = SystemClock.elapsedRealtimeNanos()
        return LensHandover(
            fromCamera = from,
            toCamera = to,
            zoomRatio = meta.zoomRatio,
            shift = shift,
            measureMs = (end - start) / 1_000_000f,
            frameAgeMs = if (realtimeTimestamps) (end - meta.timestampNs) / 1_000_000f else null,
            frameGapMs = (meta.timestampNs - previous.timestampNs) / 1_000_000f,
        )
    }

    private fun lookup(timestampNs: Long): FrameMeta? = synchronized(recent) {
        recent.lastOrNull { it.timestampNs == timestampNs }
    }
}
