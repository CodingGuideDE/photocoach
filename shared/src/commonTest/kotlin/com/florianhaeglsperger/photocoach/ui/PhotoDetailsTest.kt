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

    @Test
    fun rowsSkipMissingValuesAndKeepOrder() {
        val details = PhotoDetails(
            takenAt = "6. Okt. 2026, 20:37",
            widthPx = 960,
            heightPx = 1280,
            exposureTimeSeconds = 0.01,
            iso = 200,
            flashFired = false,
            fileName = "PhotoCoach_20261006_203715.jpg",
            album = "Pictures/PhotoCoach",
        )
        assertEquals(
            listOf(
                "Aufgenommen" to "6. Okt. 2026, 20:37",
                "Auflösung" to "960 × 1280 · 1,2 MP",
                "Belichtungszeit" to "1/100 s",
                "ISO" to "200",
                "Blitz" to "Aus",
                "Datei" to "Pictures/PhotoCoach/PhotoCoach_20261006_203715.jpg",
            ),
            details.rows(),
        )
    }

    @Test
    fun emptyDetailsHaveNoRows() {
        assertEquals(emptyList(), PhotoDetails().rows())
    }

    @Test
    fun zeroValuesFromIncompleteExifAreHidden() {
        // Manche Kameras schreiben 0 statt das Feld wegzulassen.
        assertEquals(emptyList(), PhotoDetails(iso = 0, fNumber = 0.0, widthPx = 0, heightPx = 0).rows())
    }
}
