package com.florianhaeglsperger.photocoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Rueckfrage vor dem Loeschen — als [ViewerSheet], das unten aus der Aktionsleiste hochfaehrt.
 *
 * **Warum kein Material-`AlertDialog`:**
 * - *Ort:* Geloescht wird unten in der Leiste. Ein Dialog in der Bildmitte zwingt den Daumen
 *   quer ueber den Bildschirm; das Panel kommt dort hoch, wo der Finger schon ist.
 * - *Gegenstand:* Ein Dialog deckt genau das Foto ab, um das es geht. Hier hebt der Viewer
 *   das Foto an, bis es ganz ueber dem Panel steht ([onSizeChanged] liefert dafuer die
 *   Groesse) — man sieht, *was* geloescht wird, nicht nur *dass*. Eine kleine Vorschau im
 *   Panel war in einer frueheren Fassung doppelt: das echte Foto stand direkt darueber.
 * - *Gewichtung:* Im Dialog leuchtete "Abbrechen" in der Akzentfarbe heller als "Loeschen".
 *   Hier ist Loeschen die eine gefuellte Flaeche in der Warnfarbe, Abbrechen zurueckhaltend.
 *
 * **Abbrechen sitzt unten** — dort, wo der Finger gerade "Loeschen" in der Leiste getippt
 * hat. Ein versehentlicher Doppeltipp trifft so das Harmlose, nicht das Endgueltige.
 *
 * **Querformat ([sideways]):** Unten waere kaum Hoehe uebrig — das Foto schrumpfte zur
 * Briefmarke. Dort dockt das Panel rechts an, und das Foto steht gross links daneben.
 *
 * Schliessen: Abbrechen, Tipp daneben, wegziehen (nach unten bzw. rechts), Zurueck.
 */
@Composable
internal fun BoxScope.DeleteConfirmSheet(
    visible: Boolean,
    sideways: Boolean,
    onSizeChanged: (IntSize) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ViewerSheet(
        visible = visible,
        sideways = sideways,
        onSizeChanged = onSizeChanged,
        onDismiss = onDismiss,
    ) {
        Text(
            text = "Foto löschen?",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Es verschwindet auch aus deiner Galerie und lässt sich nicht wiederherstellen.",
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        val haptics = LocalHapticFeedback.current
        SheetButton(
            text = "Löschen",
            background = DESTRUCTIVE,
            content = Color.Black,
            icon = true,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onConfirm()
            },
        )
        Spacer(Modifier.height(4.dp))
        // Ohne Flaeche: Loeschen soll die einzige laute Stelle im Panel sein.
        SheetButton(
            text = "Abbrechen",
            background = Color.Transparent,
            content = Color.White.copy(alpha = 0.85f),
            icon = false,
            onClick = onDismiss,
        )
    }
}

@Composable
private fun SheetButton(
    text: String,
    background: Color,
    content: Color,
    icon: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val stroke = size.width * 0.1f
                val w = size.width
                val h = size.height
                drawLine(content, Offset(w * 0.08f, h * 0.22f), Offset(w * 0.92f, h * 0.22f), stroke)
                drawLine(content, Offset(w * 0.36f, h * 0.22f), Offset(w * 0.36f, h * 0.06f), stroke)
                drawLine(content, Offset(w * 0.64f, h * 0.22f), Offset(w * 0.64f, h * 0.06f), stroke)
                drawLine(content, Offset(w * 0.36f, h * 0.06f), Offset(w * 0.64f, h * 0.06f), stroke)
                drawLine(content, Offset(w * 0.2f, h * 0.22f), Offset(w * 0.27f, h * 0.94f), stroke)
                drawLine(content, Offset(w * 0.8f, h * 0.22f), Offset(w * 0.73f, h * 0.94f), stroke)
                drawLine(content, Offset(w * 0.27f, h * 0.94f), Offset(w * 0.73f, h * 0.94f), stroke)
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, color = content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
