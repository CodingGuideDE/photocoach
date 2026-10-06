package com.florianhaeglsperger.photocoach.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

/** Feste Stufen oberhalb von 1×. Hoeher wird es auf den meisten Geraeten rein digital. */
private val TELE_PRESETS = listOf(2f, 5f)

/**
 * Feste Stufen oberhalb von 1× fuer die Frontkamera. Bei Armlaenge ist 5× rein digital und
 * zeigt kaum mehr als ein Auge — 3× ist dort die sinnvolle Obergrenze.
 */
private val SELFIE_TELE_PRESETS = listOf(2f, 3f)

/**
 * Dauer eines Stufenwechsels. Kurz genug, dass der Tipp sofort wirkt; lang genug, dass man
 * sieht, wohin das Bild faehrt — das "Steife" am harten Sprung war, dass der Ausschnitt
 * ohne Uebergang ein anderer war.
 */
internal const val ZOOM_ANIMATION_MS = 300

/** Farbe der aktiven Stufe (wie die Markierung in der System-Kamera). */
private val ACTIVE_COLOR = Color(0xFFFFD54F)

/**
 * Relative Toleranz beim Vergleich von Zoomwerten. CameraX meldet z.B. 0,99999 statt 1,0 —
 * ohne Toleranz waere dann die 0,5×-Stufe statt der 1×-Stufe aktiv.
 */
private const val ZOOM_TOLERANCE = 0.02f

/**
 * Welche Zoom-Stufen als Buttons angeboten werden.
 *
 * - Liegt [CameraZoom.min] unter 1, hat das Geraet ein Ultraweitwinkel, das die Plattform
 *   ueber die logische Kamera zugaenglich macht — dann wird genau dieser Wert die erste
 *   Stufe (0,5× oder 0,6× je nach Geraet, nicht pauschal 0,5).
 * - 1× immer, sofern im Bereich.
 * - 2× und 5× (Frontkamera: 2× und 3×) nur, wenn das Geraet so weit kommt.
 *
 * Weniger als zwei Stufen bedeutet: nichts umzuschalten, die Leiste bleibt weg.
 */
internal fun zoomPresets(zoom: CameraZoom, lensFacing: LensFacing): List<Float> = buildList {
    if (zoom.min < 1f - ZOOM_TOLERANCE) add(zoom.min)
    if (1f in zoom.min..zoom.max) add(1f)
    val tele = if (lensFacing == LensFacing.FRONT) SELFIE_TELE_PRESETS else TELE_PRESETS
    tele.filterTo(this) { it <= zoom.max * (1f + ZOOM_TOLERANCE) }
}

/**
 * Die Stufe, in die [ratio] faellt: die groesste Stufe, die nicht ueber [ratio] liegt.
 * Bei 1,4× ist das die 1×-Stufe — die zeigt dann "1,4×" an, wie man es aus der
 * System-Kamera kennt.
 */
internal fun activePreset(presets: List<Float>, ratio: Float): Float? =
    presets.lastOrNull { it <= ratio * (1f + ZOOM_TOLERANCE) } ?: presets.firstOrNull()

/**
 * Zoomwert bei Fortschritt [t] (0..1) einer Animation von [from] nach [to].
 *
 * Logarithmisch, nicht linear: Zoom wird als Faktor wahrgenommen. Linear interpoliert
 * kriecht 1× → 5× anfangs und rast am Ende (der letzte Schritt von 4× auf 5× ist nur
 * noch ein Viertel mehr); geometrisch wirkt jeder Abschnitt gleich schnell.
 */
internal fun interpolateZoom(from: Float, to: Float, t: Float): Float =
    if (from <= 0f || to <= 0f) to else from * (to / from).pow(t)

