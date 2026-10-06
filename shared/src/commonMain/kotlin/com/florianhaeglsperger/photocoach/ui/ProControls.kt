package com.florianhaeglsperger.photocoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.florianhaeglsperger.photocoach.capture.ManualCapabilities
import com.florianhaeglsperger.photocoach.capture.ManualSettings
import com.florianhaeglsperger.photocoach.capture.WhiteBalance
import com.florianhaeglsperger.photocoach.capture.formatEv
import com.florianhaeglsperger.photocoach.capture.formatExposureTime
import com.florianhaeglsperger.photocoach.capture.formatFocusDistance
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Markiert manuell gesetzte Werte — dieselbe Farbe wie die aktive Zoomstufe. */
private val MANUAL_COLOR = Color(0xFFFFD54F)

/** Die einzelnen Regler des Pro-Modus, in der Reihenfolge wie in der System-Kamera. */
private enum class ProParam(val caption: String) {
    EV("EV"),
    ISO("ISO"),
    SHUTTER("S"),
    WHITE_BALANCE("WB"),
    FOCUS("MF"),
    ;

    fun isAvailable(c: ManualCapabilities): Boolean = when (this) {
        EV -> c.exposureCompensation != null
        ISO -> c.isoStops.isNotEmpty()
        SHUTTER -> c.exposureTimeStopsNs.isNotEmpty()
        WHITE_BALANCE -> c.whiteBalanceModes.isNotEmpty()
        FOCUS -> c.minFocusDiopters != null
    }

    fun isManual(s: ManualSettings): Boolean = when (this) {
        EV -> s.exposureCompensation != 0
        ISO -> s.iso != null
        SHUTTER -> s.exposureTimeNs != null
        WHITE_BALANCE -> s.whiteBalance != WhiteBalance.AUTO
        FOCUS -> s.focusDiopters != null
    }

    fun valueLabel(s: ManualSettings, c: ManualCapabilities): String = when (this) {
        EV -> formatEv(s.exposureCompensation * (c.exposureCompensation?.stepEv ?: 0f))
        ISO -> s.iso?.toString() ?: "Auto"
        SHUTTER -> s.exposureTimeNs?.let(::formatExposureTime) ?: "Auto"
        WHITE_BALANCE -> s.whiteBalance.label
        FOCUS -> s.focusDiopters?.let(::formatFocusDistance) ?: "AF"
    }
}

/**
 * Oeffnet und schliesst den Pro-Modus (links neben dem Ausloeser, Gegenstueck zum
 * Objektiv-Wechsel rechts).
 *
 * Gelb, sobald irgendetwas nicht auf Automatik steht — auch bei geschlossenem Panel. Sonst
 * vergisst man eine manuelle Belichtung und wundert sich spaeter ueber dunkle Fotos.
 */
