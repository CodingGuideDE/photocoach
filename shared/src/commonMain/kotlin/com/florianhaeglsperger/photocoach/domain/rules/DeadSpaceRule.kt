package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.geometry.THIRD_HIGH
import com.florianhaeglsperger.photocoach.domain.geometry.THIRD_LOW
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import com.florianhaeglsperger.photocoach.domain.model.StructureGrid
import com.florianhaeglsperger.photocoach.domain.subject.DefaultSubjectResolver
import com.florianhaeglsperger.photocoach.domain.subject.SubjectResolver
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Warnt vor toter Flaeche (Plan 3.2): eine Bildhaelfte, in der praktisch nichts ist, ohne
 * dass es dafuer einen erkennbaren Grund gibt.
 *
 * ## Was gemessen wird
 *
 * Fuer jede Bildhaelfte (links/rechts, oben/unten), welcher Anteil ihrer Rasterzellen
 * **Bildstruktur** traegt — Kanten, Textur, Details ([FrameAnalysis.structure], ab
 * [STRUCTURE_THRESHOLD]). Ist das in einer Haelfte weniger als [EMPTY_COVERAGE], waehrend
 * die andere deutlich belegt ist, ist sie "tot": blanker Himmel, glatte Wand.
 *
 * **Warum nicht mehr ueber die Saliency (bis 08.10.2026):** Saliency misst, was am
 * staerksten *hervorsticht*, normiert aufs Maximum. Bei Kontrast-Aufnahmen mit etwas im
 * Vordergrund drueckte der harte Kontrast jede schwaechere Struktur unter die Schwelle — eine
 * strukturierte Mauer neben einem hellen Objekt galt als leer, und die Regel meldete
 * "Bildhaelfte fast leer". Ausserdem markiert die Saliency bei grossen Objekten nur die
 * Konturen, deren Inneres zaehlte als leer. Struktur wird dagegen absolut und je Zelle
 * gemessen: was eine Haelfte enthaelt, haengt nicht davon ab, wie auffaellig die andere ist.
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
 * ueber den ersten Grund aber bereits abgedeckt. Gewollter Negativraum (Minimalismus) wird
 * noch nicht als Grund erkannt — siehe CLAUDE.md.
 */
object DeadSpaceRule : ScoredRule {

    /**
     * Ab diesem lokalen Kontrast traegt eine Zelle Struktur.
     *
     * Gemessen (siehe `ImageStructure`): glatter Himmelsverlauf mit Sensorrauschen ~0,010-0,015,
     * sehr schwache Textur ~0,03, deutliche Textur (Mauer, Laub) ~0,08, Holzwand im
     * Emulator-Foto 0,05-0,25, Kanten von Objekten ab ~0,2. 0,03 liegt mit Faktor 2 ueber
     * dem glatten Himmel.
     */
    const val STRUCTURE_THRESHOLD = 0.03f

    /** Traegt eine Haelfte in weniger als diesem Anteil ihrer Zellen Struktur, ist sie leer. */
    const val EMPTY_COVERAGE = 0.1f

    /**
     * So viel muss die *andere* Haelfte mindestens enthalten, damit von einer Asymmetrie die
     * Rede sein kann. Sind beide fast leer (Nebel, weisse Wand), ist das kein
     * Kompositionsfehler einer Haelfte.
     */
    const val MIN_CONTENT_COVERAGE = 0.25f

    /**
     * Unter diesem Anteil strukturierter Zellen im ganzen Bild hat die Regel keine Meinung —
     * ein strukturloses Bild hat keine "tote Haelfte", es ist insgesamt leer.
     */
    const val MIN_TOTAL_COVERAGE = 0.05f

    /** Ab dieser Belegung gilt eine Haelfte fuer den Score als voll (Score 1). */
    private const val COMFORT_COVERAGE = 0.3f

    /** Score einer voellig leeren, unbegruendeten Haelfte — schlecht, aber kein Totalausfall. */
    private const val MIN_SCORE = 0.3f

    /**
     * Wie nah (normiert, je Achse) das Motiv an einer Drittel-Linie liegen muss, damit die
     * leere Gegenseite als gewollt gilt. 0,1 laesst die uebliche Ungenauigkeit beim Halten
     * zu, schliesst aber ein Motiv am Bildrand (0,1 oder 0,9) aus.
     */
    const val THIRDS_BAND = 0.1f

    private val resolver: SubjectResolver = DefaultSubjectResolver

    override fun assess(analysis: FrameAnalysis): RuleAssessment? {
        val structure = analysis.structure ?: return null
        if (structure.coverage { _, _ -> true } < MIN_TOTAL_COVERAGE) return null

        val subject = resolver.resolve(analysis)
        val horizontal = axis(
            first = structure.coverage { column, _ -> column < structure.columns / 2 },
            second = structure.coverage { column, _ -> column >= structure.columns / 2 },
            subjectCoordinate = subject?.x,
        )
        val vertical = axis(
            first = structure.coverage { _, row -> row < structure.rows / 2 },
            second = structure.coverage { _, row -> row >= structure.rows / 2 },
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

    /** Ergebnis fuer eine Achse. [emptyIsFirst] = die leerere Haelfte ist die linke bzw. obere. */
    private class Axis(val score: Float, val deadHalf: Boolean, val emptyIsFirst: Boolean)

    /** [first]/[second]: Strukturanteil der linken/rechten bzw. oberen/unteren Haelfte. */
    private fun axis(first: Float, second: Float, subjectCoordinate: Float?): Axis {
        val emptier = min(first, second)
        val fuller = max(first, second)
        val emptyIsFirst = first < second

        // Keine Asymmetrie, wenn auch die vollere Haelfte kaum etwas enthaelt.
        val asymmetric = fuller >= MIN_CONTENT_COVERAGE
        val justified = subjectCoordinate != null && isNearThirdsLine(subjectCoordinate)
        if (!asymmetric || justified) return Axis(score = 1f, deadHalf = false, emptyIsFirst = emptyIsFirst)

        val score = MIN_SCORE + (1f - MIN_SCORE) * smoothstep(0f, COMFORT_COVERAGE, emptier)
        return Axis(score = score, deadHalf = emptier < EMPTY_COVERAGE, emptyIsFirst = emptyIsFirst)
    }

    private fun isNearThirdsLine(coordinate: Float): Boolean =
        abs(coordinate - THIRD_LOW) <= THIRDS_BAND || abs(coordinate - THIRD_HIGH) <= THIRDS_BAND

    /** Anteil der Zellen, die [inHalf] auswaehlt und die Struktur tragen. */
    private fun StructureGrid.coverage(inHalf: (column: Int, row: Int) -> Boolean): Float {
        var cells = 0
        var structured = 0
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if (!inHalf(column, row)) continue
                cells++
                if (this[column, row] >= STRUCTURE_THRESHOLD) structured++
            }
        }
        return if (cells == 0) 0f else structured.toFloat() / cells
    }
}
