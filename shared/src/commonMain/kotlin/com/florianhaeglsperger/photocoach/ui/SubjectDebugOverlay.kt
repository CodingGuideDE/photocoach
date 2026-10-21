package com.florianhaeglsperger.photocoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.florianhaeglsperger.photocoach.domain.subject.SubjectPoint
import com.florianhaeglsperger.photocoach.domain.subject.SubjectSource
import kotlin.math.roundToInt

/** Gesicht: kraeftiges Magenta; Saliency: Gelb — beide heben sich von Raster (weiss) ab. */
private val FACE_COLOR = Color(0xFFFF2D95)
private val SALIENCY_COLOR = Color(0xFFFFD60A)

/** Angetippt: dasselbe Tuerkis wie der Objekt-Rahmen der Tipp-Auswahl. */
private val PINNED_COLOR = Color(0xFF4DD0E1)

/**
 * Debug: zeichnet das Motiv, das die Regeln gerade sehen, als Rechteck mit Mittelpunkt in
 * den Sucher — damit sich pruefen laesst, ob die Motiverkennung stimmt (und woher sie kommt:
 * Gesicht oder Saliency, mit Verlaesslichkeit).
 *
 * Gehoert in denselben Rahmen wie die Vorschau und das Raster, damit die normierten
 * Koordinaten auf dem Bildausschnitt liegen. Ohne Motiv zeigt es nichts.
 */
@Composable
fun SubjectDebugOverlay(subject: SubjectPoint?, modifier: Modifier = Modifier) {
    if (subject == null) return
    val color = when (subject.source) {
        SubjectSource.PINNED -> PINNED_COLOR
        SubjectSource.FACE -> FACE_COLOR
        SubjectSource.SALIENCY -> SALIENCY_COLOR
    }
    val bounds = subject.bounds

    BoxWithConstraints(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (bounds != null) {
                drawRect(
                    color = color,
                    topLeft = Offset(bounds.left * size.width, bounds.top * size.height),
                    size = Size((bounds.right - bounds.left) * size.width, (bounds.bottom - bounds.top) * size.height),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
            drawCircle(color, radius = 5.dp.toPx(), center = Offset(subject.x * size.width, subject.y * size.height))
        }
        if (bounds != null) {
            val label = when (subject.source) {
                SubjectSource.PINNED -> "Angetippt"
                SubjectSource.FACE -> "Gesicht"
                SubjectSource.SALIENCY -> "Saliency"
            }
            Text(
                text = "$label ${(subject.confidence * 100).roundToInt()} %",
                color = Color.Black,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    // Linke obere Ecke des Rechtecks, auf Rahmenfarbe als Etikett.
                    .offset(maxWidth * bounds.left, maxHeight * bounds.top)
                    .background(color)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
