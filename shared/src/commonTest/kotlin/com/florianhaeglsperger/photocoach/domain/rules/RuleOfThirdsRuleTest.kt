package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.CENTER
import com.florianhaeglsperger.photocoach.domain.LANDSCAPE_16_9
import com.florianhaeglsperger.photocoach.domain.PORTRAIT_9_16
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
    fun `deutlicher Versatz nennt Prozentwert und Kamerarichtung`() {
        val rechts = faceAt(THIRD_LEFT + 0.12f, THIRD_TOP)

        val hint = RuleOfThirdsRule.evaluate(frame(faces = listOf(rechts)))

        assertEquals(
            "Motiv ca. 12 % zu weit rechts — Kamera etwas nach rechts schwenken.",
            assertNotNull(hint).message,
        )
    }

    @Test
    fun `Versatz nach links nennt die Gegenrichtung der Kamera`() {
        // Kamerafuehrung ist entgegengesetzt zum Versatz: Motiv zu weit links heisst, die
        // Kamera muss nach links schwenken, damit der Bildinhalt nach rechts wandert.
        val links = faceAt(THIRD_LEFT - 0.12f, THIRD_TOP)

        val hint = RuleOfThirdsRule.evaluate(frame(faces = listOf(links)))

        assertEquals(
            "Motiv ca. 12 % zu weit links — Kamera etwas nach links schwenken.",
            assertNotNull(hint).message,
        )
    }

    @Test
    fun `senkrechter Versatz wird genannt — wenn er die dominante Achse ist`() {
        // Bewusst nur bis knapp unter die Bildmitte: ab y = 0.5 waere der *untere*
        // Drittel-Punkt der naehere, und das Motiv saesse dann ueber seinem Ziel.
        val tief = faceAt(THIRD_LEFT, 0.45f)

        val hint = RuleOfThirdsRule.evaluate(frame(faces = listOf(tief)))

        assertTrue(
            assertNotNull(hint).message.contains("zu weit unten"),
            "unerwarteter Hinweis: ${hint.message}",
        )
        assertTrue(hint.message.contains("nach unten schwenken"))
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
