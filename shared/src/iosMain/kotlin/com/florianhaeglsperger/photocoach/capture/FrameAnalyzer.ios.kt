package com.florianhaeglsperger.photocoach.capture

import com.florianhaeglsperger.photocoach.domain.model.FaceRect
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import com.florianhaeglsperger.photocoach.domain.model.SaliencyPoint
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.Foundation.NSError
import platform.Vision.VNDetectFaceRectanglesRequest
import platform.Vision.VNDetectHorizonRequest
import platform.Vision.VNFaceObservation
import platform.Vision.VNGenerateAttentionBasedSaliencyImageRequest
import platform.Vision.VNHorizonObservation
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNSaliencyImageObservation
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * iOS-Frame: haelt den rohen [CVPixelBufferRef] plus den Zeitstempel des Frames.
 *
 * Bewusst ein duenner Wrapper, analog zu androidMain — kein Kopieren der Bilddaten.
 * Der Buffer muss waehrend des `analyze()`-Aufrufs gueltig bleiben; wer ihn erzeugt hat,
 * gibt ihn danach frei.
 *
 * Weil hier ein *Bild* steht und keine Kamera, laesst sich der Analyzer vollstaendig mit
 * Standbildern im Simulator testen — siehe `FrameAnalyzerTest` in iosTest.
 */
@OptIn(ExperimentalForeignApi::class)
actual class CameraFrame(
    internal val pixelBuffer: CVPixelBufferRef,
    internal val timestampMs: Long = 0L,
)

/**
 * iOS-Implementierung auf Basis des Vision-Frameworks (Plan 3.1).
 *
 * Alle drei Requests laufen on-device und ohne eigenes Modell — auf iOS ist damit auch
 * `saliencyRegions` schon echt, waehrend Android dafuer noch auf die TFLite-Modell-
 * Entscheidung wartet (Plan 11).
 *
 * Gegenueber Android gibt es einen inhaltlichen Unterschied beim Horizont:
 * `VNDetectHorizonRequest` arbeitet auf dem *Bild* und erkennt deshalb auch einen schiefen
 * Horizont bei gerade gehaltenem Geraet — der Fall, den die Sensor-Loesung auf Android
 * bewusst nicht abdeckt (Plan 3.3).
 */
