package com.florianhaeglsperger.photocoach.capture

import com.florianhaeglsperger.photocoach.capture.saliency.LumaGrid
import com.florianhaeglsperger.photocoach.capture.saliency.SpectralResidualSaliency
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Wie weit sich der Bildinhalt zwischen zwei Bildern verschoben hat, normiert auf Bildbreite
 * ([dx]) bzw. Bildhoehe ([dy]). Positiv = der Inhalt liegt im zweiten Bild weiter rechts bzw.
 * weiter unten.
 *
 * [confidence] ist die Hoehe des Korrelations-Maximums (0..1): 1 = die Bilder sind bis auf
 * die Verschiebung identisch, Werte nahe 0 = kein gemeinsamer Inhalt gefunden.
 * [distinctness] ist das Verhaeltnis zum zweithoechsten Maximum: wie eindeutig die Antwort ist.
 *
 * Auch unsichere Messungen werden geliefert (fuers Protokoll, um die Schwellen am Geraet
 * nachzuziehen) — verwenden darf man nur die mit [isReliable].
 */
data class ImageShift(
    val dx: Float,
    val dy: Float,
    val confidence: Float,
    val distinctness: Float,
) {
    val isReliable: Boolean
        get() = confidence >= ImageRegistration.MIN_CONFIDENCE &&
            distinctness >= ImageRegistration.MIN_DISTINCTNESS &&
            abs(dx) <= ImageRegistration.MAX_SHIFT &&
            abs(dy) <= ImageRegistration.MAX_SHIFT
}

/**
 * Misst die Verschiebung zwischen zwei Helligkeitsbildern per **Phasenkorrelation**.
 *
 * Gebraucht beim Wechsel zwischen Haupt- und Telekamera innerhalb einer logischen Kamera
 * (siehe `LensHandover`): die Objektive sitzen nebeneinander, und das Bild springt beim
 * Wechsel um einige Prozent. Gemessen wird zwischen dem letzten Bild der alten und dem
 * ersten der neuen Kamera; die Vorschau gleicht den Sprung dann weich aus.
 *
 * **Warum Phasenkorrelation:** Sie vergleicht nur die *Phase* der beiden Spektren, nicht die
 * Amplitude — damit ist sie unempfindlich gegen das, was sich beim Kamerawechsel sonst noch
 * aendert: Helligkeit, Kontrast, Farbe (hier ohnehin nur Helligkeit). Und sie kostet auf 64x64
 * drei FFTs, also deutlich unter einer Millisekunde.
 *
 * Der Zoom kann sich zwischen den beiden Bildern leicht unterscheiden (beim Zoomen ist er
 * staendig in Bewegung). Phasenkorrelation sieht nur Verschiebungen — deshalb wird das erste
 * Bild vorher um [estimateShift]s `zoomChange` um die Mitte vergroessert.
 */
object ImageRegistration {

    /**
     * Unter dieser Korrelation gilt die Messung als unsicher. Im Test: echte Verschiebung (mit
     * Helligkeitsunterschied, Rauschen, Zoomunterschied) 0,74-0,88, zwei verschiedene Szenen
     * 0,35. Allein trennt das nicht sicher genug — entscheidend ist [MIN_DISTINCTNESS].
     */
    const val MIN_CONFIDENCE = 0.15f

    /**
     * So viel hoeher als das zweithoechste muss das Maximum mindestens sein. Im Test: echte
     * Verschiebung 15-24, zwei verschiedene Szenen 2,7. 5 laesst Luft fuer echte Bilder
     * (Bewegungsunschaerfe, Fokuswechsel des Teles). Am Geraet nachpruefen — das
     * Feldtest-Protokoll schreibt beide Werte mit.
     */
    const val MIN_DISTINCTNESS = 5f

    /**
     * Groesster Versatz (Anteil der Bildkante), der noch als Messung gilt. Ein Objektivwechsel
     * verschiebt das Bild um wenige Prozent; alles darueber ist eher eine schnelle
     * Kamerabewegung zwischen den beiden Bildern als Parallaxe.
     */
    const val MAX_SHIFT = 0.2f

