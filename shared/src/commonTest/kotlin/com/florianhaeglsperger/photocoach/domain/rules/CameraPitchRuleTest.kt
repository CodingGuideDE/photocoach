package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.frame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CameraPitchRuleTest {

    private fun pitched(degrees: Float?) = frame().copy(cameraPitchDegrees = degrees)

    @Test
    fun `ohne Messung keine Meinung`() {
        assertNull(CameraPitchRule.assess(pitched(null)))
    }

    @Test
    fun `leichte Neigung beim Halten ist in Ordnung`() {
        val assessment = assertNotNull(CameraPitchRule.assess(pitched(8f)))

        assertNull(assessment.hint)
    }

    @Test
    fun `deutlich nach oben geneigt warnt`() {
        assertEquals(
            "Kamera nach oben geneigt — gerade halten, sonst kippen Linien",
            CameraPitchRule.evaluate(pitched(25f))?.message,
        )
    }

    @Test
    fun `nach unten geneigt gibt nie einen Hinweis`() {
        // Bewusste Entscheidung (18.10.2026): nach unten fotografieren ist oft gewollt.
        assertNull(CameraPitchRule.assess(pitched(-25f)))
        assertNull(CameraPitchRule.assess(pitched(-85f)))
    }

    @Test
    fun `steiler Blick nach oben ist gewollt`() {
        // Himmel, Baumkrone — da waere "gerade halten" falsch.
        assertNull(CameraPitchRule.assess(pitched(70f)))
    }

    @Test
    fun `Score sinkt mit der Neigung`() {
        val straight = CameraPitchRule.assess(pitched(0f))!!.score
        val tilted = CameraPitchRule.assess(pitched(25f))!!.score

        assertEquals(1f, straight)
        assertTrue(tilted < 0.5f, "25° -> $tilted")
    }
}
