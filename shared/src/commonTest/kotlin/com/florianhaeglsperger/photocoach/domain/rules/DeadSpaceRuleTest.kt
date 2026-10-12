package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.SMOOTH
import com.florianhaeglsperger.photocoach.domain.TEXTURED
import com.florianhaeglsperger.photocoach.domain.THIRD_BOTTOM
import com.florianhaeglsperger.photocoach.domain.THIRD_LEFT
import com.florianhaeglsperger.photocoach.domain.faceAt
import com.florianhaeglsperger.photocoach.domain.frame
import com.florianhaeglsperger.photocoach.domain.structure
import com.florianhaeglsperger.photocoach.domain.subjectAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeadSpaceRuleTest {

    @Test
    fun `ohne Strukturdaten keine Meinung`() {
        assertNull(DeadSpaceRule.assess(frame()))
    }

    @Test
    fun `voellig strukturloses Bild hat keine tote Haelfte`() {
        // Weisse Wand, Nebel: das ganze Bild ist leer, keine Haelfte ist "falsch" leer.
        assertNull(DeadSpaceRule.assess(frame(structure = structure { _, _ -> SMOOTH })))
    }

    @Test
    fun `gleichmaessig strukturiertes Bild ist in Ordnung`() {
        val assessment = assertNotNull(DeadSpaceRule.assess(frame(structure = structure { _, _ -> TEXTURED })))

        assertNull(assessment.hint)
        assertEquals(1f, assessment.score)
    }

    @Test
    fun `alles unten - darueber glatter Himmel - warnt`() {
        val assessment = assertNotNull(
            DeadSpaceRule.assess(frame(structure = structure { _, row -> if (row >= 6) TEXTURED else SMOOTH })),
        )

        assertEquals("Obere Bildhälfte fast leer — Kamera etwas nach unten schwenken.", assessment.hint?.message)
        assertTrue(assessment.score < 0.5f, "score=${assessment.score}")
    }

    @Test
    fun `alles links - rechte Haelfte glatt - warnt mit Schwenk nach links`() {
        // Schwenkt die Kamera nach links, wandert der Inhalt im Bild nach rechts in die
        // leere Haelfte hinein.
        val hint = DeadSpaceRule.evaluate(frame(structure = structure { column, _ -> if (column < 6) TEXTURED else SMOOTH }))

        assertEquals("Rechte Bildhälfte fast leer — Kamera etwas nach links schwenken.", hint?.message)
    }

    @Test
    fun `alles rechts - warnt mit Schwenk nach rechts`() {
        val hint = DeadSpaceRule.evaluate(frame(structure = structure { column, _ -> if (column >= 6) TEXTURED else SMOOTH }))

        assertEquals("Linke Bildhälfte fast leer — Kamera etwas nach rechts schwenken.", hint?.message)
    }

    @Test
    fun `strukturierte aber unauffaellige Haelfte gilt nicht als leer`() {
        // Der gemeldete Fehler: kontrastreiches Objekt links, rechts eine Mauer mit
        // schwacher, aber vorhandener Struktur. Ueber die Saliency galt die Mauer als leer,
        // weil der Kontrast links alles andere ueberstrahlte.
        val hint = DeadSpaceRule.evaluate(
            frame(structure = structure { column, _ -> if (column < 6) 1.0f else 0.05f }),
        )

        assertNull(hint)
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
        val leftOnly = structure { column, _ -> if (column < 6) TEXTURED else SMOOTH }

        val assessment = assertNotNull(
            DeadSpaceRule.assess(frame(saliency = saliency, aspectRatio = 1f, structure = leftOnly)),
        )

        assertNull(assessment.hint)
    }

    @Test
    fun `Gesicht auf dem Drittel begruendet die Leere ebenfalls`() {
        val hint = DeadSpaceRule.evaluate(
            frame(
                faces = listOf(faceAt(THIRD_LEFT, 0.5f, size = 0.2f)),
                structure = structure { column, _ -> if (column < 6) TEXTURED else SMOOTH },
            ),
        )

        assertNull(hint)
    }

    @Test
    fun `Motiv am Rand begruendet nichts`() {
        val saliency = listOf(subjectAt(0.08f, 0.5f), subjectAt(0.1f, 0.55f), subjectAt(0.08f, 0.45f))

        val hint = DeadSpaceRule.evaluate(
            frame(
                saliency = saliency,
                aspectRatio = 1f,
                structure = structure { column, _ -> if (column < 6) TEXTURED else SMOOTH },
            ),
        )

        assertEquals("Rechte Bildhälfte fast leer — Kamera etwas nach links schwenken.", hint?.message)
    }

    @Test
    fun `Score faellt weich - je leerer die Haelfte`() {
        fun scoreWithRightColumns(structuredRightColumns: Int): Float =
            DeadSpaceRule.assess(
                frame(structure = structure { column, _ -> if (column < 6 + structuredRightColumns) TEXTURED else SMOOTH }),
            )!!.score

        assertTrue(scoreWithRightColumns(3) > scoreWithRightColumns(1))
        assertTrue(scoreWithRightColumns(1) > scoreWithRightColumns(0))
    }
}
