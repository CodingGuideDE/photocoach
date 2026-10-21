package com.florianhaeglsperger.photocoach.capture

import com.florianhaeglsperger.photocoach.capture.saliency.LumaGrid
import com.florianhaeglsperger.photocoach.domain.model.ExposureStats

/**
 * Misst, wie viel vom Bild ausgefressen (fast weiss) oder abgesoffen (fast schwarz) ist.
 *
 * Auf dem verkleinerten 64x64-Helligkeitsbild, nicht auf Einzelpixeln: jeder Wert dort ist
 * schon ein Mittel ueber einen kleinen Bereich. Ein einzelnes Glanzlicht faellt dadurch nicht
 * ins Gewicht — gezaehlt werden *Flaechen* ohne Zeichnung, und um die geht es beim Hinweis.
 */
object ImageExposure {

    /** Ab dieser Helligkeit (0..1) gilt ein Bereich als ausgefressen. */
    const val OVER_LEVEL = 0.95f

    /** Bis zu dieser Helligkeit gilt ein Bereich als abgesoffen. */
    const val UNDER_LEVEL = 0.05f

    fun of(luma: LumaGrid): ExposureStats {
        var over = 0
        var under = 0
        var sum = 0.0
        for (value in luma.values) {
            if (value >= OVER_LEVEL) over++
            if (value <= UNDER_LEVEL) under++
            sum += value
        }
        val n = luma.values.size.coerceAtLeast(1)
        return ExposureStats(
            overexposedFraction = over.toFloat() / n,
            underexposedFraction = under.toFloat() / n,
            meanBrightness = (sum / n).toFloat(),
        )
    }
}
