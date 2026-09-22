package com.florianhaeglsperger.photocoach.domain.rules

import com.florianhaeglsperger.photocoach.domain.geometry.NormalizedPoint
import com.florianhaeglsperger.photocoach.domain.geometry.distanceInWidths

/**
 * Haelt den einmal gewaehlten Drittel-Punkt ueber Frames hinweg fest (Plan 3.2.1, Schritt 5).
 *
 * **Warum es das braucht:** Ein Motiv nahe der Bildmitte ist von zwei Drittel-Punkten fast
 * gleich weit entfernt. Waehlt man pro Frame stur den naechstgelegenen, kippt die Wahl bei
 * minimalem Zittern der Motiverkennung hin und her — und der Hinweis springt im Sekundentakt
 * zwischen "nach links" und "nach rechts". Die Regel waere rechnerisch korrekt und fuehlte
 * sich trotzdem kaputt an.
 *
 * **Warum getrennt von [RuleOfThirdsRule]:** Die Regel ist eine pure Funktion und soll es
 * bleiben — ohne Zustand ist sie in Millisekunden testbar. Der Zustand gehoert deshalb in
 * die aufrufende Schicht, die ohnehin ueber Frames hinweg lebt.
 */
class ThirdsTargetTracker(private val switchMargin: Float = DEFAULT_SWITCH_MARGIN) {

    private var current: NormalizedPoint? = null

    /**
     * Liefert den Zielpunkt fuer dieses Frame.
     *
     * Der bisherige bleibt gueltig, solange kein anderer deutlich naeher liegt — "deutlich"
     * heisst [switchMargin] relativ zum bisherigen Abstand.
     */
    fun select(x: Float, y: Float, aspectRatio: Float): NormalizedPoint {
        val nearest = RuleOfThirdsRule.nearestThirdsPoint(x, y, aspectRatio)
        val held = current

        if (held == null) {
            current = nearest
            return nearest
        }
        if (nearest == held) return held

        val heldDistance = distanceInWidths(x, y, held.x, held.y, aspectRatio)
        val nearestDistance = distanceInWidths(x, y, nearest.x, nearest.y, aspectRatio)

        // Nur wechseln, wenn der neue Punkt den bisherigen klar schlaegt. Bei knapp
        // gleichem Abstand gewinnt bewusst der bisherige — Stabilitaet vor Optimum.
        if (nearestDistance < heldDistance * (1f - switchMargin)) {
            current = nearest
            return nearest
        }
        return held
    }

    /**
     * Vergisst den gehaltenen Zielpunkt.
     *
     * Aufzurufen, wenn der Bildinhalt keinen Bezug mehr zum vorherigen hat — etwa wenn
     * laengere Zeit kein Motiv erkannt wurde oder die Kamera gewechselt hat. Sonst wuerde
     * ein alter Zielpunkt eine voellig neue Szene festhalten.
     */
    fun reset() {
        current = null
    }

    private companion object {
        /** Der neue Punkt muss mindestens 15 % naeher liegen als der bisherige. */
        const val DEFAULT_SWITCH_MARGIN = 0.15f
    }
}