    /**
     * Mindest-Standardabweichung der Helligkeit. Darunter (Objektivdeckel, weisse Wand) ist
     * das Korrelationsmaximum reines Rauschen.
     */
    private const val MIN_CONTRAST = 0.01

    /**
     * Breite des Tiefpasses im Spektrum, als Anteil der Kantenlaenge. 1/8 laesst bei 64 Pixeln
     * Strukturen ab etwa 3 Pixeln voll durch — feiner ist bei 64x64 ohnehin fast nur Rauschen.
     */
    private const val LOW_PASS_SIGMA = 0.125

    /** Halbe Kantenlaenge (Pixel) des Bereichs um das Maximum, der beim zweithoechsten nicht zaehlt. */
    private const val PEAK_RADIUS = 3

    /**
     * Die Verschiebung von [before] nach [after], oder `null`, wenn eines der Bilder zu
     * kontrastarm ist, um ueberhaupt etwas zu messen. Ob das Ergebnis taugt, sagt
     * [ImageShift.isReliable].
     *
     * @param zoomChange Zoom von [after] geteilt durch Zoom von [before]. Bei 1,03 ist der
     *   Inhalt in [after] 3 % groesser; [before] wird vorher entsprechend vergroessert.
     */
    fun estimateShift(before: LumaGrid, after: LumaGrid, zoomChange: Float = 1f): ImageShift? {
        val n = before.width
        require(before.height == n && after.width == n && after.height == n) {
            "Phasenkorrelation erwartet zwei gleich grosse quadratische Bilder"
        }
        require(n > 1 && n and (n - 1) == 0) { "Kantenlaenge muss eine Zweierpotenz sein, ist $n" }

        val aRe = windowed(magnified(before, zoomChange)) ?: return null
        val bRe = windowed(after.values) ?: return null
        val aIm = DoubleArray(n * n)
        val bIm = DoubleArray(n * n)
        SpectralResidualSaliency.fft2d(aRe, aIm, n, inverse = false)
        SpectralResidualSaliency.fft2d(bRe, bIm, n, inverse = false)

        // Normiertes Kreuzleistungsspektrum B · conj(A) / |B · conj(A)|. Ist b(x) = a(x − d),
        // steht im Ruecktransformierten ein Ausschlag bei d. Jede Frequenz zaehlt nach dem
        // Normieren gleich viel — auch die hohen, in denen bei Kamerabildern fast nur Rauschen
        // steckt. Die Tiefpass-Gewichtung nimmt sie zurueck; sie macht den Ausschlag zugleich
        // breiter, was die Unterpixel-Schaetzung erst genau macht (bei einem einzelnen Pixel
        // breiten Ausschlag liegt die Parabel bis zu einem halben Pixel daneben).
        val re = DoubleArray(n * n)
        val im = DoubleArray(n * n)
        var weightSum = 0.0
        for (i in 0 until n * n) {
            val weight = lowPass(i % n, i / n, n)
            weightSum += weight
            val cRe = bRe[i] * aRe[i] + bIm[i] * aIm[i]
            val cIm = bIm[i] * aRe[i] - bRe[i] * aIm[i]
            val magnitude = sqrt(cRe * cRe + cIm * cIm)
            if (magnitude > 1e-12) {
                re[i] = cRe / magnitude * weight
                im[i] = cIm / magnitude * weight
            }
        }
        SpectralResidualSaliency.fft2d(re, im, n, inverse = true)

        var peak = 0
        for (i in 1 until n * n) if (re[i] > re[peak]) peak = i
        val px = peak % n
        val py = peak / n
        // Bei identischen Bildern ergaebe sich genau weightSum / n² — darauf normiert, damit
        // die Sicherheit wieder 0..1 ist.
        val confidence = (re[peak] * n * n / weightSum).toFloat()
        // Zweithoechstes Maximum ausserhalb des Ausschlags selbst (der ist durch den Tiefpass
        // gut einen Pixel breit) — bei zwei verschiedenen Szenen liegen mehrere fast gleichauf.
        var second = 0.0
        for (i in 0 until n * n) {
            val distX = abs(i % n - px).let { minOf(it, n - it) }
            val distY = abs(i / n - py).let { minOf(it, n - it) }
            if (distX > PEAK_RADIUS || distY > PEAK_RADIUS) second = maxOf(second, re[i])
        }
        val distinctness = if (second > 0.0) (re[peak] / second).toFloat() else Float.MAX_VALUE

        // Unterpixel-Lage per Parabel durch das Maximum und seine Nachbarn. Die Korrelation ist
        // periodisch, die Nachbarn am Rand liegen also auf der anderen Seite.
        fun at(x: Int, y: Int) = re[((y + n) % n) * n + (x + n) % n]
        val subX = parabolaPeak(at(px - 1, py), re[peak], at(px + 1, py))
        val subY = parabolaPeak(at(px, py - 1), re[peak], at(px, py + 1))

        // Indizes ueber der Haelfte sind negative Verschiebungen (periodisch).
        val dx = ((if (px > n / 2) px - n else px) + subX) / n
        val dy = ((if (py > n / 2) py - n else py) + subY) / n
        return ImageShift(dx.toFloat(), dy.toFloat(), confidence, distinctness)
    }

