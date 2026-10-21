package com.florianhaeglsperger.photocoach.domain.subject

import com.florianhaeglsperger.photocoach.domain.model.FaceRect
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import com.florianhaeglsperger.photocoach.domain.model.SaliencyPoint
import kotlin.math.max
import kotlin.math.sqrt

/** Leitet aus einer [FrameAnalysis] den Motivpunkt ab, oder `null`, wenn keiner erkennbar ist. */
fun interface SubjectResolver {
    fun resolve(analysis: FrameAnalysis): SubjectPoint?
}

/**
 * Vorgabe-Implementierung (Plan 3.2.1, Schritt 2).
 *
 * Reihenfolge:
 *  1. Vom Nutzer angetipptes Motiv ([FrameAnalysis.pinnedSubject]) → dessen Schwerpunkt,
 *     `confidence = 1`. Vor dem Gesicht: tippt jemand bei einem Gruppenfoto die zweite
 *     Person an oder bei einem Portraet den Gegenstand im Vordergrund, ist *das* sein Motiv.
 *  2. Gesicht ab [MIN_FACE_WIDTH] Breite → dessen Mittelpunkt, `confidence = 1`
 *  3. sonst der staerkste Saliency-Klumpen, falls er ausgepraegt genug ist
 *  4. sonst `null`
 *
 * **Warum Saliency nicht einfach "Liste nicht leer" heisst:** Die Gewichte kommen normiert
 * auf das Maximum des jeweiligen Frames herein (siehe `readHeatmap` in iosMain). Die
 * staerkste Zelle hat damit in *jedem* Bild den Wert 1.0 — auch bei einer weissen Wand oder
 * einem gleichmaessigen Himmel. Ein Schwellwert auf dem Gewicht traegt nach dieser Normierung
 * also keine Information mehr; aussagekraeftig ist nur noch, wie die Punkte im Bild *liegen*.
 *
 * Deshalb das Kriterium [MIN_CLUSTER_SHARE]: der staerkste Klumpen muss einen nennenswerten
 * Anteil des gesamten Gewichts halten. Bei gleichmaessiger Flaeche liegen alle Punkte
 * gleichauf ueber das Bild verteilt, der Klumpen um das Maximum haelt nur einen Bruchteil —
 * kein Motiv. Bei zwei etwa gleich starken Motiven sind es rund 50 %, bei dreien nur noch
 * rund 33 %. Der Schwellwert entscheidet damit, *ab wie vielen konkurrierenden Bereichen es
 * kein "das Motiv" mehr gibt*; bei 0.4 lautet die Antwort "ab drei".
 */
object DefaultSubjectResolver : SubjectResolver {

    /**
     * Mindestbreite eines Gesichts (Anteil der Bildbreite), damit es als Motiv zaehlt.
     *
     * Entspricht dem, was ML Kit auf Android ohnehin schon filtert
     * (`setMinFaceSize(0.1f)`). Auf iOS filtert `VNDetectFaceRectanglesRequest` gar nicht —
     * ohne diese Huerde wuerde dort ein winziges Gesicht im Hintergrund als Hauptmotiv
     * gelten und beide Plattformen liefen auseinander.
     */
    const val MIN_FACE_WIDTH = 0.1f

    /** Umkreis (in Bildbreiten) um den staerksten Punkt, der noch zum Klumpen zaehlt. */
    const val CLUSTER_RADIUS = 0.15f

    /** Gewichtsanteil, den der staerkste Klumpen mindestens halten muss. */
    const val MIN_CLUSTER_SHARE = 0.4f

    /** Kleinste halbe Kantenlaenge (normiert) des Debug-Rechtecks um einen Saliency-Klumpen. */
    private const val MIN_BOUNDS_HALF_SIZE = 0.03f

    override fun resolve(analysis: FrameAnalysis): SubjectPoint? =
        resolveFromPin(analysis) ?: resolveFromFace(analysis) ?: resolveFromSaliency(analysis)

