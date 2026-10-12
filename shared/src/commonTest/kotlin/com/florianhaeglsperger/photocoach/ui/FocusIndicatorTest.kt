package com.florianhaeglsperger.photocoach.ui

import com.florianhaeglsperger.photocoach.capture.ExposureCompensation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FocusIndicatorTest {

    /** Typischer Bereich: ±2 EV in Drittelstufen = Index -6..6. */
    private val range = ExposureCompensation(minIndex = -6, maxIndex = 6, stepEv = 1f / 3f)
    private val track = 300f

    @Test
    fun `nach oben ziehen macht heller`() {
        assertTrue(evIndexForDrag(startIndex = 0, dragPx = -100f, trackPx = track, range = range) > 0)
    }

    @Test
    fun `nach unten ziehen macht dunkler`() {
        assertTrue(evIndexForDrag(startIndex = 0, dragPx = 100f, trackPx = track, range = range) < 0)
    }

    @Test
    fun `die ganze Leiste entspricht dem ganzen Bereich`() {
        // Von ganz unten (-6) die volle Laenge nach oben: ganz oben (+6).
        assertEquals(6, evIndexForDrag(startIndex = -6, dragPx = -track, trackPx = track, range = range))
    }

    @Test
    fun `ueber das Ende hinaus wird begrenzt`() {
        assertEquals(6, evIndexForDrag(startIndex = 4, dragPx = -1000f, trackPx = track, range = range))
        assertEquals(-6, evIndexForDrag(startIndex = -4, dragPx = 1000f, trackPx = track, range = range))
    }

    @Test
    fun `kleines Zittern aendert nichts`() {
        // Eine Stufe sind hier 25 px — 5 px Wackeln beim Festhalten duerfen nicht springen.
        assertEquals(2, evIndexForDrag(startIndex = 2, dragPx = 5f, trackPx = track, range = range))
    }

    @Test
    fun `Sonne steht bei 0 in der Mitte und an den Enden oben bzw unten`() {
        assertEquals(0.5f, exposureFraction(0, range))
        assertEquals(1f, exposureFraction(6, range))
        assertEquals(0f, exposureFraction(-6, range))
    }

    @Test
    fun `Regler weicht am rechten Rand nach links aus`() {
        assertFalse(sliderOnLeft(0.5f))
        assertTrue(sliderOnLeft(0.9f))
    }
}
