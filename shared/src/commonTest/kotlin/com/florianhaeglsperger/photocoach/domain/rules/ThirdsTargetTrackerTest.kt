package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.LANDSCAPE_16_9
import com.florianhaeglsperger.photocoach.domain.THIRD_LEFT
import com.florianhaeglsperger.photocoach.domain.THIRD_TOP
import com.florianhaeglsperger.photocoach.domain.geometry.NormalizedPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThirdsTargetTrackerTest {

    /** Ein Motiv, das um die Bildmitte herum zittert — der kritische Fall. */
    private val wackelnUmDieMitte =
        listOf(0.48f, 0.51f, 0.49f, 0.52f, 0.50f, 0.53f, 0.55f, 0.60f)

    private fun ThirdsTargetTracker.verfolge(xs: List<Float>): List<NormalizedPoint> =
        xs.map { select(it, THIRD_TOP, LANDSCAPE_16_9) }

    private fun List<NormalizedPoint>.wechsel(): Int =
        zipWithNext().count { (vorher, nachher) -> vorher != nachher }

    @Test
    fun `erster Aufruf nimmt den naechstgelegenen Punkt`() {
        val ziel = ThirdsTargetTracker().select(THIRD_LEFT, THIRD_TOP, LANDSCAPE_16_9)

        assertEquals(NormalizedPoint(THIRD_LEFT, THIRD_TOP), ziel)
    }

    @Test
    fun `zitterndes Motiv wechselt den Zielpunkt nur einmal`() {
        val verlauf = ThirdsTargetTracker().verfolge(wackelnUmDieMitte)

        assertEquals(1, verlauf.wechsel(), "Zielpunkt-Verlauf: $verlauf")
    }

    @Test
    fun `ohne Hysterese wuerde derselbe Verlauf mehrfach springen`() {
        // Belegt, dass der Test oben wirklich die Hysterese misst und nicht zufaellig
        // schon durch die Eingabe stabil ist.
        val verlauf = ThirdsTargetTracker(switchMargin = 0f).verfolge(wackelnUmDieMitte)

        assertTrue(verlauf.wechsel() > 1, "Zielpunkt-Verlauf: $verlauf")
    }

    @Test
    fun `deutlicher Wechsel wird uebernommen`() {
        val tracker = ThirdsTargetTracker()
        tracker.select(THIRD_LEFT, THIRD_TOP, LANDSCAPE_16_9)

        val ziel = tracker.select(2f / 3f, 2f / 3f, LANDSCAPE_16_9)

        assertEquals(NormalizedPoint(2f / 3f, 2f / 3f), ziel)
    }

    @Test
    fun `reset vergisst den gehaltenen Punkt`() {
        val tracker = ThirdsTargetTracker()
        tracker.select(THIRD_LEFT, THIRD_TOP, LANDSCAPE_16_9)
        tracker.reset()

        // Nach dem Zuruecksetzen greift keine Hysterese mehr: der naechstgelegene Punkt
        // gewinnt sofort, auch wenn der Abstand knapp ist.
        val ziel = tracker.select(0.51f, THIRD_TOP, LANDSCAPE_16_9)

        assertEquals(NormalizedPoint(2f / 3f, THIRD_TOP), ziel)
    }
}
