package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.faceAt
import com.florianhaeglsperger.photocoach.domain.frame
import com.florianhaeglsperger.photocoach.domain.model.FaceRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortraitFramingRuleTest {

    @Test
    fun `kein Gesicht ergibt keinen Hinweis`() {
        assertNull(PortraitFramingRule.evaluate(frame()))
    }

    @Test
    fun `zentriertes gut gerahmtes Gesicht ergibt keinen Hinweis`() {
        val face = faceAt(centerX = 0.5f, centerY = 0.5f, size = 0.3f)

        assertNull(PortraitFramingRule.evaluate(frame(faces = listOf(face))))
    }

    @Test
    fun `Kopf zu nah am oberen Rand warnt`() {
        val face = faceAt(centerX = 0.5f, centerY = 0.1f, size = 0.2f) // top = 0.0

        val hint = PortraitFramingRule.evaluate(frame(faces = listOf(face)))

        assertEquals("Kopf fast abgeschnitten — Kamera etwas nach oben schwenken.", hint?.message)
    }

    @Test
    fun `Kinn zu nah am unteren Rand warnt`() {
        val face = faceAt(centerX = 0.5f, centerY = 0.9f, size = 0.2f) // bottom = 1.0

        val hint = PortraitFramingRule.evaluate(frame(faces = listOf(face)))

        assertEquals("Kinn fast abgeschnitten — Kamera etwas nach unten schwenken.", hint?.message)
    }

    @Test
    fun `Gesicht am linken Rand warnt vor seitlichem Anschnitt`() {
        val face = FaceRect(left = 0f, top = 0.3f, right = 0.3f, bottom = 0.6f)

        val hint = PortraitFramingRule.evaluate(frame(faces = listOf(face)))

        assertEquals(
            "Gesicht am linken Rand angeschnitten — Kamera etwas nach links schwenken.",
            hint?.message,
        )
    }

    @Test
    fun `Gesicht am rechten Rand warnt vor seitlichem Anschnitt`() {
        val face = FaceRect(left = 0.75f, top = 0.3f, right = 0.995f, bottom = 0.6f)

        val hint = PortraitFramingRule.evaluate(frame(faces = listOf(face)))

        assertEquals(
            "Gesicht am rechten Rand angeschnitten — Kamera etwas nach rechts schwenken.",
            hint?.message,
        )
    }

    @Test
    fun `Gesicht am Drittel mit Abstand zum Rand ist unkritisch`() {
        // Regressionsfall zur frueheren Blickraum-Pruefung: die konnte mit echten, auf 0..1
        // begrenzten Rechtecken nie ausloesen. Die Nachfolgerin darf hier ebenfalls
        // schweigen — aber weil der Rand frei ist, nicht weil sie nie greift.
        val face = faceAt(centerX = 1f / 3f, centerY = 0.4f, size = 0.25f)

        val assessment = PortraitFramingRule.assess(frame(faces = listOf(face)))

        assertNull(assessment?.hint)
        assertEquals(1f, assessment?.score)
    }

    @Test
    fun `Kopf-Abschnitt hat Vorrang vor dem seitlichen Anschnitt`() {
        // Beides trifft zu: Kopf oben abgeschnitten UND am linken Rand - laut Prioritaet
        // in der Regel muss der Kopf-Hinweis gewinnen.
        val face = FaceRect(left = 0f, top = 0.0f, right = 0.4f, bottom = 0.4f)

        val hint = PortraitFramingRule.evaluate(frame(faces = listOf(face)))

        assertEquals("Kopf fast abgeschnitten — Kamera etwas nach oben schwenken.", hint?.message)
    }

    @Test
    fun `Score sinkt - je naeher das Gesicht dem Rand kommt`() {
        fun scoreAtTop(top: Float) = PortraitFramingRule.assess(
            frame(faces = listOf(FaceRect(left = 0.4f, top = top, right = 0.6f, bottom = top + 0.2f))),
        )!!.score

        assertTrue(scoreAtTop(0.2f) > scoreAtTop(0.08f))
        assertTrue(scoreAtTop(0.08f) > scoreAtTop(0.02f))
        assertEquals(0f, scoreAtTop(0f))
    }

    @Test
    fun `nur das groesste Gesicht zaehlt`() {
        // Kleines Gesicht im Hintergrund ist schlecht gerahmt (Kopf abgeschnitten), das
        // groessere im Vordergrund ist einwandfrei — die Regel darf sich nicht vom
        // Hintergrund-Gesicht verwirren lassen.
        val backgroundFace = faceAt(centerX = 0.1f, centerY = 0.02f, size = 0.05f)
        val foregroundFace = faceAt(centerX = 0.5f, centerY = 0.5f, size = 0.4f)

        val hint = PortraitFramingRule.evaluate(
            frame(faces = listOf(backgroundFace, foregroundFace)),
        )

        assertNull(hint)
    }
}