    private fun resolveFromPin(analysis: FrameAnalysis): SubjectPoint? {
        val pin = analysis.pinnedSubject ?: return null
        return SubjectPoint(
            x = pin.centerX,
            y = pin.centerY,
            confidence = 1f,
            source = SubjectSource.PINNED,
            bounds = SubjectBounds(pin.left, pin.top, pin.right, pin.bottom),
        )
    }

    private fun resolveFromFace(analysis: FrameAnalysis): SubjectPoint? {
        val face = analysis.faces
            .filter { it.width >= MIN_FACE_WIDTH }
            .maxByOrNull { it.area }
            ?: return null

        return SubjectPoint(
            x = (face.left + face.right) / 2f,
            y = (face.top + face.bottom) / 2f,
            confidence = 1f,
            source = SubjectSource.FACE,
            bounds = SubjectBounds(face.left, face.top, face.right, face.bottom),
        )
    }

    private fun resolveFromSaliency(analysis: FrameAnalysis): SubjectPoint? {
        val points = analysis.saliencyRegions
        val strongest = points.maxByOrNull { it.weight } ?: return null

        val totalWeight = points.sumOf { it.weight.toDouble() }.toFloat()
        if (totalWeight <= 0f) return null

        // Bewusst nur die Punkte um das Maximum herum, nicht alle: bei zwei Motiven links
        // und rechts laege der Schwerpunkt ueber alle Punkte genau in der Bildmitte — die
        // Regel wuerde ein Motiv melden, das es dort gar nicht gibt.
        val cluster = points.filter { it.distanceTo(strongest, analysis.aspectRatio) <= CLUSTER_RADIUS }
        val clusterWeight = cluster.sumOf { it.weight.toDouble() }.toFloat()

        val share = clusterWeight / totalWeight
        if (share < MIN_CLUSTER_SHARE) return null

        return SubjectPoint(
            x = cluster.weightedMean { it.x } / clusterWeight,
            y = cluster.weightedMean { it.y } / clusterWeight,
            // Der Anteil ist bereits eine 0..1-Groesse und beschreibt genau das, was hier
            // Verlaesslichkeit ausmacht: wie klar sich der Klumpen vom Rest abhebt.
            confidence = share,
            source = SubjectSource.SALIENCY,
            bounds = cluster.bounds(),
        )
    }

    /**
     * Huelle der Punkte, mindestens [MIN_BOUNDS_HALF_SIZE] gross: ein Klumpen aus einem
     * einzigen Punkt waere sonst ein unsichtbares Rechteck.
     */
    private fun List<SaliencyPoint>.bounds(): SubjectBounds {
        val cx = (minOf { it.x } + maxOf { it.x }) / 2f
        val cy = (minOf { it.y } + maxOf { it.y }) / 2f
        val halfW = max((maxOf { it.x } - minOf { it.x }) / 2f, MIN_BOUNDS_HALF_SIZE)
        val halfH = max((maxOf { it.y } - minOf { it.y }) / 2f, MIN_BOUNDS_HALF_SIZE)
        return SubjectBounds(
            (cx - halfW).coerceAtLeast(0f),
            (cy - halfH).coerceAtLeast(0f),
            (cx + halfW).coerceAtMost(1f),
            (cy + halfH).coerceAtMost(1f),
        )
    }

    private fun List<SaliencyPoint>.weightedMean(select: (SaliencyPoint) -> Float): Float =
        sumOf { (select(it) * it.weight).toDouble() }.toFloat()

    private fun SaliencyPoint.distanceTo(other: SaliencyPoint, aspectRatio: Float): Float {
        val dx = x - other.x
        // Wie in `distanceInWidths`: in normierten Koordinaten ist ein y-Versatz eine andere
        // physische Strecke als derselbe x-Versatz, sonst waere der Umkreis ein Oval.
        val dy = (y - other.y) / aspectRatio
        return sqrt(dx * dx + dy * dy)
    }
}

private val FaceRect.width: Float get() = right - left
private val FaceRect.area: Float get() = (right - left) * (bottom - top)
