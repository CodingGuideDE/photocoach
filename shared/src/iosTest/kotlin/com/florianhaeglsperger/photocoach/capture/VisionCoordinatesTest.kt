package com.florianhaeglsperger.photocoach.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testet die Umrechnung von Vision-Koordinaten in das Koordinatensystem von `FrameAnalysis`.
 *
 * Vision: Ursprung **unten links**, Y zeigt nach oben.
 * FrameAnalysis: Ursprung **oben links**, Y zeigt nach unten (wie Bildschirm und ML Kit).
 *
 * Ohne die Spiegelung sitzt jedes Gesicht vertikal vertauscht — eine Portraet-Regel wuerde
 * dann "Kopf zu weit oben" melden, wenn er zu weit unten sitzt.
 */
class VisionCoordinatesTest {

    @Test
    fun `Gesicht im oberen Bilddrittel landet oben`() {
        // Vision: unteres Ende bei y=0.7, Hoehe 0.2 -> reicht bis 0.9, also weit oben im Bild.
        val rect = visionBoxToFaceRect(originX = 0.4, originY = 0.7, width = 0.2, height = 0.2)

        assertEquals(0.1f, rect.top, absoluteTolerance = 0.001f)
        assertEquals(0.3f, rect.bottom, absoluteTolerance = 0.001f)
        assertTrue(rect.top < 0.5f, "Gesicht sollte in der oberen Bildhaelfte liegen")
    }

    @Test
    fun `Gesicht im unteren Bilddrittel landet unten`() {
        val rect = visionBoxToFaceRect(originX = 0.4, originY = 0.1, width = 0.2, height = 0.2)

        assertEquals(0.7f, rect.top, absoluteTolerance = 0.001f)
        assertEquals(0.9f, rect.bottom, absoluteTolerance = 0.001f)
        assertTrue(rect.top > 0.5f, "Gesicht sollte in der unteren Bildhaelfte liegen")
    }

    @Test
    fun `horizontale Achse wird nicht gespiegelt`() {
        // Nur Y ist gegenlaeufig — X zeigt in beiden Systemen nach rechts.
        val rect = visionBoxToFaceRect(originX = 0.1, originY = 0.4, width = 0.2, height = 0.2)

        assertEquals(0.1f, rect.left, absoluteTolerance = 0.001f)
        assertEquals(0.3f, rect.right, absoluteTolerance = 0.001f)
    }

    @Test
    fun `top ist immer kleiner als bottom`() {
        // Klingt trivial, ist aber genau das, was bei falschem Vorzeichen kaputtgeht.
        val rect = visionBoxToFaceRect(originX = 0.3, originY = 0.25, width = 0.4, height = 0.5)

        assertTrue(rect.top < rect.bottom, "top=${rect.top} bottom=${rect.bottom}")
        assertTrue(rect.left < rect.right, "left=${rect.left} right=${rect.right}")
    }

    @Test
    fun `angeschnittenes Gesicht wird auf den Bildbereich begrenzt`() {
        // Vision kann Boxen liefern, die ueber den Rand hinausragen. Regeln in domain/
        // duerfen sich darauf verlassen, dass Koordinaten in 0..1 liegen.
        val rect = visionBoxToFaceRect(originX = -0.1, originY = -0.1, width = 0.3, height = 0.3)

        assertTrue(rect.left in 0f..1f && rect.right in 0f..1f)
        assertTrue(rect.top in 0f..1f && rect.bottom in 0f..1f)
    }
}
