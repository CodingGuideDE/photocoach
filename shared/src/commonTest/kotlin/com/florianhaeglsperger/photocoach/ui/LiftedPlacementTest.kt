package com.florianhaeglsperger.photocoach.ui

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiftedPlacementTest {

    /** Wo das Foto nach dem Anheben liegt (graphicsLayer skaliert um die Bildmitte). */
    private fun placed(image: Rect, free: Rect): Rect {
        val p = liftedPlacement(image, free)
        val cx = image.center.x + p.offsetX
        val cy = image.center.y + p.offsetY
        val w = image.width * p.scale
        val h = image.height * p.scale
        return Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    private fun Rect.containsRect(other: Rect) =
        other.left >= left - 0.5f && other.top >= top - 0.5f &&
            other.right <= right + 0.5f && other.bottom <= bottom + 0.5f

    @Test
    fun portraitPhotoEndsUpFullyAboveTheSheet() {
        // Hochformat: 3:4-Foto bildschirmbreit, Panel unten 700 px hoch.
        val image = Rect(0f, 480f, 1080f, 1920f)
        val free = Rect(0f, 150f, 1080f, 2400f - 700f - 40f)
        val r = placed(image, free)
        assertTrue(free.containsRect(r), "$r liegt nicht in $free")
    }

    @Test
    fun photoIsCenteredInTheFreeArea() {
        val image = Rect(0f, 480f, 1080f, 1920f)
        val free = Rect(0f, 150f, 1080f, 1660f)
        val r = placed(image, free)
        assertEquals(r.top - free.top, free.bottom - r.bottom, 0.5f)
        assertEquals(r.left - free.left, free.right - r.right, 0.5f)
    }

    @Test
    fun landscapePhotoMovesLeftBesideTheSidePanel() {
        // Querformat: 4:3-Foto mittig, Panel rechts — das Foto weicht nach links aus.
        val image = Rect(600f, 0f, 1840f, 930f)
        val free = Rect(40f, 80f, 2400f - 945f - 40f - 40f, 1080f - 40f)
        val r = placed(image, free)
        assertTrue(free.containsRect(r), "$r liegt nicht in $free")
        assertTrue(r.center.x < image.center.x, "Foto sollte nach links ruecken")
    }

    @Test
    fun neverEnlargesEvenWhenThereIsRoom() {
        val image = Rect(0f, 900f, 1080f, 1500f)
        assertEquals(0.92f, liftedPlacement(image, Rect(0f, 0f, 1080f, 2000f)).scale, 1e-4f)
    }

    @Test
    fun keepsAMinimumSizeWhenThereIsAlmostNoRoom() {
        val image = Rect(0f, 480f, 1080f, 1920f)
        assertEquals(0.2f, liftedPlacement(image, Rect(0f, 150f, 1080f, 160f)).scale, 1e-4f)
    }
}
