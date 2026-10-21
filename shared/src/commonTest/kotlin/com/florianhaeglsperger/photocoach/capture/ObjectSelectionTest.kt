package com.florianhaeglsperger.photocoach.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ObjectSelectionTest {

    /** Maske [w]x[h] mit 1.0 in den angegebenen Rechtecken (Pixel, Ende exklusiv). */
    private fun mask(w: Int, h: Int, vararg rects: IntArray): FloatArray {
        val m = FloatArray(w * h)
        for (r in rects) for (y in r[1] until r[3]) for (x in r[0] until r[2]) m[y * w + x] = 1f
        return m
    }

    @Test
    fun boundsOfTheTappedObject() {
        val m = mask(100, 100, intArrayOf(20, 30, 60, 80))
        val obj = assertNotNull(regionAt(m, 100, 100, 0.4f, 0.5f))
        assertEquals(0.2f, obj.left, 1e-4f)
        assertEquals(0.3f, obj.top, 1e-4f)
        assertEquals(0.6f, obj.right, 1e-4f)
        assertEquals(0.8f, obj.bottom, 1e-4f)
        assertEquals(0.2f, obj.areaFraction, 1e-4f)
        assertEquals(0.4f, obj.centerX, 1e-3f)
        assertEquals(0.55f, obj.centerY, 1e-3f)
    }

    @Test
    fun strayBlobElsewhereDoesNotInflateTheBox() {
        // Objekt links, ein Fleck des Modells am rechten Rand: der Rahmen bleibt beim Objekt.
        val m = mask(100, 100, intArrayOf(10, 10, 40, 40), intArrayOf(90, 85, 95, 95))
        val obj = assertNotNull(regionAt(m, 100, 100, 0.2f, 0.2f))
        assertEquals(0.4f, obj.right, 1e-4f)
        assertEquals(0.4f, obj.bottom, 1e-4f)
    }

    @Test
    fun tapJustBesideAThinEdgeStillHitsTheObject() {
        val m = mask(100, 100, intArrayOf(50, 10, 52, 90))
        val obj = assertNotNull(regionAt(m, 100, 100, 0.47f, 0.5f))
        assertEquals(0.5f, obj.left, 1e-4f)
    }

    @Test
    fun tapIntoEmptySpaceFindsNothing() {
        val m = mask(100, 100, intArrayOf(80, 80, 95, 95))
        assertNull(regionAt(m, 100, 100, 0.1f, 0.1f))
    }

    @Test
    fun specksBelowMinimumAreaAreIgnored() {
        val m = mask(100, 100, intArrayOf(50, 50, 51, 51))
        assertNull(regionAt(m, 100, 100, 0.5f, 0.5f))
    }

    @Test
    fun respectsTheThreshold() {
        val m = FloatArray(100 * 100) { 0.4f }
        assertNull(regionAt(m, 100, 100, 0.5f, 0.5f))
        assertNotNull(regionAt(m, 100, 100, 0.5f, 0.5f, threshold = 0.3f))
    }

    @Test
    fun largeObjectDoesNotOverflow() {
        // Ganze Flaeche 768 x 768 — iterativer Flood-Fill, keine Rekursion.
        val m = FloatArray(768 * 768) { 1f }
        assertEquals(1f, assertNotNull(regionAt(m, 768, 768, 0.5f, 0.5f)).areaFraction, 1e-4f)
    }
}
