package com.florianhaeglsperger.photocoach.capture

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Manuelle Kamera-Einstellungen ("Pro-Modus"), plattformneutral beschrieben.
 *
 * `null` bzw. [WhiteBalance.AUTO] heisst jeweils: die Kamera regelt selbst. Die Plattform
 * setzt nur um, was hier steht — welche Werte es ueberhaupt gibt, beschreiben die
 * [ManualCapabilities] des gerade aktiven Objektivs.
 *
 * Belichtung haengt zusammen: sobald [iso] ODER [exposureTimeNs] manuell ist, ist die
 * Belichtungsautomatik aus, und die Plattform muss den jeweils anderen Wert selbst fuellen
 * (Android: mit dem zuletzt von der Automatik gemessenen Wert, damit das Bild beim
 * Umschalten nicht springt). [exposureCompensation] wirkt dann nicht mehr — sie ist eine
 * Vorgabe *an* die Automatik.
 */
data class ManualSettings(
    /** Belichtungskorrektur in Stufen der Plattform, siehe [ExposureCompensation.stepEv]. */
    val exposureCompensation: Int = 0,
    val iso: Int? = null,
    val exposureTimeNs: Long? = null,
    val whiteBalance: WhiteBalance = WhiteBalance.AUTO,
    /** Fokusabstand in Dioptrien (1 / Meter), 0 = unendlich. `null` = Autofokus. */
    val focusDiopters: Float? = null,
) {
    val isManualExposure: Boolean get() = iso != null || exposureTimeNs != null

    /** Alles auf Automatik — der Normalfall; der Pro-Knopf zeigt, wenn das nicht so ist. */
    val isAllAuto: Boolean get() = this == ManualSettings()

    /**
     * Passt die Einstellungen an das an, was das Objektiv kann. Noetig nach einem
     * Objektivwechsel: die Frontkamera hat typischerweise weder manuellen Fokus noch den
     * ISO-Bereich der Rueckkamera.
     */
    fun coercedTo(capabilities: ManualCapabilities): ManualSettings = ManualSettings(
        exposureCompensation = capabilities.exposureCompensation
            ?.let { exposureCompensation.coerceIn(it.minIndex, it.maxIndex) }
            ?: 0,
        iso = iso?.let { capabilities.isoStops.nearestTo(it) },
        exposureTimeNs = exposureTimeNs?.let { capabilities.exposureTimeStopsNs.nearestTo(it) },
        whiteBalance = whiteBalance.takeIf { it in capabilities.whiteBalanceModes }
            ?: WhiteBalance.AUTO,
        focusDiopters = capabilities.minFocusDiopters
            ?.let { max -> focusDiopters?.coerceIn(0f, max) },
    )

    /** Kurzform fuers Feldtest-Protokoll, z.B. "ISO 400 · 1/125 s · WB Tageslicht". */
    fun summary(stepEv: Float?): String {
        if (isAllAuto) return "Auto"
        return buildList {
            if (exposureCompensation != 0 && stepEv != null) {
                add("EV ${formatEv(exposureCompensation * stepEv)}")
            }
            iso?.let { add("ISO $it") }
            exposureTimeNs?.let { add("${formatExposureTime(it)} s") }
            if (whiteBalance != WhiteBalance.AUTO) add("WB ${whiteBalance.label}")
            focusDiopters?.let { add("Fokus ${formatFocusDistance(it)}") }
        }.joinToString(" · ")
    }
}

/** Weissabgleich-Voreinstellungen, wie sie Android (`CONTROL_AWB_MODE_*`) und iOS anbieten. */
enum class WhiteBalance(val label: String) {
    AUTO("Auto"),
    DAYLIGHT("Tageslicht"),
    CLOUDY("Bewoelkt"),
    SHADE("Schatten"),
    INCANDESCENT("Gluehlampe"),
    FLUORESCENT("Leuchtstoff"),
}

/** Bereich der Belichtungskorrektur: Index-Stufen, eine Stufe = [stepEv] Blendenstufen. */
data class ExposureCompensation(val minIndex: Int, val maxIndex: Int, val stepEv: Float)

/**
 * Was das aktive Objektiv manuell zulaesst. Leere Liste bzw. `null` = nicht verfuegbar,
 * dann bietet die UI den Regler gar nicht erst an.
 *
 * ISO und Verschlusszeit sind bewusst als feste Stufen hinterlegt (die ueblichen
 * Drittelstufen), nicht als stufenloser Bereich: so zeigt der Regler Werte, die man von
 * jeder Kamera kennt ("1/125"), statt "1/117".
 */
