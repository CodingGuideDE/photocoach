package com.florianhaeglsperger.photocoach.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Gemeinsamer UI-Einstiegspunkt fuer alle Plattformen. Wird von androidApp/MainActivity.kt
 * (spaeter auch von iosApp) aufgerufen. Aktuell direkt der Sucher-Screen — Navigation
 * (Sucher <-> Galerie) kommt in Phase 2 dazu, wenn es einen zweiten Screen gibt.
 */
@Composable
fun App() {
    PhotoCoachTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            CameraScreen()
        }
    }
}
