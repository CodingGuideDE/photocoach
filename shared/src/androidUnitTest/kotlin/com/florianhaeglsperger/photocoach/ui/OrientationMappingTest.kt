package com.florianhaeglsperger.photocoach.ui

import android.view.Surface
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testet die Umrechnung vom `OrientationEventListener`-Winkel auf `Surface.ROTATION_*`.
 *
 * Die Zuordnung ist **gegenlaeufig** — `Surface.ROTATION_*` beschreibt die Drehung der
 * Grafik, der Listener die des Geraets. Genau diese Verwechslung hat in diesem Projekt
 * schon einmal zugeschlagen (siehe `HorizonSensorTest`). Ein Fehler hier speichert jedes
 * Querformat-Foto um 90 Grad verdreht.
 */
class OrientationMappingTest {

    @Test
    fun `aufrecht ergibt ROTATION_0`() {
        assertEquals(Surface.ROTATION_0, 0.toSurfaceRotation())
        assertEquals(Surface.ROTATION_0, 10.toSurfaceRotation())
        assertEquals(Surface.ROTATION_0, 350.toSurfaceRotation())
    }

    @Test
    fun `Geraet im Uhrzeigersinn gedreht ergibt ROTATION_270`() {
        // Geraet 90 Grad im Uhrzeigersinn = Grafik muss gegen den Uhrzeigersinn kompensieren.
        assertEquals(Surface.ROTATION_270, 90.toSurfaceRotation())
    }

    @Test
    fun `kopfueber ergibt ROTATION_180`() {
        assertEquals(Surface.ROTATION_180, 180.toSurfaceRotation())
    }

    @Test
    fun `Geraet gegen den Uhrzeigersinn gedreht ergibt ROTATION_90`() {
        assertEquals(Surface.ROTATION_90, 270.toSurfaceRotation())
    }

    @Test
    fun `die vier Sektoren grenzen sauber aneinander`() {
        // Genau an den Grenzen: 45/135/225/315 gehoeren jeweils zum naechsten Sektor.
        assertEquals(Surface.ROTATION_270, 45.toSurfaceRotation())
        assertEquals(Surface.ROTATION_180, 135.toSurfaceRotation())
        assertEquals(Surface.ROTATION_90, 225.toSurfaceRotation())
        assertEquals(Surface.ROTATION_0, 315.toSurfaceRotation())
        assertEquals(Surface.ROTATION_0, 44.toSurfaceRotation().let { if (it == Surface.ROTATION_0) it else -1 })
    }

    @Test
    fun `jeder Winkel liefert eine gueltige Rotation`() {
        val valid = setOf(
            Surface.ROTATION_0, Surface.ROTATION_90,
            Surface.ROTATION_180, Surface.ROTATION_270,
        )
        (0..359).forEach { degrees ->
            assertEquals(true, degrees.toSurfaceRotation() in valid, "fehlgeschlagen bei $degrees")
        }
    }
}
