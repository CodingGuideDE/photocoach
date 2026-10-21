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
 * **Hinweistext beschreibt nur das Motiv, ohne Kamera-Anweisung und ohne Prozent**
 * ("Motiv etwas zu hoch", "Motiv viel zu weit links"). Bis 10.10.2026 stand dort
 * "Motiv ca. 17 % zu weit oben — Kamera etwas nach oben schwenken". Das war physikalisch
 * richtig (Kamera hoch → Bildinhalt wandert nach unten), wurde aber beim kurzen Blick als
 * Widerspruch gelesen ("oben … oben"), und eine Prozentzahl sagt beim Fotografieren nichts
 * darueber, wie weit man die Kamera bewegen muss. "Etwas"/"viel" ([LARGE_OFFSET_PERCENT])
 * transportiert genau das. Die Richtung der Kamera ergibt sich aus dem Sucher von selbst;
 * ein Pfeil dafuer ist Plan-Phase 4.
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

    /**
     * Ab diesem Versatz (Prozent der Bildbreite) heisst es "viel zu …" statt "etwas zu …".
     *
     * Zum Vergleich: von der Bildmitte bis zur Drittel-Linie sind es rund 17 %. Ab 12 % sitzt
     * das Motiv also naeher an der Mitte (oder am Rand) als am Drittel — das ist eine
     * spuerbare Kamerabewegung, keine Feinkorrektur mehr.
     */
    const val LARGE_OFFSET_PERCENT = 12

    /**
     * Liegt das Motiv auf einer Achse so nah an der Bildmitte (Anteil der Bildbreite bzw.
     * -hoehe), hat es auf dieser Achse **keine Richtung**: der linke und der rechte (bzw.
     * obere und untere) Drittel-Punkt sind praktisch gleich weit weg.
     *
     * Gefunden 19.10.2026 (Nutzer, angetipptes Motiv in der Bildmitte): Die Regel meldete
     * "Motiv viel zu tief". Bei exakt gleichem Abstand nahm [nearestThirdsPoint] einfach den
     * ersten Punkt der Liste (links oben) — gemessen daran liegt die Mitte "zu tief", und im
     * Hochformat wiegt die senkrechte Achse schwerer. Die Richtung kam aus der Reihenfolge
     * einer Liste, nicht aus dem Bild.
     *
     * 4 %: etwa die Messungenauigkeit des Motivpunkts (Saliency-Raster ~8 % je Zelle). Bei
     * 5 % fiel schon ein Motiv bei x = 0,453 hinein — 12 % vom linken, aber 21 % vom rechten
     * Drittel, da ist "zu weit rechts" noch eine echte Richtung.
     */
    const val CENTER_BAND = 0.04f

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

        // Mittig auf einer Achse = auf dieser Achse keine Richtung (siehe CENTER_BAND).
        val centeredX = abs(evaluation.subject.x - 0.5f) < CENTER_BAND
        val centeredY = abs(evaluation.subject.y - 0.5f) < CENTER_BAND
        // Eine Achse mit Richtung, auf der das Motiv deutlich daneben liegt, geht vor —
        // dort ist der Hinweis eindeutig.
        val clearX = !centeredX && abs(offsetX) >= MIN_OFFSET_PERCENT
        val clearY = !centeredY && abs(offsetY) >= MIN_OFFSET_PERCENT
        if (!clearX && !clearY) {
            return when {
                centeredX && centeredY -> Hint("Motiv in der Bildmitte — aufs Drittel gesetzt wirkt es spannender")
                centeredX -> Hint("Motiv waagerecht mittig — nach links oder rechts aufs Drittel")
                else -> Hint("Motiv auf halber Höhe — höher oder tiefer aufs Drittel")
            }
        }

        // Nur die dominante Achse nennen: zwei Korrekturen gleichzeitig sind im Sucher
        // nicht umsetzbar, und der Rule-Contract sieht ohnehin genau einen Hinweis vor.
        // Achsen ohne Richtung (mittig) scheiden dabei aus.
        val horizontal = if (clearX && clearY) abs(offsetX) >= abs(offsetY) else clearX
        val offset = if (horizontal) offsetX else offsetY
        val amount = if (abs(offset) >= LARGE_OFFSET_PERCENT) "viel" else "etwas"
        val direction = when {
            horizontal && offset < 0 -> "zu weit links"
            horizontal -> "zu weit rechts"
            offset < 0 -> "zu hoch"
            else -> "zu tief"
        }
        return Hint("Motiv $amount $direction")
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
