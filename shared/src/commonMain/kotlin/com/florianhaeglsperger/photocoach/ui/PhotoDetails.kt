package com.florianhaeglsperger.photocoach.ui

import kotlin.math.roundToLong

/**
 * Aufnahmedaten eines Fotos, wie die [PhotoLibrary] der Plattform sie liefert.
 *
 * Rohwerte statt fertiger Texte — die Formatierung ([exposureTiles], [detailRows]) liegt hier
 * in commonMain und ist damit auf allen Plattformen gleich und ohne Geraet testbar. Ausnahme
 * sind [takenDate] und [takenTime]: Datum und Uhrzeit formatiert die Plattform, weil das Sache
 * der Spracheinstellung des Geraets ist (wie [CaptureResult.Success.location], nur zum
 * Anzeigen).
 *
 * Jedes Feld kann fehlen — je nach Geraet schreibt die Kamera nicht alle EXIF-Werte.
 */
data class PhotoDetails(
    /** Langes Datum, z. B. "Dienstag, 6. Oktober 2026" — die Ueberschrift der Info. */
    val takenDate: String? = null,
    /** Uhrzeit, z. B. "20:37". */
    val takenTime: String? = null,
    val fileName: String? = null,
    val album: String? = null,
    /** Breite und Hoehe so, wie das Foto angezeigt wird (EXIF-Drehung schon beruecksichtigt). */
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val sizeBytes: Long? = null,
    val iso: Int? = null,
    val exposureTimeSeconds: Double? = null,
    val fNumber: Double? = null,
    val focalLengthMm: Double? = null,
    val whiteBalanceManual: Boolean? = null,
    val flashFired: Boolean? = null,
    /** Hersteller und Modell, z. B. "Google Pixel 8". */
    val device: String? = null,
)

/**
 * Die vier Belichtungswerte als Kacheln (Wert, Bezeichnung) — das, was Fotografen auf einen
 * Blick sehen wollen, in der ueblichen Reihenfolge von Kamera-Apps. Fehlende fallen weg.
 */
internal fun PhotoDetails.exposureTiles(): List<Pair<String, String>> = buildList {
    fNumber?.takeIf { it > 0 }?.let { add(formatAperture(it) to "Blende") }
    exposureTimeSeconds?.takeIf { it > 0 }?.let { add(formatExposureTime(it) to "Belichtung") }
    iso?.takeIf { it > 0 }?.let { add(it.toString() to "ISO") }
    focalLengthMm?.takeIf { it > 0 }?.let { add("${formatDecimal(it, 1)} mm" to "Brennweite") }
}

/**
 * Die uebrigen Angaben als Zeilen (Bezeichnung, Wert) unter den Kacheln. Fehlende Werte
 * erscheinen gar nicht — eine Zeile "Blitz: —" hilft niemandem.
 */
internal fun PhotoDetails.detailRows(): List<Pair<String, String>> = buildList {
    if (widthPx != null && heightPx != null && widthPx > 0 && heightPx > 0) {
        add("Auflösung" to formatResolution(widthPx, heightPx))
    }
    sizeBytes?.takeIf { it > 0 }?.let { add("Dateigröße" to formatFileSize(it)) }
    whiteBalanceManual?.let { add("Weißabgleich" to if (it) "Manuell" else "Automatisch") }
    flashFired?.let { add("Blitz" to if (it) "Ausgelöst" else "Aus") }
}

/** Unterzeile unter dem Datum: "20:37 · Google Pixel 8" — was davon da ist. */
internal fun PhotoDetails.subtitle(): String? =
    listOfNotNull(takenTime, device).joinToString(" · ").takeIf { it.isNotEmpty() }

/** "4032 × 3024 · 12,2 MP" */
internal fun formatResolution(width: Int, height: Int): String {
    val megapixels = width.toDouble() * height / 1_000_000
    return "$width × $height · ${formatDecimal(megapixels, 1)} MP"
}

/**
 * Wie Kameras es anzeigen: unter einer Sekunde als Bruch ("1/120 s"), darueber als Zahl
 * ("2,5 s"). Ein Bruch wie "1/8,3 s" waere korrekt, aber unlesbar — deshalb wird der Nenner
 * gerundet.
 */
internal fun formatExposureTime(seconds: Double): String =
    if (seconds >= 1.0) {
        "${formatDecimal(seconds, 1)} s"
    } else {
        "1/${(1.0 / seconds).roundToLong()} s"
    }

/** "f/1,8", "f/4" */
internal fun formatAperture(fNumber: Double): String = "f/${formatDecimal(fNumber, 1)}"

/** Dezimal (1000er-Stufen) wie der Dateimanager von Android: "198 KB", "3,2 MB". */
internal fun formatFileSize(bytes: Long): String = when {
    bytes < 1_000 -> "$bytes B"
    bytes < 1_000_000 -> "${(bytes / 1_000.0).roundToLong()} KB"
    else -> "${formatDecimal(bytes / 1_000_000.0, 1)} MB"
}

/**
 * Feste Nachkommastellen mit deutschem Komma, ",0" faellt weg. Von Hand, weil commonMain
 * kein `String.format` kennt.
 */
internal fun formatDecimal(value: Double, decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (value * factor).roundToLong()
    val whole = scaled / factor
    val fraction = (scaled % factor).toString().padStart(decimals, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$whole" else "$whole,$fraction"
}
