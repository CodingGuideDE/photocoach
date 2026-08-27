package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.frame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        assertNull(HorizonRule.evaluate(frame(tiltDegrees = 2f)))
        assertNull(HorizonRule.evaluate(frame(tiltDegrees = -1.9f)))
    }

    @Test
    fun `Grenzfall knapp ueber der Toleranz warnt bereits`() {
        val hint = HorizonRule.evaluate(frame(tiltDegrees = 2.1f))

        assertEquals("Horizont ca. 2° schief — rechte Seite anheben.", hint?.message)
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
}
