package com.florianhaeglsperger.photocoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.florianhaeglsperger.photocoach.diagnostics.FieldLog
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import com.florianhaeglsperger.photocoach.domain.scoring.CompositionScorer
import com.florianhaeglsperger.photocoach.domain.scoring.ScoreSmoother
import com.florianhaeglsperger.photocoach.domain.scoring.Verdict
import com.florianhaeglsperger.photocoach.domain.scoring.VerdictStabilizer
import kotlinx.coroutines.delay

/** Wie lange die Rueckmeldung nach dem Ausloesen stehen bleibt. */
private const val FEEDBACK_DURATION_MS = 2500L

/**
 * Der Sucher-Screen: Live-Vorschau, Ausloeser und eine Statusflaeche darueber.
 *
 * Die Bedienelemente liegen bewusst in commonMain — sie sind auf allen Plattformen gleich,
 * nur die [CameraPreview] darunter ist plattformspezifisch. Ab Phase 1 kommen hier
 * ScoreOverlay und Grid-Overlay dazu, die dann auf `FrameAnalysis` reagieren.
 */
@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    var state: CameraState by remember { mutableStateOf(CameraState.Initializing) }
    var capturing by remember { mutableStateOf(false) }
    var feedback: CaptureResult? by remember { mutableStateOf(null) }
    var analysis: FrameAnalysis? by remember { mutableStateOf(null) }
    var verdict: Verdict by remember { mutableStateOf(Verdict.NoSubject) }
    var score: Int? by remember { mutableStateOf(null) }
    val hint = (verdict as? Verdict.Fix)?.hint

    // Alle drei halten Zustand ueber Frames hinweg (Hysterese, Mindestanzeigezeit,
    // Glaettung) und gehoeren deshalb an den Screen, nicht in den Callback.
    val scorer = remember { CompositionScorer() }
    val verdictStabilizer = remember { VerdictStabilizer() }
    val scoreSmoother = remember { ScoreSmoother() }
    // Beim Feldtest (Plan 3.5) standardmaessig an: ohne sichtbare Drittel-Linien laesst
    // sich nicht beurteilen, ob ein Hinweis stimmt.
    var showGrid by remember { mutableStateOf(true) }
    var frameCount by remember { mutableStateOf(0) }
    // Die Pinch-Geste startet nur einmal und braucht trotzdem den jeweils aktuellen Zustand.
    val latestState by rememberUpdatedState(state)
    var proOpen by remember { mutableStateOf(false) }
    val zoomAnimator = rememberZoomAnimator()

    // Letztes Foto: rund neben dem Ausloeser, antippen oeffnet es im Vollbild. Beim Start
    // wird das juengste schon vorhandene Foto der App gesucht, danach ersetzt jede neue
    // Aufnahme es.
    val photoLibrary = rememberPhotoLibrary()
    var lastPhoto: CapturedPhoto? by remember { mutableStateOf(null) }
    var thumbnail: ImageBitmap? by remember { mutableStateOf(null) }
    var thumbnailBounds: Rect? by remember { mutableStateOf(null) }
    var viewerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(photoLibrary) {
        if (lastPhoto == null) lastPhoto = photoLibrary.latest()
    }
    LaunchedEffect(lastPhoto) {
        thumbnail = lastPhoto?.let { photoLibrary.load(it, THUMBNAIL_MAX_DIMENSION) }
    }

    // Rueckmeldung nach kurzer Zeit wieder ausblenden, damit sie den Sucher nicht dauerhaft
    // verstellt. Key ist das Ergebnis selbst: zwei Aufnahmen hintereinander starten den
    // Timer jeweils neu.
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(FEEDBACK_DURATION_MS)
            feedback = null
        }
    }

    // Nach einem Objektivwechsel ist es eine neue Szene: der alte Drittel-Zielpunkt und die
    // alte Aussage haben dazu keinen Bezug mehr (ThirdsTargetTracker.reset-Doku).
    val activeLens = (state as? CameraState.Running)?.lensFacing
    LaunchedEffect(activeLens) {
        scorer.reset()
        verdictStabilizer.reset()
        // Eine noch laufende Zoom-Animation gehoert zur alten Kamera.
        zoomAnimator.cancel()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Schwarzer Grund, damit vor dem ersten Kamerabild nichts aufblitzt.
            .background(Color.Black),
    ) {
        // Vorschau im Sensor-Format (4:3), nicht im Vollbild. Im Vollbild schnitt die
        // Vorschau links und rechts ein grosses Stueck ab: das gespeicherte Foto war
        // spuerbar weitwinkliger als das, was der Sucher gezeigt hatte, und Drittel-Raster
        // und Hinweise bezogen sich auf einen anderen Ausschnitt als das Foto.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val aspect = if (maxWidth > maxHeight) SENSOR_ASPECT_RATIO else 1f / SENSOR_ASPECT_RATIO
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .aspectRatio(aspect)
                    .pinchToZoom(
                        currentZoom = { (latestState as? CameraState.Running)?.zoom },
                        onZoom = {
                            // Der Finger hat Vorrang vor einer noch laufenden Animation.
                            zoomAnimator.cancel()
                            (latestState as? CameraState.Running)?.setZoom?.invoke(it)
                        },
                    ),
            ) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onState = { state = it },
                    onAnalysis = {
                        analysis = it
                        val assessment = scorer.assess(it)
                        val next = verdictStabilizer.update(assessment.verdict, it.timestampMs)
                        score = scoreSmoother.update(assessment.score, it.timestampMs)
                        // Nur bei Aenderung protokollieren, nicht bei jedem Frame — sonst steht
                        // in der Datei zehnmal pro Sekunde dasselbe.
                        if (next != verdict) {
                            FieldLog.append("${next.displayText()} (Score ${score ?: "—"})")
                        }
                        verdict = next
                        frameCount++
                    },
                )

                // Im selben Rahmen wie die Vorschau: das Raster muss auf dem Bildausschnitt
                // liegen, nicht auf dem Bildschirm. Nur waehrend die Kamera laeuft.
                if (state is CameraState.Running && showGrid) {
                    ThirdsGrid(modifier = Modifier.fillMaxSize())
                }
            }
        }

        when (val current = state) {
            CameraState.Initializing -> StatusPanel {
                CircularProgressIndicator(color = Color.White)
                Text(
                    text = "Kamera wird gestartet …",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }

            is CameraState.PermissionRequired -> StatusPanel {
                Text(
                    text = "PhotoCoach braucht Zugriff auf die Kamera, um die Komposition " +
                        "im Sucher bewerten zu koennen. Die Bilder bleiben dabei auf dem Geraet.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = current.requestPermission) {
                    Text("Kamera freigeben")
                }
            }

            is CameraState.Error -> StatusPanel {
                Text(
                    text = "Kamera konnte nicht gestartet werden.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = current.message,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }

            // Laeuft — freie Sicht auf das Motiv, nur der Ausloeser am unteren Rand.
            is CameraState.Running -> {
                // Pro-Panel und Zoomleiste teilen sich denselben Platz ueber dem Ausloeser.
                // Zoomen geht bei offenem Panel weiter per Pinch.
                if (proOpen) {
                    ProPanel(
                        capabilities = current.manualCapabilities,
                        settings = current.manualSettings,
                        onChange = current.setManualSettings,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 132.dp),
                    )
                } else {
                    ZoomBar(
                        zoom = current.zoom,
                        lensFacing = current.lensFacing,
                        onSelect = { ratio ->
                            FieldLog.append("Zoom ${formatZoom(ratio)}")
                            zoomAnimator.animate(from = current.zoom.ratio, to = ratio) {
                                (latestState as? CameraState.Running)?.setZoom?.invoke(it)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 132.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 48.dp),
                ) {
                    // Ausloeser bleibt mittig — er ist das Hauptziel und soll dort liegen, wo
                    // der Daumen ihn blind findet. Der Objektiv-Wechsel haengt daneben, ohne
                    // die Mitte zu verschieben.
                    ShutterButton(
                        enabled = !capturing,
                        modifier = Modifier.align(Alignment.Center),
                        onClick = {
                            capturing = true
                            // Markiert im Protokoll den Moment der Aufnahme — darueber lassen
                            // sich Fotos und Hinweise hinterher zusammenbringen.
                            FieldLog.append(
                                ">>> FOTO (${current.lensFacing}, ${formatZoom(current.zoom.ratio)}, " +
                                    "${current.manualSettings.summary(current.manualCapabilities.exposureCompensation?.stepEv)}" +
                                    ") — angezeigt war: " +
                                    (hint?.message ?: "kein Hinweis"),
                            )
                            current.takePhoto { result ->
                                capturing = false
                                feedback = result
                                (result as? CaptureResult.Success)?.photo?.let { lastPhoto = it }
                            }
                        },
                    )

                    // Gegenstueck zum Objektiv-Wechsel, links vom Ausloeser — dort, wo man
                    // es von jeder Kamera-App kennt. Erscheint erst, wenn es ein Foto gibt.
                    thumbnail?.let { bitmap ->
                        PhotoThumbnail(
                            bitmap = bitmap,
                            onClick = { viewerOpen = true },
                            onBounds = { thumbnailBounds = it },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 30.dp),
                        )
                    }

                    if (current.canSwitchLens) {
                        LensToggle(
                            lensFacing = current.lensFacing,
                            enabled = !capturing,
                            onClick = {
                                FieldLog.append("Objektiv gewechselt zu ${current.lensFacing.opposite()}")
                                current.switchLens()
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 32.dp),
                        )
                    }
                }
            }
        }

        feedback?.let { result ->
            CaptureFeedback(
                result = result,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Ueber dem offenen Pro-Panel, sonst verdeckt es die Regler.
                    .padding(bottom = if (proOpen) 330.dp else 200.dp),
            )
        }

        // Nur waehrend die Kamera laeuft: waehrend Berechtigungsdialog oder Fehler liegt
        // ohnehin ein Status-Panel darueber, und ein "Komposition passt" auf schwarzem
        // Grund waere schlicht falsch.
        if (state is CameraState.Running) {
            GridToggle(
                enabled = showGrid,
                onClick = { showGrid = !showGrid },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 108.dp, end = 16.dp),
            )

            // Unter dem Raster-Knopf, gleiche Groesse: zwei Einstellungen, eine Spalte. Der
            // Platz links vom Ausloeser gehoert seit dem 05.10.2026 der Foto-Vorschau.
            val running = state as CameraState.Running
            ProToggle(
                open = proOpen,
                anyManual = !running.manualSettings.isAllAuto,
                onClick = { proOpen = !proOpen },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 160.dp, end = 16.dp)
                    .size(40.dp),
            )

            ScoreOverlay(
                verdict = verdict,
                score = score,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
            )
        }

        analysis?.let { current ->
            AnalysisDebugBadge(
                analysis = current,
                frameCount = frameCount,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // Unter das ScoreOverlay geschoben. Das Badge ist weiterhin nuetzlich
                    // fuer den anstehenden Geraetetest (Plan 3.5) — es faellt weg, sobald
                    // dort bestaetigt ist, dass die Daten stimmen.
                    .padding(start = 16.dp, top = 108.dp),
            )
        }

        // Zuoberst: liegt im Vollbild ueber allem, auch ueber Ausloeser und Hinweisleiste.
        val openPhoto = lastPhoto
        if (viewerOpen && openPhoto != null) {
            PhotoViewer(
                photo = openPhoto,
                preview = thumbnail,
                origin = thumbnailBounds,
                library = photoLibrary,
                onDeleted = { newest -> lastPhoto = newest },
                onClosed = { viewerOpen = false },
            )
        }
    }
}