    /** Gauss-Gewicht der Frequenz am FFT-Index ([kx], [ky]); Indizes ueber n/2 sind negativ. */
    private fun lowPass(kx: Int, ky: Int, n: Int): Double {
        val fx = (if (kx > n / 2) kx - n else kx).toDouble()
        val fy = (if (ky > n / 2) ky - n else ky).toDouble()
        val sigma = n * LOW_PASS_SIGMA
        return exp(-(fx * fx + fy * fy) / (2 * sigma * sigma))
    }

    /** Lage des Scheitels einer Parabel durch drei Punkte, relativ zum mittleren (-0,5..0,5). */
    private fun parabolaPeak(left: Double, center: Double, right: Double): Double {
        val curvature = left - 2 * center + right
        if (curvature >= 0.0) return 0.0
        return ((left - right) / (2 * curvature)).coerceIn(-0.5, 0.5)
    }

    /**
     * Mittelwertfrei und mit Hann-Fenster: Die FFT behandelt das Bild als periodisch, der
     * rechte Rand stoesst an den linken. Ohne Fenster waere diese Scheinkante das staerkste
     * gemeinsame Merkmal beider Bilder — und haette die Verschiebung 0.
     */
    private fun windowed(values: FloatArray): DoubleArray? {
        val n = sqrt(values.size.toDouble()).toInt()
        val mean = values.average()
        var variance = 0.0
        for (v in values) variance += (v - mean) * (v - mean)
        if (sqrt(variance / values.size) < MIN_CONTRAST) return null
        val window = DoubleArray(n) { sin(PI * (it + 0.5) / n).let { s -> s * s } }
        return DoubleArray(n * n) { i -> (values[i] - mean) * window[i % n] * window[i / n] }
    }

    /** [image] um [factor] um die Bildmitte vergroessert, bilinear abgetastet. */
    private fun magnified(image: LumaGrid, factor: Float): FloatArray {
        val n = image.width
        if (abs(factor - 1f) < 1e-4f) return image.values
        val center = n / 2f
        return FloatArray(n * n) { i ->
            // Pixelmitten: Zielpixel x liegt bei x + 0,5 und kommt von c + (x + 0,5 − c) / f.
            val sx = center + (i % n + 0.5f - center) / factor - 0.5f
            val sy = center + (i / n + 0.5f - center) / factor - 0.5f
            bilinear(image, sx, sy)
        }
    }

    private fun bilinear(image: LumaGrid, x: Float, y: Float): Float {
        val n = image.width
        val cx = x.coerceIn(0f, n - 1f)
        val cy = y.coerceIn(0f, n - 1f)
        val x0 = floor(cx).toInt().coerceAtMost(n - 2)
        val y0 = floor(cy).toInt().coerceAtMost(n - 2)
        val fx = cx - x0
        val fy = cy - y0
        val v = image.values
        val top = v[y0 * n + x0] * (1 - fx) + v[y0 * n + x0 + 1] * fx
        val bottom = v[(y0 + 1) * n + x0] * (1 - fx) + v[(y0 + 1) * n + x0 + 1] * fx
        return top * (1 - fy) + bottom * fy
    }
}
