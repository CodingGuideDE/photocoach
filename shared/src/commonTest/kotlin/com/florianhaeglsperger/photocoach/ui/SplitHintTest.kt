package com.florianhaeglsperger.photocoach.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SplitHintTest {

    @Test
    fun `Hinweis wird in Problem und Handlung geteilt`() {
        val (headline, action) = splitHint("Motiv ca. 16 % zu weit unten — Kamera etwas nach unten schwenken.")

        assertEquals("Motiv ca. 16 % zu weit unten", headline)
        assertEquals("Kamera etwas nach unten schwenken.", action)
    }

    @Test
    fun `Text ohne Trenner bleibt eine Zeile`() {
        val (headline, action) = splitHint("Komposition passt")

        assertEquals("Komposition passt", headline)
        assertNull(action)
    }
}
