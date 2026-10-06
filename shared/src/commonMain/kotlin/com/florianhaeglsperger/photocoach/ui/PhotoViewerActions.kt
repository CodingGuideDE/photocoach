package com.florianhaeglsperger.photocoach.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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

/** Hoehe der [ViewerActionBar] samt Abstand zum unteren Rand — darueber liegt das Info-Panel. */
internal val ACTION_BAR_CLEARANCE = 124.dp

/**
 * Aktionsleiste am unteren Rand der Vollbild-Ansicht (Planung/Foto-Bearbeitung.md, Stufe A).
 *
 * Bewusst nur das, was schon funktioniert: Teilen und Info. "Analyse" und "Bearbeiten"
 * kommen mit Stufe B/C dazu — ausgegraute Platzhalter waeren Knoepfe ohne Wirkung.
 * Loeschen sitzt oben rechts, getrennt von den haeufigen Aktionen, damit man es nicht
 * versehentlich trifft.
 */
@Composable
internal fun ViewerActionBar(
    infoOpen: Boolean,
    onShare: () -> Unit,
    onInfo: () -> Unit,
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
        ActionButton(label = "Teilen", active = false, onClick = onShare) { color -> drawShareIcon(color) }
        ActionButton(label = "Info", active = infoOpen, onClick = onInfo) { color -> drawInfoIcon(color) }
    }
}

@Composable
private fun ActionButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: DrawScope.(Color) -> Unit,
) {
    // Gleiche Akzentfarbe wie die aktive Zoom-Stufe im Sucher: "das ist gerade an".
    val color by animateColorAsState(
        targetValue = if (active) Color(0xFFFFD54F) else Color.White,
        animationSpec = tween(200),
    )
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

/** "i" im Kreis. */
private fun DrawScope.drawInfoIcon(color: Color) {
    val stroke = size.width * 0.08f
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(color, radius = size.width / 2f - stroke / 2f, center = c, style = Stroke(stroke))
    drawCircle(color, radius = stroke * 0.75f, center = Offset(c.x, size.height * 0.3f))
    drawLine(color, Offset(c.x, size.height * 0.45f), Offset(c.x, size.height * 0.74f), stroke * 1.2f)
}

/**
 * Aufnahmedaten des angezeigten Fotos, ueber der Aktionsleiste. Bleibt beim Blaettern offen
 * und zeigt dann die Daten des neuen Fotos — so lassen sich zwei Aufnahmen vergleichen.
 *
 * [details] `null` heisst: laedt noch ([loading]) oder es gibt nichts zu zeigen.
 */
@Composable
internal fun PhotoInfoPanel(
    details: PhotoDetails?,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            // Beruehrungen auf dem Panel gehoeren dem Panel: ein Wisch hier soll weder blaettern
            // noch das Foto wegziehen.
            .pointerInput(Unit) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val rows = details?.rows().orEmpty()
        when {
            loading -> CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp).align(Alignment.CenterHorizontally),
            )
            rows.isEmpty() -> Text(
                text = "Keine Aufnahmedaten verfügbar",
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> rows.forEach { (label, value) ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = label,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.widthIn(min = 112.dp, max = 112.dp),
                    )
                    Text(
                        text = value,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
