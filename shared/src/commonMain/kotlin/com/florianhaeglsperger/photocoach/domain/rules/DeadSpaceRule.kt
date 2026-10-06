package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.geometry.THIRD_HIGH
import com.florianhaeglsperger.photocoach.domain.geometry.THIRD_LOW
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import com.florianhaeglsperger.photocoach.domain.model.SaliencyPoint
import com.florianhaeglsperger.photocoach.domain.subject.DefaultSubjectResolver
import com.florianhaeglsperger.photocoach.domain.subject.SubjectResolver
import kotlin.math.abs
import kotlin.math.min

/**
 * Warnt vor toter Flaeche (Plan 3.2): eine Bildhaelfte, in der praktisch nichts passiert,
 * ohne dass es dafuer einen erkennbaren Grund gibt.
 *
 * ## Was gemessen wird
 *
 * Plan-Wortlaut: "Anteil des Bilds ohne Saliency pro Bildhaelfte vergleichen". Gemessen wird
 * der Anteil des gesamten Saliency-Gewichts, der in jeder Haelfte liegt — links/rechts und
 * oben/unten. Haelt die leerere Haelfte weniger als [EMPTY_SHARE], ist sie "tot".
 *
 * ## Der erkennbare Grund
 *
 * Eine leere Haelfte ist fuer sich kein Fehler. Sitzt das Motiv auf einer Drittel-Linie,
 * ist die Gegenseite *zwangslaeufig* leerer — das ist genau die Komposition, die
 * [RuleOfThirdsRule] empfiehlt. Wuerde diese Regel dann warnen, widerspraechen sich zwei
 * Hinweise gegenseitig. Deshalb gilt die Asymmetrie als begruendet, wenn das Motiv auf
 * der betroffenen Achse nahe einer Drittel-Linie liegt ([THIRDS_BAND]).
 *
 * Der Plan nennt als zweiten Grund die **Blickrichtung** (Platz vor dem Gesicht). Die ist
 * noch nicht verfuegbar (siehe [PortraitFramingRule]); ein Gesicht auf dem Drittel ist
 * ueber den ersten Grund aber bereits abgedeckt.
 *
 * ## Wofuer die Regel damit eigentlich da ist
 *
 * Fuer Szenen **ohne** einzelnes Hauptmotiv — Landschaft, Strasse, mehrere Objekte —, bei
 * denen [RuleOfThirdsRule] mangels Motiv schweigt. Klassischer Fall: alles Interessante in
 * der unteren Bildhaelfte, darueber leerer, strukturloser Himmel.
 */
object DeadSpaceRule : ScoredRule {

    /** Haelt eine Haelfte weniger als diesen Anteil des Saliency-Gewichts, gilt sie als leer. */
    const val EMPTY_SHARE = 0.1f

    /**
     * Ab diesem Anteil gilt eine Haelfte fuer den Score als ausreichend belegt (Score 1).
     * Dazwischen faellt der Score weich ab.
     */
    private const val COMFORT_SHARE = 0.3f

    /** Score einer voellig leeren, unbegruendeten Haelfte — schlecht, aber kein Totalausfall. */
    private const val MIN_SCORE = 0.3f

    /**
     * Wie nah (normiert, je Achse) das Motiv an einer Drittel-Linie liegen muss, damit die
     * leere Gegenseite als gewollt gilt. 0,1 laesst die uebliche Ungenauigkeit beim Halten
     * zu, schliesst aber ein Motiv am Bildrand (0,1 oder 0,9) aus.
     */
    const val THIRDS_BAND = 0.1f

    /**
     * Mindestanzahl Saliency-Punkte, damit die Regel eine Meinung hat. Ein oder zwei
     * Rasterzellen sind ein einzelnes kleines Detail — das ist Sache der Drittel-Regel,
     * nicht eine Aussage ueber die Verteilung im Bild.
     */
    const val MIN_POINTS = 3

    private val resolver: SubjectResolver = DefaultSubjectResolver

    override fun assess(analysis: FrameAnalysis): RuleAssessment? {
        val points = analysis.saliencyRegions
        if (points.size < MIN_POINTS) return null
        val total = points.sumOf { it.weight.toDouble() }.toFloat()
        if (total <= 0f) return null

        val subject = resolver.resolve(analysis)

        val horizontal = axis(
            leftOrTopShare = share(points, total) { it.x },
            subjectCoordinate = subject?.x,
        )
        val vertical = axis(
            leftOrTopShare = share(points, total) { it.y },
            subjectCoordinate = subject?.y,
        )

        // Die schlechtere Achse bestimmt Score und Hinweis — zwei Korrekturen gleichzeitig
        // waeren im Sucher nicht umsetzbar.
        val worst = if (horizontal.score <= vertical.score) horizontal else vertical
        val hint = when {
            !worst.deadHalf -> null
            worst === horizontal && worst.emptyIsFirst ->
                Hint("Linke Bildhälfte fast leer — Kamera etwas nach rechts schwenken.")
            worst === horizontal ->
                Hint("Rechte Bildhälfte fast leer — Kamera etwas nach links schwenken.")
            worst.emptyIsFirst ->
                Hint("Obere Bildhälfte fast leer — Kamera etwas nach unten schwenken.")
            else ->
                Hint("Untere Bildhälfte fast leer — Kamera etwas nach oben schwenken.")
        }
        return RuleAssessment(score = worst.score, hint = hint)
    }

    /**
     * Ergebnis fuer eine Achse. [emptyIsFirst] = die leerere Haelfte ist die linke bzw. obere.
     */
    private class Axis(val score: Float, val deadHalf: Boolean, val emptyIsFirst: Boolean)

    private fun axis(leftOrTopShare: Float, subjectCoordinate: Float?): Axis {
        val emptierShare = min(leftOrTopShare, 1f - leftOrTopShare)
        val emptyIsFirst = leftOrTopShare < 0.5f

        val justified = subjectCoordinate != null && isNearThirdsLine(subjectCoordinate)
        if (justified) return Axis(score = 1f, deadHalf = false, emptyIsFirst = emptyIsFirst)

        val score = MIN_SCORE + (1f - MIN_SCORE) * smoothstep(0f, COMFORT_SHARE, emptierShare)
        return Axis(score = score, deadHalf = emptierShare < EMPTY_SHARE, emptyIsFirst = emptyIsFirst)
    }

    private fun isNearThirdsLine(coordinate: Float): Boolean =
        abs(coordinate - THIRD_LOW) <= THIRDS_BAND || abs(coordinate - THIRD_HIGH) <= THIRDS_BAND

    /**
     * Anteil des Gewichts in der linken bzw. oberen Haelfte. Ein Punkt genau auf der Mitte
     * zaehlt je zur Haelfte auf beide Seiten — sonst entschiede eine Rundung ueber "leer".
     */
    private fun share(points: List<SaliencyPoint>, total: Float, coordinate: (SaliencyPoint) -> Float): Float {
        var first = 0.0
        for (point in points) {
            val c = coordinate(point)
            first += when {
                c < 0.5f -> point.weight.toDouble()
                c == 0.5f -> point.weight / 2.0
                else -> 0.0
            }
        }
        return (first / total).toFloat()
    }
}

