package com.florianhaeglsperger.photocoach.capture

import android.view.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testet die Winkel-Mathematik hinter der Horizont-Erkennung.
 *
 * Ohne diese Tests waeren Vorzeichen und Rotations-Normalisierung nur behauptet — man
 * merkt einen Vorzeichenfehler sonst erst, wenn die App auf einem echten Geraet in die
 * falsche Richtung korrigiert.
 *
 * Bezugsgroesse ist der Schwerkraft-Vektor in Geraete-Koordinaten: aufrecht im Hochformat
 * liest der Sensor (0, 9.81, 0).
 */
class HorizonSensorTest {

    @Test
    fun `aufrecht gehaltenes Geraet meldet keine Neigung`() {
        val tilt = tiltFromGravity(gravityX = 0f, gravityY = 9.81f, displayRotation = Surface.ROTATION_0)

        assertEquals(0f, tilt)
    }

    @Test
    fun `nach rechts geneigt ergibt positiven Winkel`() {
        // 30 Grad: (sin 30, cos 30) * 9.81 = (4.905, 8.496)
        val tilt = tiltFromGravity(gravityX = 4.905f, gravityY = 8.496f, displayRotation = Surface.ROTATION_0)

        assertTrue(tilt != null && tilt > 0f, "erwartet positiv, war $tilt")
        assertEquals(30f, tilt!!, absoluteTolerance = 0.2f)
    }

    @Test
    fun `nach links geneigt ergibt negativen Winkel`() {
        val tilt = tiltFromGravity(gravityX = -4.905f, gravityY = 8.496f, displayRotation = Surface.ROTATION_0)

        assertTrue(tilt != null && tilt < 0f, "erwartet negativ, war $tilt")
        assertEquals(-30f, tilt!!, absoluteTolerance = 0.2f)
    }

    // Zur Konvention, weil sie sich leicht falsch herum merkt und beide Faelle hier schon
    // einmal vertauscht waren: `Display.getRotation()` meldet die Drehung der *Grafik*,
    // nicht des Geraets — beides ist gegenlaeufig. Android-Doku: "if the device is rotated
    // 90 degrees counter-clockwise ... the returned value will be Surface.ROTATION_90".
    //
    // ROTATION_90  = Geraet gegen den Uhrzeigersinn gedreht, Oberkante zeigt nach LINKS.
    //                Geraete-X zeigt dann nach oben, Schwerkraft liegt auf -X.
    // ROTATION_270 = Geraet im Uhrzeigersinn gedreht, Oberkante nach RECHTS,
    //                Schwerkraft auf +X.

    @Test
    fun `quer mit Oberkante links gilt als gerade`() {
        val tilt = tiltFromGravity(gravityX = -9.81f, gravityY = 0f, displayRotation = Surface.ROTATION_90)

        assertEquals(0f, tilt, "ohne Normalisierung gegen die Display-Rotation kaeme -90 heraus")
    }

    @Test
    fun `quer mit Oberkante rechts gilt als gerade`() {
        val tilt = tiltFromGravity(gravityX = 9.81f, gravityY = 0f, displayRotation = Surface.ROTATION_270)

        assertEquals(0f, tilt)
    }

    @Test
    fun `quer gehalten und dabei zusaetzlich geneigt`() {
        // Oberkante links (ROTATION_90) und dabei 15 Grad nach rechts gekippt:
        // Grundstellung ist (-9.81, 0), die Neigung dreht den Vektor um 15 Grad.
        // (sin(-90+15), cos(-90+15)) * 9.81 = (-9.476, 2.539)
        val tilt = tiltFromGravity(gravityX = -9.476f, gravityY = 2.539f, displayRotation = Surface.ROTATION_90)

        assertEquals(15f, tilt!!, absoluteTolerance = 0.2f)
    }

    @Test
    fun `flach liegendes Geraet meldet keinen Wert`() {
        // Auf dem Tisch: Schwerkraft steckt fast ganz in Z, X und Y sind nahe null.
        val tilt = tiltFromGravity(gravityX = 0.1f, gravityY = 0.2f, displayRotation = Surface.ROTATION_0)

        assertNull(tilt, "bei flach liegendem Geraet ist die seitliche Neigung nicht bestimmbar")
    }

    @Test
    fun `Ergebnis bleibt im Bereich -180 bis 180`() {
        // Kopfueber: hier koennte eine fehlende Normalisierung 270 statt -90 liefern.
        val tilt = tiltFromGravity(gravityX = 0f, gravityY = -9.81f, displayRotation = Surface.ROTATION_90)

        assertTrue(tilt != null && tilt in -180f..180f, "ausserhalb des Bereichs: $tilt")
    }
}
