package com.florianhaeglsperger.photocoach.domain.scoring

import com.florianhaeglsperger.photocoach.domain.rules.Hint

/**
 * Haelt einen Hinweis lange genug stehen, dass man ihn lesen kann.
 *
 * Die Analyse laeuft mit ~10 Hz. Ohne Daempfung wuerde der Text im Sucher bis zu zehnmal
 * pro Sekunde wechseln, sobald zwei Regeln abwechselnd greifen oder ein Wert um eine
 * Schwelle pendelt — unlesbar und nervig, also genau das Gegenteil des Ziels aus Plan 3.4
 * ("nicht-invasives Feedback").
 *
 * Regel: Ein neuer Hinweis (auch "kein Hinweis") uebernimmt erst, wenn der aktuelle
 * mindestens [minDisplayMs] gestanden hat. Der allererste Hinweis erscheint sofort —
 * beim Start soll nichts kuenstlich verzoegert werden.
 *
 * Bewusst hier in `domain/` und nicht in der UI: es ist eine Entscheidung darueber, *was*
 * gezeigt wird, keine Frage der Darstellung — und so ohne Compose testbar.
 */
class HintStabilizer(private val minDisplayMs: Long = DEFAULT_MIN_DISPLAY_MS) {

    private var current: Hint? = null
    private var shownSinceMs: Long = 0L

    /**
     * @param candidate was die Regeln fuer dieses Frame vorschlagen (`null` = alles in Ordnung)
     * @param nowMs Zeitstempel des Frames
     * @return der Hinweis, der jetzt angezeigt werden soll
     */
    fun update(candidate: Hint?, nowMs: Long): Hint? {
        if (candidate == current) return current

        // Steht gerade ein Hinweis und ist seine Mindestzeit noch nicht um, bleibt er.
        if (current != null && nowMs - shownSinceMs < minDisplayMs) return current

        current = candidate
        shownSinceMs = nowMs
        return current
    }

    companion object {
        /**
         * Rund zwei Sekunden Lesezeit. Kuerzer wirkt hektisch, deutlich laenger laesst das
         * Feedback der Kamerabewegung spuerbar hinterherhinken.
         */
        const val DEFAULT_MIN_DISPLAY_MS = 1800L
    }
}
