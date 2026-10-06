package com.florianhaeglsperger.photocoach.domain.scoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreSmootherTest {

    @Test
    fun `erster Wert kommt unveraendert durch`() {
        assertEquals(80, ScoreSmoother().update(80, nowMs = 0L))
    }

    @Test
    fun `ein Sprung wird gedaempft - kommt aber an`() {
        val smoother = ScoreSmoother(timeConstantMs = 500L)
        smoother.update(40, nowMs = 0L)

        val after100 = smoother.update(90, nowMs = 100L)!!
        assertTrue(after100 in 41..60, "nach 100 ms: $after100")

        var value = after100
        for (t in 200L..3000L step 100L) value = smoother.update(90, nowMs = t)!!
        assertEquals(90, value)
    }

    @Test
    fun `ohne Score wird zurueckgesetzt`() {
        val smoother = ScoreSmoother()
        smoother.update(20, nowMs = 0L)

        assertNull(smoother.update(null, nowMs = 100L))
        // Neue Szene: der alte Wert darf nicht nachwirken.
        assertEquals(90, smoother.update(90, nowMs = 200L))
    }
}
