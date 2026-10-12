package com.florianhaeglsperger.photocoach.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Hoehe der [ViewerActionBar] samt Abstand zum unteren Rand — darueber stehen Meldungen. */
internal val ACTION_BAR_CLEARANCE = 124.dp

/** Ueber der Bewertungsleiste — dort stehen Meldungen wie "Teilen nicht möglich". */
internal val SCORE_BAR_CLEARANCE = 184.dp

/** Unterkante der Knoepfe oben (X, Info) samt Abstand — darunter klappt das Info-Panel auf. */
internal val TOP_CONTROLS_CLEARANCE = 104.dp

/**
 * Aktionsleiste am unteren Rand der Vollbild-Ansicht (Planung/Foto-Bearbeitung.md, Stufe A).
 *
 * Bewusst nur das, was schon funktioniert: Teilen und Loeschen. "Analyse" und "Bearbeiten"
 * kommen mit Stufe B/C dazu — ausgegraute Platzhalter waeren Knoepfe ohne Wirkung.
 * Versehentliches Loeschen faengt die Rueckfrage ab; Info sitzt oben rechts ([InfoButton]).
 */
@Composable
internal fun ViewerActionBar(
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(label = "Teilen", onClick = onShare) { color -> drawShareIcon(color) }
        ActionButton(label = "Löschen", onClick = onDelete) { color -> drawTrashIcon(color) }
    }
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    icon: DrawScope.(Color) -> Unit,
) {
    val color = Color.White
    Column(
        modifier = Modifier
            .widthIn(min = 72.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(modifier = Modifier.size(22.dp)) { icon(color) }
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

/** Das uebliche Teilen-Symbol: drei Punkte, verbunden zu einem liegenden "<". */
private fun DrawScope.drawShareIcon(color: Color) {
    val r = size.width * 0.14f
    val stroke = size.width * 0.08f
    val right1 = Offset(size.width * 0.78f, size.height * 0.2f)
    val left = Offset(size.width * 0.22f, size.height * 0.5f)
    val right2 = Offset(size.width * 0.78f, size.height * 0.8f)
    drawLine(color, left, right1, stroke)
    drawLine(color, left, right2, stroke)
    listOf(right1, left, right2).forEach { drawCircle(color, radius = r, center = it) }
}

/** Papierkorb: Deckel mit Griff ueber einem sich verjuengenden Eimer. */
private fun DrawScope.drawTrashIcon(color: Color) {
    val stroke = size.width * 0.09f
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.08f, h * 0.22f), Offset(w * 0.92f, h * 0.22f), stroke)
    drawLine(color, Offset(w * 0.36f, h * 0.22f), Offset(w * 0.36f, h * 0.06f), stroke)
    drawLine(color, Offset(w * 0.64f, h * 0.22f), Offset(w * 0.64f, h * 0.06f), stroke)
    drawLine(color, Offset(w * 0.36f, h * 0.06f), Offset(w * 0.64f, h * 0.06f), stroke)
    drawLine(color, Offset(w * 0.2f, h * 0.22f), Offset(w * 0.27f, h * 0.94f), stroke)
    drawLine(color, Offset(w * 0.8f, h * 0.22f), Offset(w * 0.73f, h * 0.94f), stroke)
    drawLine(color, Offset(w * 0.27f, h * 0.94f), Offset(w * 0.73f, h * 0.94f), stroke)
}

/**
 * Info-Knopf oben rechts, gleiche Form wie das X oben links. Solange das Info-Panel offen
 * ist, leuchtet das "i" in der Akzentfarbe (wie die aktive Zoom-Stufe im Sucher).
 */
@Composable
internal fun InfoButton(open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        targetValue = if (open) Color(0xFFFFD54F) else Color.White,
        animationSpec = tween(200),
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val stroke = size.width * 0.08f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color, radius = size.width / 2f - stroke / 2f, center = c, style = Stroke(stroke))
            drawCircle(color, radius = stroke * 0.75f, center = Offset(c.x, size.height * 0.3f))
            drawLine(color, Offset(c.x, size.height * 0.45f), Offset(c.x, size.height * 0.74f), stroke * 1.2f)
        }
    }
}

/**
 * Platzhalter in Form und Lage des [ScoreOverlay], solange das Foto noch bewertet wird —
 * damit die Leiste beim Blaettern nicht verschwindet und wieder auftaucht.
 */
@Composable
internal fun PhotoScorePending(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Text(text = "Komposition wird bewertet …", color = Color(0xFFD0D0D0), style = MaterialTheme.typography.bodyMedium)
    }
}