/**
 * Provisorische Anzeige, dass der Datenfluss Kamera -> FrameAnalyzer -> UI wirklich laeuft.
 *
 * Bewusst haesslich und offensichtlich temporaer. Zeigt nur Rohdaten (Neigung, Gesichter,
 * Anzahl Saliency-Punkte) — Hinweise und Score gehoeren ins [ScoreOverlay]. Faellt weg,
 * sobald der Geraetetest (Plan 3.5) bestaetigt hat, dass die Daten stimmen.
 */
@Composable
private fun AnalysisDebugBadge(
    analysis: FrameAnalysis,
    frameCount: Int,
    modifier: Modifier = Modifier,
) {
    val tilt = analysis.horizonTiltDegrees
        ?.let { "${it}°" }
        ?: "—"

    Column(modifier = modifier) {
        Text(
            text = "debug · $frameCount Frames · Neigung $tilt · " +
                "${analysis.faces.size} Gesichter · ${analysis.saliencyRegions.size} Saliency",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * Klassischer Kamera-Ausloeser: weisser Ring mit gefuelltem Kreis.
 *
 * Bewusst gross und am unteren Rand — er muss mit dem Daumen erreichbar sein, ohne dass
 * die Hand den Sucher verdeckt.
 */
@Composable
private fun ShutterButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(width = 4.dp, color = Color.White.copy(alpha = alpha), shape = CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = alpha)),
        )
    }
}

