package com.florianhaeglsperger.photocoach.capture

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.florianhaeglsperger.photocoach.domain.model.FaceRect
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
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
)

/**
 * Android-Implementierung.
 *
 * Stand Phase 1 (teilweise):
 *  - `faces` — echt, ueber ML Kit Face Detection (on-device, gebuendeltes Modell)
 *  - `horizonTiltDegrees` — echt, aus dem Schwerkraft-Sensor (siehe [HorizonSensor])
 *  - `saliencyRegions` — noch leer. Braucht ein TFLite-Modell; welches, ist noch offen
 *    (Planung/Plan-zur-Umsetzung.md 11 und Planung/ML-Architektur.md 6.1).
 *
 * Bewusst leer gelassen statt mit Platzhalter-Werten gefuellt: eine Regel, die auf
 * erfundenen Saliency-Punkten rechnet, wuerde plausibel aussehen und waere trotzdem falsch.
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

        val faces = if (mediaImage == null) {
            emptyList()
        } else {
            detectFaces(InputImage.fromMediaImage(mediaImage, rotation))
                .map { it.toFaceRect(uprightWidth, uprightHeight) }
        }

        return FrameAnalysis(
            horizonTiltDegrees = frame.tiltDegrees,
            saliencyRegions = emptyList(),
            faces = faces,
            // imageInfo.timestamp ist in Nanosekunden seit Boot.
            timestampMs = frame.image.imageInfo.timestamp / 1_000_000L,
            aspectRatio = uprightWidth.toFloat() / uprightHeight.toFloat(),
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
     * Bei Timeout oder Fehler lieber keine Gesichter melden als die ganze Pipeline
     * abreissen zu lassen: ein einzelnes verlorenes Frame faellt bei 10 Hz nicht auf.
     */
    private fun detectFaces(input: InputImage): List<Face> = try {
        Tasks.await(detector.process(input), DETECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    } catch (_: Exception) {
        emptyList()
    }

    private companion object {
        const val MIN_FACE_SIZE = 0.1f
        const val DETECT_TIMEOUT_MS = 500L

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
