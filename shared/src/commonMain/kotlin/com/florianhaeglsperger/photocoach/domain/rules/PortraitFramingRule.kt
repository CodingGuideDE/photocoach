package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.model.FaceRect
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import kotlin.math.min

/**
 * Warnt bei schlecht gerahmten Portraets (Plan 3.2): Kopf oben, Kinn unten oder Gesicht
 * seitlich am Bildrand angeschnitten.
 *
 * Gilt nur "bei erkanntem Gesicht" (Plan-Wortlaut) — ohne Gesicht macht die Regel keine
 * Aussage. Sind mehrere Gesichter im Bild, zaehlt das groesste (per Flaeche): das ist mit
 * hoher Wahrscheinlichkeit das eigentliche Motiv, kleinere Gesichter im Hintergrund sollen
 * die Rahmung nicht verfaelschen.
 *
 * Prueft in dieser Reihenfolge und meldet hoechstens *einen* Hinweis (Rule-Contract, siehe
 * [Rule]) — der Kopf oben ist der haeufigste und auffaelligste Anschnitt und geht vor:
 * 1. Kopf oben abgeschnitten
 * 2. Kinn unten abgeschnitten
 * 3. Gesicht seitlich am Rand
 *
 * **Warum es keine Blickraum-Pruefung mehr gibt:** Die fruehere Version schaetzte die
 * Blickrichtung aus der Lage des Gesichts ("links der Mitte → blickt nach rechts") und
 * pruefte dann den Platz in *dieser* Richtung. Das ist zirkulaer: ein Gesicht links der
 * Mitte hat nach rechts per Konstruktion mehr als die halbe Bildbreite Platz. Ausloesen
 * konnte die Pruefung nur bei Gesichtern, die ueber 80 % der Bildbreite einnehmen — die
 * Unit-Tests dafuer brauchten Rechtecke, die ueber den Bildrand hinausragen, was echte Daten
 * nie liefern (`FaceRect` ist auf 0..1 begrenzt). Echter Blickraum braucht die echte
 * Blickrichtung (ML Kit liefert die Kopfdrehung als `headEulerAngleY`); deren Vorzeichen muss
 * aber erst am Geraet belegt werden, sonst schickt der Hinweis den Nutzer in die falsche
 * Richtung (siehe CLAUDE.md, "Bekannte Luecke").
 *
 * **Kamerafuehrung im Hinweistext:** die Korrektur ist bewusst als Kamera-Aktion formuliert
 * (schwenken), nicht als Motiv-Aktion — der Nutzer bewegt beim Fotografieren die Kamera,
 * nicht das Motiv. Schwenkt man die Kamera in eine Richtung, wandert der Bildinhalt relativ
 * dazu in die *gegenteilige* Richtung im Sucher — Kamera nach oben schwenken schafft also
 * mehr Platz *ueber* dem Kopf.
 */
object PortraitFramingRule : ScoredRule {

    /** Mindestabstand zum oberen/unteren Rand, unterhalb dessen der Kopf als abgeschnitten gilt. */
    private const val HEAD_CLIP_MARGIN = 0.04f

    /**
     * Mindestabstand zum seitlichen Rand. Kleiner als oben/unten, weil die ML-Kit-Box die
     * Ohren nicht einschliesst, den Haaransatz aber schon fast — seitlich bleibt also von
     * Haus aus etwas Luft.
     */
    private const val SIDE_CLIP_MARGIN = 0.02f

    /**
     * Ab diesem Vielfachen der jeweiligen Schwelle gilt der Randabstand als voellig
     * unkritisch (Score 1). Genau an der Schwelle liegt der Score bei rund 0,26.
     */
    private const val COMFORT_FACTOR = 3f

    override fun assess(analysis: FrameAnalysis): RuleAssessment? {
        val face = analysis.faces.maxByOrNull { it.area } ?: return null

        val score = min(
            min(marginScore(face.top, HEAD_CLIP_MARGIN), marginScore(1f - face.bottom, HEAD_CLIP_MARGIN)),
            min(marginScore(face.left, SIDE_CLIP_MARGIN), marginScore(1f - face.right, SIDE_CLIP_MARGIN)),
        )
        return RuleAssessment(score = score, hint = hintFor(face))
    }

    private fun hintFor(face: FaceRect): Hint? = when {
        face.top < HEAD_CLIP_MARGIN ->
            Hint("Kopf fast abgeschnitten — Kamera etwas nach oben schwenken.")

        1f - face.bottom < HEAD_CLIP_MARGIN ->
            Hint("Kinn fast abgeschnitten — Kamera etwas nach unten schwenken.")

        face.left < SIDE_CLIP_MARGIN ->
            Hint("Gesicht am linken Rand angeschnitten — Kamera etwas nach links schwenken.")

        1f - face.right < SIDE_CLIP_MARGIN ->
            Hint("Gesicht am rechten Rand angeschnitten — Kamera etwas nach rechts schwenken.")

        else -> null
    }

    private fun marginScore(margin: Float, threshold: Float): Float =
        smoothstep(0f, threshold * COMFORT_FACTOR, margin)
}

private val FaceRect.area: Float get() = (right - left) * (bottom - top)
