package com.florianhaeglsperger.photocoach.capture

import kotlin.math.max
import kotlin.math.min

/** Was mit einer neuen Erkennung beim Verfolgen passiert, siehe [TrackingGate]. */
sealed interface TrackDecision {
    /** Plausibel — wird uebernommen, und an ihrem Schwerpunkt wird weitergesucht. */
    data class Accept(val obj: SegmentedObject) : TrackDecision

    /**
     * Unplausibel oder nichts gefunden — verworfen. Das zuletzt gute Objekt ([lastGood],
     * vor der ersten Erkennung `null`) bleibt stehen, gesucht wird weiter an *seiner* Stelle.
     */
    data class Hold(val lastGood: SegmentedObject?, val reason: TrackRejection) : TrackDecision

    /** Zu oft hintereinander verworfen — das Objekt gilt als verloren. */
    data class Lose(val reason: TrackRejection) : TrackDecision
}

/** Warum eine Erkennung verworfen wurde — fuer Tests und Protokoll. */
enum class TrackRejection {
    /** Das Modell fand unter dem Punkt gar nichts. */
    NOTHING_FOUND,

    /** Fuellt (fast) das ganze Bild oder stoesst an drei Bildraender — sieht nach Hintergrund aus. */
    BACKGROUND,

    /** Schlagartig viel groesser als zuletzt. */
    GREW,

    /** Schlagartig viel kleiner als zuletzt. */
    SHRANK,

    /** Liegt woanders als zuletzt — kaum Ueberschneidung, Schwerpunkt weit weg. */
    JUMPED,
}

/**
 * Plausibilitaetspruefung beim Verfolgen eines angetippten Objekts.
 *
 * **Das Problem, das sie loest:** Verfolgt wird, indem das Modell bei jedem neuen Bild am
 * Schwerpunkt des zuletzt erkannten Objekts sucht. Bewegt sich das Objekt weg (oder die
 * Kamera), liegt dieser Punkt ploetzlich auf dem Hintergrund — und das Modell markiert dann
 * brav den *Hintergrund*: Wand, Himmel, oft fast das ganze Bild. Ohne Pruefung wurde das
 * uebernommen, und am naechsten Schwerpunkt — wieder Hintergrund — weitergesucht. Das
 * eigentliche Objekt war damit endgueltig weg, der Rahmen zeigte den ganzen Bildschirm.
 *
 * **Was sie tut:** Jede Erkennung wird mit der letzten *guten* verglichen. Wer schlagartig
 * waechst, schrumpft, springt oder nach Hintergrund aussieht, wird verworfen — der alte Rahmen
 * bleibt stehen, und gesucht wird weiter an der alten Stelle (oft taucht das Objekt im
 * naechsten Bild dort wieder auf, z. B. nach einem Wackler). Erst nach [MAX_MISSES]
 * Fehlversuchen in Folge gilt es als verloren — dann lieber "verloren" als ein falscher Rahmen.
 *
 * Die Schwellen sind grosszuegig, weil zwischen zwei Erkennungen am Geraet ~0,2-0,5 s liegen
 * und sich Groesse und Lage dabei echt aendern (Zoomen, Naehertreten, Schwenken). Pure Logik
 * ohne Plattform — unit-getestet in `TrackingGateTest`. Haelt Zustand; nicht thread-sicher.
 */
class TrackingGate {

    private var lastGood: SegmentedObject? = null
    private var misses = 0

    /** Neue Auswahl oder aufgehoben: alles vergessen. */
    fun reset() {
        lastGood = null
        misses = 0
    }

    /** Beurteilt die naechste Erkennung ([candidate] `null` = nichts gefunden). */
    fun judge(candidate: SegmentedObject?): TrackDecision {
        val rejection = rejectionOf(candidate, lastGood)
        if (rejection == null) {
            lastGood = candidate
            misses = 0
            return TrackDecision.Accept(candidate!!)
        }
        misses++
        return if (misses >= MAX_MISSES) TrackDecision.Lose(rejection) else TrackDecision.Hold(lastGood, rejection)
    }

    companion object {
        /** So viele verworfene Erkennungen in Folge, dann gilt das Objekt als verloren. */
        const val MAX_MISSES = 3

        /** Ab diesem Flaechenanteil ist es fast sicher Hintergrund, kein angetipptes Objekt. */
        const val BACKGROUND_AREA = 0.85f

        /**
         * Stoesst die Flaeche an drei oder vier Bildraender und ist dabei mindestens so gross,
         * ist es Hintergrund — ein Objekt, das man antippt, steht selten bis an drei Raender.
         */
        const val EDGE_BACKGROUND_AREA = 0.4f

        /** Groesser als das 2,5-fache bzw. kleiner als ein Viertel der letzten Flaeche: verworfen. */
        const val MAX_GROWTH = 2.5f
        const val MAX_SHRINK = 4f

        /**
         * Gesprungen, wenn sich die Rahmen kaum ueberschneiden (IoU unter diesem Wert) *und*
         * der neue Schwerpunkt ausserhalb des um [JUMP_MARGIN] vergroesserten alten Rahmens liegt.
         */
        const val MIN_OVERLAP = 0.1f
        const val JUMP_MARGIN = 0.5f

        /** Toleranz, ab der ein Rahmen als "stoesst an den Rand" gilt (normiert). */
        private const val EDGE = 0.01f

        internal fun rejectionOf(candidate: SegmentedObject?, previous: SegmentedObject?): TrackRejection? {
            if (candidate == null) return TrackRejection.NOTHING_FOUND
            if (candidate.areaFraction >= BACKGROUND_AREA) return TrackRejection.BACKGROUND
            if (edgesTouched(candidate) >= 3 && candidate.areaFraction >= EDGE_BACKGROUND_AREA) {
                // Stand schon das gute Objekt an drei Raendern (sehr nah, bildfuellend), ist das
                // kein Zeichen fuer Hintergrund — dann entscheiden die uebrigen Pruefungen.
                if (previous == null || edgesTouched(previous) < 3) return TrackRejection.BACKGROUND
            }
            previous ?: return null

            if (candidate.areaFraction > previous.areaFraction * MAX_GROWTH) return TrackRejection.GREW
            if (candidate.areaFraction < previous.areaFraction / MAX_SHRINK) return TrackRejection.SHRANK
            if (overlap(candidate, previous) < MIN_OVERLAP && !centerNear(candidate, previous)) {
                return TrackRejection.JUMPED
            }
            return null
        }

        private fun edgesTouched(o: SegmentedObject): Int =
            listOf(o.left <= EDGE, o.top <= EDGE, o.right >= 1f - EDGE, o.bottom >= 1f - EDGE).count { it }

        /** Intersection over Union der beiden Rahmen. */
        internal fun overlap(a: SegmentedObject, b: SegmentedObject): Float {
            val w = min(a.right, b.right) - max(a.left, b.left)
            val h = min(a.bottom, b.bottom) - max(a.top, b.top)
            if (w <= 0f || h <= 0f) return 0f
            val inter = w * h
            val union = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - inter
            return if (union <= 0f) 0f else inter / union
        }

        private fun centerNear(candidate: SegmentedObject, previous: SegmentedObject): Boolean {
            val mx = (previous.right - previous.left) * JUMP_MARGIN
            val my = (previous.bottom - previous.top) * JUMP_MARGIN
            return candidate.centerX in (previous.left - mx)..(previous.right + mx) &&
                candidate.centerY in (previous.top - my)..(previous.bottom + my)
        }
    }
}
