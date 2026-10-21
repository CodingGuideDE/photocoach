package com.florianhaeglsperger.photocoach.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextOverflow
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
    // Zweiteilig wie die Regeltexte (splitHint): oben was zu tun ist, darunter wozu.
    Verdict.AwaitingSubject -> "Motiv antippen — dann prüft die App seine Platzierung"
}

/**
 * Live-Feedback zur Komposition (Plan 3.4): Score 0-100 plus *ein* Hinweis.
 *
 * Bewusst eine schmale Karte am oberen Bildrand statt eines Overlays ueber dem Motiv:
 * der Sucher soll frei bleiben — man fotografiert das Bild, nicht die App. Immer nur *ein*
 * Hinweis, ausgewaehlt vom `CompositionScorer`.
 *
 * **Zwei Zeilen mit fester Rolle statt eines umbrechenden Satzes:** oben *was* nicht stimmt
 * (fett, in der Zustandsfarbe), darunter *was zu tun ist* (gedaempft). Vorher brach der
 * lange Satz irgendwo mitten im Text um, und die Karte sprang zwischen ein- und
 * zweizeiligen Zustaenden. Die Mindesthoehe haelt die Karte ruhig.
 *
 * Drei Zustaende, nicht zwei: "Hinweis" (orange), "passt" (gruen) und "kein Motiv"
 * (neutral grau). Den dritten gab es frueher nicht — dann stand "Komposition passt", sobald
 * der Horizont gerade war, auch wenn die App vom Bildinhalt gar nichts erkannt hatte.
 *
 * [score] fehlt (`null`) genau im Zustand "kein Motiv": eine Zahl ohne Grundlage waere
 * dieselbe Falschaussage wie das falsche Lob.
 *
 * [onClick] ist fuer das Diagnose-Badge (Rohdaten ein-/ausblenden), kein Nutzerfeature.
 */
@Composable
fun ScoreOverlay(
    verdict: Verdict,
    score: Int?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val (headline, action) = splitHint(verdict.displayText())
    val color = when (verdict) {
        is Verdict.Fix -> HINT_COLOR
        Verdict.Good -> OK_COLOR
        Verdict.NoSubject, Verdict.AwaitingSubject -> NEUTRAL_COLOR
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = MIN_HEIGHT)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (score != null) ScoreBadge(score)
        StatusIcon(verdict)

        // AnimatedVisibility waere hier falsch: der Text soll sich *austauschen*, nicht
        // ein- und ausblenden. Die Daempfung gegen Flackern passiert eine Ebene hoeher
        // in VerdictStabilizer, nicht durch eine Animation.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (action != null) {
                Text(
                    text = action,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Mindesthoehe der Karte: zwei Textzeilen plus Rand, damit sie nicht zwischen Zustaenden springt. */
private val MIN_HEIGHT = 64.dp

/**
 * Teilt einen Hinweis in "was stimmt nicht" und "was tun".
 *
 * Alle Regeln liefern ihre Hinweise als "Problem — Handlung." (siehe `domain/rules`); ohne
 * Trenner (z.B. "Komposition passt") gibt es nur die erste Zeile. Reine Darstellung —
 * die Regeltexte selbst bleiben ein Satz, damit Feldtest-Protokoll und Tests unveraendert
 * bleiben.
 */
internal fun splitHint(message: String): Pair<String, String?> {
    val index = message.indexOf(HINT_SEPARATOR)
    if (index < 0) return message to null
    return message.substring(0, index) to message.substring(index + HINT_SEPARATOR.length)
}

private const val HINT_SEPARATOR = " — "

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
            Verdict.NoSubject, Verdict.AwaitingSubject -> drawCircle(
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
