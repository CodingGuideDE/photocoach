package com.florianhaeglsperger.photocoach.capture

import com.florianhaeglsperger.photocoach.capture.saliency.LumaGrid

/**
 * Laengere Kante, mit der ein gespeichertes Foto fuer die Bewertung geladen wird.
 *
 * Dieselbe Groessenordnung wie die Analyse-Frames im Sucher (CameraX-Standard 640 x 480):
 * Gesichter haben dann dieselbe Mindestgroesse in Pixeln, und die Bewertung eines Fotos
 * entspricht dem, was der Sucher im Moment der Aufnahme gesehen hat. Mehr Aufloesung brauchen
 * weder ML Kit noch Spectral Residual (das ohnehin auf 64 x 64 rechnet).
 */
const val STILL_ANALYSIS_MAX_DIMENSION = 640

/**
 * Tastet ein Foto (ARGB-Pixel, zeilenweise, schon aufrecht) auf ein [size]x[size]-
 * Helligkeitsraster ab — das Gegenstueck zu `sampleUprightLuma` fuer Kamera-Frames, damit
 * die Saliency eines Fotos aus genau derselben Eingabeform entsteht wie im Sucher.
 *
 * Helligkeit nach BT.601 (`0,299 R + 0,587 G + 0,114 B`), also derselbe Y-Wert, den die
 * Kamera in der Y-Ebene ihrer YUV-Frames liefert. Pro Zielpixel werden wie dort 3 x 3
 * Stichproben gemittelt.
 *
 * Als pure Funktion auf einem `IntArray` statt direkt auf einem Bitmap, damit sie sich ohne
 * Grafik-Laufzeit testen laesst.
 */
fun lumaGridFromArgb(pixels: IntArray, width: Int, height: Int, size: Int): LumaGrid {
    require(width > 0 && height > 0) { "Leeres Bild ($width x $height)" }
    require(pixels.size >= width * height) { "pixels.size passt nicht zu $width x $height" }
    val values = FloatArray(size * size)
    for (gy in 0 until size) {
        for (gx in 0 until size) {
            var sum = 0f
            for (sy in 0 until SUBSAMPLES) {
                for (sx in 0 until SUBSAMPLES) {
                    val px = (((gx + (sx + 0.5f) / SUBSAMPLES) / size) * width).toInt().coerceIn(0, width - 1)
                    val py = (((gy + (sy + 0.5f) / SUBSAMPLES) / size) * height).toInt().coerceIn(0, height - 1)
                    val argb = pixels[py * width + px]
                    val r = (argb shr 16) and 0xFF
                    val g = (argb shr 8) and 0xFF
                    val b = argb and 0xFF
                    sum += 0.299f * r + 0.587f * g + 0.114f * b
                }
            }
            values[gy * size + gx] = sum / (SUBSAMPLES * SUBSAMPLES * 255f)
        }
    }
    return LumaGrid(size, size, values)
}

private const val SUBSAMPLES = 3
