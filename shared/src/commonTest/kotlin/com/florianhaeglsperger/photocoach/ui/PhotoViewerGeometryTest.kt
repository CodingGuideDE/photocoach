package com.florianhaeglsperger.photocoach.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoViewerGeometryTest {

    private val portraitScreen = Size(1080f, 2400f)

    @Test
    fun `Hochformat-Foto fuellt die Breite und sitzt vertikal mittig`() {
        val rect = fittedRect(portraitScreen, aspect = 3f / 4f)

        assertEquals(0f, rect.left)
        assertEquals(1080f, rect.width)
        assertEquals(1440f, rect.height, 0.5f)
        assertEquals(480f, rect.top, 0.5f)
    }

    @Test
    fun `Querformat-Foto auf Hochformat-Bildschirm wird nicht beschnitten`() {
        val rect = fittedRect(portraitScreen, aspect = 4f / 3f)

        assertEquals(1080f, rect.width)
        assertEquals(810f, rect.height, 0.5f)
    }

    @Test
    fun `ohne Vergroesserung laesst sich nichts verschieben`() {
        val image = Rect(0f, 480f, 1080f, 1920f)

        val clamped = clampPan(Offset(300f, -200f), image, portraitScreen, zoom = 1f)

        // Komponentenweise mit Toleranz: aus coerceIn(-0, 0) kommt -0.0, und Offset
        // vergleicht die Bitmuster.
        assertEquals(0f, clamped.x, 0.001f)
        assertEquals(0f, clamped.y, 0.001f)
    }

    @Test
    fun `vergroessert stoesst die Bildkante hoechstens an den Bildschirmrand`() {
        val image = Rect(0f, 480f, 1080f, 1920f)

        // Bei 2x ist das Bild 2160 breit: 540 px Ueberstand je Seite. In der Hoehe 2880 bei
        // 2400 Bildschirm: 240 px je Seite.
        val clamped = clampPan(Offset(5000f, -5000f), image, portraitScreen, zoom = 2f)

        assertEquals(Offset(540f, -240f), clamped)
    }

    @Test
    fun `weit genug gezogen schliesst auch ohne Schwung`() {
        assertEquals(true, shouldDismiss(dragY = 400f, velocityY = 0f, containerHeight = 2400f, flingVelocity = 2600f))
    }

    @Test
    fun `kurzer langsamer Zug federt zurueck`() {
        assertEquals(false, shouldDismiss(dragY = 120f, velocityY = 300f, containerHeight = 2400f, flingVelocity = 2600f))
    }

    @Test
    fun `kurzes schnelles Schnippen nach unten schliesst`() {
        assertEquals(true, shouldDismiss(dragY = 120f, velocityY = 4000f, containerHeight = 2400f, flingVelocity = 2600f))
    }

    @Test
    fun `Schwung ohne Weg nach unten schliesst nicht`() {
        // Zurueck auf 0 gezogen und dann schnell losgelassen: das ist kein Wegwischen.
        assertEquals(false, shouldDismiss(dragY = 0f, velocityY = 4000f, containerHeight = 2400f, flingVelocity = 2600f))
    }

    @Test
    fun `beim Zoomen bleibt der Punkt unter dem Finger stehen`() {
        val pan = Offset(30f, -20f)
        val zoom = 1.5f
        val focus = Offset(200f, -100f) // Fingerposition relativ zur Bildmitte
        val factor = 2f

        val newPan = panForZoomAround(pan, focus, factor)

        // Bildpunkt unter dem Finger vorher: (focus - pan) / zoom. Nachher muss er wieder
        // bei focus erscheinen: punkt * (zoom * factor) + newPan == focus.
        val imagePoint = (focus - pan) / zoom
        val shownAt = imagePoint * (zoom * factor) + newPan
        assertEquals(focus.x, shownAt.x, 0.01f)
        assertEquals(focus.y, shownAt.y, 0.01f)
    }

    // --- Blaettern -------------------------------------------------------------------

    private fun step(dragX: Float, velocityX: Float = 0f, hasNewer: Boolean = true, hasOlder: Boolean = true) =
        pageStep(dragX, velocityX, pageWidth = 1080f, flingVelocity = 1600f, hasNewer = hasNewer, hasOlder = hasOlder)

    @Test
    fun `weit nach links gezogen blaettert zum aelteren Foto`() {
        assertEquals(1, step(dragX = -400f))
    }

    @Test
    fun `weit nach rechts gezogen blaettert zum neueren Foto`() {
        assertEquals(-1, step(dragX = 400f))
    }

    @Test
    fun `kurzer langsamer Zug rastet zurueck`() {
        assertEquals(0, step(dragX = -100f, velocityX = -300f))
    }

    @Test
    fun `kurzes Schnippen blaettert`() {
        assertEquals(1, step(dragX = -80f, velocityX = -3000f))
    }

    @Test
    fun `Schnippen gegen die Zugrichtung blaettert nicht`() {
        // Erst nach links gezogen, dann schnell zurueck nach rechts: der Nutzer hat es sich
        // anders ueberlegt — weder vor noch zurueck.
        assertEquals(0, step(dragX = -80f, velocityX = 3000f))
    }

    @Test
    fun `am Ende der Reihe gibt es nichts zu blaettern`() {
        assertEquals(0, step(dragX = -600f, hasOlder = false))
        assertEquals(0, step(dragX = 600f, hasNewer = false))
    }
}
