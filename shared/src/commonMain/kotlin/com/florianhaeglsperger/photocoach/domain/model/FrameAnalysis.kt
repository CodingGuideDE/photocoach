package com.florianhaeglsperger.photocoach.domain.model

import kotlin.math.PI
import kotlin.math.atan

/**
 * Ergebnis einer einzelnen Frame-Analyse. Wird von [com.florianhaeglsperger.photocoach.capture.FrameAnalyzer]
 * produziert und ist der einzige Kontaktpunkt zwischen den plattformspezifischen
 * Vision-Implementierungen (iOS: Vision framework, Android: CameraX + ML Kit) und der
 * gemeinsamen domain-/UI-Logik. Siehe CLAUDE.md "Core cross-platform contract".
 */
data class FrameAnalysis(
    /**
     * Seitliche Neigung des Horizonts in Grad, oder `null`, wenn sie sich gerade nicht
     * bestimmen laesst (Geraet zu flach, kein Horizont im Bild) — das ist ein bewusster
     * Unterschied zu `0f` ("gerade") und darf von Regeln nicht gleich behandelt werden.
     *
     * Vorzeichen-Konvention, verbindlich fuer jede `FrameAnalyzer`-Implementierung:
     * **positiv = rechte Seite tiefer, negativ = linke Seite tiefer, 0 = gerade** — exakt
     * die Konvention aus `HorizonSensor.kt` (Android, per Unit-Test abgesichert). Auf iOS
     * ist das aus der Rotationsrichtung von `VNHorizonObservation.angle` hergeleitet, aber
     * noch nicht an einem echten Geraet verifiziert (siehe CLAUDE.md).
     */
    val horizonTiltDegrees: Float?,
    val saliencyRegions: List<SaliencyPoint>,
    val faces: List<FaceRect>,
    val timestampMs: Long,
    /**
     * Breite geteilt durch Hoehe des *aufrecht gedrehten* Analyse-Frames (16/9 ≈ 1.78 quer,
     * 9/16 ≈ 0.56 hoch).
     *
     * Noetig, weil alle Koordinaten in dieser Klasse auf 0..1 normiert sind: dort ist ein
     * Versatz von 0.1 in x eine andere physische Strecke als 0.1 in y. Wer Abstaende
     * rechnet, muss das herausrechnen — sonst bewertet z.B. die Drittel-Regel im Hochformat
     * vertikale Abweichungen systematisch zu milde (siehe `distanceInWidths`).
     */
    val aspectRatio: Float,
    /**
     * Wie viel Bildstruktur (lokaler Kontrast) in jedem Bereich steckt — die Antwort auf
     * "ist hier ueberhaupt etwas?", die `DeadSpaceRule` braucht. `null`, wenn die Plattform
     * sie nicht liefert; dann hat die Regel keine Meinung.
     *
     * Bewusst getrennt von [saliencyRegions]: Saliency beantwortet "was sticht am staerksten
     * hervor?" und ist aufs Maximum normiert. Ein harter Kontrast im Vordergrund drueckt dort
     * jede schwaechere Struktur unter die Schwelle — eine strukturierte Mauer galt dann als
     * leer. Struktur wird absolut gemessen und kennt dieses Problem nicht.
     */
    val structure: StructureGrid? = null,
    /**
     * Das Motiv, das der Nutzer per Tipp festgelegt hat — oder `null`, wenn er keins
     * gewaehlt hat. Hat Vorrang vor jeder automatischen Motivwahl (Gesicht, Saliency):
     * wer selbst sagt, was das Motiv ist, soll nicht von der App ueberstimmt werden.
     *
     * Kommt nicht von der Bildanalyse, sondern aus der Tipp-Segmentierung; die UI setzt es,
     * bevor sie bewertet. Koordinaten wie ueberall hier: normiert, so wie die Vorschau sie
     * zeigt (Frontkamera gespiegelt).
     */
    val pinnedSubject: PinnedSubject? = null,
    /**
     * Wie weit die Kamera nach oben (positiv) oder unten (negativ) zeigt, in Grad; 0 =
     * waagerecht. `null`, wenn unbekannt — bei der Frontkamera bewusst immer, weil dort das
     * Vorzeichen umgekehrt waere und "Linien kippen" beim Selfie ohnehin kein Thema ist.
     */
    val cameraPitchDegrees: Float? = null,
    /** Helligkeitsverteilung des Bildes, siehe [ExposureStats]. `null`, wenn nicht gemessen. */
    val exposure: ExposureStats? = null,
    /**
     * Die im **Bild** erkannte Horizontlinie (Himmel/Boden-Kante), oder `null`, wenn keine
     * deutliche durchgehende Linie zu sehen ist. Ergaenzt [horizonTiltDegrees], das aus dem
     * Lagesensor kommt: der Sensor weiss, wie schief das *Geraet* ist, aber nicht, *wo* im
     * Bild der Horizont liegt — genau das braucht der Tipp "Horizont in der Bildmitte".
     */
    val horizonLine: HorizonLine? = null,
)

/**
 * Eine im Bild erkannte, (fast) waagerechte Linie ueber die ganze Breite: ihre Hoehe am
 * linken und am rechten Bildrand, normiert (0 = oben, 1 = unten), so wie die Vorschau sie
 * zeigt.
 */
