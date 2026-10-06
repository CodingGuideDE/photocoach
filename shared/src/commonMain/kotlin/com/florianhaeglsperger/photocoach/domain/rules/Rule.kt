package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis

/**
 * Ein konkreter, umsetzbarer Hinweis fuer den Nutzer (Plan 3.2, z.B. "Motiv 12% zu weit
 * links von der optimalen Position").
 *
 * Bewusst nur eine Nachricht, keine Prioritaet: das Kombinieren mehrerer Regel-Ergebnisse
 * zu einem Live-Score und zu *einem* Hinweis ist Aufgabe des `CompositionScorer`, nicht der
 * einzelnen Regel.
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

/**
 * Was eine Regel ueber ein Frame zu sagen hat: *wie gut* es ist und, falls noetig, *was*
 * zu tun ist.
 *
 * Der [score] ist das, was "Hinweis ja/nein" nicht ausdruecken kann — ein Horizont mit 3°
 * ist noch kein Hinweis wert, aber schlechter als einer mit 0°. Erst damit laesst sich der
 * 0-100-Score des `CompositionScorer` bilden (Plan 3.2).
 */
data class RuleAssessment(
    /** 1 = einwandfrei, 0 = so schlecht, wie es fuer diese Regel geht. */
    val score: Float,
    /** Der Hinweis, oder `null`, wenn die Abweichung (noch) keinen wert ist. */
    val hint: Hint?,
)

/**
 * Eine [Rule], die zusaetzlich einen Schweregrad meldet.
 *
 * `assess` liefert `null`, wenn die Regel **keine Meinung** hat (kein Gesicht, keine
 * Saliency, Neigung nicht bestimmbar). Das ist bewusst etwas anderes als Score 0: eine Regel
 * ohne Datenlage darf den Gesamtscore weder hoch- noch runterziehen.
 */
interface ScoredRule : Rule {
    fun assess(analysis: FrameAnalysis): RuleAssessment?

    override fun evaluate(analysis: FrameAnalysis): Hint? = assess(analysis)?.hint
}

/**
 * Weicher Uebergang von 0 (bei [edge0]) nach 1 (bei [edge1]).
 *
 * Fuer alle Score-Kennlinien der Regeln: flach an beiden Enden, damit kleines Zittern der
 * Messung nahe am Optimum (oder weit daneben) den Live-Score nicht sichtbar zappeln laesst.
 */
internal fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
