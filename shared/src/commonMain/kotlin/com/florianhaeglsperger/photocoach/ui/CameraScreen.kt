package com.florianhaeglsperger.photocoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.florianhaeglsperger.photocoach.diagnostics.FieldLog
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import com.florianhaeglsperger.photocoach.domain.rules.Hint
import com.florianhaeglsperger.photocoach.domain.rules.HorizonRule
import com.florianhaeglsperger.photocoach.domain.rules.PortraitFramingRule
import com.florianhaeglsperger.photocoach.domain.rules.Rule
import com.florianhaeglsperger.photocoach.domain.rules.RuleOfThirdsRule
import com.florianhaeglsperger.photocoach.domain.rules.ThirdsTargetTracker
import com.florianhaeglsperger.photocoach.domain.scoring.HintSelector
import com.florianhaeglsperger.photocoach.domain.scoring.HintStabilizer
import com.florianhaeglsperger.photocoach.domain.subject.DefaultSubjectResolver
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
    var hint: Hint? by remember { mutableStateOf(null) }

    // Beide halten Zustand ueber Frames hinweg (Hysterese bzw. Mindestanzeigezeit) und
    // gehoeren deshalb an den Screen, nicht in den Callback.
    val hintSelector = remember { HintSelector() }
    val hintStabilizer = remember { HintStabilizer() }
    // Beim Feldtest (Plan 3.5) standardmaessig an: ohne sichtbare Drittel-Linien laesst
    // sich nicht beurteilen, ob ein Hinweis stimmt.
    var showGrid by remember { mutableStateOf(true) }
    var frameCount by remember { mutableStateOf(0) }

    // Rueckmeldung nach kurzer Zeit wieder ausblenden, damit sie den Sucher nicht dauerhaft
    // verstellt. Key ist das Ergebnis selbst: zwei Aufnahmen hintereinander starten den
    // Timer jeweils neu.
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(FEEDBACK_DURATION_MS)
            feedback = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Schwarzer Grund, damit vor dem ersten Kamerabild nichts aufblitzt.
            .background(Color.Black),
    ) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onState = { state = it },
            onAnalysis = {
                analysis = it
                val next = hintStabilizer.update(hintSelector.select(it), it.timestampMs)
                // Nur bei Aenderung protokollieren, nicht bei jedem Frame — sonst steht
                // in der Datei zehnmal pro Sekunde dasselbe.
                if (next != hint) {
                    FieldLog.append(next?.message ?: "— kein Hinweis —")
                }
                hint = next
                frameCount++
            },
        )

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
            is CameraState.Running -> ShutterButton(
                enabled = !capturing,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                onClick = {
                    capturing = true
                    // Markiert im Protokoll den Moment der Aufnahme — darueber lassen sich
                    // Fotos und Hinweise hinterher zusammenbringen.
                    FieldLog.append(">>> FOTO — angezeigt war: ${hint?.message ?: "kein Hinweis"}")
                    current.takePhoto { result ->
                        capturing = false
                        feedback = result
                    }
                },
            )
        }

        feedback?.let { result ->
            CaptureFeedback(
                result = result,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp),
            )
        }

        // Nur waehrend die Kamera laeuft: waehrend Berechtigungsdialog oder Fehler liegt
        // ohnehin ein Status-Panel darueber, und ein "Komposition passt" auf schwarzem
        // Grund waere schlicht falsch.
        // Unter allen Bedienelementen, ueber dem Kamerabild.
        if (state is CameraState.Running && showGrid) {
            ThirdsGrid(modifier = Modifier.fillMaxSize())
        }

        if (state is CameraState.Running) {
            GridToggle(
                enabled = showGrid,
                onClick = { showGrid = !showGrid },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 108.dp, end = 16.dp),
            )

            ScoreOverlay(
                hint = hint,
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
    }
}

/**
 * Provisorische Anzeige, dass der Datenfluss Kamera -> FrameAnalyzer -> UI wirklich laeuft.
 *
 * Bewusst haesslich und offensichtlich temporaer: sie faellt weg, sobald hier in Phase 1/2
 * das echte ScoreOverlay haengt (Plan 3.4). Solange dort noch nichts haengt, ist die
 * Hinweiszeile hier auch der einzige Weg, die Regeln aus Plan 3.2 live zu sehen statt nur
 * per Unit-Test. Zeigt bewusst *alle* zutreffenden Regel-Hinweise gleichzeitig (nicht nur
 * einen priorisierten) — das Zusammenfassen zu einem einzigen Hinweis ist Aufgabe des noch
 * nicht implementierten `CompositionScorer`.
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
