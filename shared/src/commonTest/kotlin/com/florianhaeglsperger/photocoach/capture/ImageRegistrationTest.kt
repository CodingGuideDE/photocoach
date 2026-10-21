package com.florianhaeglsperger.photocoach.capture

import com.florianhaeglsperger.photocoach.capture.saliency.LumaGrid
import kotlin.math.exp
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prueft die Versatzmessung fuer den Wechsel Haupt- ↔ Telekamera an synthetischen Szenen.
 *
 * Die Szene ist eine Funktion auf kontinuierlichen Koordinaten (Summe weicher Flecken), damit
 * sich auch Verschiebungen um Bruchteile eines Pixels und Vergroesserungen exakt erzeugen
 * lassen — so, wie sie beim Objektivwechsel tatsaechlich auftreten.
 */
class ImageRegistrationTest {

    private val size = 64
    private var nextNoiseSeed = 100

    /** Zufaellige, aber reproduzierbare Szene: 40 weiche Flecken auf 0..1-Koordinaten. */
    private class Scene(seed: Int) {
        private val random = Random(seed)
        private val blobs = List(40) {
            doubleArrayOf(
                random.nextDouble(), // x
                random.nextDouble(), // y
                0.02 + random.nextDouble() * 0.08, // Radius
                random.nextDouble() * 2 - 1, // Helligkeit
            )
        }

        fun at(x: Double, y: Double): Double = 0.5 + 0.25 * blobs.sumOf { (bx, by, r, a) ->
            val d2 = (x - bx) * (x - bx) + (y - by) * (y - by)
            a * exp(-d2 / (2 * r * r))
        }
    }

    /**
     * Die Szene, wie die Kamera sie sieht: um [zoom] um die Mitte vergroessert, dann um
     * ([dx], [dy]) (Anteil der Bildkante) verschoben, mit [gain]/[offset] in der Helligkeit
     * und leichtem Rauschen.
     */
    private fun capture(
        scene: Scene,
        dx: Double = 0.0,
        dy: Double = 0.0,
        zoom: Double = 1.0,
        gain: Double = 1.0,
        offset: Double = 0.0,
        // Jedes Bild eigenes Rauschen, wie bei zwei echten Kamerabildern. Gleiches Rauschen in
        // beiden Bildern waere ein gemeinsames Muster ohne Verschiebung und verfaelschte den Test.
        noiseSeed: Int = nextNoiseSeed++,
    ): LumaGrid {
        val noise = Random(noiseSeed)
        return LumaGrid(size, size, FloatArray(size * size) { i ->
            val x = (i % size + 0.5) / size
            val y = (i / size + 0.5) / size
            // Inhalt bei p erscheint bei 0,5 + zoom · (p − 0,5) + d — also umgekehrt nachschlagen.
            val sx = 0.5 + (x - dx - 0.5) / zoom
            val sy = 0.5 + (y - dy - 0.5) / zoom
            (gain * scene.at(sx, sy) + offset + noise.nextDouble() * 0.01).toFloat()
        })
    }

    /** Eine Messung auf ein Viertel Pixel genau. */
    private val tolerance = 0.25f / size

    @Test
    fun `ganzzahlige Verschiebung wird gefunden`() {
        val scene = Scene(1)
        val shift = assertNotNull(
            ImageRegistration.estimateShift(capture(scene), capture(scene, dx = 3.0 / size, dy = -2.0 / size)),
        )

        assertTrue(shift.isReliable, "$shift")
        assertEquals(3f / size, shift.dx, tolerance)
        assertEquals(-2f / size, shift.dy, tolerance)
    }

    @Test
    fun `Verschiebung um Bruchteile eines Pixels wird gefunden`() {
        val scene = Scene(2)
        val shift = assertNotNull(
            ImageRegistration.estimateShift(capture(scene), capture(scene, dx = -0.023, dy = 0.031)),
        )

        assertTrue(shift.isReliable, "$shift")
        assertEquals(-0.023f, shift.dx, tolerance)
        assertEquals(0.031f, shift.dy, tolerance)
    }

    @Test
    fun `andere Helligkeit der neuen Kamera stoert nicht`() {
        // Haupt- und Telekamera belichten nie exakt gleich.
        val scene = Scene(3)
        val shift = assertNotNull(
            ImageRegistration.estimateShift(
                capture(scene),
                capture(scene, dx = 0.04, gain = 0.6, offset = 0.15),
            ),
        )

        assertTrue(shift.isReliable, "$shift")
        assertEquals(0.04f, shift.dx, tolerance)
        assertEquals(0f, shift.dy, tolerance)
    }

    @Test
    fun `Zoomunterschied zwischen den Bildern wird herausgerechnet`() {
        // Beim Zoomen aendert sich der Zoom auch zwischen zwei aufeinanderfolgenden Bildern.
        val scene = Scene(4)
        val before = capture(scene, zoom = 1.0)
        val after = capture(scene, zoom = 1.06, dx = 0.03, dy = 0.02)

        val shift = assertNotNull(ImageRegistration.estimateShift(before, after, zoomChange = 1.06f))

        assertTrue(shift.isReliable, "$shift")
        assertEquals(0.03f, shift.dx, tolerance)
        assertEquals(0.02f, shift.dy, tolerance)
    }

    @Test
    fun `gleiches Bild ergibt keinen Versatz mit hoher Sicherheit`() {
        val scene = Scene(5)
        val shift = assertNotNull(ImageRegistration.estimateShift(capture(scene), capture(scene)))

        assertEquals(0f, shift.dx, tolerance)
        assertEquals(0f, shift.dy, tolerance)
        assertTrue(shift.confidence > 0.5f, "Sicherheit ${shift.confidence}")
        assertTrue(shift.isReliable)
    }

    @Test
    fun `zwei verschiedene Szenen ergeben keine verlaessliche Messung`() {
        val shift = assertNotNull(ImageRegistration.estimateShift(capture(Scene(6)), capture(Scene(7))))
        assertFalse(shift.isReliable, "$shift")
    }

    @Test
    fun `strukturlose Flaeche ergibt keine Messung`() {
        val flat = LumaGrid(size, size, FloatArray(size * size) { 0.5f })
        assertNull(ImageRegistration.estimateShift(flat, flat))
    }

    @Test
    fun `zu grosser Versatz gilt nicht als Objektivwechsel`() {
        val scene = Scene(8)
        val shift = assertNotNull(ImageRegistration.estimateShift(capture(scene), capture(scene, dx = 0.3)))
        assertFalse(shift.isReliable, "$shift")
    }
}
