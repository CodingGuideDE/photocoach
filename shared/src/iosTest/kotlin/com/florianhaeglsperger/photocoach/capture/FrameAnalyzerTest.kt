package com.florianhaeglsperger.photocoach.capture

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ende-zu-Ende-Test des iOS-`FrameAnalyzer` (Plan 3.1) — im Simulator, ohne Kamera.
 *
 * Moeglich, weil Vision auf Bildern arbeitet: die Testbilder kommen aus [createTestFrame]
 * statt aus `AVCaptureVideoDataOutput`, alles dahinter ist identisch zur Produktion.
 */
@OptIn(ExperimentalForeignApi::class)
class FrameAnalyzerTest {

    private val analyzer = FrameAnalyzer()

    @AfterTest
    fun tearDown() = analyzer.close()

    // ACHTUNG: Hier steht bewusst KEIN Test der Art "Motiv links -> Saliency links".
    //
    // Nachgemessen am 27.08.2026: Visions Saliency-Modelle laufen im Simulator durch und
    // melden Erfolg, werten den Bildinhalt aber nicht aus. Einfarbige Flaeche, helles
    // Rechteck und Streifenmuster mit maximalem Kontrast liefern dieselbe Heatmap
    // (68x68, max=0.36, identische Verteilung) — attention- wie objectness-basiert.
    // Betrifft beide Eingabewege (CVPixelBuffer und CGImage) und ist unabhaengig von
    // IOSurface-Backing.
    //
    // Ein Positionstest waere hier also gruen oder rot je nach Zufall und wuerde nichts
    // ueber unseren Code aussagen. Die inhaltliche Richtigkeit der Saliency muss auf
    // echter Hardware geprueft werden (siehe shared/src/iosMain/README.md).
    //
    // Was hier trotzdem sinnvoll prueftbar ist und unten auch geprueft wird: dass die
    // Verarbeitungskette haelt — Buffer rein, FrameAnalysis raus, Koordinaten normiert,
    // Zeitstempel durchgereicht, mehrfach aufrufbar.

    @Test
    fun `alle Saliency-Koordinaten liegen im normierten Bereich`() {
        val frame = createTestFrame { context ->
            context.fill(640.0, 480.0, gray = 0.1)
            context.drawSubject(CGRectMake(200.0, 100.0, 200.0, 200.0))
        }

        val result = analyzer.analyze(frame)

        result.saliencyRegions.forEach { point ->
            assertTrue(point.x in 0f..1f, "x ausserhalb 0..1: ${point.x}")
            assertTrue(point.y in 0f..1f, "y ausserhalb 0..1: ${point.y}")
            assertTrue(point.weight in 0f..1f, "weight ausserhalb 0..1: ${point.weight}")
        }
    }

    @Test
    fun `einfarbiges Bild liefert keine Gesichter und stuerzt nicht ab`() {
        val frame = createTestFrame { context -> context.fill(640.0, 480.0, gray = 0.5) }

        val result = analyzer.analyze(frame)

        assertEquals(0, result.faces.size)
    }

    @Test
    fun `Zeitstempel wird unveraendert durchgereicht`() {
        val buffer = createTestFrame { context -> context.fill(640.0, 480.0, gray = 0.5) }
        val frame = CameraFrame(buffer.pixelBuffer, timestampMs = 123_456L)

        assertEquals(123_456L, analyzer.analyze(frame).timestampMs)
    }

    @Test
    fun `Analyzer laesst sich mehrfach hintereinander aufrufen`() {
        // Die Request-Objekte werden wiederverwendet — hier wuerde auffallen, wenn Vision
        // damit ein Problem haette (alte Ergebnisse, Zustand zwischen Aufrufen).
        val frame = createTestFrame { context ->
            context.fill(640.0, 480.0, gray = 0.05)
            context.drawSubject(CGRectMake(120.0, 160.0, 160.0, 160.0))
        }

        val first = analyzer.analyze(frame)
        val second = analyzer.analyze(frame)

        assertEquals(first.saliencyRegions.size, second.saliencyRegions.size)
    }
}
