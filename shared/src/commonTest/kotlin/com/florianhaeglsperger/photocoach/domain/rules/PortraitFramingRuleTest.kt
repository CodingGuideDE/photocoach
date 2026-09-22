package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.faceAt
import com.florianhaeglsperger.photocoach.domain.frame
import com.florianhaeglsperger.photocoach.domain.model.FaceRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `Gesicht links der Mitte mit wenig Platz rechts warnt in Blickrichtung`() {
        // Bewusst per FaceRect statt faceAt konstruiert: die Mitte muss klar links liegen,
        // waehrend der rechte Rand fast den Bildrand erreicht — mit faceAt (symmetrisch um
        // die Mitte) laesst sich das nicht ausdruecken.
        val face = FaceRect(left = -0.1f, top = 0.3f, right = 0.95f, bottom = 0.7f)

        val hint = PortraitFramingRule.evaluate(frame(faces = listOf(face)))

        assertEquals("Wenig Platz in Blickrichtung — Kamera etwas nach rechts schwenken.", hint?.message)
    }

    @Test
    fun `Gesicht rechts der Mitte mit wenig Platz links warnt in Blickrichtung`() {
        val face = FaceRect(left = 0.05f, top = 0.3f, right = 1.1f, bottom = 0.7f)

        val hint = PortraitFramingRule.evaluate(frame(faces = listOf(face)))

        assertEquals("Wenig Platz in Blickrichtung — Kamera etwas nach links schwenken.", hint?.message)
    }

    @Test
    fun `Gesicht nahe der Mitte loest trotz wenig Randabstand keine Blickraum-Warnung aus`() {
        // Ohne die Zentrums-Totzone waere hier die "wenig Platz rechts"-Warnung faellig
        // (centerX = 0.46, rechter Rand nur 0.1 Platz) - so nah an der Mitte ist die
        // geschaetzte Blickrichtung aber nicht verlaesslich.
        val face = FaceRect(left = 0.02f, top = 0.3f, right = 0.9f, bottom = 0.7f)

        assertNull(PortraitFramingRule.evaluate(frame(faces = listOf(face))))
    }

    @Test
    fun `Kopf-Abschnitt hat Vorrang vor der Blickraum-Warnung`() {
        // Beides trifft zu: Kopf oben abgeschnitten UND wenig Platz rechts - laut Prioritaet
        // in der Regel muss der Kopf-Hinweis gewinnen.
        val face = FaceRect(left = -0.1f, top = 0.0f, right = 0.95f, bottom = 0.4f)

        val hint = PortraitFramingRule.evaluate(frame(faces = listOf(face)))

        assertEquals("Kopf fast abgeschnitten — Kamera etwas nach oben schwenken.", hint?.message)
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
