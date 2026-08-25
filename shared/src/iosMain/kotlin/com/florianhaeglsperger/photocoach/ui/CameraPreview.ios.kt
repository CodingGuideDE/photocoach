package com.florianhaeglsperger.photocoach.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS-Implementierung, bewusst kleinerer Umfang als CameraPreview.android.kt (Stand: Xcode
 * gerade erst installiert, noch kein physisches iPhone zum Testen).
 *
 * Was hier schon echt ist und sich im Simulator verifizieren laesst: die Kamera-Berechtigung
 * wird ueber den echten Systemdialog abgefragt (`AVCaptureDevice.requestAccessForMediaType`),
 * und `AVAuthorizationStatus` bestimmt den [CameraState].
 *
 * Was bewusst fehlt: `AVCaptureSession` mit Preview-Layer, `AVCaptureVideoDataOutput` fuer
 * die Frame-Analyse und `AVCapturePhotoOutput` fuers Ausloesen (das AVFoundation-Gegenstueck
 * zu CameraPreview.android.kt). Grund: der iOS-Simulator hat keine Kamera-Hardware —
 * `AVCaptureDevice.defaultDeviceWithMediaType` liefert dort immer `null` — die Session-Logik
 * liesse sich also nur blind schreiben, nicht verifizieren. Kommt in Phase 1 zusammen mit dem
 * Test auf einem echten iPhone, siehe `shared/src/iosMain/README.md`.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(
    modifier: Modifier,
    onState: (CameraState) -> Unit,
    onAnalysis: (FrameAnalysis) -> Unit,
) {
    var state by remember { mutableStateOf<CameraState>(CameraState.Initializing) }

    val requestPermission: () -> Unit = remember {
        {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                // Der Completion-Handler kommt auf einem AVFoundation-eigenen Hintergrund-
                // Thread zurueck - State-Aenderungen muessen aber auf den Main-Thread.
                dispatch_async(dispatch_get_main_queue()) {
                    state = evaluateCameraState()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> state = evaluateCameraState()
            AVAuthorizationStatusNotDetermined -> requestPermission()
            else -> state = CameraState.PermissionRequired(requestPermission)
        }
    }

    LaunchedEffect(state) { onState(state) }

    // Kein Vorschaubild in diesem Zwischenstand (siehe Klassenkommentar) - CameraScreen
    // (commonMain) legt ueber `state` sowieso schon einen Status-/Fehlertext darueber.
    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize())
}

@OptIn(ExperimentalForeignApi::class)
private fun evaluateCameraState(): CameraState {
    if (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) != AVAuthorizationStatusAuthorized) {
        return CameraState.PermissionRequired {}
    }
    val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
    return if (device == null) {
        CameraState.Error(
            "Keine Kamera gefunden. Normal im Simulator (der hat keine Kamera-Hardware) — " +
                "auf einem echten iPhone testen, sobald AVCaptureSession angebunden ist.",
        )
    } else {
        CameraState.Error(
            "Kamera gefunden (${device.localizedName}), aber AVCaptureSession/Preview/" +
                "Aufnahme ist hier noch nicht angebunden (Phase 1, siehe README).",
        )
    }
}
