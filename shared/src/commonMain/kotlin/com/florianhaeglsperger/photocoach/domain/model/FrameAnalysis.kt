package com.florianhaeglsperger.photocoach.domain.model

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
