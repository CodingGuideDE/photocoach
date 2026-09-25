package com.florianhaeglsperger.photocoach.domain.model

import com.florianhaeglsperger.photocoach.domain.CENTER
import com.florianhaeglsperger.photocoach.domain.faceAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testet die Spiegelung fuer die Frontkamera.
 *
 * Ein Fehler hier schickt den Nutzer beim Selfie exakt in die falsche Richtung — und faellt
 * bei der Rueckkamera nie auf, weil dort nicht gespiegelt wird. Also genau die Sorte Fehler,
 * die man erst beim Nutzer bemerkt.
 */
class MirroringTest {

    @Test
    fun `Gesicht links landet rechts`() {
        val mirrored = faceAt(centerX = 0.2f, centerY = CENTER, size = 0.2f).mirroredHorizontally()

        assertEquals(0.7f, mirrored.left, absoluteTolerance = 0.001f)
        assertEquals(0.9f, mirrored.right, absoluteTolerance = 0.001f)
    }

    @Test
    fun `Gesicht rechts landet links`() {
        val mirrored = faceAt(centerX = 0.8f, centerY = CENTER, size = 0.2f).mirroredHorizontally()

        assertTrue(mirrored.right < CENTER, "erwartet linke Bildhaelfte, war ${mirrored.right}")
    }

    @Test
    fun `left bleibt kleiner als right`() {
        // Der naheliegende Fehler waere ein simples `left = 1 - left`, das die Box umdreht.
        val mirrored = faceAt(centerX = 0.25f, centerY = CENTER, size = 0.3f).mirroredHorizontally()

        assertTrue(mirrored.left < mirrored.right, "Box verdreht: $mirrored")
    }

    @Test
    fun `senkrechte Achse bleibt unangetastet`() {
        val face = faceAt(centerX = 0.3f, centerY = 0.25f, size = 0.2f)

        val mirrored = face.mirroredHorizontally()

        assertEquals(face.top, mirrored.top)
        assertEquals(face.bottom, mirrored.bottom)
    }

    @Test
    fun `mittiges Gesicht bleibt mittig`() {
        val face = faceAt(centerX = CENTER, centerY = CENTER, size = 0.2f)

        val mirrored = face.mirroredHorizontally()

        assertEquals(face.left, mirrored.left, absoluteTolerance = 0.001f)
        assertEquals(face.right, mirrored.right, absoluteTolerance = 0.001f)
    }

    @Test
    fun `zweimal spiegeln ergibt das Original`() {
        val face = faceAt(centerX = 0.18f, centerY = 0.4f, size = 0.22f)

        val twice = face.mirroredHorizontally().mirroredHorizontally()

        assertEquals(face.left, twice.left, absoluteTolerance = 0.001f)
        assertEquals(face.right, twice.right, absoluteTolerance = 0.001f)
    }

    @Test
    fun `Saliency-Punkt wird an derselben Achse gespiegelt`() {
        val point = SaliencyPoint(x = 0.25f, y = 0.4f, weight = 1f).mirroredHorizontally()

        assertEquals(0.75f, point.x, absoluteTolerance = 0.001f)
        assertEquals(0.4f, point.y)
    }
}
