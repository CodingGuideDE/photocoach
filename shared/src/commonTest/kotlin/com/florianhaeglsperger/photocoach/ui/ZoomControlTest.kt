package com.florianhaeglsperger.photocoach.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZoomControlTest {

    @Test
    fun ultraWideDeviceOffersItsOwnMinimumAsFirstPreset() {
        // Kein pauschales 0,5×: manche Geraete kommen nur bis 0,6×.
        assertEquals(listOf(0.6f, 1f, 2f, 5f), zoomPresets(CameraZoom(1f, 0.6f, 10f), LensFacing.BACK))
    }

    @Test
    fun withoutUltraWideThereIsNoPresetBelowOne() {
        assertEquals(listOf(1f, 2f), zoomPresets(CameraZoom(1f, 1f, 4f), LensFacing.BACK))
    }

    @Test
    fun minimumJustBelowOneIsNotAnUltraWide() {
        // Rundungsrauschen von CameraX, kein eigenes Objektiv.
        assertEquals(listOf(1f, 2f), zoomPresets(CameraZoom(1f, 0.995f, 4f), LensFacing.BACK))
    }

    @Test
    fun cameraWithoutZoomHasSinglePreset() {
        // -> ZoomBar blendet sich aus.
        assertEquals(listOf(1f), zoomPresets(CameraZoom(1f, 1f, 1f), LensFacing.BACK))
    }

    @Test
    fun presetJustAboveMaxStillCountsWhenWithinTolerance() {
        assertEquals(listOf(1f, 2f), zoomPresets(CameraZoom(1f, 1f, 1.99f), LensFacing.BACK))
    }

    @Test
    fun selfieCameraStopsAtThreeInsteadOfFive() {
        assertEquals(listOf(1f, 2f, 3f), zoomPresets(CameraZoom(1f, 1f, 8f), LensFacing.FRONT))
    }

    @Test
    fun selfieCameraOnlyOffersThreeWhenItReachesIt() {
        assertEquals(listOf(1f, 2f), zoomPresets(CameraZoom(1f, 1f, 2.5f), LensFacing.FRONT))
    }

    @Test
    fun activePresetIsLargestNotAboveRatio() {
        val presets = listOf(0.5f, 1f, 2f, 5f)
        assertEquals(0.5f, activePreset(presets, 0.5f))
        assertEquals(0.5f, activePreset(presets, 0.8f))
        assertEquals(1f, activePreset(presets, 1.4f))
        assertEquals(2f, activePreset(presets, 3f))
        assertEquals(5f, activePreset(presets, 8f))
    }

    @Test
    fun activePresetToleratesRoundingNoise() {
        assertEquals(1f, activePreset(listOf(0.5f, 1f, 2f), 0.9999f))
    }

    @Test
    fun activePresetOfEmptyListIsNull() {
        assertNull(activePreset(emptyList(), 1f))
    }

    @Test
    fun formatsWithGermanCommaAndDropsTrailingZero() {
        assertEquals("0,5×", formatZoom(0.5f))
        assertEquals("1×", formatZoom(1f))
        assertEquals("1×", formatZoom(0.9999f))
        assertEquals("1,4×", formatZoom(1.43f))
        assertEquals("10×", formatZoom(10f))
    }

    @Test
    fun interpolationStartsAndEndsExactlyAtTheStages() {
        assertEquals(1f, interpolateZoom(1f, 5f, 0f), 1e-5f)
        assertEquals(5f, interpolateZoom(1f, 5f, 1f), 1e-5f)
    }

    @Test
    fun interpolationIsGeometricNotLinear() {
        // Halbe Strecke von 1× nach 4× ist 2× (gleicher Faktor je Haelfte), nicht 2,5×.
        assertEquals(2f, interpolateZoom(1f, 4f, 0.5f), 1e-5f)
        // Rueckwaerts genauso — und ins Ultraweitwinkel.
        assertEquals(2f, interpolateZoom(4f, 1f, 0.5f), 1e-5f)
        assertEquals(0.75f, interpolateZoom(1f, 0.5625f, 0.5f), 1e-5f)
    }
}
