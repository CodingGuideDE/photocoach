package com.florianhaeglsperger.photocoach.capture

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.florianhaeglsperger.photocoach.capture.saliency.LumaGrid
import com.florianhaeglsperger.photocoach.capture.saliency.SpectralResidualSaliency
import com.florianhaeglsperger.photocoach.domain.model.FaceRect
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import com.florianhaeglsperger.photocoach.domain.model.SaliencyPoint
import com.florianhaeglsperger.photocoach.domain.model.mirroredHorizontally
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.TimeUnit

/**
 * Android-Frame: haelt das rohe [ImageProxy] aus dem CameraX-`ImageAnalysis`-Use-Case,
 * plus die Geraete-Neigung zum Zeitpunkt des Frames.
 *
 * Bewusst ein duenner Wrapper statt einer eigenen Kopie der Bilddaten — ein Frame pro
 * Analyse zu kopieren waere bei 5-10 Hz unnoetiger Aufwand. Dafuer gilt: das [ImageProxy]
 * ist nur waehrend des `analyze()`-Aufrufs gueltig, danach schliesst der Aufrufer es
 * wieder (siehe CameraPreview.android.kt). Nichts aus diesem Objekt darf also ueber den
 * Aufruf hinaus festgehalten werden.
 *
 * [tiltDegrees] kommt aus [HorizonSensor] und nicht aus dem Bild — es gehoert trotzdem
 * hierher, weil es den Zustand *zu diesem Frame* beschreibt.
 */
actual class CameraFrame(
    internal val image: ImageProxy,
    internal val tiltDegrees: Float?,
    /**
     * `true`, wenn die Vorschau gespiegelt dargestellt wird (Frontkamera).
     *
     * Die Analyse-Frames kommen roh vom Sensor und sind **nicht** gespiegelt. Damit die
     * Koordinaten in [FrameAnalysis] zu dem passen, was der Nutzer sieht, wird in
     * `analyze()` nachgespiegelt.
     */
    internal val mirrored: Boolean = false,
)

/**
 * Android-Implementierung. Alle drei Felder sind echt:
 *  - `faces` — ML Kit Face Detection (on-device, gebuendeltes Modell)
 *  - `horizonTiltDegrees` — Schwerkraft-Sensor (siehe [HorizonSensor])
 *  - `saliencyRegions` — Spectral Residual auf der Y-Ebene (siehe
 *    [SpectralResidualSaliency]), modellfrei. Ein gelerntes Modell (Plan 11) kann spaeter
 *    hier einspringen, ohne dass sich an den Regeln etwas aendert.
 */
