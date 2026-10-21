package com.florianhaeglsperger.photocoach.capture

import com.florianhaeglsperger.photocoach.capture.saliency.LumaGrid
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageExposureTest {

    private fun grid(value: (Int) -> Float) = LumaGrid(10, 10, FloatArray(100) { value(it) })

    @Test
    fun `zaehlt ausgefressene und abgesoffene Anteile`() {
        // 30 Punkte weiss, 20 schwarz, Rest mittelgrau.
        val stats = ImageExposure.of(grid { i -> if (i < 30) 1f else if (i < 50) 0f else 0.5f })

        assertEquals(0.3f, stats.overexposedFraction, 0.001f)
        assertEquals(0.2f, stats.underexposedFraction, 0.001f)
        assertEquals(0.55f, stats.meanBrightness, 0.001f)
    }

    @Test
    fun `knapp unter Weiss zaehlt noch nicht als ausgefressen`() {
        val stats = ImageExposure.of(grid { 0.9f })

        assertEquals(0f, stats.overexposedFraction)
    }
}
