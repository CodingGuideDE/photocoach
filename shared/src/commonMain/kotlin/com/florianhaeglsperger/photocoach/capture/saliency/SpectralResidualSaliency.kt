package com.florianhaeglsperger.photocoach.capture.saliency

import com.florianhaeglsperger.photocoach.domain.model.SaliencyPoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Helligkeitsbild in Graustufen, Werte 0..1, zeilenweise (Index = y * width + x).
 *
 * Bereits **aufrecht** gedreht und — bei der Frontkamera — **nicht** gespiegelt: die
 * Spiegelung passiert wie bei den Gesichtern erst auf den fertigen Punkten
 * (`SaliencyPoint.mirroredHorizontally`), damit es genau eine Stelle dafuer gibt.
 */
class LumaGrid(val width: Int, val height: Int, val values: FloatArray) {
    init {
        require(values.size == width * height) { "values.size passt nicht zu $width x $height" }
    }
}

/**
 * Saliency ohne Modell: Spectral Residual (Hou & Zhang, CVPR 2007).
 *
 * **Warum dieses Verfahren (Plan 11):** Es ist die im Plan genannte modellfreie Option —
 * pures Kotlin, keine Modell-Datei, keine Lizenzfrage, keine TFLite-Abhaengigkeit, und es
 * laeuft auf 64x64 Pixeln in unter einer Millisekunde. Damit ist der "Engpass Nr. 1" fuer
 * Android aufgeloest, ohne die Modell-Entscheidung vorwegzunehmen: ein gelerntes Modell
 * (U²-Netp o. ae.) kann spaeter an derselben Stelle einspringen, weil die Regeln nur die
 * [SaliencyPoint]-Liste sehen.
 *
 * **Was es misst:** Die Idee ist, dass natuerliche Bilder ein recht gleichfoermiges
 * log-Amplitudenspektrum haben. Was davon abweicht — der "Rest" nach Abzug des geglaetteten
 * Spektrums — sind die Bildteile, die aus ihrer Umgebung herausstechen. Das trifft Objekte
 * vor ruhigem Hintergrund gut, Flaechen gleicher Textur (Himmel, Wand, Rasen) gar nicht.
 *
 * **Was es nicht misst:** Bedeutung. Ein kontrastreiches Schild im Hintergrund kann
 * "salienter" sein als die Person davor. Deshalb bevorzugt `DefaultSubjectResolver`
 * weiterhin ein erkanntes Gesicht.
 *
 * Die Ausgabe folgt bewusst derselben Form wie die iOS-Heatmap (`readHeatmap` in iosMain):
 * 12x12-Raster, auf das Maximum normiert, nur Zellen ab [THRESHOLD]. So rechnen
 * `SubjectResolver` und `DeadSpaceRule` auf beiden Plattformen mit derselben Bedeutung.
 */
object SpectralResidualSaliency {

    /** Kantenlaenge des Eingangsbilds. Zweierpotenz (FFT), und 64 ist der Wert aus dem Paper. */
    const val INPUT_SIZE = 64

    /** Kantenlaenge des Ausgangsrasters — wie auf iOS. */
    const val GRID = 12

    /** Anteil des Maximums, ab dem eine Rasterzelle als salient gilt — wie auf iOS. */
    const val THRESHOLD = 0.45f

    /**
     * Mindest-Standardabweichung der Helligkeit (0..1-Skala), damit das Bild ueberhaupt
     * etwas zu sagen hat.
     *
     * Unterhalb davon (Objektivdeckel, Dunkelheit, weisse Wand ohne Struktur) waere die
     * Saliency-Karte reines Sensorrauschen — normiert aufs Maximum saehe sie trotzdem aus
     * wie eine Karte mit Inhalt. Lieber gar keine Punkte als erfundene.
     */
    const val MIN_CONTRAST = 0.02f

    /**
     * Breite des Randstreifens (in Pixeln des 64er-Bilds), der aus der Karte ausgeblendet
     * wird.
     *
     * Die FFT behandelt das Bild als periodisch: der rechte Rand stoesst an den linken, der
     * obere an den unteren. Jeder Helligkeitsunterschied zwischen gegenueberliegenden Raendern
     * (z. B. heller Himmel oben, dunkler Boden unten) wird so zu einer kuenstlichen Kante,
     * die Spectral Residual zuverlaessig als "salient" meldet. Ohne diesen Streifen saesse
     * das vermeintliche Motiv bei fast jeder Landschaft am Bildrand.
     */
    private const val BORDER = 3

