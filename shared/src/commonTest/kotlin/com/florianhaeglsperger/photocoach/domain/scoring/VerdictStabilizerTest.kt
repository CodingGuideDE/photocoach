package com.florianhaeglsperger.photocoach.domain.scoring

import com.florianhaeglsperger.photocoach.domain.rules.Hint
import kotlin.test.Test
import kotlin.test.assertEquals

class VerdictStabilizerTest {

    private val a = Verdict.Fix(Hint("Horizont schief"))
    private val b = Verdict.Fix(Hint("Kopf zu weit oben"))

    @Test
    fun `erste Aussage erscheint sofort`() {
        assertEquals(a, VerdictStabilizer(minDisplayMs = 1000L).update(a, nowMs = 0L))
    }

    @Test
    fun `eine andere Aussage verdraengt die aktuelle nicht sofort`() {
        val stabilizer = VerdictStabilizer(minDisplayMs = 1000L)
        stabilizer.update(a, nowMs = 0L)

        // Genau der Fall, um den es geht: zwei Regeln greifen abwechselnd bei 10 Hz.
        assertEquals(a, stabilizer.update(b, nowMs = 100L))
        assertEquals(a, stabilizer.update(b, nowMs = 999L))
    }

    @Test
    fun `nach der Mindestzeit uebernimmt die neue Aussage`() {
        val stabilizer = VerdictStabilizer(minDisplayMs = 1000L)
        stabilizer.update(a, nowMs = 0L)

        assertEquals(b, stabilizer.update(b, nowMs = 1000L))
    }

    @Test
    fun `auch das Verschwinden wird gedaempft`() {
        val stabilizer = VerdictStabilizer(minDisplayMs = 1000L)
        stabilizer.update(a, nowMs = 0L)

        assertEquals(a, stabilizer.update(Verdict.Good, nowMs = 300L))
        assertEquals(Verdict.Good, stabilizer.update(Verdict.Good, nowMs = 1000L))
    }

    @Test
    fun `auch passt steht die Mindestzeit - kein Aufblitzen fuer ein Frame`() {
        // Der Fehler des Vorgaengers: stand kein Hinweis, kam der naechste sofort durch.
        // Pendelte ein Wert um die Schwelle, blitzte "passt" fuer 100 ms auf.
        val stabilizer = VerdictStabilizer(minDisplayMs = 1000L)
        stabilizer.update(a, nowMs = 0L)
        stabilizer.update(Verdict.Good, nowMs = 1000L)

        assertEquals(Verdict.Good, stabilizer.update(a, nowMs = 1100L))
        assertEquals(a, stabilizer.update(a, nowMs = 2000L))
    }

    @Test
    fun `dieselbe Aussage verlaengert die Anzeigezeit nicht`() {
        // Wichtig: sonst koennte eine dauerhaft anliegende Aussage nie abgeloest werden.
        val stabilizer = VerdictStabilizer(minDisplayMs = 1000L)
        stabilizer.update(a, nowMs = 0L)
        stabilizer.update(a, nowMs = 900L)

        assertEquals(b, stabilizer.update(b, nowMs = 1000L))
    }

    @Test
    fun `nach reset erscheint die naechste Aussage sofort`() {
        val stabilizer = VerdictStabilizer(minDisplayMs = 1000L)
        stabilizer.update(a, nowMs = 0L)

        stabilizer.reset()

        assertEquals(b, stabilizer.update(b, nowMs = 10L))
    }
}
