package com.florianhaeglsperger.photocoach.domain.scoring

import com.florianhaeglsperger.photocoach.domain.CENTER
import com.florianhaeglsperger.photocoach.domain.faceAt
import com.florianhaeglsperger.photocoach.domain.frame
import com.florianhaeglsperger.photocoach.domain.rules.ThirdsTargetTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prueft die Rangfolge aus [HintSelector] — nicht die Regeln selbst, die haben eigene Tests.
 */
class HintSelectorTest {

    @Test
    fun `ohne Beanstandung kommt kein Hinweis`() {
        val selector = HintSelector()

        // Gerades Bild, gut gerahmtes Gesicht nahe einem Drittel-Punkt.
        val result = selector.select(
            frame(tiltDegrees = 0f, faces = listOf(faceAt(1f / 3f, 1f / 3f, size = 0.25f))),
        )

        assertNull(result)
    }

    @Test
    fun `Portraet-Rahmung geht vor Horizont`() {
        val selector = HintSelector()

        // Beides gleichzeitig verletzt: Kopf am oberen Rand UND deutlich schiefer Horizont.
        val result = selector.select(
            frame(tiltDegrees = 12f, faces = listOf(faceAt(CENTER, 0.04f, size = 0.2f))),
        )

        assertNotNull(result)
        assertTrue(
            "Horizont" !in result.message,
            "erwartet wurde der Rahmungs-Hinweis, kam: ${result.message}",
        )
    }

    @Test
    fun `Horizont greift wenn die Rahmung passt`() {
        val selector = HintSelector()

        val result = selector.select(
            frame(tiltDegrees = 12f, faces = listOf(faceAt(1f / 3f, 1f / 3f, size = 0.25f))),
        )

        assertNotNull(result)
        assertTrue("Horizont" in result.message, "kam: ${result.message}")
    }

    @Test
    fun `der Drittel-Tracker wird zurueckgesetzt wenn das Motiv verschwindet`() {
        // Ohne Reset haelt der Tracker sein altes Ziel fest (Hysterese) und der erste
        // Hinweis nach einem Szenenwechsel zeigt auf einen Punkt, der zur neuen Szene
        // keinen Bezug hat.
        //
        // Geprueft wird am Tracker selbst, nicht am Hinweistext: der Text haengt an der
        // Formulierung der Regel, der Zustand ist das, worum es hier geht.
        val tracker = ThirdsTargetTracker()
        val selector = HintSelector(thirdsTracker = tracker)

        // Motiv links -> Tracker haelt den linken Drittel-Punkt
        selector.select(frame(faces = listOf(faceAt(0.15f, CENTER, size = 0.2f))))
        // Motiv verschwindet -> Reset
        selector.select(frame())

        // Jetzt direkt fragen: ein Motiv weit rechts muss den rechten Punkt bekommen.
        // Haette der Tracker den linken Punkt noch gehalten, bliebe er wegen der
        // Hysterese dabei.
        val target = tracker.select(x = 0.85f, y = CENTER, aspectRatio = 1f)

        assertEquals(2f / 3f, target.x, absoluteTolerance = 0.01f)
    }

    @Test
    fun `ohne jede Datenlage kommt kein Hinweis`() {
        // Leeres FrameAnalysis (Android ohne Saliency, kein Gesicht, Neigung unbestimmbar):
        // die App darf hier nichts erfinden.
        assertNull(HintSelector().select(frame()))
    }
}
