package com.florianhaeglsperger.photocoach.capture

import com.florianhaeglsperger.photocoach.capture.saliency.LumaGrid
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Synthetische Szenen auf dem 64x64-Helligkeitsbild, wie es die Analyse liefert — mit
 * leichtem Sensorrauschen. Inhaltlich am Geraet noch nicht belegt.
 */
class ImageHorizonTest {

    private val n = 64

    private fun image(noise: Float = 0.02f, pixel: (x: Int, y: Int) -> Float): LumaGrid {
        val random = Random(5)
        return LumaGrid(n, n, FloatArray(n * n) { pixel(it % n, it / n) + random.nextFloat() * noise })
    }

    /** Heller Himmel mit leichtem Verlauf ueber dunklerem, texturiertem Boden; Kante bei [horizonRow]. */
    private fun landscape(horizonRow: (x: Int) -> Float) = image { x, y ->
        if (y < horizonRow(x)) {
            0.85f - y / 300f
        } else {
            0.35f + 0.06f * sin(x * 1.3f) * sin(y * 1.1f)
        }
    }

    @Test
    fun `Horizont in der Bildmitte wird gefunden`() {
        val line = assertNotNull(ImageHorizon.detect(landscape { 32f }))

        assertEquals(0.5f, line.centerY, 0.04f)
        assertEquals(0f, line.angleDegrees(aspectRatio = 0.75f), 2f)
    }

    @Test
    fun `Horizont im oberen Drittel wird dort gefunden`() {
        val line = assertNotNull(ImageHorizon.detect(landscape { 21f }))

        assertEquals(1f / 3f, line.centerY, 0.04f)
    }

    @Test
    fun `schiefer Horizont - Winkel wird mitgeliefert`() {
        // Rechts 8 Zeilen tiefer als links.
        val line = assertNotNull(ImageHorizon.detect(landscape { x -> 28f + 8f * x / 63f }))

        val expected = degrees(atan((8.0 / 64.0) / 0.75))
        assertEquals(expected, line.angleDegrees(aspectRatio = 0.75f), 2f)
        assertEquals(true, line.rightY > line.leftY, "rechte Seite muss tiefer liegen")
    }

    @Test
    fun `Schnee unter dunklem Himmel - umgekehrte Helligkeit wird ebenso gefunden`() {
        val line = assertNotNull(ImageHorizon.detect(image { _, y -> if (y < 40) 0.3f else 0.9f }))

        assertEquals(40f / 64f, line.centerY, 0.04f)
    }

    @Test
    fun `gleichmaessige Textur ist kein Horizont`() {
        assertNull(ImageHorizon.detect(image { x, y -> 0.5f + 0.15f * sin(x * 1.3f) * sin(y * 1.1f) }))
    }

    @Test
    fun `einzelnes Objekt ist kein Horizont`() {
        // Dunkler Kasten mitten im hellen Bild — seine Oberkante ist nur ein Drittel breit.
        assertNull(ImageHorizon.detect(image { x, y -> if (x in 22..42 && y in 30..50) 0.1f else 0.8f }))
    }

    @Test
    fun `senkrechte Kante ist kein Horizont`() {
        assertNull(ImageHorizon.detect(image { x, _ -> if (x < 32) 0.2f else 0.8f }))
    }

    @Test
    fun `schwacher Uebergang reicht nicht`() {
        // Nebel: oben und unten fast gleich hell.
        assertNull(ImageHorizon.detect(image { _, y -> if (y < 32) 0.55f else 0.5f }))
    }

    @Test
    fun `Spiegelung tauscht linke und rechte Hoehe`() {
        val line = assertNotNull(ImageHorizon.detect(landscape { x -> 28f + 8f * x / 63f }))
        val mirrored = line.mirroredHorizontally()

        assertEquals(line.leftY, mirrored.rightY)
        assertEquals(line.rightY, mirrored.leftY)
    }

    @Test
    fun `Winkelumrechnung beruecksichtigt das Seitenverhaeltnis`() {
        // Hoehenunterschied 0,1 der Bildhoehe: im Hochformat (3:4) steiler als im Querformat (4:3).
        val line = com.florianhaeglsperger.photocoach.domain.model.HorizonLine(0.45f, 0.55f, 0.2f)

        val portrait = line.angleDegrees(aspectRatio = 0.75f)
        val landscape = line.angleDegrees(aspectRatio = 4f / 3f)

        assertEquals(degrees(atan(0.1 / 0.75)), portrait, 0.01f)
        assertEquals(true, portrait > landscape)
        // Gegenprobe ueber tan: atan(tan(x)) == x
        assertEquals(0.1f / 0.75f, tan(portrait * PI / 180.0).toFloat(), 0.001f)
    }
}

private fun degrees(radians: Double): Float = (radians * 180.0 / PI).toFloat()
