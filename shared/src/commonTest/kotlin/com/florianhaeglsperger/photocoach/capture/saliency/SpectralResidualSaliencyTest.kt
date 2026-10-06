package com.florianhaeglsperger.photocoach.capture.saliency

import com.florianhaeglsperger.photocoach.domain.frame
import com.florianhaeglsperger.photocoach.domain.subject.DefaultSubjectResolver
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prueft, dass die Saliency den **Bildinhalt** auswertet — genau das, was auf iOS im
 * Simulator nicht belegbar ist (Plan 12). Hier geht es, weil das Verfahren pures Kotlin ist:
 * synthetische Bilder rein, Punkte raus.
 *
 * Die Bilder bekommen ein leichtes Rauschen, wie es jeder echte Sensor liefert. Ohne das
 * pruefte man einen Fall, der in der Kamera nicht vorkommt.
 */
class SpectralResidualSaliencyTest {

    private val size = SpectralResidualSaliency.INPUT_SIZE

    private fun image(seed: Int = 1, pixel: (x: Int, y: Int) -> Float): LumaGrid {
        val random = Random(seed)
        return LumaGrid(size, size, FloatArray(size * size) {
            pixel(it % size, it / size) + random.nextFloat() * 0.02f
        })
    }

    /** Heller Fleck mit Radius [radius] (Pixel) auf mittelgrauem Grund. */
    private fun blobAt(cx: Int, cy: Int, radius: Int = 5) = image { x, y ->
        if ((x - cx) * (x - cx) + (y - cy) * (y - cy) < radius * radius) 0.9f else 0.3f
    }

    private fun subjectOf(luma: LumaGrid) =
        DefaultSubjectResolver.resolve(frame(saliency = SpectralResidualSaliency.analyze(luma), aspectRatio = 1f))

    @Test
    fun `ein Fleck am linken Drittel wird dort gefunden`() {
        val subject = assertNotNull(subjectOf(blobAt(cx = 21, cy = 32)))

        assertEquals(1f / 3f, subject.x, 0.06f)
        assertEquals(0.5f, subject.y, 0.06f)
    }

    @Test
    fun `wandert der Fleck - wandert der Motivpunkt mit`() {
        // Die Gegenprobe, die im iOS-Simulator scheitert: gleiches Motiv, andere Stelle.
        val left = assertNotNull(subjectOf(blobAt(cx = 16, cy = 40)))
        val right = assertNotNull(subjectOf(blobAt(cx = 48, cy = 20)))

        assertTrue(left.x < 0.35f && right.x > 0.65f, "links=${left.x}, rechts=${right.x}")
        assertTrue(left.y > 0.55f && right.y < 0.4f, "links=${left.y}, rechts=${right.y}")
    }

    @Test
    fun `dunkles Motiv vor Helligkeitsverlauf wird gefunden`() {
        // Typische Aussenszene: Himmel wird nach unten dunkler, Motiv unten rechts.
        val luma = image { x, y ->
            if ((x - 44) * (x - 44) + (y - 44) * (y - 44) < 36) 0.1f else 0.9f - y / 90f
        }

        val subject = assertNotNull(subjectOf(luma))

        assertEquals(44f / 64f, subject.x, 0.08f)
        assertEquals(44f / 64f, subject.y, 0.08f)
    }

    @Test
    fun `Baum am Horizont zieht den Motivpunkt zu sich`() {
        // Harte Kanten ohne Rauschen erzeugten beim Entwickeln ein Geistermuster weit weg
        // vom Objekt (siehe AMPLITUDE_FLOOR) — deshalb dieser Fall bewusst ohne Rauschen.
        val luma = LumaGrid(size, size, FloatArray(size * size) {
            val x = it % size
            val y = it / size
            if (x in 10..15 && y in 20..40) 0.1f else if (y < 32) 0.85f else 0.3f
        })

        val subject = assertNotNull(subjectOf(luma))

        assertTrue(subject.x < 0.3f, "Motiv muesste beim Baum links liegen, x=${subject.x}")
    }

    @Test
    fun `gleichmaessige Textur ergibt kein Motiv`() {
        val luma = image { x, y -> 0.5f + 0.3f * sin(x * 0.9f) * sin(y * 0.7f) }

        assertNull(subjectOf(luma))
    }

    @Test
    fun `zwei Flecken links und rechts ergeben kein Motiv in der Mitte`() {
        val luma = image { x, y ->
            val left = (x - 16) * (x - 16) + (y - 32) * (y - 32) < 25
            val right = (x - 48) * (x - 48) + (y - 32) * (y - 32) < 25
            if (left || right) 0.9f else 0.3f
        }

        val subject = subjectOf(luma)

        // Entweder einer der beiden oder gar keiner — aber nicht die leere Mitte.
        if (subject != null) assertTrue(abs(subject.x - 0.5f) > 0.15f, "x=${subject.x}")
    }

    @Test
    fun `strukturlose Flaeche liefert gar keine Punkte`() {
        // Objektivdeckel, weisse Wand: normiert aufs Maximum saehe Rauschen sonst aus wie Inhalt.
        val luma = LumaGrid(size, size, FloatArray(size * size) { 0.5f + (it % 3) * 0.001f })

        assertTrue(SpectralResidualSaliency.analyze(luma).isEmpty())
    }

    @Test
    fun `Punkte haben dieselbe Form wie auf iOS`() {
        val points = SpectralResidualSaliency.analyze(blobAt(cx = 40, cy = 24))

        assertTrue(points.isNotEmpty())
        assertEquals(1f, points.maxOf { it.weight }, 0.0001f)
        assertTrue(points.all { it.weight >= SpectralResidualSaliency.THRESHOLD })
        assertTrue(points.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun `Helligkeitssprung zwischen oberem und unterem Rand erzeugt keine Randpunkte`() {
        // Die FFT sieht das Bild periodisch: heller oberer Rand stoesst an dunklen unteren.
        // Ohne Randstreifen saesse dort ein Scheinmotiv.
        val luma = image { _, y -> if (y < 40) 0.85f else 0.25f }

        val points = SpectralResidualSaliency.analyze(luma)
        val cell = 1f / SpectralResidualSaliency.GRID

        assertTrue(points.none { it.y < cell || it.y > 1f - cell }, "Randpunkte: $points")
    }
}
