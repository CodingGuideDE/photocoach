package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Warnt vor einem schiefen Horizont (Plan 3.2: "Warnung bei `horizonTiltDegrees` > 2°").
 *
 * Toleranz statt 0°: kleine Abweichungen fallen aus der Hand beim Fotografieren ohnehin
 * an, eine Warnung darunter waere nur Rauschen und wuerde eher nerven als helfen.
 *
 * Die Korrekturrichtung im Hinweistext folgt der Vorzeichen-Konvention aus
 * [FrameAnalysis.horizonTiltDegrees]: positiv = rechte Seite tiefer → rechte Seite muss
 * angehoben werden, und umgekehrt.
 */
object HorizonRule : Rule {

    /** Ab dieser Abweichung (Grad) gilt der Horizont als schief, siehe Plan 3.2. */
    private const val TOLERANCE_DEGREES = 2f

    override fun evaluate(analysis: FrameAnalysis): Hint? {
        // null heisst "nicht bestimmbar" (siehe Doc an FrameAnalysis.horizonTiltDegrees) -
        // die Regel darf daraus keinen Hinweis erfinden.
        val tilt = analysis.horizonTiltDegrees ?: return null
        if (abs(tilt) <= TOLERANCE_DEGREES) return null

        val side = if (tilt > 0f) "rechte" else "linke"
        // Auf ganze Grad runden: die Nachkommastelle der Sensor-/Vision-Messung ist fuer
        // den Nutzer keine sinnvolle Information, nur die ungefaehre Groessenordnung zaehlt.
        val degrees = abs(tilt).roundToInt()
        return Hint("Horizont ca. $degrees° schief — $side Seite anheben.")
    }
}
