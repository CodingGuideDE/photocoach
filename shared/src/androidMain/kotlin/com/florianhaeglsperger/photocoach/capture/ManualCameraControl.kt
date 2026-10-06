package com.florianhaeglsperger.photocoach.capture

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Handler
import android.os.Looper
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.Preview
import java.util.concurrent.Executor

/** Startwerte fuer manuelle Belichtung, falls die Automatik noch nichts gemessen hat. */
private const val FALLBACK_ISO = 200
private const val FALLBACK_EXPOSURE_NS = 1_000_000_000L / 60

/**
 * Setzt [ManualSettings] auf einer CameraX-Kamera um ("Pro-Modus").
 *
 * CameraX selbst kennt nur Belichtungskorrektur; ISO, Verschlusszeit, Weissabgleich und
 * manueller Fokus gehen ueber Camera2Interop direkt als Camera2-Request-Parameter an die
 * Kamera. Die gelten fuer alle gebundenen Use-Cases — Vorschau, Analyse und Foto zeigen
 * also dieselbe Belichtung, und die Hinweise beziehen sich auf das, was gespeichert wird.
 *
 * Voraussetzung fuer ISO/Zeit/Fokus ist die Camera2-Faehigkeit `MANUAL_SENSOR`. Die haben
 * praktisch alle Rueckkameras der Mittel- und Oberklasse, Frontkameras oft nicht.
 */
@OptIn(ExperimentalCamera2Interop::class)
internal class ManualCameraControl {

    // Letzte Werte der Belichtungsautomatik. Geschrieben vom Camera2-Callback-Thread,
    // gelesen beim Anwenden auf dem Main-Thread.
    @Volatile private var meteredIso: Int? = null
    @Volatile private var meteredExposureNs: Long? = null

