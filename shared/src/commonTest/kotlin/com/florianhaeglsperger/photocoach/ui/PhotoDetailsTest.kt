package com.florianhaeglsperger.photocoach.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoDetailsTest {

    @Test
    fun shortExposureIsShownAsFraction() {
        assertEquals("1/100 s", formatExposureTime(0.01))
        // 1/30 liegt als 0,0333… in der Datei — der Nenner wird gerundet.
        assertEquals("1/30 s", formatExposureTime(0.0333))
        assertEquals("1/8 s", formatExposureTime(0.12))
    }

    @Test
    fun longExposureIsShownAsSeconds() {
        assertEquals("1 s", formatExposureTime(1.0))
        assertEquals("2,5 s", formatExposureTime(2.5))
    }

    @Test
    fun apertureUsesGermanCommaAndDropsTrailingZero() {
        assertEquals("f/1,8", formatAperture(1.8))
        assertEquals("f/4", formatAperture(4.0))
    }

    @Test
    fun resolutionShowsPixelsAndMegapixels() {
        assertEquals("4032 × 3024 · 12,2 MP", formatResolution(4032, 3024))
        assertEquals("960 × 1280 · 1,2 MP", formatResolution(960, 1280))
    }

    @Test
    fun fileSizeUsesDecimalUnits() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("198 KB", formatFileSize(197_894))
        assertEquals("3,2 MB", formatFileSize(3_210_000))
    }

    @Test
    fun decimalFormatting() {
        assertEquals("4,3", formatDecimal(4.32, 1))
        assertEquals("4", formatDecimal(3.999, 1))
        assertEquals("0,05", formatDecimal(0.05, 2))
        assertEquals("1,5", formatDecimal(1.50, 2))
    }

    private val sample = PhotoDetails(
        takenDate = "Dienstag, 6. Oktober 2026",
        takenTime = "20:37",
        widthPx = 960,
        heightPx = 1280,
        sizeBytes = 197_894,
        exposureTimeSeconds = 0.01,
        fNumber = 4.0,
        iso = 200,
        flashFired = false,
        device = "Google Pixel 8",
        fileName = "PhotoCoach_20261006_203715.jpg",
        album = "Pictures/PhotoCoach",
    )

    @Test
    fun exposureTilesInCameraOrderAndSkipMissing() {
        // Brennweite fehlt hier — die Kachel faellt weg, statt "—" zu zeigen.
        assertEquals(
            listOf("f/4" to "Blende", "1/100 s" to "Belichtung", "200" to "ISO"),
            sample.exposureTiles(),
        )
    }

    @Test
    fun detailRowsSkipMissingValuesAndKeepOrder() {
        // Weissabgleich fehlt hier.
        assertEquals(
            listOf(
                "Auflösung" to "960 × 1280 · 1,2 MP",
                "Dateigröße" to "198 KB",
                "Blitz" to "Aus",
            ),
            sample.detailRows(),
        )
    }

    @Test
    fun subtitleJoinsTimeAndDevice() {
        assertEquals("20:37 · Google Pixel 8", sample.subtitle())
        assertEquals("Google Pixel 8", sample.copy(takenTime = null).subtitle())
        assertEquals(null, PhotoDetails().subtitle())
    }

    @Test
    fun emptyDetailsHaveNothingToShow() {
        assertEquals(emptyList(), PhotoDetails().exposureTiles())
        assertEquals(emptyList(), PhotoDetails().detailRows())
    }

    @Test
    fun zeroValuesFromIncompleteExifAreHidden() {
        // Manche Kameras schreiben 0 statt das Feld wegzulassen.
        val zeros = PhotoDetails(iso = 0, fNumber = 0.0, widthPx = 0, heightPx = 0, exposureTimeSeconds = 0.0)
        assertEquals(emptyList(), zeros.exposureTiles())
        assertEquals(emptyList(), zeros.detailRows())
    }
}
