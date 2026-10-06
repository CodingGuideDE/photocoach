package com.florianhaeglsperger.photocoach.capture

import androidx.camera.core.ImageProxy
import com.florianhaeglsperger.photocoach.capture.saliency.LumaGrid

/**
 * Rechnet einen Punkt im **aufrechten** Bild (so, wie der Nutzer es sieht und wie ML Kit
 * seine Koordinaten liefert) auf die Stelle im **rohen Sensorbild** zurueck, beides
 * normiert auf 0..1.
 *
 * `rotationDegrees` ist die CameraX-Angabe aus `ImageInfo`: um wie viel Grad das Sensorbild
 * **im Uhrzeigersinn** gedreht werden muss, damit es aufrecht steht. Hier wird die
 * umgekehrte Richtung gebraucht — vom aufrechten Bild zurueck zum Sensor —, weil pro
 * Ausgabepixel nachgeschlagen wird, woher es kommt.
 *
 * Herausgezogen und getestet (`LumaSamplerTest`), weil genau hier ein Vorzeichen- oder
 * Achsenfehler die Saliency still um 90° verdreht: die Regeln wuerden dann ein Motiv
 * "oben" melden, das in Wahrheit links sitzt.
 */
internal fun uprightToSensor(ux: Float, uy: Float, rotationDegrees: Int): Pair<Float, Float> =
    when (rotationDegrees) {
        90 -> uy to 1f - ux
        180 -> 1f - ux to 1f - uy
        270 -> 1f - uy to ux
        else -> ux to uy
    }

/**
 * Tastet ein Sensorbild auf ein aufrechtes [size]x[size]-Helligkeitsraster ab.
 *
 * Pro Zielpixel werden 3x3 Stichproben gemittelt statt alle Quellpixel — bei 640x480 und
 * 64x64 waeren das rund 75 Pixel pro Zelle, und die anschliessende Glaettung in
 * `SpectralResidualSaliency` macht den Unterschied ohnehin unsichtbar.
 *
 * [readLuma] liefert den Y-Wert (0..255) an einer Sensor-Pixelposition. Als Parameter
 * statt direkt auf [ImageProxy], damit sich die Abbildung ohne Kamera testen laesst.
 */
internal fun sampleUprightLuma(
    sensorWidth: Int,
    sensorHeight: Int,
    rotationDegrees: Int,
    size: Int,
    readLuma: (x: Int, y: Int) -> Int,
): LumaGrid {
    val values = FloatArray(size * size)
    for (gy in 0 until size) {
        for (gx in 0 until size) {
            var sum = 0
            for (sy in 0 until SUBSAMPLES) {
                for (sx in 0 until SUBSAMPLES) {
                    val ux = (gx + (sx + 0.5f) / SUBSAMPLES) / size
                    val uy = (gy + (sy + 0.5f) / SUBSAMPLES) / size
                    val (nx, ny) = uprightToSensor(ux, uy, rotationDegrees)
                    val px = (nx * sensorWidth).toInt().coerceIn(0, sensorWidth - 1)
                    val py = (ny * sensorHeight).toInt().coerceIn(0, sensorHeight - 1)
                    sum += readLuma(px, py)
                }
            }
            values[gy * size + gx] = sum / (SUBSAMPLES * SUBSAMPLES * 255f)
        }
    }
    return LumaGrid(size, size, values)
}

private const val SUBSAMPLES = 3

/**
 * Das aufrechte Helligkeitsraster eines CameraX-Frames (Y-Ebene von YUV_420_888).
 *
 * Nur waehrend `analyze()` aufrufen — danach ist das [ImageProxy] geschlossen (siehe
 * [CameraFrame]).
 */
internal fun ImageProxy.uprightLuma(size: Int): LumaGrid {
    val plane = planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    return sampleUprightLuma(width, height, imageInfo.rotationDegrees, size) { x, y ->
        buffer.get(y * rowStride + x * pixelStride).toInt() and 0xFF
    }
}
