package com.florianhaeglsperger.photocoach.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * iOS: es gibt noch keine Aufnahme (pausiert, siehe CLAUDE.md), also auch nichts zu zeigen.
 * Existiert nur, damit die gemeinsame UI kompiliert. Das echte Gegenstueck liest spaeter ueber
 * `PHAsset` die eigenen Aufnahmen.
 */
@Composable
actual fun rememberPhotoLibrary(): PhotoLibrary = NoPhotoLibrary

private object NoPhotoLibrary : PhotoLibrary {
    override suspend fun latest(): CapturedPhoto? = null
    override suspend fun all(): List<CapturedPhoto> = emptyList()
    override suspend fun load(photo: CapturedPhoto, maxDimension: Int): ImageBitmap? = null
    override suspend fun delete(photo: CapturedPhoto): Boolean = false
    override suspend fun details(photo: CapturedPhoto): PhotoDetails? = null
    override fun share(photo: CapturedPhoto): Boolean = false
}

/** iOS hat keine systemweite Zurueck-Taste; geschlossen wird ueber den Schliessen-Knopf. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