actual class FrameAnalyzer actual constructor() {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            // FAST statt ACCURATE: im Live-Sucher zaehlt die Bildrate mehr als das letzte
            // bisschen Genauigkeit bei der Gesichtsgrenze.
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            // Landmarks/Konturen kosten spuerbar Rechenzeit und werden fuer die
            // Kompositions-Regeln nicht gebraucht — dort geht es nur um Lage und Groesse.
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            // Gesichter unter 10 % der Bildbreite sind fuer Portraet-Regeln irrelevant
            // und kosten nur Suchzeit.
            .setMinFaceSize(MIN_FACE_SIZE)
            .build(),
    )

    @OptIn(ExperimentalGetImage::class)
    actual fun analyze(frame: CameraFrame): FrameAnalysis {
        val mediaImage = frame.image.image
        val rotation = frame.image.imageInfo.rotationDegrees

        // Nach dem Drehen um `rotation` steht das Bild aufrecht — bei 90/270 Grad sind
        // Breite und Hoehe dabei vertauscht. ML Kit liefert Koordinaten in genau diesem
        // aufrechten System, also muss hier dasselbe gelten. Aus denselben Werten kommt
        // auch das Seitenverhaeltnis: es beschreibt das Bild so, wie die normierten
        // Koordinaten es meinen.
        val upright = rotation == 90 || rotation == 270
        val uprightWidth = if (upright) frame.image.height else frame.image.width
        val uprightHeight = if (upright) frame.image.width else frame.image.height

        val faces = when {
            mediaImage == null -> emptyList()
            // Die letzte Erkennung laeuft noch (langsames Geraet) — siehe [pendingLiveTask].
            pendingLiveTask?.isComplete == false -> lastLiveFaces
            else -> {
                val task = detector.process(InputImage.fromMediaImage(mediaImage, rotation))
                val detected = awaitFaces(task, DETECT_TIMEOUT_MS)
                pendingLiveTask = if (detected == null) task else null
                (detected ?: emptyList())
                    .map { it.toFaceRect(uprightWidth, uprightHeight) }
                    // Frontkamera: Vorschau ist gespiegelt, der Analyse-Frame nicht.
                    // Ohne das zeigt jeder Hinweis beim Selfie in die falsche Richtung.
                    .map { if (frame.mirrored) it.mirroredHorizontally() else it }
                    .also { lastLiveFaces = it }
            }
        }

        // Ein Helligkeitsraster fuer beides, Saliency und Struktur — einmal abtasten reicht.
        val luma = uprightLumaOrNull(frame.image)
        // Dieselbe Spiegelung wie bei den Gesichtern — alles muss zu dem passen, was der
        // Nutzer in der (gespiegelten) Frontkamera-Vorschau sieht.
        val saliency = luma?.let(::saliencyOf).orEmpty()
            .map { if (frame.mirrored) it.mirroredHorizontally() else it }
        val structure = luma?.let(ImageStructure::of)
            ?.let { if (frame.mirrored) it.mirroredHorizontally() else it }

        return FrameAnalysis(
            horizonTiltDegrees = frame.tiltDegrees,
            saliencyRegions = saliency,
            faces = faces,
            // imageInfo.timestamp ist in Nanosekunden seit Boot.
            timestampMs = frame.image.imageInfo.timestamp / 1_000_000L,
            aspectRatio = uprightWidth.toFloat() / uprightHeight.toFloat(),
            structure = structure,
        )
    }

    /**
     * Gleiche Bausteine wie [analyze] — derselbe Detektor, dieselbe Saliency —, nur mit einem
     * Bitmap als Quelle. Nicht gespiegelt: das gespeicherte Foto ist es auch nicht (anders
     * als die Frontkamera-Vorschau), und die Koordinaten sollen zu dem passen, was die
     * Vollbild-Ansicht zeigt.
     */
    actual fun analyzeStill(image: ImageBitmap): FrameAnalysis {
        // ImageDecoder liefert ab API 28 gern HARDWARE-Bitmaps. Die liegen nur im
        // Grafikspeicher — getPixels() wirft dort, und ML Kit muesste sie ohnehin kopieren.
        val source = image.asAndroidBitmap()
        val bitmap = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }
        val width = bitmap.width
        val height = bitmap.height

        // Grosszuegiger als im Sucher: ein einzelner Aufruf, auf den der Nutzer ohnehin
        // wartet — und ein Portraet ohne erkanntes Gesicht waere schlicht falsch bewertet.
        val faces = awaitFaces(detector.process(InputImage.fromBitmap(bitmap, 0)), STILL_DETECT_TIMEOUT_MS)
            .orEmpty()
            .map { it.toFaceRect(width, height) }

        val luma = try {
            val pixels = IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }
            lumaGridFromArgb(pixels, width, height, SpectralResidualSaliency.INPUT_SIZE)
        } catch (_: Exception) {
            null
        }

        return FrameAnalysis(
            horizonTiltDegrees = null,
            saliencyRegions = luma?.let(::saliencyOf).orEmpty(),
            faces = faces,
            timestampMs = 0L,
            aspectRatio = width.toFloat() / height.toFloat(),
            structure = luma?.let(ImageStructure::of),
        )
    }

    actual fun close() {
        detector.close()
    }

    /**
     * ML Kit ist Task-basiert, der Contract von [analyze] synchron.
     *
     * Hier bewusst blockierend abgewartet statt den Contract auf `suspend` umzustellen:
     * der Aufruf laeuft ohnehin schon auf dem eigenen Analyse-Thread (weder Main- noch
     * CameraX-Thread), und das Warten ist sogar noetig — der Aufrufer schliesst das
     * [ImageProxy] direkt nach `analyze()`, ML Kit muss also vorher fertig sein.
     *
     * `null` bei Timeout oder Fehler. Im Sucher heisst das: lieber ein Frame ohne Gesichter
     * als eine abgerissene Pipeline — ein einzelnes verlorenes Frame faellt bei 10 Hz nicht auf.
     */
    private fun awaitFaces(task: Task<List<Face>>, timeoutMs: Long): List<Face>? = try {
        Tasks.await(task, timeoutMs, TimeUnit.MILLISECONDS)
    } catch (_: Exception) {
        null
    }

    /**
     * Die Sucher-Erkennung, die beim letzten Frame nicht rechtzeitig fertig wurde — oder
     * `null`, wenn keine aussteht.
     *
     * **Warum:** Ein Timeout bricht nur das *Warten* ab, nicht den Auftrag. ML Kit arbeitet
     * alle Auftraege nacheinander auf einem gemeinsamen Thread ab. Ist ein Geraet langsamer
     * als die Analyse-Taktung (gemessen im Emulator: 1,2-1,6 s pro Erkennung bei ~0,6 s
     * Takt), waechst die Warteschlange ohne Ende — Hinweise kommen immer spaeter, und eine
     * Foto-Bewertung im Viewer ([analyzeStill], derselbe Thread) steht ganz hinten und lief
     * frueher stumm ohne Gesichter in ihren Timeout. Deshalb: solange eine aussteht, keine
     * neue einreihen und die letzten Gesichter weiterverwenden. So liegt aus dem Sucher
     * hoechstens ein Auftrag in der Schlange.
     *
     * Nur vom Analyse-Thread des Suchers beruehrt (CameraX-Executor, einthreadig).
     */
    private var pendingLiveTask: Task<List<Face>>? = null
    private var lastLiveFaces: List<FaceRect> = emptyList()

    /**
     * Wie bei den Gesichtern: lieber ein Frame ohne Saliency und Struktur als eine
     * abgerissene Pipeline. Faellt z. B. an, wenn ein Geraet ein anderes Bildformat als
     * YUV_420_888 liefert.
     */
    private fun uprightLumaOrNull(image: ImageProxy): LumaGrid? = try {
        image.uprightLuma(SpectralResidualSaliency.INPUT_SIZE)
    } catch (_: Exception) {
        null
    }

    private fun saliencyOf(luma: LumaGrid): List<SaliencyPoint> = try {
        SpectralResidualSaliency.analyze(luma)
    } catch (_: Exception) {
        emptyList()
    }

    private companion object {
        const val MIN_FACE_SIZE = 0.1f
        const val DETECT_TIMEOUT_MS = 500L
        const val STILL_DETECT_TIMEOUT_MS = 10_000L

        /** Rechnet die Pixel-Box von ML Kit in normierte 0.0–1.0-Koordinaten um. */
        fun Face.toFaceRect(imageWidth: Int, imageHeight: Int): FaceRect {
            val box = boundingBox
            val width = imageWidth.toFloat()
            val height = imageHeight.toFloat()
            // ML Kit kann Boxen liefern, die leicht ueber den Bildrand hinausragen
            // (Gesicht halb angeschnitten) — fuer die Regeln in domain/ soll aber gelten,
            // dass Koordinaten immer in 0..1 liegen.
            return FaceRect(
                left = (box.left / width).coerceIn(0f, 1f),
                top = (box.top / height).coerceIn(0f, 1f),
                right = (box.right / width).coerceIn(0f, 1f),
                bottom = (box.bottom / height).coerceIn(0f, 1f),
            )
        }
    }
}
