package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.frame
import com.florianhaeglsperger.photocoach.domain.model.HorizonLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HorizonPlacementRuleTest {

    private fun withLine(left: Float, right: Float = left, aspectRatio: Float = 0.75f) =
        frame(aspectRatio = aspectRatio).copy(horizonLine = HorizonLine(left, right, strength = 0.2f))

    @Test
    fun `ohne erkannte Linie keine Meinung`() {
        assertNull(HorizonPlacementRule.assess(frame()))
    }

    @Test
    fun `Horizont mittig warnt`() {
        assertEquals(
            "Horizont in der Bildmitte — ins obere oder untere Drittel legen",
            HorizonPlacementRule.evaluate(withLine(0.5f))?.message,
        )
    }

    @Test
    fun `Horizont auf dem oberen oder unteren Drittel passt`() {
        val upper = assertNotNull(HorizonPlacementRule.assess(withLine(1f / 3f)))
        val lower = assertNotNull(HorizonPlacementRule.assess(withLine(2f / 3f)))

        assertNull(upper.hint)
        assertNull(lower.hint)
        assertEquals(1f, upper.score)
        assertEquals(1f, lower.score)
    }

    @Test
    fun `leicht schiefer Horizont zaehlt mit seiner Mitte`() {
        // Links 0,45, rechts 0,55: Mitte 0,5 — mittig, auch wenn die Enden davon abweichen.
        assertNotNull(HorizonPlacementRule.evaluate(withLine(0.45f, 0.55f)))
    }

    @Test
    fun `steile Diagonale ist kein Horizont`() {
        // Im Hochformat: 0,3 Hoehenunterschied entspricht ueber 20 Grad.
        assertNull(HorizonPlacementRule.assess(withLine(0.35f, 0.65f, aspectRatio = 0.75f)))
    }

    @Test
    fun `Score steigt von der Mitte zum Drittel`() {
        val middle = HorizonPlacementRule.assess(withLine(0.5f))!!.score
        val between = HorizonPlacementRule.assess(withLine(0.42f))!!.score
        val third = HorizonPlacementRule.assess(withLine(1f / 3f))!!.score

        assertTrue(middle < between && between < third, "Mitte=$middle dazwischen=$between Drittel=$third")
    }
}
