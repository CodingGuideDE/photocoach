package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.model.FaceRect
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis

/**
 * Warnt bei schlecht gerahmten Portraets (Plan 3.2): Kopf nicht zu weit oben/unten
 * abgeschnitten, ausreichend Platz in der (grob geschaetzten) Blickrichtung.
 *
 * Gilt nur "bei erkanntem Gesicht" (Plan-Wortlaut) — ohne Gesicht macht die Regel keine
 * Aussage. Sind mehrere Gesichter im Bild, zaehlt das groesste (per Flaeche): das ist mit
 * hoher Wahrscheinlichkeit das eigentliche Motiv, kleinere Gesichter im Hintergrund sollen
 * die Rahmung nicht verfaelschen.
 *
 * Prueft in dieser Reihenfolge und meldet hoechstens *einen* Hinweis (Rule-Contract, siehe
 * [Rule]) — Kopf-Abschnitt ist der eindeutigere/schwerwiegendere Fehler und geht vor:
 * 1. Kopf oben abgeschnitten
 * 2. Kinn unten abgeschnitten
 * 3. Zu wenig Platz in der geschaetzten Blickrichtung
 *
 * **Blickrichtungs-Heuristik:** ohne echte Blickerkennung wird die Blickrichtung grob aus
 * der Position des Gesichts relativ zur Bildmitte geschaetzt (Plan-Wortlaut) — Motiv links
 * der Mitte → Annahme "blickt nach rechts" (in den groesseren, offenen Teil des Bilds),
 * und umgekehrt. Das ist die uebliche Portraet-Komposition (Motiv am Drittel-Punkt, Blick
 * ins Bild hinein). Liegt das Gesicht zu nah an der Mitte, ist keine Richtung verlaesslich
 * schaetzbar — dann macht die Regel dazu keine Aussage.
 *
 * **Kamerafuehrung im Hinweistext:** die Korrektur ist bewusst als Kamera-Aktion formuliert
 * (schwenken), nicht als Motiv-Aktion — der Nutzer bewegt beim Fotografieren die Kamera,
 * nicht das Motiv. Schwenkt man die Kamera in eine Richtung, wandert der Bildinhalt relativ
 * dazu in die *gegenteilige* Richtung im Sucher — Kamera nach oben schwenken schafft also
 * mehr Platz *ueber* dem Kopf.
 */
object PortraitFramingRule : Rule {

    /** Mindestabstand zum Rand, unterhalb dessen der Kopf als abgeschnitten gilt. */
    private const val HEAD_CLIP_MARGIN = 0.04f

    /**
     * Halbe Breite der Zone um die Bildmitte, innerhalb derer keine Blickrichtung
     * geschaetzt wird — ein nahezu zentriertes Gesicht liefert kein verlaessliches Signal,
     * auf welche Seite es "blickt".
     */
    private const val CENTER_DEAD_ZONE = 0.05f

    /** Mindestplatz in der geschaetzten Blickrichtung, sonst "wenig Blickraum". */
    private const val MIN_LOOK_ROOM = 0.15f

    override fun evaluate(analysis: FrameAnalysis): Hint? {
        val face = analysis.faces.maxByOrNull { it.area } ?: return null

        if (face.top < HEAD_CLIP_MARGIN) {
            return Hint("Kopf fast abgeschnitten — Kamera etwas nach oben schwenken.")
        }
        if (1f - face.bottom < HEAD_CLIP_MARGIN) {
            return Hint("Kinn fast abgeschnitten — Kamera etwas nach unten schwenken.")
        }

        val centerX = face.centerX
        return when {
            centerX < CENTER - CENTER_DEAD_ZONE && 1f - face.right < MIN_LOOK_ROOM ->
                Hint("Wenig Platz in Blickrichtung — Kamera etwas nach rechts schwenken.")

            centerX > CENTER + CENTER_DEAD_ZONE && face.left < MIN_LOOK_ROOM ->
                Hint("Wenig Platz in Blickrichtung — Kamera etwas nach links schwenken.")

            else -> null
        }
    }
}

private const val CENTER = 0.5f

private val FaceRect.centerX: Float get() = (left + right) / 2f
private val FaceRect.area: Float get() = (right - left) * (bottom - top)
