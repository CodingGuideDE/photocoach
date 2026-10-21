package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis

/**
 * Warnt, wenn die Kamera deutlich **nach oben** geneigt ist: senkrechte Linien (Gebaeude,
 * Baeume, Tueren) laufen dann im Bild nach oben zusammen und "kippen nach hinten" — der
 * haeufigste Anfaengerfehler bei Architektur.
 *
 * **Nach unten gibt es bewusst keinen Hinweis** (Nutzerentscheidung, 18.10.2026): nach unten
 * zu fotografieren ist oft gewollt — Motive am Boden, Kinder, Tiere, Tisch, Blick von oben —,
 * und die kippenden Linien fallen dort kaum auf. Die Regel hat dann keine Meinung.
 *
 * Nur in einem mittleren Bereich ([MIN_DEGREES] bis [MAX_DEGREES]): Ein paar Grad sind beim
 * Halten normal und unauffaellig. Steil nach oben (Himmel, Baumkronen) ist dagegen eine
 * bewusste Perspektive — da waere der Hinweis falsch.
 *
 * Die Messung kommt aus dem Lagesensor (`cameraPitchDegrees`), nicht aus dem Bild. Ob im Bild
 * ueberhaupt senkrechte Linien sind, weiss die Regel nicht — bei einer Landschaft ohne
 * Senkrechte kann der Hinweis deshalb ueberfluessig sein. Das ist die bewusste Grenze dieser
 * Stufe; eine Linienerkennung kaeme spaeter.
 */
object CameraPitchRule : ScoredRule {

    /** Ab dieser Neigung (Grad) gibt es einen Hinweis. */
    const val MIN_DEGREES = 15f

    /** Ab dieser Neigung gilt sie als gewollt (Blick nach oben/unten) — keine Aussage mehr. */
    const val MAX_DEGREES = 50f

    override fun assess(analysis: FrameAnalysis): RuleAssessment? {
        val pitch = analysis.cameraPitchDegrees ?: return null
        // Nach unten oder steil nach oben: keine Meinung (siehe Klassen-Doku).
        if (pitch < 0f || pitch > MAX_DEGREES) return null

        val hint = if (pitch >= MIN_DEGREES) {
            Hint("Kamera nach oben geneigt — gerade halten, sonst kippen Linien")
        } else {
            null
        }
        return RuleAssessment(score = 1f - smoothstep(5f, 30f, pitch), hint = hint)
    }
}
