package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis

/**
 * Warnt vor Belichtungsfehlern, die sich hinterher nicht mehr retten lassen: grosse
 * ausgefressene Flaechen (dort ist keine Zeichnung mehr im Bild) und ein insgesamt zu
 * dunkles Bild.
 *
 * Gehoert nicht zur Komposition im engen Sinn, ist aber ein Tipp, der fuer *jedes* Foto gilt
 * — auch ohne angetipptes Motiv. Der `CompositionScorer` nimmt ihn deshalb in den Hinweis,
 * nicht in den Kompositions-Score.
 *
 * Bewusst zurueckhaltend: helle Fenster im Innenraum oder tiefe Schatten bei Gegenlicht sind
 * normal. Gewarnt wird erst, wenn ein **grosser** Teil des Bildes betroffen ist — und bei
 * "zu dunkel" nur, wenn das Bild *insgesamt* dunkel ist, nicht schon bei kontrastreichen
 * Szenen mit schwarzen Schatten.
 *
 * Die Schwellen sind gesetzt, nicht gemessen — Kalibrierung gehoert in den Feldtest.
 */
object ExposureRule : ScoredRule {

    /** Ab diesem Anteil ausgefressener Flaeche gibt es einen Hinweis. */
    const val OVER_HINT_FRACTION = 0.15f

    /** Ab diesem Anteil fast schwarzer Flaeche — zusammen mit [DARK_MEAN] — ist das Bild zu dunkel. */
    const val UNDER_HINT_FRACTION = 0.35f

    /** Mittlere Helligkeit, unter der das Bild insgesamt als dunkel gilt. */
    const val DARK_MEAN = 0.2f

    override fun assess(analysis: FrameAnalysis): RuleAssessment? {
        val stats = analysis.exposure ?: return null

        val overScore = 1f - smoothstep(0.03f, 0.4f, stats.overexposedFraction)
        val underScore = if (stats.meanBrightness < DARK_MEAN) {
            1f - smoothstep(0.1f, 0.7f, stats.underexposedFraction)
        } else {
            1f
        }

        val hint = when {
            stats.overexposedFraction >= OVER_HINT_FRACTION ->
                Hint("Helle Bereiche überbelichtet — Belichtung verringern")
            stats.underexposedFraction >= UNDER_HINT_FRACTION && stats.meanBrightness < DARK_MEAN ->
                Hint("Bild zu dunkel — Belichtung erhöhen")
            else -> null
        }
        return RuleAssessment(score = minOf(overScore, underScore), hint = hint)
    }
}
