package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.THIRD_BOTTOM
import com.florianhaeglsperger.photocoach.domain.THIRD_LEFT
import com.florianhaeglsperger.photocoach.domain.faceAt
import com.florianhaeglsperger.photocoach.domain.frame
import com.florianhaeglsperger.photocoach.domain.model.SaliencyPoint
import com.florianhaeglsperger.photocoach.domain.subjectAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeadSpaceRuleTest {

    /** Ein Streifen gleich starker Punkte ueber die ganze Breite auf Hoehe [y]. */
    private fun row(y: Float, from: Float = 0.1f, to: Float = 0.9f): List<SaliencyPoint> =
        (0..8).map { subjectAt(from + (to - from) * it / 8f, y) }

    /** Eine Spalte gleich starker Punkte ueber die ganze Hoehe auf [x]. */
    private fun column(x: Float): List<SaliencyPoint> = (0..8).map { subjectAt(x, 0.1f + 0.1f * it) }

    @Test
    fun `ohne Saliency keine Meinung`() {
        assertNull(DeadSpaceRule.assess(frame()))
    }

    @Test
    fun `zu wenige Punkte ergeben keine Meinung`() {
        assertNull(DeadSpaceRule.assess(frame(saliency = listOf(subjectAt(0.1f, 0.1f), subjectAt(0.2f, 0.1f)))))
    }

    @Test
    fun `gleichmaessig verteilter Inhalt ist in Ordnung`() {
        val saliency = row(0.2f) + row(0.5f) + row(0.8f)

        val assessment = assertNotNull(DeadSpaceRule.assess(frame(saliency = saliency)))

        assertNull(assessment.hint)
        assertEquals(1f, assessment.score)
    }

    @Test
    fun `alles unten - darueber leerer Himmel - warnt`() {
        // Breit verteilt, damit es kein einzelnes Motiv gibt, das die Leere begruenden wuerde.
        val saliency = row(0.75f) + row(0.9f)

        val assessment = assertNotNull(DeadSpaceRule.assess(frame(saliency = saliency)))

        assertEquals(
            "Obere Bildhälfte fast leer — Kamera etwas nach unten schwenken.",
            assessment.hint?.message,
        )
        assertTrue(assessment.score < 0.5f, "score=${assessment.score}")
    }

    @Test
    fun `alles links - rechte Haelfte leer - warnt mit Schwenk nach links`() {
        // Schwenkt die Kamera nach links, wandert der Inhalt im Bild nach rechts in die
        // leere Haelfte hinein.
        val saliency = column(0.05f) + column(0.15f)

        val hint = DeadSpaceRule.evaluate(frame(saliency = saliency))

        assertEquals("Rechte Bildhälfte fast leer — Kamera etwas nach links schwenken.", hint?.message)
    }

    @Test
    fun `alles rechts - warnt mit Schwenk nach rechts`() {
        val saliency = column(0.85f) + column(0.95f)

        val hint = DeadSpaceRule.evaluate(frame(saliency = saliency))

        assertEquals("Linke Bildhälfte fast leer — Kamera etwas nach rechts schwenken.", hint?.message)
    }

    @Test
    fun `Motiv auf dem Drittel begruendet die leere Gegenseite`() {
        // Genau die Komposition, die die Drittel-Regel empfiehlt — hier darf nicht gewarnt
        // werden, sonst widersprechen sich die beiden Regeln.
        val saliency = listOf(
            subjectAt(THIRD_LEFT, THIRD_BOTTOM),
            subjectAt(THIRD_LEFT + 0.05f, THIRD_BOTTOM),
            subjectAt(THIRD_LEFT, THIRD_BOTTOM + 0.05f),
        )

        val assessment = assertNotNull(DeadSpaceRule.assess(frame(saliency = saliency, aspectRatio = 1f)))

        assertNull(assessment.hint)
        assertEquals(1f, assessment.score)
    }

    @Test
    fun `Gesicht auf dem Drittel begruendet die Leere ebenfalls`() {
        val saliency = column(0.3f) + column(0.35f)

        val hint = DeadSpaceRule.evaluate(
            frame(saliency = saliency, faces = listOf(faceAt(THIRD_LEFT, 0.5f, size = 0.2f))),
        )

        assertNull(hint)
    }

    @Test
    fun `Motiv am Rand begruendet nichts`() {
        // Ein einzelnes Motiv ganz links ist kein Drittel — die leere rechte Haelfte bleibt tot.
        val saliency = listOf(subjectAt(0.08f, 0.5f), subjectAt(0.1f, 0.55f), subjectAt(0.08f, 0.45f))

        val hint = DeadSpaceRule.evaluate(frame(saliency = saliency, aspectRatio = 1f))

        assertEquals("Rechte Bildhälfte fast leer — Kamera etwas nach links schwenken.", hint?.message)
    }

    @Test
    fun `Score faellt weich - je leerer die Haelfte`() {
        fun scoreWithRightShare(rightPoints: Int): Float {
            val left = (0 until 20).map { subjectAt(0.1f + (it % 4) * 0.1f, 0.1f + (it / 4) * 0.2f) }
            val right = (0 until rightPoints).map { subjectAt(0.9f, 0.1f + it * 0.1f) }
            return DeadSpaceRule.assess(frame(saliency = left + right))!!.score
        }

        assertTrue(scoreWithRightShare(8) > scoreWithRightShare(3))
        assertTrue(scoreWithRightShare(3) > scoreWithRightShare(0))
    }
}
