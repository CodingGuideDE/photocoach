package com.florianhaeglsperger.photocoach.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.florianhaeglsperger.photocoach.capture.ObjectSelection
import com.florianhaeglsperger.photocoach.diagnostics.FieldLog
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import com.florianhaeglsperger.photocoach.domain.model.PinnedSubject
import com.florianhaeglsperger.photocoach.domain.scoring.CompositionScorer
import com.florianhaeglsperger.photocoach.domain.scoring.ScoreSmoother
import com.florianhaeglsperger.photocoach.domain.scoring.Verdict
import com.florianhaeglsperger.photocoach.domain.scoring.VerdictStabilizer
import com.florianhaeglsperger.photocoach.domain.subject.DefaultSubjectResolver
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Wie lange die Rueckmeldung nach dem Ausloesen stehen bleibt. */
private const val FEEDBACK_DURATION_MS = 2500L

/**
 * Wie lange die Vorschau nach einem Objektivwechsel (Haupt ↔ Tele) braucht, um vom
 * ausgeglichenen Versatz in ihre normale Lage zu gleiten. Gleich lang wie eine Zoom-Animation:
 * kuerzer wirkt wie ein zweiter, kleinerer Sprung, laenger wie ein Nachziehen.
 */
private const val LENS_HANDOVER_GLIDE_MS = ZOOM_ANIMATION_MS

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
    var verdict: Verdict by remember { mutableStateOf(Verdict.AwaitingSubject) }
    var score: Int? by remember { mutableStateOf(null) }
    val hint = (verdict as? Verdict.Fix)?.hint

    // Alle drei halten Zustand ueber Frames hinweg (Hysterese, Mindestanzeigezeit,
    // Glaettung) und gehoeren deshalb an den Screen, nicht in den Callback.
    // Im Sucher: Hinweise zur Platzierung erst nach dem Antippen eines Motivs.
    val scorer = remember { CompositionScorer(placementNeedsPinnedSubject = true) }
    val verdictStabilizer = remember { VerdictStabilizer() }
    val scoreSmoother = remember { ScoreSmoother() }
    // Beim Feldtest (Plan 3.5) standardmaessig an: ohne sichtbare Drittel-Linien laesst
    // sich nicht beurteilen, ob ein Hinweis stimmt.
    var showGrid by remember { mutableStateOf(true) }
    var frameCount by remember { mutableStateOf(0) }
    // Rohdaten-Zeile: standardmaessig aus, damit der Sucher ruhig bleibt. Antippen der
    // Hinweiskarte blendet sie ein — fuer den Geraetetest (Plan 3.5) weiter erreichbar.
    var showDebug by remember { mutableStateOf(false) }
    // Die Pinch-Geste startet nur einmal und braucht trotzdem den jeweils aktuellen Zustand.
    val latestState by rememberUpdatedState(state)
    var proOpen by remember { mutableStateOf(false) }
    // Hoehe des Stapels aus Zoomleiste und Pro-Panel — die Speicher-Rueckmeldung steht darueber.
    var bottomControlsHeight by remember { mutableStateOf(0) }

    // Tippen zum Scharfstellen: wo, wie weit, und eine laufende Nummer, damit ein spaeter
    // Bescheid der Kamera nicht einen neueren Tipp ueberschreibt.
    var focusPoint: FocusPoint? by remember { mutableStateOf(null) }
    // Das per Tipp gewaehlte Objekt. Solange es erkannt ist, ist es das Motiv der Bewertung
    // (FrameAnalysis.pinnedSubject) — siehe ObjectBoxOverlay.
    var objectSelection: ObjectSelection? by remember { mutableStateOf(null) }
    var focusState by remember { mutableStateOf(FocusState.FOCUSING) }
    var focusTaps by remember { mutableIntStateOf(0) }
    val zoomAnimator = rememberZoomAnimator()
    // Objektivwechsel innerhalb der Kamera (Haupt ↔ Tele): um so viel ist die Vorschau gerade
    // gegen den Bildsprung verschoben (Anteil der Rahmenbreite/-hoehe). Nach jedem Wechsel
    // gleitet sie auf 0 zurueck — aus dem Sprung wird eine kurze Bewegung.
    val handoverOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val handoverScope = rememberCoroutineScope()

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
        // Das neue Objektiv startet ohnehin mit Messung ueber das ganze Bild.
        focusPoint = null
        // Das fixierte Motiv lag im Bild der alten Kamera.
        (latestState as? CameraState.Running)?.clearObjectSelection()
        objectSelection = null
        // Eine noch laufende Zoom-Animation gehoert zur alten Kamera.
        zoomAnimator.cancel()
        // Ebenso ein noch laufender Ausgleich eines Objektivwechsels.
        handoverOffset.snapTo(Offset.Zero)
    }

    // Ein eingerasteter Fokuspunkt gilt fuer den Bildausschnitt, in dem er angetippt wurde.
    // Nach dem Zoomen laege er auf etwas anderem — also zurueck zur Messung ueber das ganze Bild.
    val zoomRatio = (state as? CameraState.Running)?.zoom?.ratio
    LaunchedEffect(zoomRatio) {
        if (focusPoint != null) {
            (latestState as? CameraState.Running)?.resetFocus?.invoke()
            focusPoint = null
        }
        // Gleiches fuer das fixierte Motiv: nachgefuehrt wird es am letzten Schwerpunkt im
        // *Bildausschnitt* — nach dem Zoomen laege der auf anderem Inhalt.
        if (objectSelection != null) {
            (latestState as? CameraState.Running)?.clearObjectSelection()
            objectSelection = null
        }
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
                    // Waehrend des Ausgleichs nach einem Objektivwechsel ist die Vorschau
                    // verschoben — sie darf dabei nicht ueber den Rahmen hinaus zeichnen.
                    .clipToBounds()
                    .pinchToZoom(
                        currentZoom = { (latestState as? CameraState.Running)?.zoom },
                        onZoom = {
                            // Der Finger hat Vorrang vor einer noch laufenden Animation.
                            zoomAnimator.cancel()
                            (latestState as? CameraState.Running)?.setZoom?.invoke(it)
                        },
                    )
                    // Antippen: Fokus und Belichtung auf diesen Punkt. Lange druecken: zurueck
                    // auf das ganze Bild. Zwei Finger (Pinch) loesen beides nicht aus — die
                    // Zoom-Geste verbraucht die Bewegung, und ein Tipp ohne Bewegung ist hier
                    // per Definition ein Finger.
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                val running = latestState as? CameraState.Running ?: return@detectTapGestures
                                val x = (offset.x / size.width).coerceIn(0f, 1f)
                                val y = (offset.y / size.height).coerceIn(0f, 1f)
                                // Ein Tipp waehlt zugleich das Objekt an dieser Stelle (Test der
                                // Tipp-Segmentierung) — auch ohne Fokus-Unterstuetzung.
                                running.selectObjectAt(x, y)
                                if (!running.canFocusAtPoint) return@detectTapGestures
                                // Manueller Fokus aus dem Pro-Modus wuerde den Tipp ueberstimmen
                                // (er schaltet den Autofokus ab). Wer tippt, will scharfstellen.
                                if (running.manualSettings.focusDiopters != null) {
                                    running.setManualSettings(running.manualSettings.copy(focusDiopters = null))
                                }
                                val id = ++focusTaps
                                focusPoint = FocusPoint(x, y, id)
                                focusState = FocusState.FOCUSING
                                FieldLog.append("Fokus/Belichtung auf ${(x * 100).roundToInt()} % / ${(y * 100).roundToInt()} %")
                                running.focusAt(x, y) { focused ->
                                    // Ein spaeter Bescheid zu einem schon abgeloesten Tipp zaehlt nicht.
                                    if (focusPoint?.id == id) {
                                        focusState = if (focused) FocusState.FOCUSED else FocusState.UNFOCUSED
                                    }
                                }
                            },
                            onLongPress = {
                                val running = latestState as? CameraState.Running ?: return@detectTapGestures
                                running.clearObjectSelection()
                                if (focusPoint == null) return@detectTapGestures
                                running.resetFocus()
                                focusPoint = null
                                FieldLog.append("Fokus/Belichtung zurueck auf ganzes Bild")
                            },
                        )
                    },
            ) {
                CameraPreview(
                    // Nur die Vorschau gleitet, nicht das Raster: das zeigt den Ausschnitt des
                    // Fotos, und der springt nicht. Fokusring und Objektrahmen sind beim
                    // Wechsel ohnehin weg (jede Zoomaenderung hebt sie auf, siehe oben).
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        translationX = handoverOffset.value.x * size.width
                        translationY = handoverOffset.value.y * size.height
                    },
                    onState = { state = it },
                    onLensHandover = { handover ->
                        val shift = handover.shift?.takeIf { it.isReliable }
                        FieldLog.append(handover.logLine(compensated = shift != null))
                        if (shift != null) {
                            handoverScope.launch {
                                // Der neue Inhalt liegt um shift versetzt — die Vorschau also
                                // um genau das zurueck, dann weich in die Ruhelage. Vom aktuellen
                                // Stand aus, falls der vorige Ausgleich noch laeuft.
                                handoverOffset.snapTo(handoverOffset.value - Offset(shift.dx, shift.dy))
                                handoverOffset.animateTo(
                                    Offset.Zero,
                                    tween(LENS_HANDOVER_GLIDE_MS, easing = FastOutSlowInEasing),
                                )
                            }
                        }
                    },
                    onObjectSelection = { selection ->
                        logPinChange(objectSelection, selection)
                        // Neues Motiv angetippt: der alte Drittel-Zielpunkt gehoert zum alten.
                        if (selection is ObjectSelection.Searching) scorer.reset()
                        objectSelection = selection
                    },
                    onAnalysis = { raw ->
                        // Das fixierte Motiv geht mit in die Analyse, damit *alle* Regeln
                        // dasselbe Motiv sehen (Drittel, tote Flaeche, Debug-Rechteck).
                        val it = raw.copy(pinnedSubject = objectSelection.pinnedSubject())
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

                // Debug (gleicher Schalter wie die Rohdaten-Zeile: Hinweiskarte antippen):
                // das Motiv, das die Regeln sehen, als Rechteck im Bild.
                if (state is CameraState.Running && showDebug) {
                    SubjectDebugOverlay(
                        subject = analysis?.let { DefaultSubjectResolver.resolve(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Testhilfe Tipp-Segmentierung: Rahmen um das erkannte Objekt.
                if (state is CameraState.Running) {
                    ObjectBoxOverlay(
                        selection = objectSelection,
                        onClear = { (latestState as? CameraState.Running)?.clearObjectSelection() },
                        modifier = Modifier.fillMaxSize(),
                        showDetails = showDebug,
                    )
                }

                // Ueber dem Raster, im selben Rahmen: der Punkt ist relativ zum Bildausschnitt.
                val running = state as? CameraState.Running
                val point = focusPoint
                // Steht im Pro-Modus manueller Fokus, gilt der Punkt nicht mehr fuer den Fokus.
                if (running != null && point != null && running.manualSettings.focusDiopters == null) {
                    FocusIndicator(
                        point = point,
                        state = focusState,
                        // Bei manueller ISO/Verschlusszeit regelt keine Automatik mehr, an die
                        // sich eine Korrektur richten koennte — dann nur der Ring.
                        exposure = running.manualCapabilities.exposureCompensation
                            ?.takeIf { !running.manualSettings.isManualExposure },
                        exposureIndex = running.manualSettings.exposureCompensation,
                        onExposureChange = { index ->
                            (latestState as? CameraState.Running)?.let {
                                it.setManualSettings(it.manualSettings.copy(exposureCompensation = index))
                            }
                        },
                    )
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
                // Ueber dem Ausloeser gestapelt: unten die Zoomleiste, darueber — wenn offen —
                // das Pro-Panel. Die Zoomleiste bleibt dabei immer an derselben Stelle; frueher
                // verdraengte das Panel sie, und Zoomen ging im Pro-Modus nur noch per Pinch.
                // Hat die Kamera keine Zoomstufen, faellt die Leiste weg und das Panel rueckt
                // nach unten (ZoomBar zeichnet dann nichts, spacedBy laesst keine Luecke).
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 132.dp)
                        .onSizeChanged { bottomControlsHeight = it.height },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (proOpen) {
                        ProPanel(
                            capabilities = current.manualCapabilities,
                            settings = current.manualSettings,
                            onChange = current.setManualSettings,
                        )
                    }
                    ZoomBar(
                        zoom = current.zoom,
                        lensFacing = current.lensFacing,
                        onSelect = { ratio ->
                            FieldLog.append("Zoom ${formatZoom(ratio)}")
                            zoomAnimator.animate(from = current.zoom.ratio, to = ratio) {
                                (latestState as? CameraState.Running)?.setZoom?.invoke(it)
                            }
                        },
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
                    // Ueber Zoomleiste und (offenem) Pro-Panel, sonst verdeckt es die Regler.
                    .padding(bottom = 132.dp + with(LocalDensity.current) { bottomControlsHeight.toDp() } + 12.dp),
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
                    .padding(top = 124.dp, end = 16.dp),
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
                    .padding(top = 172.dp, end = 16.dp)
                    .size(40.dp),
            )

            ScoreOverlay(
                verdict = verdict,
                score = score,
                onClick = { showDebug = !showDebug },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
            )
        }

        analysis?.takeIf { showDebug && state is CameraState.Running }?.let { current ->
            AnalysisDebugBadge(
                analysis = current,
                frameCount = frameCount,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // Unter das ScoreOverlay geschoben. Das Badge ist weiterhin nuetzlich
                    // fuer den anstehenden Geraetetest (Plan 3.5) — es faellt weg, sobald
                    // dort bestaetigt ist, dass die Daten stimmen.
                    .padding(start = 16.dp, top = 124.dp, end = 72.dp),
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

/**
 * Das fixierte Motiv fuer die Bewertung: nur ein erkanntes Objekt zaehlt. Waehrend der ersten
 * Erkennung, nach dem Verlieren oder ohne Modell entscheidet wieder die automatische
 * Motivwahl — lieber kurz automatisch als an einer Stelle festhalten, an der nichts mehr ist.
 */
private fun ObjectSelection?.pinnedSubject(): PinnedSubject? {
    val obj = (this as? ObjectSelection.Found)?.obj ?: return null
    return PinnedSubject(obj.left, obj.top, obj.right, obj.bottom, obj.centerX, obj.centerY)
}

/** Feldtest-Protokoll: nur die Uebergaenge (fixiert / verloren / aufgehoben), nicht jedes Nachfuehren. */
/**
 * Protokollzeile zu einem Objektivwechsel, z. B.
 * "Objektivwechsel 56 → 52 bei 2,9×: Versatz +1,8 % / −0,6 %, ausgeglichen
 * (Korrelation 62 %, Eindeutigkeit 14,1, Messung 0,8 ms, Bildalter 31 ms, Abstand 33 ms)".
 * Die Kennwerte sind fuer die Kalibrierung der Schwellen in `ImageRegistration`.
 */
private fun LensHandover.logLine(compensated: Boolean): String {
    val head = "Objektivwechsel $fromCamera → $toCamera bei ${formatZoom(zoomRatio)}"
    val details = "Messung ${measureMs.tenths()} ms, " +
        "Bildalter ${frameAgeMs?.roundToInt()?.let { "$it ms" } ?: "?"}, " +
        "Abstand ${frameGapMs.roundToInt()} ms"
    val s = shift ?: return "$head: kein Versatz messbar (zu kontrastarm) ($details)"
    return "$head: Versatz ${(s.dx * 100).tenths(signed = true)} % / ${(s.dy * 100).tenths(signed = true)} %, " +
        (if (compensated) "ausgeglichen" else "nicht ausgeglichen") +
        " (Korrelation ${(s.confidence * 100).roundToInt()} %, " +
        "Eindeutigkeit ${s.distinctness.coerceAtMost(999f).tenths()}, $details)"
}

/** Eine Nachkommastelle mit deutschem Komma, optional mit Vorzeichen. */
private fun Float.tenths(signed: Boolean = false): String {
    val t = (this * 10).roundToInt()
    val sign = when {
        t < 0 -> "−"
        signed && t > 0 -> "+"
        else -> ""
    }
    val a = kotlin.math.abs(t)
    return "$sign${a / 10},${a % 10}"
}

private fun logPinChange(before: ObjectSelection?, after: ObjectSelection?) {
    val wasPinned = before is ObjectSelection.Found
    when {
        after is ObjectSelection.Found && !wasPinned ->
            FieldLog.append("Motiv fixiert (${(after.obj.centerX * 100).roundToInt()} % / ${(after.obj.centerY * 100).roundToInt()} %)")
        after is ObjectSelection.Lost && wasPinned -> FieldLog.append("Fixiertes Motiv verloren")
        after == null && before != null -> FieldLog.append("Motiv-Fixierung aufgehoben")
    }
}