    /**
     * Untergrenze der Amplitude vor dem Logarithmus.
     *
     * Sehr glatte Flaechen und harte Rechteck-Kanten erzeugen im Spektrum exakte Nullstellen;
     * deren Logarithmus geht gegen minus unendlich, und der "spektrale Rest" daneben wird
     * riesig. Ergebnis war beim Ausprobieren ein periodisches Geistermuster quer durchs Bild,
     * weit weg vom eigentlichen Objekt. Echtes Kamerarauschen verdeckt das meist, verlassen
     * sollte man sich darauf nicht — der Boden macht das Verfahren davon unabhaengig.
     */
    private const val AMPLITUDE_FLOOR = 1e-2

    /** Sigma der abschliessenden Glaettung, in Pixeln des 64er-Bilds. */
    private const val BLUR_SIGMA = 2.5

    /** Liefert die Saliency-Punkte fuer ein Helligkeitsbild, oder eine leere Liste. */
    fun analyze(luma: LumaGrid): List<SaliencyPoint> {
        val map = saliencyMap(luma) ?: return emptyList()
        return toGridPoints(map, INPUT_SIZE)
    }

    /**
     * Die volle Saliency-Karte ([INPUT_SIZE]²), auf 0..1 normiert — oder `null`, wenn das
     * Bild zu kontrastarm ist, um etwas auszusagen.
     */
    fun saliencyMap(luma: LumaGrid): FloatArray? {
        require(luma.width == INPUT_SIZE && luma.height == INPUT_SIZE) {
            "Spectral Residual erwartet ${INPUT_SIZE}x$INPUT_SIZE, bekam ${luma.width}x${luma.height}"
        }
        val n = INPUT_SIZE
        if (standardDeviation(luma.values) < MIN_CONTRAST) return null

        // Mittelwert abziehen: sonst traegt die Grundhelligkeit mit in die Rekonstruktion,
        // und helle Flaechen (Himmel) erscheinen allein wegen ihrer Helligkeit "salient".
        // Mittelwertfrei zaehlen helle und dunkle Abweichungen gleich, weil am Ende das
        // Betragsquadrat genommen wird.
        val mean = luma.values.average()
        val re = DoubleArray(n * n) { luma.values[it] - mean }
        val im = DoubleArray(n * n)
        fft2d(re, im, n, inverse = false)

        // log-Amplitude und Phase trennen.
        val logAmplitude = DoubleArray(n * n)
        val phase = DoubleArray(n * n)
        for (i in 0 until n * n) {
            logAmplitude[i] = ln(sqrt(re[i] * re[i] + im[i] * im[i]) + AMPLITUDE_FLOOR)
            phase[i] = atan2(im[i], re[i])
        }

        // Spektraler Rest = log-Amplitude minus ihr lokaler 3x3-Mittelwert.
        val averaged = boxFilter3(logAmplitude, n)
        for (i in 0 until n * n) {
            val residual = exp(logAmplitude[i] - averaged[i])
            re[i] = residual * cos(phase[i])
            im[i] = residual * sin(phase[i])
        }
        fft2d(re, im, n, inverse = true)

        val map = DoubleArray(n * n) { re[it] * re[it] + im[it] * im[it] }
        val blurred = gaussianBlur(map, n, BLUR_SIGMA)

        for (y in 0 until n) {
            for (x in 0 until n) {
                if (x < BORDER || y < BORDER || x >= n - BORDER || y >= n - BORDER) {
                    blurred[y * n + x] = 0.0
                }
            }
        }

        val max = blurred.max()
        if (max <= 0.0) return null
        return FloatArray(n * n) { (blurred[it] / max).toFloat() }
    }

    /**
     * Mittelt eine quadratische Karte auf [GRID]x[GRID] Zellen und liefert die Zellen ab
     * [THRESHOLD] als Punkte — dieselbe Form wie `readHeatmap` auf iOS.
     */
    fun toGridPoints(map: FloatArray, size: Int): List<SaliencyPoint> {
        val cells = FloatArray(GRID * GRID)
        val counts = IntArray(GRID * GRID)
        for (y in 0 until size) {
            val cellY = y * GRID / size
            for (x in 0 until size) {
                val index = cellY * GRID + x * GRID / size
                cells[index] += map[y * size + x]
                counts[index]++
            }
        }
        var max = 0f
        for (i in cells.indices) {
            if (counts[i] > 0) cells[i] /= counts[i]
            if (cells[i] > max) max = cells[i]
        }
        if (max <= 0f) return emptyList()

        val points = mutableListOf<SaliencyPoint>()
        for (cellY in 0 until GRID) {
            for (cellX in 0 until GRID) {
                val weight = cells[cellY * GRID + cellX] / max
                if (weight < THRESHOLD) continue
                points += SaliencyPoint(
                    x = (cellX + 0.5f) / GRID,
                    y = (cellY + 0.5f) / GRID,
                    weight = weight,
                )
            }
        }
        return points
    }

