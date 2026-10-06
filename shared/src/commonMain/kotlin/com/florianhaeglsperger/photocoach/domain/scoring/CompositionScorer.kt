package com.florianhaeglsperger.photocoach.domain.scoring

import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import com.florianhaeglsperger.photocoach.domain.rules.DeadSpaceRule
import com.florianhaeglsperger.photocoach.domain.rules.Hint
import com.florianhaeglsperger.photocoach.domain.rules.HorizonRule
import com.florianhaeglsperger.photocoach.domain.rules.PortraitFramingRule
import com.florianhaeglsperger.photocoach.domain.rules.RuleAssessment
import com.florianhaeglsperger.photocoach.domain.rules.RuleOfThirdsRule
import com.florianhaeglsperger.photocoach.domain.rules.ThirdsTargetTracker
import com.florianhaeglsperger.photocoach.domain.subject.DefaultSubjectResolver
import com.florianhaeglsperger.photocoach.domain.subject.SubjectResolver
import kotlin.math.roundToInt

/**
 * Was die App zu einem Frame sagt — die Grundlage fuer das ScoreOverlay.
 */
sealed interface Verdict {
    /** Es gibt etwas zu korrigieren. */
    data class Fix(val hint: Hint) : Verdict

    /** Die Komposition wurde bewertet und passt. */
    data object Good : Verdict

    /**
     * Es gibt nichts, woran sich die Komposition beurteilen liesse — kein Gesicht, keine
     * verwertbare Saliency. Hoechstens der Horizont war messbar.
     *
     * Bewusst ein eigener Zustand und nicht [Good]: Frueher stand hier "Komposition passt",
     * sobald der Horizont gerade war — also praktisch immer, weil auf Android die Saliency
     * fehlte und ohne Gesicht keine andere Regel eine Meinung hatte. Ein gerader Horizont
     * allein ist aber keine gute Komposition, und die App darf nicht loben, was sie nicht
     * gesehen hat.
     */
    data object NoSubject : Verdict
}

/** Gesamtergebnis eines Frames. */
data class CompositionAssessment(
    /**
     * 0-100, oder `null`, wenn keine *inhaltliche* Regel (Rahmung, Drittel, tote Flaeche)
     * eine Meinung hatte — siehe [Verdict.NoSubject].
     */
    val score: Int?,
    val verdict: Verdict,
)

/**
 * Kombiniert alle Regeln zu **einem** Hinweis und einem 0-100-Score (Plan 3.2).
 *
 * ## Der Hinweis: Rangfolge nach Reparierbarkeit
 *
 * Plan 3.2: "nur 1 Hinweis gleichzeitig anzeigen, wichtigsten zuerst — sonst Overload".
 *
 * 1. **Portraet-Rahmung** — ein angeschnittener Kopf ist der einzige Fehler hier, der sich
 *    hinterher *nicht* reparieren laesst. Was weg ist, ist weg.
 * 2. **Horizont** — objektiv falsch und eindeutig benennbar, aber notfalls durch Drehen und
 *    Beschneiden zu retten. Kostet dann Bildrand.
 * 3. **Drittel-Regel** — eine Empfehlung, keine Regel, und je nach Motiv bewusst verletzbar.
 * 4. **Tote Flaeche** — am leichtesten hinterher per Beschnitt zu beheben, und bei einem
 *    Motiv ohnehin meist schon ueber die Drittel-Regel abgedeckt.
 *
 * ## Der Score: gewichteter Mittelwert
 *
 * Jede Regel mit Meinung traegt ihren Score (0..1) mit einem Gewicht bei; Regeln ohne
 * Meinung fallen heraus, statt als 0 zu zaehlen (siehe `ScoredRule`). Die Gewichte:
 *  - Rahmung und Horizont: [WEIGHT_STRONG] — eindeutige Fehler.
 *  - Drittel: [WEIGHT_THIRDS] mal Motiv-Sicherheit. Plan 3.2.1: "nur mittleres Gewicht —
 *    Symmetrie ist eine legitime Bildsprache". Ein wackliger Saliency-Klumpen zaehlt
 *    entsprechend weniger als ein erkanntes Gesicht.
 *  - Tote Flaeche: [WEIGHT_DEAD_SPACE].
 *
 * Haelt Zustand (ueber [ThirdsTargetTracker]) und ist deshalb eine Klasse, kein `object` —
 * pro Sucher-Sitzung eine Instanz.
 */
class CompositionScorer(
    private val subjectResolver: SubjectResolver = DefaultSubjectResolver,
    private val thirdsTracker: ThirdsTargetTracker = ThirdsTargetTracker(),
) {

    fun assess(analysis: FrameAnalysis): CompositionAssessment {
        // Alle Regeln auswerten, nicht nur bis zum ersten Hinweis: der Score braucht alle,
        // und der Drittel-Tracker braucht jedes Frame, sonst veraltet seine Hysterese,
        // sobald laengere Zeit eine hoeher priorisierte Regel greift.
        val portrait = PortraitFramingRule.assess(analysis)
        val horizon = HorizonRule.assess(analysis)
        val thirds = assessThirds(analysis)
        val deadSpace = DeadSpaceRule.assess(analysis)

        val hint = portrait?.hint ?: horizon?.hint ?: thirds?.assessment?.hint ?: deadSpace?.hint

        val contentEvaluated = portrait != null || thirds != null || deadSpace != null
        val score = if (contentEvaluated) {
            weightedScore(
                listOfNotNull(
                    portrait?.let { it to WEIGHT_STRONG },
                    horizon?.let { it to WEIGHT_STRONG },
                    thirds?.let { it.assessment to WEIGHT_THIRDS * it.confidence },
                    deadSpace?.let { it to WEIGHT_DEAD_SPACE },
                ),
            )
        } else {
            null
        }

        val verdict = when {
            hint != null -> Verdict.Fix(hint)
            contentEvaluated -> Verdict.Good
            else -> Verdict.NoSubject
        }
        return CompositionAssessment(score = score, verdict = verdict)
    }

    /**
     * Vergisst den Drittel-Zielpunkt — aufzurufen, wenn die Szene keinen Bezug mehr zur
     * vorherigen hat (Objektivwechsel).
     */
    fun reset() {
        thirdsTracker.reset()
    }

    private class ThirdsResult(val assessment: RuleAssessment, val confidence: Float)

    private fun assessThirds(analysis: FrameAnalysis): ThirdsResult? {
        val subject = subjectResolver.resolve(analysis)
        if (subject == null) {
            // Ohne erkennbares Motiv den Tracker zuruecksetzen: sonst haelt er einen
            // Zielpunkt fest, der zur naechsten Szene keinen Bezug mehr hat, und der
            // erste Hinweis danach zeigt in die falsche Richtung.
            thirdsTracker.reset()
            return null
        }
        val target = thirdsTracker.select(subject.x, subject.y, analysis.aspectRatio)
        val assessment = RuleOfThirdsRule.assess(analysis, target) ?: return null
        return ThirdsResult(assessment, subject.confidence)
    }

    private fun weightedScore(parts: List<Pair<RuleAssessment, Float>>): Int? {
        val totalWeight = parts.sumOf { it.second.toDouble() }
        if (totalWeight <= 0.0) return null
        val sum = parts.sumOf { (assessment, weight) -> assessment.score.toDouble() * weight }
        return (sum / totalWeight * 100).roundToInt().coerceIn(0, 100)
    }

    companion object {
        const val WEIGHT_STRONG = 1f
        const val WEIGHT_THIRDS = 0.6f
        const val WEIGHT_DEAD_SPACE = 0.7f
    }
}
