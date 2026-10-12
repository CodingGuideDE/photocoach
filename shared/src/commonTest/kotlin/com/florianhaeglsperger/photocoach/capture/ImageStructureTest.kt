package com.florianhaeglsperger.photocoach.capture

import com.florianhaeglsperger.photocoach.capture.saliency.LumaGrid
import com.florianhaeglsperger.photocoach.capture.saliency.SpectralResidualSaliency
import com.florianhaeglsperger.photocoach.domain.frame
import com.florianhaeglsperger.photocoach.domain.rules.DeadSpaceRule
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prueft die Strukturmessung und — Ende zu Ende ueber die echte Analysekette — die Faelle,
 * an denen die alte, saliency-basierte DeadSpaceRule falsch lag.
 *
 * Die Bilder bekommen ein leichtes Rauschen, wie es jeder Sensor liefert.
 */
class ImageStructureTest {

    private val size = SpectralResidualSaliency.INPUT_SIZE

    private fun image(noise: Float = 0.02f, pixel: (x: Int, y: Int) -> Float): LumaGrid {
        val random = Random(3)
        return LumaGrid(size, size, FloatArray(size * size) { pixel(it % size, it / size) + random.nextFloat() * noise })
    }

    /** Analyse wie in der App: Saliency und Struktur aus demselben Helligkeitsraster. */
    private fun deadSpaceHint(luma: LumaGrid): String? =
        DeadSpaceRule.evaluate(
            frame(
                tiltDegrees = 0f,
                saliency = SpectralResidualSaliency.analyze(luma),
                structure = ImageStructure.of(luma),
                aspectRatio = 0.75f,
            ),
        )?.message

    @Test
    fun `glatter Himmelsverlauf liegt unter der Schwelle`() {
        val structure = ImageStructure.of(image { _, y -> 0.85f - y / 200f })

        assertTrue(structure.values.all { it < DeadSpaceRule.STRUCTURE_THRESHOLD }, "max=${structure.values.max()}")
    }

    @Test
    fun `schwache Textur liegt ueber der Schwelle`() {
        val structure = ImageStructure.of(image { x, y -> 0.45f + 0.06f * sin(x * 1.3f) * sin(y * 1.1f) })

        assertTrue(structure.values.all { it > DeadSpaceRule.STRUCTURE_THRESHOLD }, "min=${structure.values.min()}")
    }

    @Test
    fun `derselbe Kontrast zaehlt in dunklen und hellen Bildteilen gleich`() {
        // Textur mit 20 % Helligkeitsschwankung, einmal dunkel, einmal hell.
        val dark = ImageStructure.of(image(noise = 0f) { x, _ -> 0.2f * (1f + 0.2f * sin(x * 1.3f)) })
        val bright = ImageStructure.of(image(noise = 0f) { x, _ -> 0.8f * (1f + 0.2f * sin(x * 1.3f)) })

        // Nicht gleich (der Dunkel-Boden daempft), aber dieselbe Groessenordnung — und beide
        // klar strukturiert. Ohne die Teilung waere dunkel viermal kleiner.
        val darkMean = dark.values.average()
        val brightMean = bright.values.average()
        assertTrue(darkMean > DeadSpaceRule.STRUCTURE_THRESHOLD && brightMean > DeadSpaceRule.STRUCTURE_THRESHOLD)
        assertTrue(darkMean / brightMean > 0.6, "dunkel=$darkMean hell=$brightMean")
    }

    @Test
    fun `Raster ist zeilenweise von oben links und wird korrekt gespiegelt`() {
        // Struktur nur in der linken Bildhaelfte.
        val structure = ImageStructure.of(image { x, y -> if (x < 32) 0.45f + 0.1f * sin(x * 1.3f + y) else 0.5f })

        assertTrue(structure[0, 5] > DeadSpaceRule.STRUCTURE_THRESHOLD)
        assertTrue(structure[11, 5] < DeadSpaceRule.STRUCTURE_THRESHOLD)
        val mirrored = structure.mirroredHorizontally()
        assertEquals(structure[0, 5], mirrored[11, 5])
        assertEquals(structure[11, 5], mirrored[0, 5])
    }

    // --- Ende zu Ende: die Faelle, um die es ging -----------------------------------------

    @Test
    fun `Kontrast vorn und schwach strukturierter Hintergrund - kein Leer-Hinweis`() {
        // Der gemeldete Fehler: helles, kontrastreiches Objekt links, rechts eine Mauer mit
        // schwacher Struktur. Die alte Regel meldete "Rechte Bildhaelfte fast leer".
        val luma = image { x, y ->
            if ((x - 18) * (x - 18) + (y - 34) * (y - 34) < 64) 0.95f
            else 0.45f + 0.06f * sin(x * 1.3f) * sin(y * 1.1f)
        }

        assertNull(deadSpaceHint(luma))
    }

    @Test
    fun `Objekt links und glatter Himmel rechts - Leer-Hinweis bleibt`() {
        // Die Regel soll nicht stumm werden: wirklich leere Flaeche wird weiter gemeldet.
        // Textur nur im linken Fuenftel — die Kante zum Himmel (dort sitzt die Saliency)
        // liegt damit weit weg von jeder Drittel-Linie. Bei x ≈ 0,4 haette die vorgesehene
        // Drittel-Ausnahme gegriffen und die Leere als gewollt durchgehen lassen.
        val luma = image { x, y -> if (x < 13) 0.45f + 0.08f * sin(x * 1.3f) * sin(y * 1.1f) else 0.8f - y / 300f }

        assertEquals("Rechte Bildhälfte fast leer — Kamera etwas nach links schwenken.", deadSpaceHint(luma))
    }

    @Test
    fun `strukturlose weisse Wand - keine Meinung`() {
        val luma = image { _, _ -> 0.9f }

        assertNull(
            DeadSpaceRule.assess(frame(structure = ImageStructure.of(luma))),
        )
        // Zur Sicherheit: die Messung selbst gibt es trotzdem.
        assertNotNull(ImageStructure.of(luma))
    }
}
