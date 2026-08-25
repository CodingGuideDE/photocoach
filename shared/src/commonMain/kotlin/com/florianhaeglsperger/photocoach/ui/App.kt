package com.florianhaeglsperger.photocoach.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Gemeinsamer UI-Einstiegspunkt fuer alle Plattformen. Wird von androidApp/MainActivity.kt
 * (spaeter auch von iosApp) aufgerufen. Aktuell direkt der Sucher-Screen — Navigation
 * (Sucher <-> Galerie) kommt in Phase 2 dazu, wenn es einen zweiten Screen gibt.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            CameraScreen()
        }
    }
}
