package com.florianhaeglsperger.photocoach.domain.geometry

import com.florianhaeglsperger.photocoach.domain.LANDSCAPE_16_9
import com.florianhaeglsperger.photocoach.domain.PORTRAIT_9_16
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThirdsTest {

    @Test
    fun `es gibt genau vier Schnittpunkte`() {
        assertEquals(4, THIRDS_POINTS.size)
        assertEquals(4, THIRDS_POINTS.toSet().size)
    }

    @Test
    fun `waagerechter Abstand ist unabhaengig vom Seitenverhaeltnis`() {
        val quer = distanceInWidths(0.2f, 0.5f, 0.5f, 0.5f, LANDSCAPE_16_9)
        val hoch = distanceInWidths(0.2f, 0.5f, 0.5f, 0.5f, PORTRAIT_9_16)

        assertEquals(0.3f, quer, 0.001f)
        assertEquals(quer, hoch, 0.001f)
    }

    @Test
    fun `senkrechter Abstand haengt am Seitenverhaeltnis`() {
        // Derselbe normierte Versatz ist im Hochformat die laengere physische Strecke —
        // genau das rechnet distanceInWidths heraus.
        val quer = distanceInWidths(0.5f, 0.2f, 0.5f, 0.5f, LANDSCAPE_16_9)
        val hoch = distanceInWidths(0.5f, 0.2f, 0.5f, 0.5f, PORTRAIT_9_16)

        assertTrue(hoch > quer, "hoch=$hoch war nicht groesser als quer=$quer")
        assertEquals(0.3f / LANDSCAPE_16_9, quer, 0.001f)
        assertEquals(0.3f / PORTRAIT_9_16, hoch, 0.001f)
    }

    @Test
    fun `Score ist 1 genau auf dem Punkt`() {
        assertEquals(1f, thirdsScore(0f))
    }

    @Test
    fun `Score bleibt 1 innerhalb des perfekten Umkreises`() {
        assertEquals(1f, thirdsScore(THIRDS_PERFECT_RADIUS))
    }

    @Test
    fun `Score ist 0 ab der Toleranzgrenze`() {
        assertEquals(0f, thirdsScore(THIRDS_TOLERANCE))
        assertEquals(0f, thirdsScore(1f))
    }

    @Test
    fun `Score faellt zwischen den Grenzen monoton`() {
        val steps = (0..10).map { thirdsScore(THIRDS_PERFECT_RADIUS + it * 0.012f) }

        steps.zipWithNext().forEach { (vorher, nachher) ->
            assertTrue(nachher <= vorher, "Score stieg an: $vorher -> $nachher")
        }
    }
}
