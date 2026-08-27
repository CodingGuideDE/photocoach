package com.florianhaeglsperger.photocoach.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testet die Test-Helfer selbst.
 *
 * Klingt zirkulaer, ist es aber nicht: sobald ab Phase 1 jede Regel ueber [faceAt] und
 * [subjectAt] beschrieben wird, wuerde ein Fehler hier stillschweigend *alle* Regel-Tests
 * verfaelschen — sie waeren gruen, obwohl die Regel etwas anderes bekommt als gedacht.
 *
 * Dient gleichzeitig als Beleg, dass die commonTest-Infrastruktur laeuft
 * (`./gradlew :shared:allTests`).
 */
class TestFramesTest {

    @Test
    fun `frame ohne Angaben ist neutral`() {
        val result = frame()

        assertNull(result.horizonTiltDegrees)
        assertTrue(result.saliencyRegions.isEmpty())
        assertTrue(result.faces.isEmpty())
    }

    @Test
    fun `faceAt zentriert die Box um den angegebenen Punkt`() {
        val face = faceAt(centerX = 0.5f, centerY = 0.4f, size = 0.2f)

        assertEquals(0.4f, face.left)
        assertEquals(0.6f, face.right)
        assertEquals(0.3f, face.top)
        assertEquals(0.5f, face.bottom)
    }

    @Test
    fun `Drittel-Konstanten liegen auf den erwarteten Linien`() {
        // Nicht auf Gleichheit mit 0.333f pruefen: THIRD_LEFT ist als Bruch gerechnet,
        // ein Literal waere ungenauer als der Wert selbst.
        assertEquals(1f / 3f, THIRD_LEFT)
        assertEquals(2f / 3f, THIRD_RIGHT)
        assertTrue(THIRD_LEFT < CENTER && CENTER < THIRD_RIGHT)
    }

    @Test
    fun `frame uebernimmt gesetzte Werte`() {
        val result = frame(
            tiltDegrees = -3.5f,
            saliency = listOf(subjectAt(THIRD_LEFT, CENTER)),
            faces = listOf(faceAt(CENTER, THIRD_TOP)),
        )

        assertEquals(-3.5f, result.horizonTiltDegrees)
        assertEquals(1, result.saliencyRegions.size)
        assertEquals(THIRD_LEFT, result.saliencyRegions.first().x)
        assertEquals(1, result.faces.size)
    }
}
