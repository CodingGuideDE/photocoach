package com.florianhaeglsperger.photocoach.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StillLumaTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun uniformImageGivesUniformGrid() {
        val pixels = IntArray(40 * 30) { argb(128, 128, 128) }
        val grid = lumaGridFromArgb(pixels, 40, 30, 8)
        assertEquals(8, grid.width)
        assertTrue(grid.values.all { kotlin.math.abs(it - 128f / 255f) < 1e-4f })
    }

    @Test
    fun usesBt601Weights() {
        // Reines Gruen ist deutlich heller als reines Blau — wie im Y-Kanal der Kamera.
        val green = lumaGridFromArgb(IntArray(4) { argb(0, 255, 0) }, 2, 2, 1).values[0]
        val blue = lumaGridFromArgb(IntArray(4) { argb(0, 0, 255) }, 2, 2, 1).values[0]
        assertEquals(0.587f, green, 1e-4f)
        assertEquals(0.114f, blue, 1e-4f)
    }

    @Test
    fun keepsPositionsInsteadOfFlipping() {
        // Linke Haelfte hell, rechte dunkel: im Raster muss es genauso liegen, sonst saesse
        // das Motiv nach der Analyse auf der falschen Seite.
        val w = 20
        val h = 10
        val pixels = IntArray(w * h) { i -> if (i % w < w / 2) argb(255, 255, 255) else argb(0, 0, 0) }
        val grid = lumaGridFromArgb(pixels, w, h, 4)
        for (y in 0 until 4) {
            assertEquals(1f, grid.values[y * 4 + 0], 1e-4f)
            assertEquals(1f, grid.values[y * 4 + 1], 1e-4f)
            assertEquals(0f, grid.values[y * 4 + 2], 1e-4f)
            assertEquals(0f, grid.values[y * 4 + 3], 1e-4f)
        }
    }

    @Test
    fun topAndBottomAreNotSwapped() {
        val w = 10
        val h = 20
        val pixels = IntArray(w * h) { i -> if (i / w < h / 2) argb(255, 255, 255) else argb(0, 0, 0) }
        val grid = lumaGridFromArgb(pixels, w, h, 2)
        assertEquals(1f, grid.values[0], 1e-4f)
        assertEquals(0f, grid.values[2], 1e-4f)
    }
}