data class HorizonLine(
    val leftY: Float,
    val rightY: Float,
    /** Mittlerer Helligkeitssprung an der Linie (0..1) — wie deutlich sie ist. */
    val strength: Float,
) {
    /** Hoehe der Linie in der Bildmitte. */
    val centerY: Float get() = (leftY + rightY) / 2f

    /**
     * Winkel im echten Bild in Grad, gleiche Vorzeichen-Konvention wie
     * [FrameAnalysis.horizonTiltDegrees]: positiv = rechte Seite tiefer.
     *
     * Die Hoehen sind auf die Bildhoehe normiert, die Breite auf 1 — der echte Anstieg ist
     * deshalb (Hoehenunterschied * Hoehe) / Breite = Hoehenunterschied / [aspectRatio].
     */
    fun angleDegrees(aspectRatio: Float): Float =
        (atan((rightY - leftY) / aspectRatio) * 180.0 / PI).toFloat()

    /** Siehe [FaceRect.mirroredHorizontally] — links und rechts tauschen. */
    fun mirroredHorizontally(): HorizonLine = copy(leftY = rightY, rightY = leftY)
}

/**
 * Wie viel vom Bild fast weiss bzw. fast schwarz ist — die Grundlage fuer den
 * Belichtungshinweis. Gemessen auf demselben 64x64-Helligkeitsbild wie Saliency und Struktur,
 * jeder Wert ein Anteil 0..1.
 */
data class ExposureStats(
    /** Anteil der Bildpunkte, die praktisch weiss sind (ausgefressen, keine Zeichnung mehr). */
    val overexposedFraction: Float,
    /** Anteil der Bildpunkte, die praktisch schwarz sind (abgesoffen). */
    val underexposedFraction: Float,
    /** Mittlere Helligkeit 0..1. */
    val meanBrightness: Float,
)

/**
 * Ein vom Nutzer angetipptes und vom Segmentierungsmodell umrissenes Objekt.
 *
 * [centerX]/[centerY] ist der Schwerpunkt der Objektflaeche, nicht die Rahmenmitte — er liegt
 * sicher *im* Objekt (bei einem "L"-foermigen Objekt laege die Rahmenmitte daneben).
 */
data class PinnedSubject(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val centerX: Float,
    val centerY: Float,
)

/**
 * Lokaler Kontrast je Rasterzelle, zeilenweise (Index = Zeile * [columns] + Spalte), Zeile 0
 * oben. Werte sind helligkeitsunabhaengig (mittlerer Helligkeitsgradient geteilt durch die
 * Helligkeit der Zelle), damit eine dunkle Szene nicht allein deshalb "leer" wirkt.
 */
data class StructureGrid(val columns: Int, val rows: Int, val values: List<Float>) {
    init {
        require(values.size == columns * rows) { "values.size passt nicht zu $columns x $rows" }
    }

    operator fun get(column: Int, row: Int): Float = values[row * columns + column]

    /** Siehe [FaceRect.mirroredHorizontally] — fuer die gespiegelte Frontkamera-Vorschau. */
    fun mirroredHorizontally(): StructureGrid = StructureGrid(
        columns,
        rows,
        List(values.size) { i -> values[(i / columns) * columns + (columns - 1 - i % columns)] },
    )
}

/** Normalisierter Punkt (0.0–1.0) im Kamerabild mit Aufmerksamkeits-Gewicht. */
data class SaliencyPoint(
    val x: Float,
    val y: Float,
    val weight: Float,
)

/** Normalisiertes Rechteck (0.0–1.0) fuer ein erkanntes Gesicht. */
data class FaceRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Spiegelt ein Rechteck an der senkrechten Bildmitte.
 *
 * **Wofuer:** Die Vorschau der Frontkamera ist gespiegelt — so kennt man es vom Spiegel,
 * und jede Kamera-App macht das so. Die Analyse-Frames sind es aber **nicht**: sie kommen
 * roh vom Sensor. Ein Gesicht, das der Nutzer links im Bild sieht, liegt in den
 * Analyse-Koordinaten also rechts.
 *
 * Ohne diese Korrektur wuerde die App beim Selfie exakt in die falsche Richtung schicken —
 * derselbe Vorzeichen-Fehlertyp wie bei der Geraeteneigung und der Bildschirmdrehung.
 *
 * **Nicht betroffen ist die Horizont-Neigung:** die misst die physische Lage des Geraets.
 * Welche Kamera aktiv ist und ob die Vorschau gespiegelt wird, aendert nicht, welche Seite
 * des Geraets tiefer liegt.
 */
fun FaceRect.mirroredHorizontally(): FaceRect = FaceRect(
    // left und right tauschen die Rollen: der linke Rand wird zum gespiegelten rechten.
    left = 1f - right,
    top = top,
    right = 1f - left,
    bottom = bottom,
)

/** Spiegelt einen Saliency-Punkt an der senkrechten Bildmitte, siehe [mirroredHorizontally]. */
fun SaliencyPoint.mirroredHorizontally(): SaliencyPoint = copy(x = 1f - x)
