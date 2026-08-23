package com.florianhaeglsperger.photocoach.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Zustand der Kamera-Vorschau, wie ihn die Plattform-Implementierung nach oben meldet.
 *
 * Die Trennung ist bewusst so geschnitten: die *Mechanik* (Berechtigung anfragen, Kamera
 * binden) ist plattformspezifisch und steckt im jeweiligen `actual`, die *Darstellung*
 * (Texte, Layout, Buttons) liegt in commonMain und ist damit auf allen Plattformen gleich.
 */
sealed interface CameraState {

    /** Vorschau wird gerade aufgebaut — Berechtigung geprueft, Kamera noch nicht gebunden. */
    data object Initializing : CameraState

    /**
     * Der Nutzer hat die Kamera-Berechtigung (noch) nicht erteilt.
     * [requestPermission] loest einen erneuten Systemdialog aus.
     */
    data class PermissionRequired(val requestPermission: () -> Unit) : CameraState

    /** Kamera laeuft, das Vorschaubild ist sichtbar. */
    data object Running : CameraState

    /** Kamera konnte nicht gestartet werden (kein Geraet, belegt, Treiberfehler, ...). */
    data class Error(val message: String) : CameraState
}

/**
 * Zeigt das Live-Bild der Rueckkamera.
 *
 * Kuemmert sich selbst um die Laufzeit-Berechtigung und um das Binden/Loesen der Kamera
 * am Lifecycle. Der aktuelle Zustand wird ueber [onState] nach oben gemeldet, damit die
 * gemeinsame UI darueber legen kann, was gerade passend ist (siehe [CameraScreen]).
 *
 * Implementierungen:
 *  - Android: CameraX (`Preview`-Use-Case + `PreviewView`)
 *  - iOS: AVFoundation (`AVCaptureVideoPreviewLayer`) — folgt, sobald die iOS-Targets
 *    aktiv sind, siehe `shared/src/iosMain/README.md`
 */
@Composable
expect fun CameraPreview(
    modifier: Modifier = Modifier,
    onState: (CameraState) -> Unit = {},
)