    private fun standardDeviation(values: FloatArray): Float {
        var sum = 0.0
        for (v in values) sum += v
        val mean = sum / values.size
        var squares = 0.0
        for (v in values) squares += (v - mean) * (v - mean)
        return sqrt(squares / values.size).toFloat()
    }

    /** 3x3-Mittelwert, periodisch am Rand — das Spektrum ist ohnehin periodisch. */
    private fun boxFilter3(values: DoubleArray, n: Int): DoubleArray {
        val out = DoubleArray(n * n)
        for (y in 0 until n) {
            for (x in 0 until n) {
                var sum = 0.0
                for (dy in -1..1) {
                    val yy = (y + dy + n) % n
                    for (dx in -1..1) {
                        sum += values[yy * n + (x + dx + n) % n]
                    }
                }
                out[y * n + x] = sum / 9.0
            }
        }
        return out
    }

    /** Separierbare Gauss-Glaettung, am Rand abgeschnitten (nicht periodisch). */
    private fun gaussianBlur(values: DoubleArray, n: Int, sigma: Double): DoubleArray {
        val radius = (sigma * 3).toInt()
        val kernel = DoubleArray(2 * radius + 1) { exp(-((it - radius) * (it - radius)) / (2 * sigma * sigma)) }

        val horizontal = DoubleArray(n * n)
        for (y in 0 until n) {
            for (x in 0 until n) {
                var sum = 0.0
                var norm = 0.0
                for (k in -radius..radius) {
                    val xx = x + k
                    if (xx < 0 || xx >= n) continue
                    sum += values[y * n + xx] * kernel[k + radius]
                    norm += kernel[k + radius]
                }
                horizontal[y * n + x] = sum / norm
            }
        }
        val out = DoubleArray(n * n)
        for (y in 0 until n) {
            for (x in 0 until n) {
                var sum = 0.0
                var norm = 0.0
                for (k in -radius..radius) {
                    val yy = y + k
                    if (yy < 0 || yy >= n) continue
                    sum += horizontal[yy * n + x] * kernel[k + radius]
                    norm += kernel[k + radius]
                }
                out[y * n + x] = sum / norm
            }
        }
        return out
    }

    /** 2D-FFT in place: erst alle Zeilen, dann alle Spalten. */
    private fun fft2d(re: DoubleArray, im: DoubleArray, n: Int, inverse: Boolean) {
        val rowRe = DoubleArray(n)
        val rowIm = DoubleArray(n)
        for (y in 0 until n) {
            for (x in 0 until n) {
                rowRe[x] = re[y * n + x]
                rowIm[x] = im[y * n + x]
            }
            fft1d(rowRe, rowIm, inverse)
            for (x in 0 until n) {
                re[y * n + x] = rowRe[x]
                im[y * n + x] = rowIm[x]
            }
        }
        for (x in 0 until n) {
            for (y in 0 until n) {
                rowRe[y] = re[y * n + x]
                rowIm[y] = im[y * n + x]
            }
            fft1d(rowRe, rowIm, inverse)
            for (y in 0 until n) {
                re[y * n + x] = rowRe[y]
                im[y * n + x] = rowIm[y]
            }
        }
    }

    /** Iterative Radix-2-FFT (Cooley-Tukey). Laenge muss eine Zweierpotenz sein. */
    private fun fft1d(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        // Bit-Umkehr-Permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var length = 2
        while (length <= n) {
            val angle = 2 * PI / length * (if (inverse) 1 else -1)
            val wRe = cos(angle)
            val wIm = sin(angle)
            var start = 0
            while (start < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until length / 2) {
                    val a = start + k
                    val b = a + length / 2
                    val tRe = re[b] * curRe - im[b] * curIm
                    val tIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                start += length
            }
            length = length shl 1
        }
        if (inverse) {
            for (i in 0 until n) {
                re[i] /= n
                im[i] /= n
            }
        }
    }
}
