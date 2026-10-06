package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.frame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HorizonRuleTest {

    @Test
    fun `gerader Horizont ergibt keinen Hinweis`() {
        assertNull(HorizonRule.evaluate(frame(tiltDegrees = 0f)))
    }

    @Test
    fun `unbestimmbare Neigung ergibt keinen Hinweis`() {
        assertNull(HorizonRule.evaluate(frame(tiltDegrees = null)))
    }

    @Test
    fun `Neigung innerhalb der Toleranz ergibt keinen Hinweis`() {
        assertNull(HorizonRule.evaluate(frame(tiltDegrees = 4f)))
        assertNull(HorizonRule.evaluate(frame(tiltDegrees = -3.9f)))
    }

    @Test
    fun `Grenzfall knapp ueber der Toleranz warnt bereits`() {
        val hint = HorizonRule.evaluate(frame(tiltDegrees = 4.1f))

        assertEquals("Horizont ca. 4° schief — rechte Seite anheben.", hint?.message)
    }

    @Test
    fun `positive Neigung nennt die rechte Seite`() {
        val hint = HorizonRule.evaluate(frame(tiltDegrees = 5f))

        assertEquals("Horizont ca. 5° schief — rechte Seite anheben.", hint?.message)
    }

    @Test
    fun `negative Neigung nennt die linke Seite`() {
        val hint = HorizonRule.evaluate(frame(tiltDegrees = -7f))

        assertEquals("Horizont ca. 7° schief — linke Seite anheben.", hint?.message)
    }

    @Test
    fun `Grad-Angabe wird gerundet`() {
        val hint = HorizonRule.evaluate(frame(tiltDegrees = 8.6f))

        assertEquals("Horizont ca. 9° schief — rechte Seite anheben.", hint?.message)
    }

    @Test
    fun `kopfueber gehaltenes Geraet ist kein schiefer Horizont`() {
        // Im Emulator beobachtet: -180° Neigung bei aufrechter Anzeige. "180° schief —
        // linke Seite anheben" ist kein befolgbarer Hinweis.
        assertNull(HorizonRule.assess(frame(tiltDegrees = -180f)))
        assertNull(HorizonRule.assess(frame(tiltDegrees = 90f)))
    }

    @Test
    fun `Score faellt mit der Neigung`() {
        val straight = HorizonRule.assess(frame(tiltDegrees = 0f))!!.score
        val slight = HorizonRule.assess(frame(tiltDegrees = 3f))!!.score
        val strong = HorizonRule.assess(frame(tiltDegrees = 15f))!!.score

        assertEquals(1f, straight)
        assertTrue(slight in 0.8f..0.99f, "3° -> $slight")
        assertEquals(0f, strong)
    }
}
