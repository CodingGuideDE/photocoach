package com.florianhaeglsperger.photocoach.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.florianhaeglsperger.photocoach.capture.CameraFrame
import com.florianhaeglsperger.photocoach.capture.FrameAnalyzer
import com.florianhaeglsperger.photocoach.capture.HorizonSensor
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import java.text.SimpleDateFormat
import java.util.concurrent.Executors
import java.util.Locale

/** Unterordner in der Galerie, in dem die Aufnahmen landen. */
private const val ALBUM_NAME = "PhotoCoach"

/**
 * Mindestabstand zwischen zwei Analysen (~10 Hz).
 *
 * Die Kamera liefert 30+ fps, aber jedes Frame zu analysieren waere bei den Modellen
 * aus Phase 1/2 Verschwendung von Akku und Rechenzeit — und der Nutzer koennte 30
 * Hinweis-Aktualisierungen pro Sekunde ohnehin nicht lesen. Siehe
 * Planung/Plan-zur-Umsetzung.md 3.1 und Planung/ML-Architektur.md 4.7.
 */
private const val ANALYSIS_INTERVAL_MS = 100L

/**
 * Android-Implementierung der Vorschau auf Basis von CameraX.
 *
 * Ablauf:
 *  1. Kamera-Berechtigung pruefen, beim ersten Anzeigen ggf. einmal automatisch anfragen
 *  2. `ProcessCameraProvider` besorgen (asynchron — daher der ListenableFuture-Listener)
 *  3. `Preview`-, `ImageAnalysis`- und `ImageCapture`-Use-Case an den Activity-Lifecycle
 *     binden
 *
 * Das Binden an den Lifecycle ist der Grund, warum CameraX hier gegenueber der rohen
 * Camera2-API die deutlich einfachere Wahl ist: Pausieren/Fortsetzen beim Wechsel in den
 * Hintergrund und das Freigeben der Kamera erledigt CameraX von selbst.
 */