@OptIn(ExperimentalForeignApi::class)
actual class FrameAnalyzer actual constructor() {

    // Request-Objekte sind wiederverwendbar (nur der Handler ist pro Bild neu) — einmal
    // anlegen spart Allokationen bei ~10 Analysen pro Sekunde.
    private val horizonRequest = VNDetectHorizonRequest()
    private val saliencyRequest = VNGenerateAttentionBasedSaliencyImageRequest()
    private val faceRequest = VNDetectFaceRectanglesRequest()

    actual fun analyze(frame: CameraFrame): FrameAnalysis {
        val handler = VNImageRequestHandler(
            cVPixelBuffer = frame.pixelBuffer,
            options = emptyMap<Any?, Any?>(),
        )

        // Alle drei Requests in einem Durchlauf: Vision teilt sich dabei die Vorverarbeitung
        // des Bildes, drei getrennte performRequests-Aufrufe waeren deutlich teurer.
        val succeeded = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            handler.performRequests(
                listOf(horizonRequest, saliencyRequest, faceRequest),
                error.ptr,
            )
        }

        // Vision normiert alle Koordinaten auf den Pixel-Buffer, also stammt auch das
        // Seitenverhaeltnis von dort — anders als auf Android ist hier nichts zu drehen,
        // der Buffer liegt bereits so, wie die Koordinaten ihn meinen.
        val aspectRatio = CVPixelBufferGetWidth(frame.pixelBuffer).toFloat() /
            CVPixelBufferGetHeight(frame.pixelBuffer).toFloat()

        if (!succeeded) {
            // Ein einzelnes fehlgeschlagenes Frame darf die Pipeline nicht abreissen —
            // bei ~10 Hz faellt es nicht auf. Zeitstempel trotzdem durchreichen, damit
            // oben sichtbar bleibt, dass Frames ankommen.
            return FrameAnalysis(
                horizonTiltDegrees = null,
                saliencyRegions = emptyList(),
                faces = emptyList(),
                timestampMs = frame.timestampMs,
                aspectRatio = aspectRatio,
            )
        }

        return FrameAnalysis(
            horizonTiltDegrees = horizonRequest.readTiltDegrees(),
            saliencyRegions = saliencyRequest.readSaliencyPoints(),
            faces = faceRequest.readFaces(),
            timestampMs = frame.timestampMs,
            aspectRatio = aspectRatio,
        )
    }

    actual fun close() = Unit

    /**
     * `VNHorizonObservation.angle` ist der Rollwinkel des erkannten Horizonts im Bogenmass.
     *
     * Wird kein Horizont gefunden (gleichmaessige Flaeche, Innenraum), ist die Ergebnisliste
     * leer — dann `null`, nicht 0. Das ist ein Unterschied, der zaehlt: 0 hiesse "gerade",
     * null heisst "nicht bestimmbar", und eine Regel darf daraus nichts ableiten.
     */
    private fun VNDetectHorizonRequest.readTiltDegrees(): Float? {
        val observation = results()?.firstOrNull() as? VNHorizonObservation ?: return null
        return horizonAngleToDegrees(observation.angle)
    }

    /**
     * Wandelt Visions Saliency-Heatmap in normierte Punkte um.
     *
     * Die Heatmap ist ein kleiner Graustufen-Buffer (typisch 68x68, ein Float pro Pixel).
     * Sie 1:1 in Punkte zu uebersetzen waere unbrauchbar — mehrere tausend Punkte pro Frame,
     * bei 10 Hz. Stattdessen wird sie auf ein grobes Raster heruntergerechnet und nur
     * behalten, was deutlich ueber dem Mittel liegt.
     *
     * Das Ergebnis ist genau das, was die Regeln brauchen: wo die Aufmerksamkeit liegt
     * (RuleOfThirds) und wie sie sich ueber das Bild verteilt (DeadSpace).
     */
    private fun VNGenerateAttentionBasedSaliencyImageRequest.readSaliencyPoints(): List<SaliencyPoint> {
        val observation = results()?.firstOrNull() as? VNSaliencyImageObservation ?: return emptyList()
        return observation.pixelBuffer?.let { readHeatmap(it) } ?: emptyList()
    }

    private fun VNDetectFaceRectanglesRequest.readFaces(): List<FaceRect> =
        results()
            ?.filterIsInstance<VNFaceObservation>()
            ?.map { it.toFaceRect() }
            ?: emptyList()
}

/**
 * Vision liefert normierte Koordinaten mit Ursprung **unten links**, [FaceRect] erwartet
 * **oben links** (wie Bildschirm- und ML-Kit-Koordinaten auf Android).
 *
 * Diese Umrechnung ist die klassische Fehlerquelle beim Vision-Framework: ohne sie sitzt
 * jedes Gesicht vertikal gespiegelt, was eine Portraet-Regel exakt falsch herum korrigieren
 * wuerde.
 */
@OptIn(ExperimentalForeignApi::class)
private fun VNFaceObservation.toFaceRect(): FaceRect = boundingBox.useContents {
    visionBoxToFaceRect(
        originX = origin.x,
        originY = origin.y,
        width = size.width,
        height = size.height,
    )
}

/**
 * Die eigentliche Umrechnung, bewusst als pure Funktion herausgezogen.
 *
 * Sie ist der einzige Teil mit echter Mathematik und damit der einzige, der falsch sein
 * kann — so laesst sie sich ohne Kamera, ohne Bild und ohne Vision testen (siehe
 * `VisionCoordinatesTest`). Ein Vorzeichenfehler faellt hier sonst erst auf, wenn die App
 * ein Portraet in die exakt falsche Richtung korrigiert.
 */
internal fun visionBoxToFaceRect(
    originX: Double,
    originY: Double,
    width: Double,
    height: Double,
): FaceRect = FaceRect(
    left = originX.toFloat().coerceIn(0f, 1f),
    top = (1.0 - (originY + height)).toFloat().coerceIn(0f, 1f),
    right = (originX + width).toFloat().coerceIn(0f, 1f),
    bottom = (1.0 - originY).toFloat().coerceIn(0f, 1f),
)

