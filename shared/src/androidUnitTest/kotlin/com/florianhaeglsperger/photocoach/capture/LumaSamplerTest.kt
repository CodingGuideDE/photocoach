package com.florianhaeglsperger.photocoach.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prueft die Abbildung Sensorbild → aufrechtes Bild fuer die Saliency.
 *
 * Konvention (CameraX `ImageInfo.rotationDegrees`): um so viel Grad muss das Sensorbild
 * **im Uhrzeigersinn** gedreht werden, um aufrecht zu stehen. Dieselbe Angabe bekommt ML Kit
 * — Saliency und Gesichter muessen im selben Koordinatensystem landen, sonst vergleicht
 * `SubjectResolver` Aepfel mit Birnen.
 *
 * Geprueft wird ueber die Ecken: Bei 90° im Uhrzeigersinn wandert die obere linke Ecke des
 * Sensorbilds nach oben rechts.
 */
class LumaSamplerTest {

    @Test
    fun `ohne Drehung bleibt alles, wo es ist`() {
        assertEquals(0.2f to 0.7f, uprightToSensor(0.2f, 0.7f, 0))
    }

    @Test
    fun `90 Grad - Sensor oben links liegt aufrecht oben rechts`() {
        assertEquals(0f to 0f, uprightToSensor(1f, 0f, 90))
        // Sensor oben rechts → aufrecht unten rechts
        assertEquals(1f to 0f, uprightToSensor(1f, 1f, 90))
    }

    @Test
    fun `180 Grad - Sensor oben links liegt aufrecht unten rechts`() {
        assertEquals(0f to 0f, uprightToSensor(1f, 1f, 180))
    }

    @Test
    fun `270 Grad - Sensor oben links liegt aufrecht unten links`() {
        assertEquals(0f to 0f, uprightToSensor(0f, 1f, 270))
        // Sensor oben rechts → aufrecht oben links
        assertEquals(1f to 0f, uprightToSensor(0f, 0f, 270))
    }

    @Test
    fun `heller Fleck oben links im Sensor erscheint bei 90 Grad oben rechts`() {
        // Querformat-Sensor 640x480, wie ihn ImageAnalysis standardmaessig liefert.
        val grid = sampleUprightLuma(640, 480, rotationDegrees = 90, size = 16) { x, y ->
            if (x < 100 && y < 100) 255 else 0
        }

        val bright = (0 until 16 * 16).filter { grid.values[it] > 0.5f }
        assertTrue(bright.isNotEmpty())
        // Alle hellen Zellen: rechter Rand, obere Haelfte.
        assertTrue(bright.all { it % 16 >= 12 && it / 16 < 8 }, "helle Zellen: $bright")
    }

    @Test
    fun `Werte sind auf 0 bis 1 normiert`() {
        val grid = sampleUprightLuma(64, 48, rotationDegrees = 0, size = 8) { _, _ -> 255 }

        assertTrue(grid.values.all { it == 1f })
    }
}
