package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.CENTER
import com.florianhaeglsperger.photocoach.domain.LANDSCAPE_16_9
import com.florianhaeglsperger.photocoach.domain.PORTRAIT_9_16
import com.florianhaeglsperger.photocoach.domain.THIRD_BOTTOM
import com.florianhaeglsperger.photocoach.domain.THIRD_LEFT
import com.florianhaeglsperger.photocoach.domain.THIRD_TOP
import com.florianhaeglsperger.photocoach.domain.faceAt
import com.florianhaeglsperger.photocoach.domain.frame
import com.florianhaeglsperger.photocoach.domain.subjectAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleOfThirdsRuleTest {

    @Test
    fun `Motiv genau auf einem Drittel-Punkt bekommt Score 1`() {
        val result = RuleOfThirdsRule.analyse(frame(faces = listOf(faceAt(THIRD_LEFT, THIRD_TOP))))

        assertEquals(1f, assertNotNull(result).score)
    }

    @Test
    fun `Motiv auf dem Drittel-Punkt ergibt keinen Hinweis`() {
        assertNull(RuleOfThirdsRule.evaluate(frame(faces = listOf(faceAt(THIRD_LEFT, THIRD_TOP)))))
    }

    @Test
    fun `Motiv in der Bildmitte wird bewertet — nicht ignoriert`() {
        // Der Unterschied, auf den es ankommt: "keine Meinung" (null) heisst, dass die Regel
        // nichts sieht. Ein mittiges Motiv sieht sie sehr wohl — es sitzt nur nicht auf
        // einem Drittel-Punkt.
        val result = RuleOfThirdsRule.analyse(frame(faces = listOf(faceAt(CENTER, CENTER))))

        assertNotNull(result)
        assertEquals(0f, result.score)
    }

    @Test
    fun `ohne erkennbares Motiv hat die Regel keine Meinung`() {
        assertNull(RuleOfThirdsRule.analyse(frame()))
        assertNull(RuleOfThirdsRule.evaluate(frame()))
    }

    @Test
    fun `bei Gesicht und Saliency zaehlt das Gesicht`() {
        val result = RuleOfThirdsRule.analyse(
            frame(
                saliency = listOf(subjectAt(CENTER, CENTER)),
                faces = listOf(faceAt(THIRD_LEFT, THIRD_TOP)),
            ),
        )

        assertEquals(1f, assertNotNull(result).score)
    }

    @Test
    fun `gleicher normierter Versatz wird im Hochformat strenger bewertet`() {
        val versatz = 0.1f
        val motiv = faceAt(THIRD_LEFT, THIRD_TOP + versatz)

        val quer = RuleOfThirdsRule.analyse(frame(faces = listOf(motiv), aspectRatio = LANDSCAPE_16_9))
        val hoch = RuleOfThirdsRule.analyse(frame(faces = listOf(motiv), aspectRatio = PORTRAIT_9_16))

        assertTrue(
            assertNotNull(hoch).score < assertNotNull(quer).score,
            "hoch=${hoch.score} war nicht strenger als quer=${quer.score}",
        )
    }

    @Test
    fun `kleiner Versatz ergibt keinen Hinweis`() {
        val knapp = faceAt(THIRD_LEFT + 0.03f, THIRD_TOP)

        assertNull(RuleOfThirdsRule.evaluate(frame(faces = listOf(knapp))))
    }

    @Test
    fun `kleiner deutlicher Versatz heisst etwas`() {
        val rechts = faceAt(THIRD_LEFT + 0.08f, THIRD_TOP)

        val hint = RuleOfThirdsRule.evaluate(frame(faces = listOf(rechts)))

        assertEquals("Motiv etwas zu weit rechts", assertNotNull(hint).message)
    }

    @Test
    fun `grosser Versatz heisst viel`() {
        val links = faceAt(THIRD_LEFT - 0.15f, THIRD_TOP)

        val hint = RuleOfThirdsRule.evaluate(frame(faces = listOf(links)))

        assertEquals("Motiv viel zu weit links", assertNotNull(hint).message)
    }

    @Test
    fun `Grenze zwischen etwas und viel liegt bei LARGE_OFFSET_PERCENT`() {
        val knappDarunter = faceAt(THIRD_LEFT + (RuleOfThirdsRule.LARGE_OFFSET_PERCENT - 1) / 100f, THIRD_TOP)
        val genauDrauf = faceAt(THIRD_LEFT + RuleOfThirdsRule.LARGE_OFFSET_PERCENT / 100f, THIRD_TOP)

        assertEquals("Motiv etwas zu weit rechts", RuleOfThirdsRule.evaluate(frame(faces = listOf(knappDarunter)))?.message)
        assertEquals("Motiv viel zu weit rechts", RuleOfThirdsRule.evaluate(frame(faces = listOf(genauDrauf)))?.message)
    }

    @Test
    fun `Motiv oberhalb des Drittel-Punkts ist zu hoch`() {
        // Hochformat, damit die senkrechte Achse die dominante ist.
        val hoch = faceAt(THIRD_LEFT, THIRD_BOTTOM - 0.08f)

        val hint = RuleOfThirdsRule.evaluate(frame(faces = listOf(hoch), aspectRatio = PORTRAIT_9_16))

        assertEquals("Motiv viel zu hoch", assertNotNull(hint).message)
    }

    @Test
    fun `Hinweis enthaelt weder Prozent noch Kamera-Anweisung`() {
        // Bewusste Entscheidung (10.10.2026): "Motiv zu hoch — Kamera nach oben" wurde als
        // Widerspruch gelesen, und Prozentwerte sagen beim Fotografieren nichts.
        val hint = assertNotNull(RuleOfThirdsRule.evaluate(frame(faces = listOf(faceAt(0.5f, THIRD_TOP)))))

        assertTrue("%" !in hint.message && "Kamera" !in hint.message, "kam: ${hint.message}")
    }

    @Test
    fun `senkrechter Versatz wird genannt — wenn er die dominante Achse ist`() {
        // Bewusst nur bis knapp unter die Bildmitte: ab y = 0.5 waere der *untere*
        // Drittel-Punkt der naehere, und das Motiv saesse dann ueber seinem Ziel.
        val tief = faceAt(THIRD_LEFT, 0.45f)

        val hint = RuleOfThirdsRule.evaluate(frame(faces = listOf(tief)))

        assertTrue(
            assertNotNull(hint).message.endsWith("zu tief"),
            "unerwarteter Hinweis: ${hint.message}",
        )
    }

    @Test
    fun `vorgegebener Zielpunkt wird statt des naechstgelegenen verwendet`() {
        val motiv = frame(faces = listOf(faceAt(THIRD_LEFT, THIRD_TOP)))
        val fern = com.florianhaeglsperger.photocoach.domain.geometry.NormalizedPoint(2f / 3f, 2f / 3f)

        val result = RuleOfThirdsRule.analyse(motiv, target = fern)

        assertEquals(fern, assertNotNull(result).target)
        assertTrue(result.score < 1f)
    }
}
