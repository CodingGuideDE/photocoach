package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.frame
import com.florianhaeglsperger.photocoach.domain.model.ExposureStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposureRuleTest {

    private fun exposed(over: Float = 0f, under: Float = 0f, mean: Float = 0.5f) =
        frame().copy(exposure = ExposureStats(over, under, mean))

    @Test
    fun `ohne Messung keine Meinung`() {
        assertNull(ExposureRule.assess(frame()))
    }

    @Test
    fun `normal belichtetes Bild ist in Ordnung`() {
        val assessment = assertNotNull(ExposureRule.assess(exposed(over = 0.02f, under = 0.03f)))

        assertNull(assessment.hint)
        assertEquals(1f, assessment.score)
    }

    @Test
    fun `grosse ausgefressene Flaeche warnt`() {
        val hint = ExposureRule.evaluate(exposed(over = 0.3f, mean = 0.7f))

        assertEquals("Helle Bereiche überbelichtet — Belichtung verringern", hint?.message)
    }

    @Test
    fun `helles Fenster im Innenraum ist noch kein Fehler`() {
        assertNull(ExposureRule.evaluate(exposed(over = 0.08f, mean = 0.4f)))
    }

    @Test
    fun `insgesamt dunkles Bild warnt`() {
        val hint = ExposureRule.evaluate(exposed(under = 0.5f, mean = 0.1f))

        assertEquals("Bild zu dunkel — Belichtung erhöhen", hint?.message)
    }

    @Test
    fun `schwarze Schatten in einem hellen Bild sind kein Fehler`() {
        // Kontrastreiche Szene: viel Schwarz, aber insgesamt nicht dunkel — gewollt.
        assertNull(ExposureRule.evaluate(exposed(under = 0.4f, mean = 0.45f)))
    }

    @Test
    fun `Score sinkt mit der ausgefressenen Flaeche`() {
        val little = ExposureRule.assess(exposed(over = 0.1f))!!.score
        val much = ExposureRule.assess(exposed(over = 0.35f))!!.score

        assertTrue(much < little, "wenig=$little viel=$much")
    }
}
