package com.florianhaeglsperger.photocoach.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Akzentfarbe der App: aktive Zoomstufe, manuelle Pro-Werte, Aktionsleiste. Hier zentral,
 * damit auch Material-Bausteine (Dialog-Knoepfe, Buttons) sie tragen statt des
 * Material-Standard-Lilas.
 */
internal val ACCENT = Color(0xFFFFD54F)

/** Fuer zerstoererische Aktionen (Loeschen) — dieselbe Farbe wie ein schlechter Score. */
internal val DESTRUCTIVE = Color(0xFFFF8A65)

/**
 * Die Flaeche von Dialogen. Fast schwarz, aber nicht ganz: ein Dialog muss sich vom
 * schwarzen Sucher-Rand und vom abgedunkelten Kamerabild dahinter abheben.
 */
private val DIALOG_SURFACE = Color(0xFF1E1E1E)

/**
 * Dunkles Farbschema passend zum Sucher.
 *
 * Die App ist durchgehend dunkel (schwarzer Grund, weisse Schrift, halbtransparente schwarze
 * Panels). Ohne eigenes Schema griff `MaterialTheme` auf das **helle** Standard-Schema
 * zurueck — jeder Material-Dialog erschien dann als weisser Kasten mit lila Knoepfen mitten
 * in der dunklen Oberflaeche.
 */
private val PhotoCoachColors = darkColorScheme(
    primary = ACCENT,
    onPrimary = Color.Black,
    secondary = ACCENT,
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF141414),
    surfaceContainer = Color(0xFF181818),
    surfaceContainerHigh = DIALOG_SURFACE,
    surfaceContainerHighest = Color(0xFF262626),
    error = DESTRUCTIVE,
    onError = Color.Black,
    outline = Color.White.copy(alpha = 0.3f),
    scrim = Color.Black,
)

/** Das Theme der ganzen App — einmal in [App] gesetzt, gilt fuer jeden Dialog darunter. */
@Composable
fun PhotoCoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PhotoCoachColors, content = content)
}
