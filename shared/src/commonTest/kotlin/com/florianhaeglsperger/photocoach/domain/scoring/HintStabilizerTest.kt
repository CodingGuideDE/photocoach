package com.florianhaeglsperger.photocoach.domain.scoring

import com.florianhaeglsperger.photocoach.domain.rules.Hint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HintStabilizerTest {

    private val a = Hint("Horizont schief")
    private val b = Hint("Kopf zu weit oben")

    @Test
    fun `erster Hinweis erscheint sofort`() {
        val stabilizer = HintStabilizer(minDisplayMs = 1000L)

        assertEquals(a, stabilizer.update(a, nowMs = 0L))
    }

    @Test
    fun `ein anderer Hinweis verdraengt den aktuellen nicht sofort`() {
        val stabilizer = HintStabilizer(minDisplayMs = 1000L)
        stabilizer.update(a, nowMs = 0L)

        // Genau der Fall, um den es geht: zwei Regeln greifen abwechselnd bei 10 Hz.
        assertEquals(a, stabilizer.update(b, nowMs = 100L))
        assertEquals(a, stabilizer.update(b, nowMs = 500L))
        assertEquals(a, stabilizer.update(b, nowMs = 999L))
    }

    @Test
    fun `nach der Mindestzeit uebernimmt der neue Hinweis`() {
        val stabilizer = HintStabilizer(minDisplayMs = 1000L)
        stabilizer.update(a, nowMs = 0L)

        assertEquals(b, stabilizer.update(b, nowMs = 1000L))
    }

    @Test
    fun `auch das Verschwinden wird gedaempft`() {
        // Sonst blitzt der Text weg, sobald ein Wert kurz unter die Schwelle rutscht.
        val stabilizer = HintStabilizer(minDisplayMs = 1000L)
        stabilizer.update(a, nowMs = 0L)

        assertEquals(a, stabilizer.update(null, nowMs = 300L))
        assertNull(stabilizer.update(null, nowMs = 1000L))
    }

    @Test
    fun `derselbe Hinweis verlaengert die Anzeigezeit nicht`() {
        // Wichtig: sonst koennte ein dauerhaft anliegender Hinweis nie abgeloest werden.
        val stabilizer = HintStabilizer(minDisplayMs = 1000L)
        stabilizer.update(a, nowMs = 0L)
        stabilizer.update(a, nowMs = 900L)

        assertEquals(b, stabilizer.update(b, nowMs = 1000L))
    }

    @Test
    fun `nach dem Wechsel gilt die Mindestzeit erneut`() {
        val stabilizer = HintStabilizer(minDisplayMs = 1000L)
        stabilizer.update(a, nowMs = 0L)
        stabilizer.update(b, nowMs = 1000L)

        assertEquals(b, stabilizer.update(a, nowMs = 1500L))
        assertEquals(a, stabilizer.update(a, nowMs = 2000L))
    }

    @Test
    fun `kein Hinweis am Anfang bleibt kein Hinweis`() {
        val stabilizer = HintStabilizer(minDisplayMs = 1000L)

        assertNull(stabilizer.update(null, nowMs = 0L))
    }
}
