package com.florianhaeglsperger.photocoach.capture

import com.florianhaeglsperger.photocoach.capture.saliency.LumaGrid
import com.florianhaeglsperger.photocoach.domain.model.StructureGrid
import kotlin.math.abs

/**
 * Misst, wie viel Struktur in jedem Bildbereich steckt — Kanten, Texturen, Details.
 *
 * Grundlage fuer `DeadSpaceRule`: eine Bildhaelfte ist "tot", wenn sie *gleichfoermig* ist
 * (blanker Himmel, glatte Wand), nicht wenn sie nur weniger auffaellt als das Hauptmotiv.
 * Die Saliency kann diese Frage nicht beantworten, siehe [StructureGrid].
 *
 * Gemessen wird je Pixel der Helligkeitsunterschied zu den Nachbarn (zentrale Differenzen in
 * x und y) und je Zelle gemittelt. Geteilt wird durch die mittlere Helligkeit der Zelle plus
 * [DARK_FLOOR] — so zaehlt derselbe Kontrast in dunklen und hellen Bildteilen gleich, und
 * Sensorrauschen in sehr dunklen Bereichen wird nicht zur "Struktur" aufgeblasen.
 */
object ImageStructure {

    /** Kantenlaenge des Ausgangsrasters — dasselbe wie bei der Saliency. */
    const val GRID = 12

    /**
     * Untergrenze des Nenners. Ohne sie wuerde in fast schwarzen Bereichen schon das
     * Rauschen des Sensors durch eine winzige Helligkeit geteilt und als Struktur gelten.
     */
    private const val DARK_FLOOR = 0.05f

    fun of(luma: LumaGrid, grid: Int = GRID): StructureGrid {
        val w = luma.width
        val h = luma.height
        val sum = FloatArray(grid * grid)
        val brightness = FloatArray(grid * grid)
        val count = IntArray(grid * grid)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val left = luma.values[y * w + (x - 1).coerceAtLeast(0)]
                val right = luma.values[y * w + (x + 1).coerceAtMost(w - 1)]
                val up = luma.values[(y - 1).coerceAtLeast(0) * w + x]
                val down = luma.values[(y + 1).coerceAtMost(h - 1) * w + x]
                val gradient = (abs(right - left) + abs(down - up)) / 2f

                val cell = (y * grid / h) * grid + x * grid / w
                sum[cell] += gradient
                brightness[cell] += luma.values[y * w + x]
                count[cell]++
            }
        }

        return StructureGrid(
            columns = grid,
            rows = grid,
            values = List(grid * grid) { cell ->
                if (count[cell] == 0) {
                    0f
                } else {
                    val meanGradient = sum[cell] / count[cell]
                    val meanBrightness = brightness[cell] / count[cell]
                    meanGradient / (meanBrightness + DARK_FLOOR)
                }
            },
        )
    }
}
