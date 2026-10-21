package com.florianhaeglsperger.photocoach.capture

import com.florianhaeglsperger.photocoach.capture.saliency.LumaGrid
import com.florianhaeglsperger.photocoach.domain.model.HorizonLine
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Findet im Bild die Horizontlinie: eine (fast) waagerechte Kante, an der sich die Helligkeit
 * ueber die **ganze Breite** in dieselbe Richtung aendert — typisch Himmel ueber Land oder
 * Wasser.
 *
 * ## Verfahren
 *
 * Fuer jeden Bildpunkt der senkrechte Helligkeitssprung: Mittel der [BAND] Zeilen darunter
 * minus Mittel der [BAND] Zeilen darueber. Ein Band statt Nachbarpixeln, weil der Horizont
 * im verkleinerten Bild nicht scharf ist, sondern ueber ein paar Zeilen verlaeuft.
 *
 * Dann werden alle Linien von Rand zu Rand ausprobiert (jede Hoehe links mit jeder Hoehe
 * rechts bis [MAX_SLOPE_ROWS] Unterschied) und entlang jeder der Sprung *mit Vorzeichen*
 * gemittelt. Das Vorzeichen ist der Kern: an einem echten Horizont ist es ueberall gleich
 * (oben heller, unten dunkler — oder umgekehrt bei Schnee). Zufaellige Textur (Laub, Wand)
 * hat mal hellere, mal dunklere Stellen und mittelt sich weg; eine einzelne Objektkante
 * traegt nur auf einem Teil der Breite bei.
 *
 * Angenommen wird die beste Linie nur, wenn ihr mittlerer Sprung mindestens [MIN_STEP]
 * betraegt **und** auf mindestens [MIN_COVERAGE] der Spalten ein Sprung derselben Richtung
 * liegt. Rechenaufwand: rund 150 000 Additionen auf dem 64x64-Bild — vernachlaessigbar.
 *
 * ## Grenzen
 *
 * Erkannt wird *jede* deutliche durchgehende Hell-Dunkel-Kante, nicht nur ein echter
 * Horizont: auch eine Tischkante oder der Uebergang Wand/Boden. Fuer den Tipp "Linie nicht in
 * die Bildmitte legen" ist das meist trotzdem richtig. Gleichmaessige waagerechte Streifen
 * (Jalousie) koennen eine Linie vortaeuschen.
 */
object ImageHorizon {

    /** Zeilen ueber und unter der Linie, deren Mittel verglichen wird. */
    const val BAND = 3

    /**
     * Groesster Hoehenunterschied zwischen linkem und rechtem Ende (in Zeilen des 64er-Bildes).
     * Entspricht je nach Format rund 15-20° — steiler ist es kein Horizont mehr, sondern eine
     * Diagonale.
     */
    const val MAX_SLOPE_ROWS = 20

    /** Mindest-Helligkeitssprung (0..1) im Mittel entlang der Linie. */
    const val MIN_STEP = 0.08f

    /** Ab diesem Sprung zaehlt eine einzelne Spalte als "traegt die Linie". */
    const val COLUMN_STEP = 0.04f

    /** Anteil der Spalten, die die Linie tragen muessen. */
    const val MIN_COVERAGE = 0.7f

    fun detect(luma: LumaGrid): HorizonLine? {
        val w = luma.width
        val h = luma.height
        if (h < 2 * BAND + 2 || w < 2) return null

        val step = verticalSteps(luma)
        val rows = BAND until h - BAND

        var bestSum = 0f
        var bestLeft = -1
        var bestRight = -1
        for (left in rows) {
            for (right in (left - MAX_SLOPE_ROWS).coerceAtLeast(rows.first)..(left + MAX_SLOPE_ROWS).coerceAtMost(rows.last)) {
                var sum = 0f
                for (x in 0 until w) {
                    sum += step[rowAt(left, right, x, w) * w + x]
                }
                if (abs(sum) > abs(bestSum)) {
                    bestSum = sum
                    bestLeft = left
                    bestRight = right
                }
            }
        }
        if (bestLeft < 0) return null

        val meanStep = bestSum / w
        if (abs(meanStep) < MIN_STEP) return null

        // Traegt die Linie ueber die Breite — oder nur ein starkes Stueck?
        var carrying = 0
        for (x in 0 until w) {
            val s = step[rowAt(bestLeft, bestRight, x, w) * w + x]
            if (s * meanStep > 0f && abs(s) >= COLUMN_STEP) carrying++
        }
        if (carrying.toFloat() / w < MIN_COVERAGE) return null

        return HorizonLine(
            leftY = (bestLeft + 0.5f) / h,
            rightY = (bestRight + 0.5f) / h,
            strength = abs(meanStep),
        )
    }

    /** Zeile der Linie (left → right) in Spalte [x]. */
    private fun rowAt(left: Int, right: Int, x: Int, w: Int): Int =
        (left + (right - left) * x / (w - 1f)).roundToInt()

    /**
     * Senkrechter Helligkeitssprung je Bildpunkt: Mittel der [BAND] Zeilen darunter minus
     * Mittel der [BAND] Zeilen darueber. Ausserhalb des gueltigen Bereichs 0.
     */
    private fun verticalSteps(luma: LumaGrid): FloatArray {
        val w = luma.width
        val h = luma.height
        val v = luma.values
        // Spaltenweise Praefixsummen: Bandmittel in konstanter Zeit.
        val prefix = FloatArray((h + 1) * w)
        for (y in 0 until h) {
            for (x in 0 until w) {
                prefix[(y + 1) * w + x] = prefix[y * w + x] + v[y * w + x]
            }
        }
        fun bandSum(x: Int, fromRow: Int, toRowExclusive: Int) = prefix[toRowExclusive * w + x] - prefix[fromRow * w + x]

        val step = FloatArray(w * h)
        for (y in BAND until h - BAND) {
            for (x in 0 until w) {
                val below = bandSum(x, y + 1, y + 1 + BAND) / BAND
                val above = bandSum(x, y - BAND, y) / BAND
                step[y * w + x] = below - above
            }
        }
        return step
    }
}