/**
 * Faehrt den Kamerazoom weich an eine neue Stufe heran, statt zu springen.
 *
 * Jeder Animationsschritt geht als eigener `setZoom`-Aufruf an die Kamera — CameraX
 * verwirft dabei ueberholte Aufrufe von selbst. Eine neue Animation (zweiter Tipp waehrend
 * der ersten) bricht die laufende ab und startet beim aktuell gemeldeten Wert.
 *
 * [cancel] ist fuer Eingriffe, die Vorrang haben: Pinch (der Finger bestimmt, nicht die
 * Animation) und Objektivwechsel (danach gehoert `setZoom` zu einer anderen Kamera).
 */
@Stable
internal class ZoomAnimator(private val scope: CoroutineScope) {
    private var job: Job? = null

    fun animate(from: Float, to: Float, setZoom: (Float) -> Unit) {
        job?.cancel()
        job = scope.launch {
            Animatable(0f).animateTo(
                targetValue = 1f,
                animationSpec = tween(ZOOM_ANIMATION_MS, easing = FastOutSlowInEasing),
            ) {
                setZoom(interpolateZoom(from, to, value))
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}

@Composable
internal fun rememberZoomAnimator(): ZoomAnimator {
    val scope = rememberCoroutineScope()
    return remember(scope) { ZoomAnimator(scope) }
}

/** "0,5×", "1×", "1,4×" — eine Nachkommastelle, deutsches Komma, ",0" faellt weg. */
internal fun formatZoom(ratio: Float): String {
    val tenths = (ratio * 10f).roundToInt()
    val whole = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0) "$whole×" else "$whole,$fraction×"
}

/**
 * Leiste mit den Zoom-Stufen ueber dem Ausloeser. Die aktive Stufe zeigt den tatsaechlichen
 * Wert (nach einer Pinch-Geste also z.B. "1,4×"), die uebrigen ihren Sollwert.
 */
@Composable
internal fun ZoomBar(
    zoom: CameraZoom,
    lensFacing: LensFacing,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = zoomPresets(zoom, lensFacing)
    if (presets.size < 2) return
    val active = activePreset(presets, zoom.ratio)

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEach { preset ->
            val isActive = preset == active
            // Der Wechsel der aktiven Stufe gleitet mit, statt umzuspringen — passend zur
            // Zoom-Animation selbst, waehrend der die Markierung ueber die Stufen wandert.
            val size by animateDpAsState(
                targetValue = if (isActive) 40.dp else 34.dp,
                animationSpec = tween(ZOOM_ANIMATION_MS, easing = FastOutSlowInEasing),
            )
            val background by animateColorAsState(
                targetValue = Color.White.copy(alpha = if (isActive) 0.25f else 0.1f),
                animationSpec = tween(ZOOM_ANIMATION_MS),
            )
            val textColor by animateColorAsState(
                targetValue = if (isActive) ACTIVE_COLOR else Color.White,
                animationSpec = tween(ZOOM_ANIMATION_MS),
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(background)
                    .clickable { onSelect(preset) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatZoom(if (isActive) zoom.ratio else preset),
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Pinch-Zoom ueber dem Sucherbild.
 *
 * Der Ausgangswert wird zu Beginn jeder Geste einmal gelesen und dann mit dem Pinch-Faktor
 * multipliziert — nicht bei jedem Event der zuletzt gemeldete Kamerawert. Der kommt ueber
 * [CameraState] erst ein Frame spaeter an, und bei 60+ Touch-Events pro Sekunde gingen so
 * Teile der Bewegung verloren (die Geste fuehlte sich zaeh an).
 *
 * [currentZoom] ist eine Funktion statt eines Werts: `pointerInput` startet nur einmal,
 * muss aber bei jeder neuen Geste den dann aktuellen Zoom sehen.
 *
 * Einfinger-Gesten bleiben unangetastet.
 */
internal fun Modifier.pinchToZoom(
    currentZoom: () -> CameraZoom?,
    onZoom: (Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val start = currentZoom() ?: return@awaitEachGesture
        var target = start.ratio
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                // Auf den Bereich begrenzen, bevor weitermultipliziert wird: sonst
                // entsteht am Anschlag eine tote Zone, durch die man erst zurueck muss.
                target = (target * event.calculateZoom()).coerceIn(start.min, start.max)
                onZoom(target)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