data class ManualCapabilities(
    val exposureCompensation: ExposureCompensation? = null,
    val isoStops: List<Int> = emptyList(),
    val exposureTimeStopsNs: List<Long> = emptyList(),
    val whiteBalanceModes: List<WhiteBalance> = emptyList(),
    /** Naechster einstellbarer Fokusabstand in Dioptrien; `null` = kein manueller Fokus. */
    val minFocusDiopters: Float? = null,
) {
    val isEmpty: Boolean
        get() = exposureCompensation == null && isoStops.isEmpty() &&
            exposureTimeStopsNs.isEmpty() && whiteBalanceModes.isEmpty() &&
            minFocusDiopters == null

    companion object {
        val NONE = ManualCapabilities()
    }
}

/** Uebliche ISO-Drittelstufen. */
private val STANDARD_ISO = listOf(
    50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600,
    2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800,
)

private const val NS_PER_SECOND = 1_000_000_000L

/** Uebliche Verschlusszeiten als Nenner (1/x s) bzw. ganze Sekunden. */
private val STANDARD_EXPOSURE_NS: List<Long> =
    listOf(8000, 4000, 2000, 1000, 500, 250, 125, 60, 30, 15, 8, 4, 2)
        .map { NS_PER_SECOND / it } + NS_PER_SECOND

/**
 * Laengste angebotene Verschlusszeit. Laenger ginge auf manchen Sensoren, aber Vorschau und
 * Analyse laufen dann nur noch mit einem Bild pro Belichtung — bei 1 s ist der Sucher schon
 * kaum noch brauchbar, und die App ist ein Sucher-Coach, keine Langzeitbelichtungs-App.
 */
internal const val MAX_EXPOSURE_NS = NS_PER_SECOND

/** Mindestens zwei Stufen, sonst gibt es nichts einzustellen. */
private fun <T> List<T>.orEmptyIfSingle(): List<T> = if (size < 2) emptyList() else this

/** Die Standard-ISO-Stufen, die in den Sensorbereich fallen. */
fun isoStopsWithin(min: Int, max: Int): List<Int> =
    STANDARD_ISO.filter { it in min..max }.orEmptyIfSingle()

/** Die Standard-Verschlusszeiten, die in den Sensorbereich fallen (hoechstens [MAX_EXPOSURE_NS]). */
fun exposureStopsWithin(minNs: Long, maxNs: Long): List<Long> =
    STANDARD_EXPOSURE_NS.filter { it in minNs..minOf(maxNs, MAX_EXPOSURE_NS) }.orEmptyIfSingle()

/** Naechstgelegener Listenwert, `null` bei leerer Liste. */
internal fun List<Int>.nearestTo(value: Int): Int? = minByOrNull { abs(it - value) }

internal fun List<Long>.nearestTo(value: Long): Long? = minByOrNull { abs(it - value) }

/** "1/125", "1/8", "1" — ohne Einheit, die haengt die Anzeige an. */
fun formatExposureTime(ns: Long): String =
    if (ns >= NS_PER_SECOND) {
        (ns.toDouble() / NS_PER_SECOND).roundToLong().toString()
    } else {
        "1/${(NS_PER_SECOND.toDouble() / ns).roundToLong()}"
    }

/** "+0,7", "−1,3", "±0" — eine Nachkommastelle, deutsches Komma. */
fun formatEv(ev: Float): String {
    val tenths = (ev * 10f).roundToInt()
    if (tenths == 0) return "±0"
    val sign = if (tenths > 0) "+" else "−"
    val magnitude = abs(tenths)
    val fraction = magnitude % 10
    return if (fraction == 0) "$sign${magnitude / 10}" else "$sign${magnitude / 10},$fraction"
}

/**
 * Fokusabstand zum Anzeigen: "∞", "2,5 m", "0,3 m", "12 m".
 *
 * Nur ein Anhaltspunkt: viele Geraete melden ihre Fokusskala als nicht kalibriert, dann
 * stimmen die Meter bloss ungefaehr. Die Richtung (nah/fern) stimmt immer.
 */
fun formatFocusDistance(diopters: Float): String {
    if (diopters <= 0.01f) return "∞"
    val meters = 1f / diopters
    if (meters >= 10f) return "${meters.roundToInt()} m"
    val tenths = (meters * 10f).roundToInt().coerceAtLeast(1)
    val fraction = tenths % 10
    return if (fraction == 0) "${tenths / 10} m" else "${tenths / 10},$fraction m"
}
