package com.florianhaeglsperger.photocoach.domain.geometry

import kotlin.math.sqrt

/** Ein Punkt im normierten Bildraum (0..1, Ursprung oben links). */
data class NormalizedPoint(val x: Float, val y: Float)

/** Die linke bzw. obere Drittel-Linie. */
const val THIRD_LOW = 1f / 3f

/** Die rechte bzw. untere Drittel-Linie. */
const val THIRD_HIGH = 2f / 3f

/** Die vier Schnittpunkte der Drittel-Linien (Plan 3.2.1, Schritt 3). */
val THIRDS_POINTS: List<NormalizedPoint> = listOf(
    NormalizedPoint(THIRD_LOW, THIRD_LOW),
    NormalizedPoint(THIRD_HIGH, THIRD_LOW),
    NormalizedPoint(THIRD_LOW, THIRD_HIGH),
    NormalizedPoint(THIRD_HIGH, THIRD_HIGH),
)

/**
 * Abstand zweier normierter Punkte, gerechnet in Einheiten der **Bildbreite**.
 *
 * In 0..1-Koordinaten ist ein Versatz von 0.1 in x eine andere physische Strecke als 0.1
 * in y — im Hochformat ist die Hoehe die laengere Kante. Ohne diese Korrektur wuerde die
 * Drittel-Regel dort vertikale Abweichungen systematisch zu milde bewerten.
 *
 * `1 / aspectRatio` ist Hoehe/Breite: es rechnet den y-Anteil in dieselbe Einheit um, in
 * der der x-Anteil schon steht.
 */
fun distanceInWidths(
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    aspectRatio: Float,
): Float {
    val dx = bx - ax
    val dy = (by - ay) / aspectRatio
    return sqrt(dx * dx + dy * dy)
}

/** Innerhalb dieses Abstands (in Bildbreiten) gilt die Platzierung als perfekt. */
const val THIRDS_PERFECT_RADIUS = 0.04f

/**
 * Ab diesem Abstand ist der Score 0.
 *
 * 1/6 ist genau der Abstand von der Bildmitte zu einer Drittel-Linie — ein Motiv in der
 * Bildmitte liegt also gerade am unteren Ende der Skala, nicht darunter.
 */
const val THIRDS_TOLERANCE = 1f / 6f

/**
 * Rechnet einen Abstand in einen Score von 1 (genau auf dem Punkt) bis 0 um.
 *
 * `smoothstep` statt linear, weil der Score live angezeigt wird: eine lineare Kennlinie
 * uebersetzt jedes kleine Zittern der Motiverkennung direkt in eine sichtbare
 * Zahlenaenderung. Der flache Verlauf an beiden Enden daempft genau das.
 */
fun thirdsScore(distance: Float): Float {
    if (distance <= THIRDS_PERFECT_RADIUS) return 1f
    if (distance >= THIRDS_TOLERANCE) return 0f

    val t = (distance - THIRDS_PERFECT_RADIUS) / (THIRDS_TOLERANCE - THIRDS_PERFECT_RADIUS)
    return 1f - t * t * (3f - 2f * t)
}
