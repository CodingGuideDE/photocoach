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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.florianhaeglsperger.photocoach.domain.scoring.Verdict

/** Warnfarbe fuer anliegende Hinweise — kraeftig genug zum Auffallen, ohne Alarm zu schreien. */
private val HINT_COLOR = Color(0xFFFFC24B)

/** Bestaetigungsfarbe, wenn nichts zu beanstanden ist. */
private val OK_COLOR = Color(0xFF7FD48B)

/** Neutral, wenn es nichts zu bewerten gibt — weder Lob noch Tadel. */
private val NEUTRAL_COLOR = Color(0xFFD0D0D0)

/** Ab diesem Score gilt die Zahl als gut (gruen), darunter bis [SCORE_FAIR] als mittel. */
private const val SCORE_GOOD = 75
private const val SCORE_FAIR = 50

/** Anzeigetext einer Aussage — auch fuer das Feldtest-Protokoll. */
fun Verdict.displayText(): String = when (this) {
    is Verdict.Fix -> hint.message
    Verdict.Good -> "Komposition passt"
    Verdict.NoSubject -> "Kein klares Motiv erkannt"
}

/**
 * Live-Feedback zur Komposition (Plan 3.4): Score 0-100 plus *ein* Satz.
 *
 * Bewusst eine schmale Leiste am oberen Bildrand statt eines Overlays ueber dem Motiv:
 * der Sucher soll frei bleiben — man fotografiert das Bild, nicht die App. Immer nur *ein*
 * Hinweis, ausgewaehlt vom `CompositionScorer`.
 *
 * Drei Zustaende, nicht zwei: "Hinweis" (orange), "passt" (gruen) und "kein Motiv"
 * (neutral grau). Den dritten gab es frueher nicht — dann stand "Komposition passt", sobald
 * der Horizont gerade war, auch wenn die App vom Bildinhalt gar nichts erkannt hatte.
 *
 * [score] fehlt (`null`) genau im Zustand "kein Motiv": eine Zahl ohne Grundlage waere
 * dieselbe Falschaussage wie das falsche Lob.
 */
@Composable
fun ScoreOverlay(
    verdict: Verdict,
    score: Int?,
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
        if (score != null) ScoreBadge(score)
        StatusIcon(verdict)

        // AnimatedVisibility waere hier falsch: der Text soll sich *austauschen*, nicht
        // ein- und ausblenden. Die Daempfung gegen Flackern passiert eine Ebene hoeher
        // in VerdictStabilizer, nicht durch eine Animation.
        Text(
            text = verdict.displayText(),
            color = when (verdict) {
                is Verdict.Fix -> HINT_COLOR
                Verdict.Good -> OK_COLOR
                Verdict.NoSubject -> NEUTRAL_COLOR
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Die Zahl, eingefaerbt nach Guete. Feste Breite, damit der Text daneben nicht springt. */
@Composable
private fun ScoreBadge(score: Int) {
    val color = when {
        score >= SCORE_GOOD -> OK_COLOR
        score >= SCORE_FAIR -> HINT_COLOR
        else -> Color(0xFFFF8A65)
    }
    Text(
        text = score.toString(),
        color = Color.Black,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(vertical = 2.dp),
    )
}

/**
 * Kleines Symbol links vom Text: Dreieck bei Hinweis, Haken wenn alles passt, Kreis wenn
 * es nichts zu bewerten gibt.
 *
 * Von Hand gezeichnet statt ueber ein Icon-Set — `material-icons` ist in diesem Projekt
 * keine Abhaengigkeit, und drei Pfade sind weniger Aufwand als ein weiteres Artefakt im
 * Build (das auf iOS mitkompiliert werden muesste).
 */
@Composable
private fun StatusIcon(verdict: Verdict) {
    Canvas(modifier = Modifier.size(18.dp)) {
        when (verdict) {
            is Verdict.Fix -> drawWarningTriangle()
            Verdict.Good -> drawCheck()
            Verdict.NoSubject -> drawCircle(
                color = NEUTRAL_COLOR,
                radius = size.width * 0.4f,
                style = Stroke(width = size.width * 0.12f),
            )
        }
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
