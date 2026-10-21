package com.florianhaeglsperger.photocoach.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TrackingGateTest {

    /** Rahmen von ([l],[t]) bis ([r],[b]); Flaeche = Rahmenflaeche, Schwerpunkt = Mitte. */
    private fun obj(l: Float, t: Float, r: Float, b: Float, area: Float = (r - l) * (b - t)) =
        SegmentedObject(l, t, r, b, centerX = (l + r) / 2, centerY = (t + b) / 2, areaFraction = area)

    private val cup = obj(0.4f, 0.4f, 0.6f, 0.6f) // 4 % des Bildes, mittig

    @Test
    fun firstDetectionOfAnObjectIsAccepted() {
        assertIs<TrackDecision.Accept>(TrackingGate().judge(cup))
    }

    @Test
    fun wholeScreenIsNeverAcceptedAsTheObject() {
        // Genau der gemeldete Fehler: Objekt verloren, das Modell markiert den Hintergrund.
        val gate = TrackingGate()
        gate.judge(cup)
        val decision = gate.judge(obj(0f, 0f, 1f, 1f, area = 0.95f))
        assertEquals(TrackRejection.BACKGROUND, assertIs<TrackDecision.Hold>(decision).reason)
        assertEquals(cup, decision.lastGood)
    }

    @Test
    fun backgroundAtTheFirstTapIsRejectedToo() {
        // Tipp auf eine Wand: das Modell liefert fast das ganze Bild — kein Rahmen darum.
        val decision = TrackingGate().judge(obj(0f, 0f, 1f, 1f, area = 0.9f))
        assertEquals(null, assertIs<TrackDecision.Hold>(decision).lastGood)
    }

    @Test
    fun regionTouchingThreeEdgesLooksLikeBackground() {
        val gate = TrackingGate()
        gate.judge(cup)
        // Unteres Bilddrittel bis zu drei Raendern, halbes Bild — der Boden, nicht die Tasse.
        val floor = obj(0f, 0.45f, 1f, 1f, area = 0.5f)
        assertEquals(TrackRejection.BACKGROUND, TrackingGate.rejectionOf(floor, cup))
    }

    @Test
    fun aCloseUpThatAlreadyFilledTheFrameMayKeepFillingIt() {
        // Nah dran: schon das gute Objekt stand an drei Raendern — dann ist das kein Hintergrund.
        val closeUp = obj(0f, 0.2f, 1f, 1f, area = 0.6f)
        val next = obj(0f, 0.15f, 1f, 1f, area = 0.65f)
        assertEquals(null, TrackingGate.rejectionOf(next, closeUp))
    }

    @Test
    fun suddenGrowthIsRejected() {
        val bigger = obj(0.3f, 0.3f, 0.7f, 0.7f) // 16 % statt 4 %: das Vierfache
        assertEquals(TrackRejection.GREW, TrackingGate.rejectionOf(bigger, cup))
    }

    @Test
    fun gradualGrowthFromApproachingIsAccepted() {
        val closer = obj(0.37f, 0.37f, 0.63f, 0.63f) // ~6,8 %: Naehertreten zwischen zwei Bildern
        assertEquals(null, TrackingGate.rejectionOf(closer, cup))
    }

    @Test
    fun suddenShrinkIsRejected() {
        val speck = obj(0.49f, 0.49f, 0.51f, 0.51f, area = 0.005f)
        assertEquals(TrackRejection.SHRANK, TrackingGate.rejectionOf(speck, cup))
    }

    @Test
    fun jumpToADifferentPlaceIsRejected() {
        val elsewhere = obj(0.05f, 0.05f, 0.25f, 0.25f)
        assertEquals(TrackRejection.JUMPED, TrackingGate.rejectionOf(elsewhere, cup))
    }

    @Test
    fun movingWithTheCameraIsAccepted() {
        // Leicht verschoben, ohne Ueberlappung zu verlieren — normales Schwenken.
        val moved = obj(0.48f, 0.42f, 0.68f, 0.62f)
        assertEquals(null, TrackingGate.rejectionOf(moved, cup))
    }

    @Test
    fun objectIsLostOnlyAfterRepeatedMisses() {
        val gate = TrackingGate()
        gate.judge(cup)
        repeat(TrackingGate.MAX_MISSES - 1) { assertIs<TrackDecision.Hold>(gate.judge(null)) }
        assertIs<TrackDecision.Lose>(gate.judge(null))
    }

    @Test
    fun oneGoodDetectionResetsTheMissCounter() {
        val gate = TrackingGate()
        gate.judge(cup)
        repeat(TrackingGate.MAX_MISSES - 1) { gate.judge(null) }
        assertIs<TrackDecision.Accept>(gate.judge(cup))
        repeat(TrackingGate.MAX_MISSES - 1) { assertIs<TrackDecision.Hold>(gate.judge(null)) }
    }

    @Test
    fun rejectedDetectionsDoNotBecomeTheNewReference() {
        // Sonst wuerde der Hintergrund nach einem Fehlgriff zum Massstab fuer den naechsten.
        val gate = TrackingGate()
        gate.judge(cup)
        gate.judge(obj(0f, 0f, 1f, 1f, area = 0.95f))
        val decision = gate.judge(obj(0.41f, 0.41f, 0.61f, 0.61f))
        assertIs<TrackDecision.Accept>(decision)
    }

    @Test
    fun resetForgetsEverything() {
        val gate = TrackingGate()
        gate.judge(cup)
        gate.reset()
        // Ohne Bezug wird ein anderes Objekt an anderer Stelle als erste Erkennung angenommen.
        assertIs<TrackDecision.Accept>(gate.judge(obj(0.05f, 0.05f, 0.25f, 0.25f)))
    }

    @Test
    fun overlapOfIdenticalBoxesIsOne() {
        assertEquals(1f, TrackingGate.overlap(cup, cup), 1e-5f)
    }
}
