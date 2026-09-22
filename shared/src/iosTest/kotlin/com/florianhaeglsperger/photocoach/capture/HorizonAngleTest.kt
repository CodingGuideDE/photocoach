package com.florianhaeglsperger.photocoach.capture

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testet die Umrechnung von `VNHorizonObservation.angle` (Bogenmass) in Grad.
 *
 * Ein Faktor- oder Vorzeichenfehler hier wuerde die App den Horizont in die falsche
 * Richtung korrigieren lassen — derselbe Fehlertyp, den `HorizonSensorTest` auf der
 * Android-Seite abfaengt.
 */
class HorizonAngleTest {

    @Test
    fun `gerader Horizont ergibt null Grad`() {
        assertEquals(0f, horizonAngleToDegrees(0.0))
    }

    @Test
    fun `ein Viertelkreis ergibt 90 Grad`() {
        assertEquals(90f, horizonAngleToDegrees(PI / 2))
    }

    @Test
    fun `negatives Bogenmass bleibt negativ`() {
        // Faengt einen Vorzeichenfehler: bei abs() oder Negation waere das Ergebnis +45.
        assertEquals(-45f, horizonAngleToDegrees(-PI / 4))
    }

    @Test
    fun `typische kleine Neigung wird korrekt umgerechnet`() {
        // 2 Grad ist die Toleranzgrenze in HorizonRule — genau der Bereich, in dem die
        // Umrechnung stimmen muss. 2 Grad = 0.034907 rad.
        assertEquals(2f, horizonAngleToDegrees(0.034907), absoluteTolerance = 0.05f)
    }

    @Test
    fun `Ergebnis wird auf eine Nachkommastelle gerundet`() {
        // Gleiche Aufloesung wie auf Android, damit die geteilten Regeln auf beiden
        // Plattformen dieselben Werte sehen.
        val degrees = horizonAngleToDegrees(0.123456)
        assertEquals(degrees, (degrees * 10f).toInt() / 10f, absoluteTolerance = 0.001f)
    }

    @Test
    fun `Umrechnung ist nicht mit Grad-zu-Bogenmass verwechselt`() {
        // Der klassische Dreher: 1 rad sind knapp 57 Grad, nicht 0.017.
        assertTrue(
            horizonAngleToDegrees(1.0) > 50f,
            "1 rad muss ~57 Grad ergeben, war ${horizonAngleToDegrees(1.0)}",
        )
    }
}
