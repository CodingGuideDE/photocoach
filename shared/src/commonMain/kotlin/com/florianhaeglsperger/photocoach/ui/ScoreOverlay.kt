package com.florianhaeglsperger.photocoach.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.florianhaeglsperger.photocoach.domain.rules.Hint

/** Warnfarbe fuer anliegende Hinweise — kraeftig genug zum Auffallen, ohne Alarm zu schreien. */
private val HINT_COLOR = Color(0xFFFFC24B)

/** Bestaetigungsfarbe, wenn nichts zu beanstanden ist. */
private val OK_COLOR = Color(0xFF7FD48B)

/**
 * Live-Feedback zur Komposition (Plan 3.4).
 *
 * Bewusst eine schmale Leiste am oberen Bildrand statt eines Overlays ueber dem Motiv:
 * der Sucher soll frei bleiben — man fotografiert das Bild, nicht die App. Immer nur *ein*
 * Hinweis, ausgewaehlt von `HintSelector`.
 *
 * Auch der ruhige Zustand wird angezeigt ("Komposition passt"), nicht nur der Fehlerfall.
 * Ohne das kann der Nutzer nicht unterscheiden zwischen "alles gut" und "die App analysiert
 * gerade nichts" — und genau diese Unsicherheit hatte die Gap-Analyse den bestehenden Apps
 * angekreidet.
 */
@Composable
fun ScoreOverlay(
    hint: Hint?,
    modifier: Modifier = Modifier,
) {
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
        StatusIcon(hasHint = hint != null)

        // AnimatedVisibility waere hier falsch: der Text soll sich *austauschen*, nicht
        // ein- und ausblenden. Die Daempfung gegen Flackern passiert eine Ebene hoeher
        // in HintStabilizer, nicht durch eine Animation.
        Text(
            text = hint?.message ?: "Komposition passt",
            color = if (hint != null) HINT_COLOR else OK_COLOR,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Kleines Symbol links vom Text: Dreieck bei Hinweis, Haken wenn alles passt.
 *
 * Von Hand gezeichnet statt ueber ein Icon-Set — `material-icons` ist in diesem Projekt
 * keine Abhaengigkeit, und zwei Pfade sind weniger Aufwand als ein weiteres Artefakt im
 * Build (das auf iOS mitkompiliert werden muesste).
 */
@Composable
private fun StatusIcon(hasHint: Boolean) {
    Canvas(modifier = Modifier.size(18.dp)) {
        if (hasHint) drawWarningTriangle() else drawCheck()
    }
}

private fun DrawScope.drawWarningTriangle() {
    val path = Path().apply {
        moveTo(size.width / 2f, 0f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, color = HINT_COLOR, style = Stroke(width = size.width * 0.12f))
    // Ausrufezeichen: Strich plus Punkt.
    drawLine(
        color = HINT_COLOR,
        start = Offset(size.width / 2f, size.height * 0.38f),
        end = Offset(size.width / 2f, size.height * 0.62f),
        strokeWidth = size.width * 0.12f,
    )
    drawCircle(
        color = HINT_COLOR,
        radius = size.width * 0.07f,
        center = Offset(size.width / 2f, size.height * 0.78f),
    )
}

private fun DrawScope.drawCheck() {
    val path = Path().apply {
        moveTo(size.width * 0.15f, size.height * 0.55f)
        lineTo(size.width * 0.4f, size.height * 0.8f)
        lineTo(size.width * 0.85f, size.height * 0.22f)
    }
    drawPath(path, color = OK_COLOR, style = Stroke(width = size.width * 0.14f))
}
