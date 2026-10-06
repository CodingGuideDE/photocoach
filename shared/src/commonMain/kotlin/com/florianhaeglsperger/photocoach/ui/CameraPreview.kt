package com.florianhaeglsperger.photocoach.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.florianhaeglsperger.photocoach.capture.ManualCapabilities
import com.florianhaeglsperger.photocoach.capture.ManualSettings
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis

/**
 * Ergebnis eines Ausloesevorgangs.
 *
 * [Success.location] ist bewusst ein anzeigbarer Text (z.B. "Pictures/PhotoCoach") und kein
 * Pfad/URI: der sieht auf jeder Plattform anders aus, und die UI in commonMain soll ihn nur
 * anzeigen, nicht interpretieren. Zum Wiederfinden dient [Success.photo] — eine
 * plattformneutrale Referenz, die nur die [PhotoLibrary] deutet. `null`, wenn die Plattform
 * keine liefert (der MediaStore meldet die URI nicht auf jedem Geraet zurueck).
 */
sealed interface CaptureResult {
    data class Success(val location: String, val photo: CapturedPhoto? = null) : CaptureResult
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
     *
     * [zoom] wird bei jeder Aenderung neu gemeldet (neue `Running`-Instanz), [setZoom]
     * begrenzt selbst auf den erlaubten Bereich. Der Zoom wirkt auf Vorschau, Analyse und
     * Foto gleichermassen — Hinweise und Aufnahme beziehen sich also auf denselben Ausschnitt.
     */
    data class Running(
        val takePhoto: (onResult: (CaptureResult) -> Unit) -> Unit,
        val lensFacing: LensFacing,
        val canSwitchLens: Boolean,
        val switchLens: () -> Unit,
        val zoom: CameraZoom,
        val setZoom: (Float) -> Unit,
        /**
         * Pro-Modus: was das aktive Objektiv manuell zulaesst, was gerade eingestellt ist,
         * und wie man es aendert. [setManualSettings] begrenzt selbst auf [manualCapabilities];
         * nach einem Objektivwechsel steht wieder alles auf Automatik.
         */
        val manualCapabilities: ManualCapabilities = ManualCapabilities.NONE,
        val manualSettings: ManualSettings = ManualSettings(),
        val setManualSettings: (ManualSettings) -> Unit = {},
    ) : CameraState

    /** Kamera konnte nicht gestartet werden (kein Geraet, belegt, Treiberfehler, ...). */
    data class Error(val message: String) : CameraState
}

/**
 * Seitenverhaeltnis (lange / kurze Seite) von Vorschau, Analyse und Foto.
 *
 * 4:3 ist das native Format praktisch aller Handy-Sensoren; jedes andere Format ist ein
 * Beschnitt. Die Plattform-Implementierungen fordern es explizit an, und [CameraScreen]
 * zeigt die Vorschau in genau diesem Format — so sieht der Nutzer im Sucher denselben
 * Ausschnitt, der gespeichert wird, und Drittel-Raster und Hinweise gelten fuer das Foto.
 */
const val SENSOR_ASPECT_RATIO = 4f / 3f

/**
 * Zoomstufe der aktiven Kamera. [min] unter 1 heisst: die Plattform schaltet unterhalb von
 * 1× selbsttaetig auf das Ultraweitwinkel um.
 */
data class CameraZoom(val ratio: Float, val min: Float, val max: Float)

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
