package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Warnt vor einem schiefen Horizont (Plan 3.2 sah 2° vor, im Feldtest zu streng: jetzt 4°).
 *
 * Toleranz statt 0°: kleine Abweichungen fallen aus der Hand beim Fotografieren ohnehin
 * an, eine Warnung darunter waere nur Rauschen und wuerde eher nerven als helfen.
 *
 * Die Korrekturrichtung im Hinweistext folgt der Vorzeichen-Konvention aus
 * [FrameAnalysis.horizonTiltDegrees]: positiv = rechte Seite tiefer → rechte Seite muss
 * angehoben werden, und umgekehrt.
 */
object HorizonRule : ScoredRule {

    /**
     * Ab dieser Abweichung (Grad) gilt der Horizont als schief. Plan 3.2 nannte 2°, das war
     * beim Ausprobieren zu empfindlich (Handzittern reicht) - daher 4°.
     */
    private const val TOLERANCE_DEGREES = 4f

    /**
     * Score-Kennlinie: bis hierhin gilt der Horizont als perfekt (Messrauschen des Sensors),
     * ab [ZERO_SCORE_DEGREES] ist der Score 0. Bei der Hinweis-Schwelle von 4° liegt er
     * bei gut 0,8 — "fast gut", nicht "schlecht".
     */
    private const val PERFECT_DEGREES = 1f
    private const val ZERO_SCORE_DEGREES = 12f

    /**
     * Ab dieser Abweichung ist es kein schiefer Horizont mehr, sondern eine andere
     * Geraetelage, als die Anzeige annimmt — etwa ein kopfueber gehaltenes Telefon, dessen
     * Oberflaeche nicht mitdreht (Android dreht standardmaessig nicht ins umgekehrte
     * Hochformat). Im Emulator stand so "Horizont ca. 180° schief — linke Seite anheben"
     * im Sucher. Kein Nutzer korrigiert 180° durch Anheben einer Seite; die Regel hat hier
     * schlicht keine Aussage.
     */
    private const val MAX_PLAUSIBLE_DEGREES = 45f

    override fun assess(analysis: FrameAnalysis): RuleAssessment? {
        // null heisst "nicht bestimmbar" (siehe Doc an FrameAnalysis.horizonTiltDegrees) -
        // die Regel darf daraus weder einen Hinweis noch einen Score erfinden.
        val tilt = analysis.horizonTiltDegrees ?: return null
        if (abs(tilt) > MAX_PLAUSIBLE_DEGREES) return null
        return RuleAssessment(
            score = 1f - smoothstep(PERFECT_DEGREES, ZERO_SCORE_DEGREES, abs(tilt)),
            hint = hintFor(tilt),
        )
    }

    private fun hintFor(tilt: Float): Hint? {
        if (abs(tilt) <= TOLERANCE_DEGREES) return null

        val side = if (tilt > 0f) "rechte" else "linke"
        // Auf ganze Grad runden: die Nachkommastelle der Sensor-/Vision-Messung ist fuer
        // den Nutzer keine sinnvolle Information, nur die ungefaehre Groessenordnung zaehlt.
        val degrees = abs(tilt).roundToInt()
        return Hint("Horizont ca. $degrees° schief — $side Seite anheben.")
    }
}
