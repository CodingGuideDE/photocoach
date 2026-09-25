package com.florianhaeglsperger.photocoach.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis

/**
 * Ergebnis eines Ausloesevorgangs.
 *
 * [Success.location] ist bewusst ein anzeigbarer Text (z.B. "Pictures/PhotoCoach") und kein
 * Pfad/URI: der sieht auf jeder Plattform anders aus, und die UI in commonMain soll ihn nur
 * anzeigen, nicht interpretieren. Ab Phase 2 (Galerie mit Score pro Foto) kommt hier eine
 * plattformneutrale Foto-ID dazu.
 */
sealed interface CaptureResult {
    data class Success(val location: String) : CaptureResult
    data class Failure(val message: String) : CaptureResult
}

/**
 * Zustand der Kamera-Vorschau, wie ihn die Plattform-Implementierung nach oben meldet.
 *
 * Die Trennung ist bewusst so geschnitten: die *Mechanik* (Berechtigung anfragen, Kamera
 * binden, Foto speichern) ist plattformspezifisch und steckt im jeweiligen `actual`, die
 * *Darstellung* (Texte, Layout, Buttons) liegt in commonMain und ist damit auf allen
 * Plattformen gleich. Jeder Zustand traegt die Aktionen, die in ihm moeglich sind.
 */
sealed interface CameraState {

    /** Vorschau wird gerade aufgebaut — Berechtigung geprueft, Kamera noch nicht gebunden. */
    data object Initializing : CameraState

    /**
     * Der Nutzer hat die Kamera-Berechtigung (noch) nicht erteilt.
     * [requestPermission] loest einen erneuten Systemdialog aus.
     */
    data class PermissionRequired(val requestPermission: () -> Unit) : CameraState

    /**
     * Kamera laeuft, das Vorschaubild ist sichtbar.
     *
     * [takePhoto] loest aus und speichert das Ergebnis auf dem Geraet; das Ergebnis kommt
     * asynchron ueber den uebergebenen Callback zurueck (Speichern dauert je nach Geraet
     * einige hundert Millisekunden).
     *
     * [canSwitchLens] ist `false`, wenn das Geraet nur eine Kamera hat — dann wird der
     * Umschalter gar nicht erst angezeigt, statt einen Knopf ohne Wirkung anzubieten.
     */
    data class Running(
        val takePhoto: (onResult: (CaptureResult) -> Unit) -> Unit,
        val lensFacing: LensFacing,
        val canSwitchLens: Boolean,
        val switchLens: () -> Unit,
    ) : CameraState

    /** Kamera konnte nicht gestartet werden (kein Geraet, belegt, Treiberfehler, ...). */
    data class Error(val message: String) : CameraState
}

/** Welche der beiden Kameras gerade aktiv ist. */
enum class LensFacing {
    /** Rueckkamera — der Normalfall fuer diese App. */
    BACK,

    /** Frontkamera. Das Vorschaubild ist dabei gespiegelt, siehe `FaceRect.mirroredHorizontally`. */
    FRONT,
    ;

    fun opposite(): LensFacing = if (this == BACK) FRONT else BACK
}

/**
 * Zeigt das Live-Bild der Rueckkamera, analysiert laufend Frames und kann Fotos aufnehmen.
 *
 * Kuemmert sich selbst um die Laufzeit-Berechtigung und um das Binden/Loesen der Kamera
 * am Lifecycle. Der aktuelle Zustand wird ueber [onState] nach oben gemeldet, damit die
 * gemeinsame UI darueber legen kann, was gerade passend ist (siehe [CameraScreen]).
 *
 * [onAnalysis] liefert das Ergebnis der laufenden Frame-Analyse — gedrosselt auf ca. 10 Hz
 * und immer auf dem Main-Thread, damit die UI es direkt in State schreiben kann. Das ist
 * die Leitung, an der ab Phase 1 das ScoreOverlay haengt.
 *
 * Implementierungen:
 *  - Android: CameraX (`Preview`-, `ImageAnalysis`- und `ImageCapture`-Use-Case,
 *    Speichern via MediaStore)
 *  - iOS: AVFoundation (`AVCaptureVideoPreviewLayer` + `AVCaptureVideoDataOutput` +
 *    `AVCapturePhotoOutput`, Speichern via `PHPhotoLibrary`) — folgt, sobald die
 *    iOS-Targets aktiv sind, siehe `shared/src/iosMain/README.md`
 */
@Composable
expect fun CameraPreview(
    modifier: Modifier = Modifier,
    onState: (CameraState) -> Unit = {},
    onAnalysis: (FrameAnalysis) -> Unit = {},
)
