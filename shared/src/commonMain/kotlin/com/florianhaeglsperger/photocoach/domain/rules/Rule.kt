package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis

/**
 * Ein konkreter, umsetzbarer Hinweis fuer den Nutzer (Plan 3.2, z.B. "Motiv 12% zu weit
 * links von der optimalen Position").
 *
 * Bewusst nur eine Nachricht, keine Prioritaet/kein Score: das Kombinieren mehrerer
 * Regel-Ergebnisse zu einem Live-Score und einer priorisierten Liste ist Aufgabe des
 * `CompositionScorer` (Plan 3.2, noch nicht implementiert), nicht der einzelnen Regel.
 */
data class Hint(val message: String)

/**
 * Eine Kompositionsregel: eine pure Funktion von [FrameAnalysis] auf einen [Hint], oder
 * `null`, wenn es nichts zu bemaengeln gibt bzw. sich (mangels Daten) noch keine Aussage
 * treffen laesst. "Pure" heisst: keine Kamera, kein Emulator noetig zum Testen — siehe
 * `TestFrames.kt` in commonTest und `HorizonRuleTest` als Beispiel.
 */
fun interface Rule {
    fun evaluate(analysis: FrameAnalysis): Hint?
}
