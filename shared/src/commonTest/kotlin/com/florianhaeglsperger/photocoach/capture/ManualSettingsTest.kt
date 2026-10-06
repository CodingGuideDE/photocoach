package com.florianhaeglsperger.photocoach.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManualSettingsTest {

    private val ms = 1_000_000L
    private val s = 1_000_000_000L

    private val backCamera = ManualCapabilities(
        exposureCompensation = ExposureCompensation(minIndex = -6, maxIndex = 6, stepEv = 1f / 3f),
        isoStops = isoStopsWithin(100, 3200),
        exposureTimeStopsNs = exposureStopsWithin(100_000L, 2 * s),
        whiteBalanceModes = WhiteBalance.entries,
        minFocusDiopters = 10f,
    )

    @Test
    fun `ISO-Stufen bleiben im Sensorbereich`() {
        assertEquals(listOf(100, 125, 160, 200), isoStopsWithin(90, 220))
    }

    @Test
    fun `eine einzige ISO-Stufe heisst nicht einstellbar`() {
        assertEquals(emptyList(), isoStopsWithin(100, 110))
    }

    @Test
    fun `Verschlusszeiten enden bei einer Sekunde auch wenn der Sensor laenger kann`() {
        val stops = exposureStopsWithin(100_000L, 30 * s)
        assertEquals(s, stops.last())
        assertEquals(s / 8000, stops.first())
    }

    @Test
    fun `Verschlusszeiten respektieren die kuerzeste Zeit des Sensors`() {
        val stops = exposureStopsWithin(minNs = 1 * ms, maxNs = s)
        assertEquals(s / 1000, stops.first())
    }

    @Test
    fun `Standardeinstellung ist alles Auto`() {
        assertTrue(ManualSettings().isAllAuto)
        assertFalse(ManualSettings().isManualExposure)
        assertFalse(ManualSettings(iso = 400).isAllAuto)
    }

    @Test
    fun `manuelle ISO allein schaltet die Belichtungsautomatik ab`() {
        assertTrue(ManualSettings(iso = 400).isManualExposure)
        assertTrue(ManualSettings(exposureTimeNs = s / 125).isManualExposure)
        // Belichtungskorrektur ist eine Vorgabe an die Automatik, keine manuelle Belichtung.
        assertFalse(ManualSettings(exposureCompensation = 3).isManualExposure)
    }

    @Test
    fun `Objektiv ohne manuelle Steuerung setzt alles auf Auto zurueck`() {
        val set = ManualSettings(
            exposureCompensation = 2,
            iso = 800,
            exposureTimeNs = s / 60,
            whiteBalance = WhiteBalance.SHADE,
            focusDiopters = 3f,
        )
        assertEquals(ManualSettings(), set.coercedTo(ManualCapabilities.NONE))
    }

    @Test
    fun `Werte ausserhalb des Bereichs werden auf den naechsten erlaubten gezogen`() {
        val set = ManualSettings(exposureCompensation = 12, iso = 12800, focusDiopters = 20f)
            .coercedTo(backCamera)
        assertEquals(6, set.exposureCompensation)
        assertEquals(3200, set.iso)
        assertEquals(10f, set.focusDiopters)
    }

    @Test
    fun `nicht angebotener Weissabgleich faellt auf Auto zurueck`() {
        val caps = backCamera.copy(whiteBalanceModes = listOf(WhiteBalance.AUTO, WhiteBalance.DAYLIGHT))
        assertEquals(
            WhiteBalance.AUTO,
            ManualSettings(whiteBalance = WhiteBalance.SHADE).coercedTo(caps).whiteBalance,
        )
    }

    @Test
    fun `Verschlusszeit wird lesbar formatiert`() {
        assertEquals("1/125", formatExposureTime(s / 125))
        assertEquals("1/60", formatExposureTime(s / 60))
        assertEquals("1", formatExposureTime(s))
    }

    @Test
    fun `Belichtungskorrektur mit Vorzeichen und deutschem Komma`() {
        assertEquals("±0", formatEv(0f))
        assertEquals("+0,7", formatEv(2 * (1f / 3f)))
        assertEquals("−1,3", formatEv(-4 * (1f / 3f)))
        assertEquals("+2", formatEv(2f))
    }

    @Test
    fun `Fokusabstand in Metern und 0 Dioptrien ist unendlich`() {
        assertEquals("∞", formatFocusDistance(0f))
        assertEquals("0,1 m", formatFocusDistance(10f))
        assertEquals("2,5 m", formatFocusDistance(0.4f))
        assertEquals("20 m", formatFocusDistance(0.05f))
    }

    @Test
    fun `Protokoll-Zusammenfassung nennt nur die manuellen Werte`() {
        assertEquals("Auto", ManualSettings().summary(stepEv = 1f / 3f))
        assertEquals(
            "ISO 400 · 1/125 s · WB Tageslicht",
            ManualSettings(iso = 400, exposureTimeNs = s / 125, whiteBalance = WhiteBalance.DAYLIGHT)
                .summary(stepEv = 1f / 3f),
        )
    }

    @Test
    fun `nearestTo waehlt den naechstgelegenen Wert`() {
        assertEquals(400, listOf(100, 200, 400, 800).nearestTo(450))
        assertNull(emptyList<Int>().nearestTo(450))
    }
}