@Composable
actual fun CameraPreview(
    modifier: Modifier,
    onState: (CameraState) -> Unit,
    onAnalysis: (FrameAnalysis) -> Unit,
) {
    val context = LocalContext.current

    // bindToLifecycle() braucht einen LifecycleOwner. Statt LocalLifecycleOwner wird die
    // Activity aus dem Context ausgepackt — das ist unabhaengig davon, welche
    // Lifecycle-Compose-Variante Compose Multiplatform gerade mitbringt.
    val activity = remember(context) { context.findComponentActivity() }

    var permissionGranted by remember { mutableStateOf(context.hasCameraPermission()) }
    var cameraState by remember { mutableStateOf<CameraState>(CameraState.Initializing) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        // Nur die Kamera entscheidet, ob die Vorschau laufen kann. Fehlt auf Alt-Geraeten
        // die Schreibberechtigung, laeuft die Vorschau trotzdem — dann scheitert erst das
        // Speichern, mit einer verstaendlichen Meldung.
        permissionGranted = result[Manifest.permission.CAMERA] ?: permissionGranted
    }

    val requestPermission: () -> Unit = remember(permissionLauncher) {
        { permissionLauncher.launch(requiredPermissions()) }
    }

    // Einmalig beim ersten Anzeigen fragen. Lehnt der Nutzer ab, uebernimmt der
    // PermissionRequired-Zustand mit einem expliziten Button — kein Dialog-Spam.
    LaunchedEffect(Unit) {
        if (!permissionGranted) requestPermission()
    }

    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // COMPATIBLE (TextureView) statt PERFORMANCE (SurfaceView): im Emulator
            // liefert SurfaceView je nach Image ein schwarzes Bild. Auf echter Hardware
            // spaeter ggf. auf PERFORMANCE umstellen (etwas geringere Latenz/Stromverbrauch).
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            // MINIMIZE_LATENCY statt MAXIMIZE_QUALITY: die App ist ein Sucher-Coach,
            // ein spuerbar traeger Ausloeser waere hier schlimmer als etwas weniger
            // aggressive Nachbearbeitung.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    // onAnalysis kann sich bei jeder Recomposition aendern; der CameraX-Analyzer wird aber
    // nur einmal gesetzt. rememberUpdatedState sorgt dafuer, dass er immer den aktuellen
    // Callback aufruft statt den vom ersten Durchlauf.
    val currentOnAnalysis by rememberUpdatedState(onAnalysis)

    val frameAnalyzer = remember { FrameAnalyzer() }

    // Der Horizont kommt auf Android aus dem Schwerkraft-Sensor, nicht aus dem Bild
    // (siehe HorizonSensor fuer die Einschraenkung, die das mit sich bringt).
    val horizonSensor = remember(context) { HorizonSensor(context) }

    DisposableEffect(horizonSensor) {
        horizonSensor.start()
        onDispose {
            horizonSensor.stop()
            // Analyzer haengt am selben Lebenszyklus: gibt den ML-Kit-Detektor frei.
            frameAnalyzer.close()
        }
    }

    // Eigener Thread fuer die Analyse: sie darf weder den Main-Thread blockieren (UI-Ruckler)
    // noch den CameraX-internen Thread (Vorschau-Ruckler). Ab Phase 1 laeuft hier die
    // eigentliche Modell-Inferenz.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val imageAnalysis = remember(context) {
        ImageAnalysis.Builder()
            // KEEP_ONLY_LATEST statt BLOCK_PRODUCER: lieber Frames verwerfen als eine
            // Warteschlange aufbauen. Veraltete Analyse-Ergebnisse waeren fuer einen
            // Live-Sucher wertlos — der Nutzer hat das Motiv laengst weitergeschwenkt.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                var lastAnalysisMs = 0L
                val mainExecutor = ContextCompat.getMainExecutor(context)

                setAnalyzer(analysisExecutor) { imageProxy ->
                    // ImageProxy MUSS in jedem Pfad geschlossen werden, auch beim
                    // Ueberspringen — sonst blockiert die Pipeline nach wenigen Frames.
                    try {
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastAnalysisMs >= ANALYSIS_INTERVAL_MS) {
                            lastAnalysisMs = nowMs
                            val result = frameAnalyzer.analyze(
                                CameraFrame(imageProxy, horizonSensor.currentTiltDegrees()),
                            )
                            // Zurueck auf den Main-Thread, damit die UI das Ergebnis
                            // direkt in Compose-State schreiben kann.
                            mainExecutor.execute { currentOnAnalysis(result) }
                        }
                    } finally {
                        imageProxy.close()
                    }
                }
            }
    }

    DisposableEffect(previewView, imageAnalysis, permissionGranted, activity) {
        if (!permissionGranted) {
            cameraState = CameraState.PermissionRequired(requestPermission)
            return@DisposableEffect onDispose { }
        }
        if (activity == null) {
            cameraState = CameraState.Error(
                "Kein ComponentActivity im Context gefunden — Kamera kann nicht an den " +
                    "Lifecycle gebunden werden.",
            )
            return@DisposableEffect onDispose { }
        }

        cameraState = CameraState.Initializing

        var disposed = false
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener(
            {
                // Kann die Composition inzwischen verlassen haben — dann nicht mehr binden.
                if (disposed) return@addListener
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider

                    // Emulator-Images haben nicht immer eine Rueckkamera konfiguriert,
                    // deshalb Fallback auf die Frontkamera statt harter Fehler.
                    val selector = when {
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                            CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                            CameraSelector.DEFAULT_FRONT_CAMERA

                        else -> null
                    }

                    if (selector == null) {
                        cameraState = CameraState.Error("Keine Kamera auf diesem Geraet gefunden.")
                        return@addListener
                    }

                    val preview = Preview.Builder().build().apply {
                        surfaceProvider = previewView.surfaceProvider
                    }

                    // Ohne targetRotation wuerde das gespeicherte Foto im Querformat
                    // falsch herum liegen. Nur beim Binden gesetzt — dreht der Nutzer das
                    // Geraet waehrend die App laeuft, muesste ein OrientationEventListener
                    // nachziehen (offen, siehe CLAUDE.md).
                    val displayRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                    imageCapture.targetRotation = displayRotation
                    // Ohne das meldet ein bewusst quer gehaltenes Geraet konstant 90 Grad
                    // Neigung, obwohl es fuer den Nutzer gerade ist.
                    horizonSensor.displayRotation = displayRotation

                    // unbindAll() vor dem Binden: sonst wirft CameraX beim erneuten
                    // Durchlauf (z.B. nach Berechtigungserteilung) einen Konflikt.
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        activity,
                        selector,
                        preview,
                        imageAnalysis,
                        imageCapture,
                    )

                    cameraState = CameraState.Running(
                        takePhoto = { onResult ->
                            imageCapture.saveToGallery(context, onResult)
                        },
                    )
                } catch (t: Throwable) {
                    cameraState = CameraState.Error(t.message ?: t::class.java.simpleName)
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            disposed = true
            provider?.unbindAll()
            imageAnalysis.clearAnalyzer()
        }
    }

    // Eigener Effect: der Executor haengt am Leben des Composables, nicht am
    // Bind-Zyklus der Kamera (der bei Berechtigungswechseln mehrfach durchlaeuft).
    DisposableEffect(analysisExecutor) {
        onDispose { analysisExecutor.shutdown() }
    }

    LaunchedEffect(cameraState) { onState(cameraState) }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

/**
 * Loest aus und legt das Foto ueber den MediaStore in der Galerie ab.
 *
 * Bewusst MediaStore und nicht der App-eigene Ordner: Fotos einer Kamera-App gehoeren in
 * die Galerie des Nutzers, bleiben dort nach einer Deinstallation erhalten und sind mit
 * anderen Apps teilbar. Ab API 29 braucht das keine Berechtigung mehr, darunter die
 * (im Manifest auf maxSdkVersion=28 begrenzte) Schreibberechtigung.
 */
private fun ImageCapture.saveToGallery(
    context: Context,
    onResult: (CaptureResult) -> Unit,
) {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        .format(System.currentTimeMillis())

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "PhotoCoach_$timestamp.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // RELATIVE_PATH gibt es erst ab API 29; darunter landet das Foto im
            // Standard-Bilderordner, was fuer diese Geraete akzeptabel ist.
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$ALBUM_NAME")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions
        .Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        .build()

    takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onResult(CaptureResult.Success("Galerie › $ALBUM_NAME"))
            }

            override fun onError(exception: ImageCaptureException) {
                onResult(
                    CaptureResult.Failure(
                        exception.message ?: "Foto konnte nicht gespeichert werden.",
                    ),
                )
            }
        },
    )
}

/**
 * Ab API 29 schreibt der MediaStore ohne Berechtigung (Scoped Storage). Nur auf
 * API 26–28 wird zusaetzlich die Schreibberechtigung gebraucht.
 */
private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    } else {
        arrayOf(Manifest.permission.CAMERA)
    }

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/** Packt die Activity aus einem moeglicherweise verschachtelten [ContextWrapper] aus. */
private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