@Composable
internal fun ProToggle(
    open: Boolean,
    anyManual: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (anyManual) MANUAL_COLOR else Color.White
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (open) 0.7f else 0.45f))
            .border(1.5.dp, color.copy(alpha = if (open) 0.9f else 0.4f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "PRO",
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Das Pro-Panel: oben eine Leiste mit allen Parametern und ihrem aktuellen Wert, darunter
 * der Regler fuer den gerade gewaehlten.
 *
 * Es bietet nur an, was das aktive Objektiv kann ([capabilities]) — ein Regler ohne
 * Wirkung waere schlimmer als keiner. Jeder Parameter hat seinen eigenen Weg zurueck auf
 * Automatik, dazu "Alles Auto" fuer den schnellen Ausstieg.
 */
@Composable
internal fun ProPanel(
    capabilities: ManualCapabilities,
    settings: ManualSettings,
    onChange: (ManualSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val params = ProParam.entries.filter { it.isAvailable(capabilities) }
    if (params.isEmpty()) {
        PanelSurface(modifier) {
            Text(
                text = "Dieses Objektiv erlaubt keine manuellen Einstellungen.",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    var selected by remember { mutableStateOf(params.first()) }
    // Nach einem Objektivwechsel kann der gewaehlte Parameter fehlen.
    if (selected !in params) selected = params.first()

    PanelSurface(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            params.forEach { param ->
                ParamChip(
                    caption = param.caption,
                    value = param.valueLabel(settings, capabilities),
                    selected = param == selected,
                    manual = param.isManual(settings),
                    onClick = { selected = param },
                )
            }
            if (!settings.isAllAuto) {
                TextPill(text = "Alles Auto", active = false, onClick = { onChange(ManualSettings()) })
            }
        }

        when (selected) {
            ProParam.EV -> ExposureCompensationControl(capabilities, settings, onChange)
            ProParam.ISO -> StopControl(
                stops = capabilities.isoStops,
                current = settings.iso,
                label = { it.toString() },
                onSelect = { onChange(settings.copy(iso = it)) },
            )
            ProParam.SHUTTER -> StopControl(
                stops = capabilities.exposureTimeStopsNs,
                current = settings.exposureTimeNs,
                label = { "${formatExposureTime(it)} s" },
                onSelect = { onChange(settings.copy(exposureTimeNs = it)) },
            )
            ProParam.WHITE_BALANCE -> Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                capabilities.whiteBalanceModes.forEach { mode ->
                    TextPill(
                        text = mode.label,
                        active = mode == settings.whiteBalance,
                        onClick = { onChange(settings.copy(whiteBalance = mode)) },
                    )
                }
            }
            ProParam.FOCUS -> FocusControl(
                minFocusDiopters = capabilities.minFocusDiopters ?: 0f,
                current = settings.focusDiopters,
                onChange = { onChange(settings.copy(focusDiopters = it)) },
            )
        }
    }
}

@Composable
private fun PanelSurface(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/** Parameter-Kachel: Kuerzel oben, aktueller Wert darunter. Gelb = manuell gesetzt. */
@Composable
private fun ParamChip(
    caption: String,
    value: String,
    selected: Boolean,
    manual: Boolean,
    onClick: () -> Unit,
) {
    val valueColor = if (manual) MANUAL_COLOR else Color.White
    Column(
        modifier = Modifier
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = if (selected) 0.22f else 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(caption, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun TextPill(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(if (active) MANUAL_COLOR.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (active) Color.Black else Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun proSliderColors() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = MANUAL_COLOR,
    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

/**
 * Regler ueber feste Stufen (ISO, Verschlusszeit) mit "Auto"-Knopf davor.
 *
 * Steht der Wert auf Auto, sitzt der Regler ausgegraut in der Mitte; das erste Ziehen
 * schaltet auf manuell. Kein extra "Manuell"-Knopf — ein Handgriff weniger.
 */
@Composable
private fun <T> StopControl(
    stops: List<T>,
    current: T?,
    label: (T) -> String,
    onSelect: (T?) -> Unit,
) {
    val index = current?.let { stops.indexOf(it) }?.takeIf { it >= 0 }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextPill(text = "Auto", active = current == null, onClick = { onSelect(null) })
        Slider(
            value = (index ?: (stops.size / 2)).toFloat(),
            onValueChange = { onSelect(stops[it.roundToInt().coerceIn(0, stops.lastIndex)]) },
            valueRange = 0f..stops.lastIndex.toFloat(),
            steps = (stops.size - 2).coerceAtLeast(0),
            colors = proSliderColors(),
            modifier = Modifier.weight(1f).then(if (current == null) Modifier.alphaDimmed() else Modifier),
        )
        Text(
            text = current?.let(label) ?: "Auto",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 56.dp),
        )
    }
}

@Composable
private fun ExposureCompensationControl(
    capabilities: ManualCapabilities,
    settings: ManualSettings,
    onChange: (ManualSettings) -> Unit,
) {
    val range = capabilities.exposureCompensation ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextPill(text = "0", active = false, onClick = { onChange(settings.copy(exposureCompensation = 0)) })
            Slider(
                value = settings.exposureCompensation.toFloat(),
                onValueChange = { onChange(settings.copy(exposureCompensation = it.roundToInt())) },
                valueRange = range.minIndex.toFloat()..range.maxIndex.toFloat(),
                steps = (range.maxIndex - range.minIndex - 1).coerceAtLeast(0),
                enabled = !settings.isManualExposure,
                colors = proSliderColors(),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatEv(settings.exposureCompensation * range.stepEv),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 56.dp),
            )
        }
        // Sonst schiebt man am Regler und nichts passiert — ohne zu wissen, warum.
        if (settings.isManualExposure) {
            Text(
                text = "Wirkt nur mit automatischer Belichtung (ISO und S auf Auto).",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Manueller Fokus: links nah, rechts unendlich (wie der Fokusring am Objektiv, von der
 * Naheinstellgrenze aus gedreht).
 *
 * Quadratisch in Dioptrien: linear waere 0,3 m bis unendlich ins rechte Drittel gequetscht
 * (bei 10 dpt Naheinstellgrenze liegt die Reglermitte sonst bei 0,2 m). So liegt die Mitte
 * bei ~0,4 m und 80 % bei ~2,5 m.
 */
@Composable
private fun FocusControl(
    minFocusDiopters: Float,
    current: Float?,
    onChange: (Float?) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextPill(text = "AF", active = current == null, onClick = { onChange(null) })
        Text("nah", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = current?.let { 1f - sqrt(it / minFocusDiopters) } ?: 1f,
            onValueChange = { onChange((1f - it) * (1f - it) * minFocusDiopters) },
            colors = proSliderColors(),
            modifier = Modifier.weight(1f).then(if (current == null) Modifier.alphaDimmed() else Modifier),
        )
        Text(
            text = current?.let(::formatFocusDistance) ?: "fern",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 44.dp),
        )
    }
}

/** Regler, der gerade nicht greift (Automatik aktiv), zurueckgenommen darstellen. */
private fun Modifier.alphaDimmed(): Modifier = alpha(0.45f)
