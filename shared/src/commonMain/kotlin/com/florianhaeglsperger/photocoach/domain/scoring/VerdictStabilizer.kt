package com.florianhaeglsperger.photocoach.domain.scoring

/**
 * Haelt eine Aussage im Sucher lange genug stehen, dass man sie lesen kann.
 *
 * Die Analyse laeuft mit ~10 Hz. Ohne Daempfung wuerde der Text im Sucher bis zu zehnmal
 * pro Sekunde wechseln, sobald zwei Regeln abwechselnd greifen oder ein Wert um eine
 * Schwelle pendelt — unlesbar und nervig, also genau das Gegenteil des Ziels aus Plan 3.4
 * ("nicht-invasives Feedback").
 *
 * Regel: Eine neue Aussage uebernimmt erst, wenn die aktuelle mindestens [minDisplayMs]
 * gestanden hat — **das gilt fuer jeden Zustand, auch fuer "passt"**. Nur die allererste
 * Aussage erscheint sofort, beim Start soll nichts kuenstlich verzoegert werden.
 *
 * Der Vorgaenger (`HintStabilizer`) liess einen neuen Hinweis sofort durch, wenn gerade
 * *kein* Hinweis stand. Pendelte ein Wert um die Schwelle, blitzte "Komposition passt"
 * fuer ein einzelnes Frame auf und wurde vom naechsten Hinweis sofort wieder verdraengt.
 *
 * Bewusst hier in `domain/` und nicht in der UI: es ist eine Entscheidung darueber, *was*
 * gezeigt wird, keine Frage der Darstellung — und so ohne Compose testbar.
 */
class VerdictStabilizer(private val minDisplayMs: Long = DEFAULT_MIN_DISPLAY_MS) {

    private var current: Verdict? = null
    private var shownSinceMs: Long = 0L

    /**
     * @param candidate was der [CompositionScorer] fuer dieses Frame sagt
     * @param nowMs Zeitstempel des Frames
     * @return die Aussage, die jetzt angezeigt werden soll
     */
    fun update(candidate: Verdict, nowMs: Long): Verdict {
        val shown = current
        if (shown == null || (candidate != shown && nowMs - shownSinceMs >= minDisplayMs)) {
            current = candidate
            shownSinceMs = nowMs
            return candidate
        }
        return shown
    }

    /** Vergisst die aktuelle Aussage, die naechste erscheint wieder sofort (Szenenwechsel). */
    fun reset() {
        current = null
    }

    companion object {
        /**
         * Rund zwei Sekunden Lesezeit. Kuerzer wirkt hektisch, deutlich laenger laesst das
         * Feedback der Kamerabewegung spuerbar hinterherhinken.
         */
        const val DEFAULT_MIN_DISPLAY_MS = 1800L
    }
}