/** Kurze Rueckmeldung, ob und wohin das Foto gespeichert wurde. */
@Composable
private fun CaptureFeedback(
    result: CaptureResult,
    modifier: Modifier = Modifier,
) {
    val (text, color) = when (result) {
        is CaptureResult.Success -> "Gespeichert in ${result.location}" to Color.White
        is CaptureResult.Failure -> result.message to MaterialTheme.colorScheme.error
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** Zentrierte Overlay-Flaeche fuer Status-/Fehlermeldungen ueber dem Vorschaubild. */
@Composable
private fun StatusPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}


/**
 * Schaltet das Drittel-Raster ein und aus (Plan 3.4: "ein-/ausblendbar").
 *
 * Bewusst klein und am Rand: Es ist eine Einstellung, kein Hauptbedienelement — der
 * Ausloeser bleibt das einzige grosse Ziel im Sucher.
 */
@Composable
private fun GridToggle(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Color.Black.copy(alpha = if (enabled) 0.65f else 0.4f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val color = Color.White.copy(alpha = if (enabled) 0.95f else 0.45f)
            listOf(1f / 3f, 2f / 3f).forEach { fraction ->
                drawLine(
                    color = color,
                    start = Offset(size.width * fraction, 0f),
                    end = Offset(size.width * fraction, size.height),
                    strokeWidth = 1.5f,
                )
                drawLine(
                    color = color,
                    start = Offset(0f, size.height * fraction),
                    end = Offset(size.width, size.height * fraction),
                    strokeWidth = 1.5f,
                )
            }
        }
    }
}