/** Kantenlaenge des groben Rasters, auf das die Heatmap heruntergerechnet wird. */
private const val SALIENCY_GRID = 12

/**
 * Anteil des Maximalwerts, ab dem eine Rasterzelle als "hier schaut jemand hin" gilt.
 * Relativ zum Maximum, nicht absolut: Vision skaliert die Heatmap je nach Bild anders.
 */
private const val SALIENCY_THRESHOLD = 0.45f

@OptIn(ExperimentalForeignApi::class)
private fun readHeatmap(buffer: CVPixelBufferRef): List<SaliencyPoint> {
    CVPixelBufferLockBaseAddress(buffer, READ_ONLY)
    try {
        val base = CVPixelBufferGetBaseAddress(buffer) ?: return emptyList()
        val width = CVPixelBufferGetWidth(buffer).toInt()
        val height = CVPixelBufferGetHeight(buffer).toInt()
        if (width == 0 || height == 0) return emptyList()

        val floats = base.reinterpret<FloatVar>()
        // bytesPerRow kann groesser als width * 4 sein (Zeilen-Padding) — deshalb aus dem
        // Buffer lesen statt zu rechnen.
        val floatsPerRow = CVPixelBufferGetBytesPerRow(buffer).toInt() / 4

        // Heatmap auf SALIENCY_GRID x SALIENCY_GRID mitteln.
        val cells = FloatArray(SALIENCY_GRID * SALIENCY_GRID)
        val counts = IntArray(SALIENCY_GRID * SALIENCY_GRID)
        for (y in 0 until height) {
            val cellY = y * SALIENCY_GRID / height
            for (x in 0 until width) {
                val cellX = x * SALIENCY_GRID / width
                val index = cellY * SALIENCY_GRID + cellX
                cells[index] += floats[y * floatsPerRow + x]
                counts[index]++
            }
        }

        var max = 0f
        for (i in cells.indices) {
            if (counts[i] > 0) cells[i] /= counts[i]
            if (cells[i] > max) max = cells[i]
        }
        if (max <= 0f) return emptyList()

        val points = mutableListOf<SaliencyPoint>()
        for (cellY in 0 until SALIENCY_GRID) {
            for (cellX in 0 until SALIENCY_GRID) {
                val weight = cells[cellY * SALIENCY_GRID + cellX] / max
                if (weight < SALIENCY_THRESHOLD) continue
                points += SaliencyPoint(
                    // Mitte der Zelle, nicht ihre Ecke.
                    x = (cellX + 0.5f) / SALIENCY_GRID,
                    // Vision-Heatmaps liegen wie das Bild: Zeile 0 ist oben. Anders als bei
                    // den Bounding-Boxen ist hier also KEINE Spiegelung noetig.
                    y = (cellY + 0.5f) / SALIENCY_GRID,
                    weight = weight,
                )
            }
        }
        return points
    } finally {
        CVPixelBufferUnlockBaseAddress(buffer, READ_ONLY)
    }
}

/** `kCVPixelBufferLock_ReadOnly` — als Konstante, weil der Name nicht ueberall exportiert wird. */
private const val READ_ONLY = 1uL

/**
 * Rechnet den Rollwinkel aus `VNHorizonObservation.angle` (Bogenmass) in Grad um.
 *
 * Bewusst als pure Funktion herausgezogen — wie [visionBoxToFaceRect] und `tiltFromGravity`
 * auf Android: Umrechnungsfaktor und Vorzeichen sind der einzige Teil, der falsch sein
 * kann, und so laesst er sich ohne Vision und ohne Geraet pruefen.
 *
 * Rundet auf eine Nachkommastelle, damit die Werte zur Android-Seite passen
 * (`HorizonSensor.tiltFromGravity` tut dasselbe). Die geteilten Regeln in `domain/` sehen
 * Werte von beiden Plattformen — sie sollten sich nicht darin unterscheiden, wie fein
 * aufgeloest sie ankommen.
 *
 * Vorzeichen folgt derselben Konvention wie Android: positiv = rechte Seite tiefer.
 */
internal fun horizonAngleToDegrees(radians: Double): Float {
    val degrees = (radians * 180.0 / PI).toFloat()
    return (degrees * 10f).roundToInt() / 10f
}
