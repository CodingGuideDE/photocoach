package com.florianhaeglsperger.photocoach.domain.scoring

import com.florianhaeglsperger.photocoach.domain.CENTER
import com.florianhaeglsperger.photocoach.domain.faceAt
import com.florianhaeglsperger.photocoach.domain.frame
import com.florianhaeglsperger.photocoach.domain.rules.ThirdsTargetTracker
import com.florianhaeglsperger.photocoach.domain.subjectAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prueft Rangfolge, Score und die drei Aussagen des [CompositionScorer] — nicht die Regeln
 * selbst, die haben eigene Tests.
 */
class CompositionScorerTest {

    private fun hintOf(assessment: CompositionAssessment): String? =
        (assessment.verdict as? Verdict.Fix)?.hint?.message

    @Test
    fun `gut gerahmtes Gesicht am Drittel bei geradem Horizont - passt - hoher Score`() {
        val result = CompositionScorer().assess(
            frame(tiltDegrees = 0f, faces = listOf(faceAt(1f / 3f, 1f / 3f, size = 0.25f))),
        )

        assertEquals(Verdict.Good, result.verdict)
        assertTrue(assertNotNull(result.score) >= 95, "score=${result.score}")
    }

    @Test
    fun `gerader Horizont allein ist keine gute Komposition`() {
        // Der gemeldete Fehler: "Komposition passt" in 95 % der Faelle, sobald der Horizont
        // gerade war. Ohne Motiv und ohne Saliency hat die App vom Inhalt nichts gesehen.
        val result = CompositionScorer().assess(frame(tiltDegrees = 0f))

        assertEquals(Verdict.NoSubject, result.verdict)
        assertNull(result.score)
    }

    @Test
    fun `ohne jede Datenlage kein Hinweis und kein Score`() {
        val result = CompositionScorer().assess(frame())

        assertEquals(Verdict.NoSubject, result.verdict)
        assertNull(result.score)
    }

    @Test
    fun `schiefer Horizont ohne Motiv wird trotzdem gemeldet`() {
        val result = CompositionScorer().assess(frame(tiltDegrees = 9f))

        assertTrue(assertNotNull(hintOf(result)).startsWith("Horizont"))
        // Aber ohne Score: der wuerde eine Komposition bewerten, die die App nicht sieht.
        assertNull(result.score)
    }

    @Test
    fun `Portraet-Rahmung geht vor Horizont`() {
        val result = CompositionScorer().assess(
            frame(tiltDegrees = 12f, faces = listOf(faceAt(CENTER, 0.04f, size = 0.2f))),
        )

        val hint = assertNotNull(hintOf(result))
        assertTrue("Horizont" !in hint, "erwartet wurde der Rahmungs-Hinweis, kam: $hint")
    }

    @Test
    fun `Horizont greift wenn die Rahmung passt`() {
        val result = CompositionScorer().assess(
            frame(tiltDegrees = 12f, faces = listOf(faceAt(1f / 3f, 1f / 3f, size = 0.25f))),
        )

        assertTrue("Horizont" in assertNotNull(hintOf(result)))
    }

    @Test
    fun `Motiv nur aus Saliency - Drittel-Regel greift auch ohne Gesicht`() {
        // Genau der Fall, der auf Android bisher nie eintrat: Motiv ohne Gesicht.
        val saliency = listOf(subjectAt(0.12f, 0.5f), subjectAt(0.14f, 0.52f), subjectAt(0.12f, 0.48f))

        val result = CompositionScorer().assess(frame(tiltDegrees = 0f, saliency = saliency, aspectRatio = 1f))

        assertTrue(assertNotNull(hintOf(result)).startsWith("Motiv"), "kam: ${hintOf(result)}")
        assertTrue(assertNotNull(result.score) < 75, "score=${result.score}")
    }

    @Test
    fun `tote Flaeche ohne Motiv wird gemeldet`() {
        // Viele gleich starke Punkte unten verteilt: kein einzelnes Motiv, oben leer.
        val saliency = (0..8).map { subjectAt(0.1f + it * 0.1f, 0.8f) } +
            (0..8).map { subjectAt(0.1f + it * 0.1f, 0.9f) }

        val result = CompositionScorer().assess(frame(tiltDegrees = 0f, saliency = saliency))

        assertEquals("Obere Bildhälfte fast leer — Kamera etwas nach unten schwenken.", hintOf(result))
        assertNotNull(result.score)
    }

    @Test
    fun `Score sinkt mit der Neigung - auch unterhalb der Hinweis-Schwelle`() {
        val face = listOf(faceAt(1f / 3f, 1f / 3f, size = 0.25f))
        val straight = CompositionScorer().assess(frame(tiltDegrees = 0f, faces = face))
        val slight = CompositionScorer().assess(frame(tiltDegrees = 3.5f, faces = face))

        assertEquals(Verdict.Good, slight.verdict)
        assertTrue(slight.score!! < straight.score!!, "gerade=${straight.score}, leicht schief=${slight.score}")
    }

    @Test
    fun `mittiges Motiv zieht den Score nur maessig herunter`() {
        // Plan 3.2.1: die Mitte ist legitime Bildsprache, die Drittel-Regel bekommt nur
        // mittleres Gewicht. Ein ansonsten tadelloses Bild darf nicht durchfallen.
        val result = CompositionScorer().assess(
            frame(tiltDegrees = 0f, faces = listOf(faceAt(CENTER, CENTER, size = 0.25f))),
        )

        assertIs<Verdict.Fix>(result.verdict)
        val score = assertNotNull(result.score)
        assertTrue(score in 60..85, "score=$score")
    }

    @Test
    fun `der Drittel-Tracker wird zurueckgesetzt wenn das Motiv verschwindet`() {
        // Ohne Reset haelt der Tracker sein altes Ziel fest (Hysterese) und der erste
        // Hinweis nach einem Szenenwechsel zeigt auf einen Punkt, der zur neuen Szene
        // keinen Bezug hat.
        val tracker = ThirdsTargetTracker()
        val scorer = CompositionScorer(thirdsTracker = tracker)

        scorer.assess(frame(faces = listOf(faceAt(0.15f, CENTER, size = 0.2f))))
        scorer.assess(frame())

        val target = tracker.select(x = 0.85f, y = CENTER, aspectRatio = 1f)
        assertEquals(2f / 3f, target.x, absoluteTolerance = 0.01f)
    }

    @Test
    fun `reset vergisst den Drittel-Zielpunkt`() {
        val tracker = ThirdsTargetTracker()
        val scorer = CompositionScorer(thirdsTracker = tracker)
        scorer.assess(frame(faces = listOf(faceAt(0.15f, CENTER, size = 0.2f))))

        scorer.reset()

        assertEquals(2f / 3f, tracker.select(x = 0.85f, y = CENTER, aspectRatio = 1f).x, 0.01f)
    }
}