/**
 * Wechselt zwischen Rueck- und Frontkamera (rechts neben dem Ausloeser).
 *
 * Waehrend einer laufenden Aufnahme gesperrt: CameraX muss beim Wechsel neu binden, und
 * das mitten im Auslesen anzustossen ist ein unnoetiges Risiko.
 *
 * Symbol von Hand gezeichnet (keine Icon-Abhaengigkeit, wie beim [GridToggle]): zwei Pfeile
 * im Kreis. Der ausgefuellte Punkt zeigt, welche Seite gerade aktiv ist.
 */
@Composable
private fun LensToggle(
    lensFacing: LensFacing,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 0.85f else 0.35f
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val color = Color.White.copy(alpha = alpha)
            val stroke = size.width * 0.09f
            val radius = size.width * 0.34f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Kreis mit Luecke oben — angedeutete Drehrichtung.
            drawArc(
                color = color,
                startAngle = -60f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = stroke),
            )
            // Pfeilspitze am oberen Ende des Bogens
            val tip = Offset(center.x + radius * 0.5f, center.y - radius * 0.86f)
            drawLine(color, tip, Offset(tip.x - stroke * 1.6f, tip.y - stroke * 0.6f), stroke)
            drawLine(color, tip, Offset(tip.x + stroke * 0.2f, tip.y + stroke * 1.7f), stroke)

            // Punkt in der Mitte: gefuellt = Frontkamera aktiv, offen = Rueckkamera.
            if (lensFacing == LensFacing.FRONT) {
                drawCircle(color, radius = size.width * 0.13f, center = center)
            } else {
                drawCircle(color, radius = size.width * 0.13f, center = center, style = Stroke(stroke * 0.8f))
            }
        }
    }
}
