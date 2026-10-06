package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.geometry.NormalizedPoint
import com.florianhaeglsperger.photocoach.domain.geometry.THIRDS_POINTS
import com.florianhaeglsperger.photocoach.domain.geometry.distanceInWidths
import com.florianhaeglsperger.photocoach.domain.geometry.thirdsScore
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import com.florianhaeglsperger.photocoach.domain.subject.DefaultSubjectResolver
import com.florianhaeglsperger.photocoach.domain.subject.SubjectPoint
import com.florianhaeglsperger.photocoach.domain.subject.SubjectResolver
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Vollstaendiges Ergebnis der Drittel-Bewertung — mehr, als ein [Hint] transportieren kann.
 *
 * Wird von [RuleOfThirdsRule.analyse] geliefert und existiert fuer zwei kuenftige Abnehmer:
 * den `CompositionScorer` (braucht den [score], nicht den Text) und das erklaerbare Overlay
 * aus Plan 4 bzw. den Richtungspfeil aus Plan 6 (brauchen [subject], [target] und den
 * Versatz, um zeichnen zu koennen).
 */
data class ThirdsEvaluation(
    val subject: SubjectPoint,
    /** Der Drittel-Punkt, gegen den bewertet wurde. */
    val target: NormalizedPoint,
    /** Abstand Motiv ↔ Zielpunkt in Bildbreiten. */
    val distance: Float,
    /** 1 = genau auf dem Punkt, 0 = Bildmitte oder weiter weg. */
    val score: Float,
    /** Versatz in Prozent der Bildbreite. Positiv = Motiv liegt rechts vom Zielpunkt. */
    val offsetXPercent: Int,
    /** Versatz in Prozent der Bildbreite. Positiv = Motiv liegt unterhalb des Zielpunkts. */
    val offsetYPercent: Int,
)

/**
 * Bewertet, ob das Hauptmotiv nahe an einem der vier Drittel-Punkte sitzt (Plan 3.2/3.2.1).
 *
 * Arbeitet nicht direkt auf `saliencyRegions`, sondern auf dem Motivpunkt aus
 * [SubjectResolver] — siehe dessen Doc fuer den Grund. Ohne erkennbares Motiv gibt die
 * Regel `null` zurueck: sie hat dann *keine Meinung*, was etwas anderes ist als "schlecht
 * platziert". Ein Motiv genau in der Bildmitte liefert dagegen sehr wohl ein Ergebnis
 * (Score 0) — die Mitte ist eine bewusste Bildsprache, aber eben keine Drittel-Komposition.
 *
 * **Kamerafuehrung im Hinweistext:** wie bei [PortraitFramingRule] als Kamera-Aktion
 * formuliert und damit *entgegengesetzt* zum Versatz: Sitzt das Motiv zu weit links im
 * Bild, muss die Kamera nach links schwenken, damit der Bildinhalt relativ dazu nach rechts
 * wandert.
 */
object RuleOfThirdsRule : ScoredRule {

    /**
     * Unterhalb dieses Versatzes (Prozent der Bildbreite) gibt es keinen Hinweis.
     *
     * Der Motivpunkt ist eine Schaetzung — bei Saliency aus einem 12x12-Raster, dessen
     * Zelle allein schon rund 8 % breit ist. Alles darunter als Fehler zu melden hiesse,
     * die eigene Messungenauigkeit als Bildfehler auszugeben.
     */
    const val MIN_OFFSET_PERCENT = 5

    private val resolver: SubjectResolver = DefaultSubjectResolver

    override fun assess(analysis: FrameAnalysis): RuleAssessment? = assess(analysis, target = null)

    /**
     * Wie [assess], aber mit vorgegebenem Zielpunkt.
     *
     * Dafuer gedacht, dass die aufrufende Schicht einen [ThirdsTargetTracker] mitfuehrt und
     * dessen stabilisierten Zielpunkt hereinreicht. `null` heisst "nimm den naechstgelegenen"
     * und ist der zustandslose Fall.
     */
    fun assess(analysis: FrameAnalysis, target: NormalizedPoint?): RuleAssessment? {
        val evaluation = analyse(analysis, target) ?: return null
        return RuleAssessment(score = evaluation.score, hint = hintFor(evaluation))
    }

    /** Wie [evaluate], aber mit vorgegebenem Zielpunkt — siehe [assess]. */
    fun evaluate(analysis: FrameAnalysis, target: NormalizedPoint?): Hint? =
        assess(analysis, target)?.hint

    private fun hintFor(evaluation: ThirdsEvaluation): Hint? {
        val offsetX = evaluation.offsetXPercent
        val offsetY = evaluation.offsetYPercent
        if (abs(offsetX) < MIN_OFFSET_PERCENT && abs(offsetY) < MIN_OFFSET_PERCENT) return null

        // Nur die dominante Achse nennen: zwei Korrekturen gleichzeitig sind im Sucher
        // nicht umsetzbar, und der Rule-Contract sieht ohnehin genau einen Hinweis vor.
        return if (abs(offsetX) >= abs(offsetY)) {
            if (offsetX < 0) {
                Hint("Motiv ca. ${abs(offsetX)} % zu weit links — Kamera etwas nach links schwenken.")
            } else {
                Hint("Motiv ca. $offsetX % zu weit rechts — Kamera etwas nach rechts schwenken.")
            }
        } else {
            if (offsetY < 0) {
                Hint("Motiv ca. ${abs(offsetY)} % zu weit oben — Kamera etwas nach oben schwenken.")
            } else {
                Hint("Motiv ca. $offsetY % zu weit unten — Kamera etwas nach unten schwenken.")
            }
        }
    }

    /** Die volle Bewertung, oder `null`, wenn kein Motiv erkennbar ist. */
    fun analyse(analysis: FrameAnalysis, target: NormalizedPoint? = null): ThirdsEvaluation? {
        val subject = resolver.resolve(analysis) ?: return null
        val aspectRatio = analysis.aspectRatio
        val chosen = target ?: nearestThirdsPoint(subject.x, subject.y, aspectRatio)

        val distance = distanceInWidths(subject.x, subject.y, chosen.x, chosen.y, aspectRatio)

        return ThirdsEvaluation(
            subject = subject,
            target = chosen,
            distance = distance,
            score = thirdsScore(distance),
            offsetXPercent = ((subject.x - chosen.x) * 100f).roundToInt(),
            // Auch der vertikale Versatz in Bildbreiten, damit beide Achsen dieselbe
            // Einheit haben und sich vergleichen lassen.
            offsetYPercent = ((subject.y - chosen.y) / aspectRatio * 100f).roundToInt(),
        )
    }

    /** Der Drittel-Punkt mit dem kleinsten Abstand zum gegebenen Punkt. */
    fun nearestThirdsPoint(x: Float, y: Float, aspectRatio: Float): NormalizedPoint =
        THIRDS_POINTS.minBy { distanceInWidths(x, y, it.x, it.y, aspectRatio) }
}