    // Nur auf dem Main-Thread benutzt.
    private var manualExposureActive = false
    private var applyGeneration = 0
    private val mainExecutor = Executor { Handler(Looper.getMainLooper()).post(it) }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            // Nur mitschreiben, solange die Automatik regelt — sonst wuerde der manuelle
            // Wert als "gemessen" zurueckgelesen.
            if (result.get(CaptureResult.CONTROL_AE_MODE) == CaptureResult.CONTROL_AE_MODE_OFF) return
            result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { meteredIso = it }
            result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { meteredExposureNs = it }
        }
    }

    /** Haengt die Messwert-Beobachtung an die Vorschau. Vor `build()` aufrufen. */
    fun attachTo(previewBuilder: Preview.Builder) {
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(captureCallback)
    }

    fun capabilitiesOf(camera: Camera): ManualCapabilities {
        // Neue Bindung = frische Kamera ohne gesetzte Optionen.
        manualExposureActive = false
        val info = Camera2CameraInfo.from(camera.cameraInfo)
        val available = info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?: IntArray(0)
        val manualSensor =
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in available

        val exposureState = camera.cameraInfo.exposureState
        val exposureCompensation = if (exposureState.isExposureCompensationSupported) {
            ExposureCompensation(
                minIndex = exposureState.exposureCompensationRange.lower,
                maxIndex = exposureState.exposureCompensationRange.upper,
                stepEv = exposureState.exposureCompensationStep.toFloat(),
            )
        } else {
            null
        }

        val isoStops = if (manualSensor) {
            info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                ?.let { isoStopsWithin(it.lower, it.upper) }
        } else {
            null
        }
        val exposureStops = if (manualSensor) {
            info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                ?.let { exposureStopsWithin(it.lower, it.upper) }
        } else {
            null
        }

        val awbModes = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            ?.toSet()
            .orEmpty()
        val whiteBalanceModes = WhiteBalance.entries.filter { it.toCamera2() in awbModes }
            .takeIf { it.size >= 2 }

        // 0 heisst Fixfokus — dann gibt es nichts zu verstellen.
        val minFocus = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        val afModes = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            ?: IntArray(0)
        val minFocusDiopters = minFocus
            ?.takeIf { manualSensor && it > 0f && CaptureRequest.CONTROL_AF_MODE_OFF in afModes }

        return ManualCapabilities(
            exposureCompensation = exposureCompensation,
            isoStops = isoStops.orEmpty(),
            exposureTimeStopsNs = exposureStops.orEmpty(),
            whiteBalanceModes = whiteBalanceModes.orEmpty(),
            minFocusDiopters = minFocusDiopters,
        )
    }

    /**
     * Wendet [settings] an. Ersetzt jedes Mal *alle* zuvor gesetzten Camera2-Optionen —
     * "zurueck auf Auto" ist also einfach ein Aufruf ohne die entsprechende Option.
     *
     * Die zurueckgegebenen Futures werden bewusst nicht abgewartet: beim Ziehen am Regler
     * kommen viele Aufrufe kurz hintereinander, und jeder neue bricht den vorigen ab. Das
     * ist gewollt — es zaehlt nur der letzte Wert. Einzige Ausnahme ist der Rueckweg von
     * manueller Belichtung zur Automatik (siehe unten).
     */
    fun apply(camera: Camera, settings: ManualSettings, capabilities: ManualCapabilities) {
        if (capabilities.exposureCompensation != null) {
            camera.cameraControl.setExposureCompensationIndex(settings.exposureCompensation)
        }

        val control = Camera2CameraControl.from(camera.cameraControl)
        val generation = ++applyGeneration
        val leavingManualExposure = manualExposureActive && !settings.isManualExposure
        manualExposureActive = settings.isManualExposure

        val iso = meteredIso
        val exposureNs = meteredExposureNs
        if (leavingManualExposure && iso != null && exposureNs != null) {
            // Rueckweg in zwei Schritten: erst die letzten Werte der Automatik manuell
            // setzen, dann die Automatik einschalten. Manche Kameras (verifiziert: die
            // Emulator-Kamera) regeln danach nur noch die Belichtungszeit und behalten den
            // zuletzt manuellen ISO-Wert — nach ISO 1000 blieb das Bild dauerhaft
            // ueberbelichtet, obwohl die Automatik "eingeschwungen" meldete.
            control.setCaptureRequestOptions(
                buildOptions(settings, capabilities, restoreExposure = iso to exposureNs),
            ).addListener(
                {
                    // Nicht ueberschreiben, falls inzwischen ein neuerer Wert kam.
                    if (generation == applyGeneration) {
                        control.setCaptureRequestOptions(buildOptions(settings, capabilities))
                    }
                },
                mainExecutor,
            )
        } else {
            control.setCaptureRequestOptions(buildOptions(settings, capabilities))
        }
    }

    private fun buildOptions(
        settings: ManualSettings,
        capabilities: ManualCapabilities,
        restoreExposure: Pair<Int, Long>? = null,
    ): CaptureRequestOptions = CaptureRequestOptions.Builder().apply {
        val manualExposure: Pair<Int, Long>? = when {
            restoreExposure != null -> restoreExposure
            settings.isManualExposure -> {
                // Camera2 kennt nur "Automatik an" oder "beides manuell". Ist nur ein Wert
                // gesetzt, kommt der andere von der letzten Messung — so springt die
                // Helligkeit beim Umschalten nicht.
                val iso = settings.iso
                    ?: meteredIso
                    ?: capabilities.isoStops.nearestTo(FALLBACK_ISO)
                    ?: FALLBACK_ISO
                val exposureNs = settings.exposureTimeNs
                    ?: meteredExposureNs
                    ?: capabilities.exposureTimeStopsNs.nearestTo(FALLBACK_EXPOSURE_NS)
                    ?: FALLBACK_EXPOSURE_NS
                iso to exposureNs
            }
            else -> null
        }
        if (manualExposure != null) {
            setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, manualExposure.first)
            setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, manualExposure.second)
        }
        if (settings.whiteBalance != WhiteBalance.AUTO) {
            setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, settings.whiteBalance.toCamera2())
        }
        settings.focusDiopters?.let { diopters ->
            setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, diopters)
        }
    }.build()
}

private fun WhiteBalance.toCamera2(): Int = when (this) {
    WhiteBalance.AUTO -> CaptureRequest.CONTROL_AWB_MODE_AUTO
    WhiteBalance.DAYLIGHT -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
    WhiteBalance.CLOUDY -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
    WhiteBalance.SHADE -> CaptureRequest.CONTROL_AWB_MODE_SHADE
    WhiteBalance.INCANDESCENT -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
    WhiteBalance.FLUORESCENT -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
}
