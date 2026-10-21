package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import kotlin.math.abs

/**
 * Warnt, wenn der Horizont genau durch die Bildmitte laeuft — der haeufigste Anfaengerfehler
 * bei Landschaften: Himmel und Boden bekommen gleich viel Platz, keiner von beiden wirkt als
 * Hauptsache, das Bild wirkt "halbiert". Besser: ins obere Drittel (Vordergrund betonen) oder
 * ins untere (Himmel betonen).
 *
 * Grundlage ist die im Bild erkannte Linie ([FrameAnalysis.horizonLine]), nicht der
 * Lagesensor — der weiss nur, wie schief das Geraet ist, nicht wo der Horizont im Bild liegt.
 *
 * Gilt ohne angetipptes Motiv: es ist ein Tipp ueber das ganze Bild, kein Motiv noetig.
 */
object HorizonPlacementRule : ScoredRule {

    /**
     * Liegt die Linie naeher als das an der Bildmitte, gilt sie als "mittig". Die
     * Drittel-Linien sind ~0,17 entfernt, dazwischen bleibt Spielraum fuer eine bewusst
     * leicht verschobene Linie.
     */
    const val CENTER_BAND = 0.08f

    /**
     * Steiler als das ist es kein Horizont mehr, sondern eine Diagonale (Hang, Dach) — dazu
     * hat die Regel keine Meinung.
     */
    const val MAX_ANGLE_DEGREES = 20f

    /** Score einer genau mittigen Linie — ein Gestaltungsmangel, kein grober Fehler. */
    private const val MIN_SCORE = 0.4f

    override fun assess(analysis: FrameAnalysis): RuleAssessment? {
        val line = analysis.horizonLine ?: return null
        if (abs(line.angleDegrees(analysis.aspectRatio)) > MAX_ANGLE_DEGREES) return null

        val fromMiddle = abs(line.centerY - 0.5f)
        val hint = if (fromMiddle < CENTER_BAND) {
            Hint("Horizont in der Bildmitte — ins obere oder untere Drittel legen")
        } else {
            null
        }
        // Weich von "mittig" (MIN_SCORE) bis "auf dem Drittel" (1).
        val score = MIN_SCORE + (1f - MIN_SCORE) * smoothstep(0.03f, 0.15f, fromMiddle)
        return RuleAssessment(score = score, hint = hint)
    }
}
