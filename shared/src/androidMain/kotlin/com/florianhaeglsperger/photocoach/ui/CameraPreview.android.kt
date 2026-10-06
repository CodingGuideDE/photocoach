package com.florianhaeglsperger.photocoach.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
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
import androidx.lifecycle.Observer
import com.florianhaeglsperger.photocoach.capture.CameraFrame
import com.florianhaeglsperger.photocoach.capture.FrameAnalyzer
import com.florianhaeglsperger.photocoach.capture.HorizonSensor
import com.florianhaeglsperger.photocoach.capture.ManualCameraControl
import com.florianhaeglsperger.photocoach.capture.ManualSettings
import com.florianhaeglsperger.photocoach.diagnostics.FieldLog
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import java.text.SimpleDateFormat
import java.util.concurrent.Executors
import java.util.Locale

/** Unterordner in der Galerie, in dem die Aufnahmen landen. */
internal const val ALBUM_NAME = "PhotoCoach"

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
    // Gewuenschtes Objektiv. Aendert sich das, laeuft der DisposableEffect unten erneut
    // und bindet die Kamera neu — CameraX kann das Objektiv nicht im laufenden Betrieb
    // wechseln.
    var desiredLens by remember { mutableStateOf(LensFacing.BACK) }
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
            // FIT statt FILL: CameraScreen gibt der Vorschau bereits einen 4:3-Rahmen. Liefert
            // ein Geraet ausnahmsweise kein 4:3, entstehen schmale Balken — besser als ein
            // Beschnitt, der weniger zeigt, als das Foto enthaelt.
            scaleType = PreviewView.ScaleType.FIT_CENTER
            // COMPATIBLE (TextureView) statt PERFORMANCE (SurfaceView): im Emulator
            // liefert SurfaceView je nach Image ein schwarzes Bild. Auf echter Hardware
            // spaeter ggf. auf PERFORMANCE umstellen (etwas geringere Latenz/Stromverbrauch).
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(sensorAspectRatio())
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
    // Pro-Modus (ISO, Verschlusszeit, ...). Haelt die letzten Messwerte der Automatik,
    // deshalb einmal pro Composable und nicht pro Bind.
    val manualControl = remember { ManualCameraControl() }
    // Tatsaechlich gebundenes Objektiv (kann vom gewuenschten abweichen, wenn es fehlt).
    // Der Analyzer braucht es, um die Frontkamera-Spiegelung auszugleichen.
    var activeLensFacing by remember { mutableStateOf(LensFacing.BACK) }

    // Der Horizont kommt auf Android aus dem Schwerkraft-Sensor, nicht aus dem Bild
    // (siehe HorizonSensor fuer die Einschraenkung, die das mit sich bringt).
    val horizonSensor = remember(context) { HorizonSensor(context) }

    /**
     * Haelt Foto-Ausrichtung und Horizont-Bezug aktuell, waehrend die App laeuft.
     *
     * **Zwei verschiedene Rotationen, bewusst getrennt behandelt:**
     *
     * - `imageCapture.targetRotation` richtet sich nach der *physischen* Lage des Geraets.
     *   Ein quer gehaltenes Geraet soll ein Querformat-Foto liefern, auch wenn die
     *   Bildschirmdrehung gesperrt ist. Deshalb der von [OrientationEventListener]
     *   abgeleitete Wert.
     * - `horizonSensor.displayRotation` richtet sich nach der *Bildschirm*-Drehung. Der
     *   Nutzer beurteilt "gerade" an dem, was er sieht. Bei gesperrter Drehung waere die
     *   physische Lage hier die falsche Bezugsgroesse — deshalb wird sie direkt vom
     *   Display gelesen und nicht aus dem Listener abgeleitet.
     *
     * Die beiden zu verwechseln faellt im Hochformat nicht auf und erst im Querformat.
     */
    val orientationListener = remember(context, previewView) {
        object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                imageCapture.targetRotation = orientation.toSurfaceRotation()
                horizonSensor.displayRotation =
                    previewView.display?.rotation ?: Surface.ROTATION_0
            }
        }
    }

    DisposableEffect(orientationListener) {
        if (orientationListener.canDetectOrientation()) orientationListener.enable()
        onDispose { orientationListener.disable() }
    }

    DisposableEffect(context) {
        FieldLog.attach(context)
        onDispose { }
    }

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
            // Selbes Format wie Vorschau und Foto, sonst beziehen sich die normalisierten
            // Koordinaten der Analyse auf einen anderen Ausschnitt als das Raster im Sucher.
            .setResolutionSelector(sensorAspectRatio())
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
                                CameraFrame(
                                    image = imageProxy,
                                    tiltDegrees = horizonSensor.currentTiltDegrees(),
                                    mirrored = activeLensFacing == LensFacing.FRONT,
                                ),
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

    DisposableEffect(previewView, imageAnalysis, permissionGranted, activity, desiredLens) {
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
        var stopZoomObserver: () -> Unit = {}
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener(
            {
                // Kann die Composition inzwischen verlassen haben — dann nicht mehr binden.
                if (disposed) return@addListener
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider

                    val hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)

                    // Gewuenschtes Objektiv, wenn vorhanden — sonst das andere. Emulator-Images
                    // haben nicht immer beide, und ein harter Fehler waere hier die schlechtere
                    // Antwort als "nimm was da ist".
                    val activeLens = when {
                        desiredLens == LensFacing.BACK && hasBack -> LensFacing.BACK
                        desiredLens == LensFacing.FRONT && hasFront -> LensFacing.FRONT
                        hasBack -> LensFacing.BACK
                        hasFront -> LensFacing.FRONT
                        else -> null
                    }

                    if (activeLens == null) {
                        cameraState = CameraState.Error("Keine Kamera auf diesem Geraet gefunden.")
                        return@addListener
                    }

                    val selector = if (activeLens == LensFacing.FRONT) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    activeLensFacing = activeLens

                    val preview = Preview.Builder()
                        .setResolutionSelector(sensorAspectRatio())
                        .also(manualControl::attachTo)
                        .build().apply {
                        surfaceProvider = previewView.surfaceProvider
                    }

                    // Ohne targetRotation wuerde das gespeicherte Foto im Querformat
                    // falsch herum liegen. Nur beim Binden gesetzt — dreht der Nutzer das
                    // Geraet waehrend die App laeuft, muesste ein OrientationEventListener
                    // nachziehen (offen, siehe CLAUDE.md).
                    // Startwerte. Ab hier haelt der OrientationEventListener oben beide
                    // aktuell — ohne ihn wuerde ein Drehen waehrend des Betriebs nicht
                    // ankommen (Foto landet falsch herum, Neigung falsch bezogen).
                    val displayRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                    imageCapture.targetRotation = displayRotation
                    horizonSensor.displayRotation = displayRotation

                    // unbindAll() vor dem Binden: sonst wirft CameraX beim erneuten
                    // Durchlauf (z.B. nach Berechtigungserteilung) einen Konflikt.
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        activity,
                        selector,
                        preview,
                        imageAnalysis,
                        imageCapture,
                    )

                    // Zoom ueber CameraControl statt Beschnitt im Nachhinein: der Sensor-
                    // Ausschnitt gilt dann fuer alle drei Use-Cases zugleich. Auf Geraeten,
                    // deren Rueckkamera eine logische Multi-Kamera ist (Pixel, neuere
                    // Samsung), liegt minZoomRatio unter 1 — Werte darunter schalten
                    // automatisch aufs Ultraweitwinkel, oben aufs Tele.
                    val zoomLiveData = camera.cameraInfo.zoomState
                    val setZoom: (Float) -> Unit = { ratio ->
                        val current = zoomLiveData.value
                        val clamped = if (current == null) {
                            ratio
                        } else {
                            ratio.coerceIn(current.minZoomRatio, current.maxZoomRatio)
                        }
                        // Das Future scheitert, wenn ein schnellerer Aufruf (Pinch) es
                        // ueberholt — gewollt, deshalb wird es nicht ausgewertet.
                        camera.cameraControl.setZoomRatio(clamped)
                    }

                    // Jede Bindung startet mit Automatik: Die Camera2-Optionen haengen an
                    // der gebundenen Kamera, und das neue Objektiv kann andere Bereiche haben.
                    val manualCapabilities = manualControl.capabilitiesOf(camera)
                    val setManualSettings: (ManualSettings) -> Unit = { requested ->
                        val settings = requested.coercedTo(manualCapabilities)
                        manualControl.apply(camera, settings, manualCapabilities)
                        (cameraState as? CameraState.Running)?.let {
                            cameraState = it.copy(manualSettings = settings)
                        }
                    }

                    cameraState = CameraState.Running(
                        takePhoto = { onResult ->
                            imageCapture.saveToGallery(context, onResult)
                        },
                        lensFacing = activeLens,
                        canSwitchLens = hasBack && hasFront,
                        switchLens = { desiredLens = activeLens.opposite() },
                        zoom = zoomLiveData.value?.toCameraZoom() ?: CameraZoom(1f, 1f, 1f),
                        setZoom = setZoom,
                        manualCapabilities = manualCapabilities,
                        manualSettings = ManualSettings(),
                        setManualSettings = setManualSettings,
                    )

                    // Jede Zoom-Aenderung (Button, Pinch) kommt hierueber zurueck und wird
                    // als neuer Running-Zustand gemeldet. Das Observe liefert den aktuellen
                    // Wert auch sofort einmal aus.
                    val observer = Observer<ZoomState> { zoomState ->
                        val running = cameraState as? CameraState.Running ?: return@Observer
                        cameraState = running.copy(zoom = zoomState.toCameraZoom())
                    }
                    zoomLiveData.observe(activity, observer)
                    stopZoomObserver = { zoomLiveData.removeObserver(observer) }
                } catch (t: Throwable) {
                    cameraState = CameraState.Error(t.message ?: t::class.java.simpleName)
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            disposed = true
            stopZoomObserver()
            // Kein clearAnalyzer() hier: der Analyzer wird nur einmal gesetzt (oben im
            // remember), dieser Effect laeuft aber bei jedem Objektivwechsel neu. Mit
            // clearAnalyzer() an dieser Stelle kam nach dem ersten Wechsel kein einziges
            // Frame mehr an. unbindAll() stoppt den Frame-Strom ohnehin.
            provider?.unbindAll()
        }
    }

    // Eigener Effect: der Executor haengt am Leben des Composables, nicht am
    // Bind-Zyklus der Kamera (der bei Berechtigungswechseln mehrfach durchlaeuft).
    DisposableEffect(analysisExecutor) {
        onDispose {
            imageAnalysis.clearAnalyzer()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(cameraState) { onState(cameraState) }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

/**
 * Fordert das Sensor-Format (4:3) an, siehe [SENSOR_ASPECT_RATIO]. Fallback auf das, was
 * das Geraet hat — ein harter Fehler waere die schlechtere Antwort.
 */
private fun sensorAspectRatio(): ResolutionSelector =
    ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .build()

private fun ZoomState.toCameraZoom() = CameraZoom(
    ratio = zoomRatio,
    min = minZoomRatio,
    max = maxZoomRatio,
)

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
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$PHOTO_NAME_PREFIX$timestamp.jpg")
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
                onResult(
                    CaptureResult.Success(
                        location = "Galerie › $ALBUM_NAME",
                        photo = output.savedUri?.let { CapturedPhoto(it.toString()) },
                    ),
                )
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

/**
 * Rechnet den Winkel des [OrientationEventListener] (0-359 Grad, im Uhrzeigersinn ab der
 * natuerlichen Lage) in eine `Surface.ROTATION_*`-Konstante um.
 *
 * Die Zuordnung ist gegenlaeufig, weil `Surface.ROTATION_*` die Drehung der *Grafik*
 * beschreibt und nicht die des Geraets — dieselbe Stolperfalle wie in `HorizonSensorTest`.
 * Entspricht dem dokumentierten CameraX-Muster.
 */
internal fun Int.toSurfaceRotation(): Int = when (this) {
    in 45 until 135 -> Surface.ROTATION_270
    in 135 until 225 -> Surface.ROTATION_180
    in 225 until 315 -> Surface.ROTATION_90
    else -> Surface.ROTATION_0
}
