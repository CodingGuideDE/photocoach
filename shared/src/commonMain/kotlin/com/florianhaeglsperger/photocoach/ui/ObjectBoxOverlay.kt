package com.florianhaeglsperger.photocoach.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.florianhaeglsperger.photocoach.capture.ObjectSelection
import kotlin.math.roundToInt

/**
 * Farbe des Objekt-Rahmens. Bewusst nicht das Gelb des Fokusrings: beides kann gleichzeitig
 * im Bild stehen (ein Tipp setzt Fokus *und* waehlt das Objekt) und soll unterscheidbar sein.
 */
private val OBJECT_COLOR = Color(0xFF4DD0E1)

/** Bewegt sich das Objekt, gleitet der Rahmen zur neuen Lage statt zu springen. */
private const val BOX_GLIDE_MS = 180

/**
 * Zeigt das angetippte und damit **fixierte Motiv** als Rahmen: an diesem Objekt richtet
 * sich die Bewertung aus (Drittel-Regel, tote Flaeche), statt an der automatischen
 * Motivwahl. "×" oder langes Druecken hebt die Fixierung auf.
 *
 * Mit [showDetails] (Debug-Schalter) stehen im Etikett zusaetzlich Flaechenanteil und
 * Rechenzeit der letzten Erkennung — damit sich am Geraet beurteilen laesst, ob
 * Treffsicherheit und Tempo des Modells reichen.
 *
 * Liegt im Vorschau-Rahmen; alle Koordinaten sind normiert auf diesen Rahmen.
 */
@Composable
fun ObjectBoxOverlay(
    selection: ObjectSelection?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    showDetails: Boolean = false,
) {
    if (selection == null) return
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        when (selection) {
            is ObjectSelection.Searching -> Label(
                text = "Erkenne Motiv …",
                busy = true,
                onClear = onClear,
                // Unter dem Tipp, aber im Bild gehalten — nahe am Rand ragte es sonst hinaus.
                modifier = Modifier.offset {
                    IntOffset(
                        ((selection.x * w).roundToInt() - 70.dp.roundToPx())
                            .coerceIn(0, (w - 170.dp.toPx()).roundToInt().coerceAtLeast(0)),
                        ((selection.y * h).roundToInt() + 28.dp.roundToPx())
                            .coerceAtMost((h - 34.dp.toPx()).roundToInt()),
                    )
                },
            )

            is ObjectSelection.Found -> {
                val obj = selection.obj
                val left by animateFloatAsState(obj.left * w, tween(BOX_GLIDE_MS))
                val top by animateFloatAsState(obj.top * h, tween(BOX_GLIDE_MS))
                val right by animateFloatAsState(obj.right * w, tween(BOX_GLIDE_MS))
                val bottom by animateFloatAsState(obj.bottom * h, tween(BOX_GLIDE_MS))

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val corner = CornerRadius(6.dp.toPx())
                    val topLeft = Offset(left, top)
                    val size = Size(right - left, bottom - top)
                    // Dunkler Saum zuerst: haelt den Rahmen auf hellem Grund sichtbar.
                    drawRoundRect(Color.Black.copy(alpha = 0.35f), topLeft, size, corner, style = Stroke(4.dp.toPx()))
                    drawRoundRect(OBJECT_COLOR, topLeft, size, corner, style = Stroke(2.dp.toPx()))
                    // Schwerpunkt — dort wird beim naechsten Bild weiter gesucht.
                    drawCircle(OBJECT_COLOR, 3.dp.toPx(), Offset(obj.centerX * w, obj.centerY * h))
                }

                // Etikett ueber dem Rahmen, oder darunter, wenn oben kein Platz ist.
                val labelAbove = top > with(density) { 40.dp.toPx() }
                Label(
                    text = if (showDetails) {
                        "Motiv fixiert · ${(obj.areaFraction * 100).roundToInt()} % · ${selection.inferenceMs} ms"
                    } else {
                        "Motiv fixiert"
                    },
                    busy = false,
                    onClear = onClear,
                    modifier = Modifier.offset {
                        IntOffset(
                            left.roundToInt().coerceIn(0, (w - 200.dp.toPx()).roundToInt().coerceAtLeast(0)),
                            if (labelAbove) (top - 34.dp.toPx()).roundToInt() else (bottom + 6.dp.toPx()).roundToInt(),
                        )
                    },
                )
            }

            is ObjectSelection.Lost -> Label(
                text = if (showDetails) {
                    "Motiv verloren · ${selection.inferenceMs} ms — neu antippen"
                } else {
                    "Motiv verloren — neu antippen"
                },
                busy = false,
                onClear = onClear,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            )

            is ObjectSelection.Unavailable -> Label(
                text = selection.reason,
                busy = false,
                onClear = onClear,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun Label(text: String, busy: Boolean, onClear: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (busy) CircularProgressIndicator(color = OBJECT_COLOR, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
        Text(text = text, color = OBJECT_COLOR, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        // "x": Auswahl aufheben. Gross genug zum Treffen, klein im Bild.
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(8.dp)) {
                val s = 1.5.dp.toPx()
                drawLine(Color.White, Offset.Zero, Offset(size.width, size.height), s)
                drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), s)
            }
        }
        Spacer(Modifier.width(0.dp))
    }
}
