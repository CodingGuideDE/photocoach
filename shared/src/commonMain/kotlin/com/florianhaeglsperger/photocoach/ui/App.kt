package com.florianhaeglsperger.photocoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Gemeinsamer UI-Einstiegspunkt fuer alle Plattformen. Wird von androidApp/MainActivity.kt
 * (spaeter auch von iosApp) aufgerufen. Aktuell nur ein Platzhalter-Screen fuer Phase 0
 * ("App startet, zeigt etwas an") — CameraScreen/ScoreOverlay kommen in Phase 1/2 dazu.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("PhotoCoach")
                Text("Phase 0 – Grundgeruest laeuft.")
            }
        }
    }
}
