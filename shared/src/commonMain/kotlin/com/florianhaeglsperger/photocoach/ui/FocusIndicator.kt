package com.florianhaeglsperger.photocoach.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.florianhaeglsperger.photocoach.capture.ExposureCompensation
import com.florianhaeglsperger.photocoach.capture.formatEv
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Ein angetippter Fokuspunkt, normiert (0..1) im Vorschau-Rahmen. [id] trennt Tipps auf dieselbe Stelle. */
data class FocusPoint(val x: Float, val y: Float, val id: Int)

enum class FocusState {
    /** Die Kamera stellt gerade scharf. */
    FOCUSING,

    /** Scharfgestellt — der Ring leuchtet kurz in der Akzentfarbe. */
    FOCUSED,

    /**
     * Nicht scharfgestellt: kein Autofokus (viele Frontkameras) oder zu wenig Kontrast.
     * Die Belichtung am Punkt gilt trotzdem, deshalb kein Fehlerzustand, nur ohne Bestaetigung.
     */
    UNFOCUSED,
}

/** Masse von Ring und Regler. Die Leiste ist so lang, dass sich jede Stufe gezielt treffen laesst. */
private val RING_SIZE = 68.dp
private val TRACK_HEIGHT = 150.dp
private val TRACK_GAP = 18.dp
private val SUN_SIZE = 26.dp

/** So lange nach der letzten Beruehrung bleiben Ring und Regler voll sichtbar. */
private const val ACTIVE_MS = 3000L

/** Danach markiert ein blasser Ring weiter, dass Fokus und Belichtung am Punkt eingerastet sind. */
private const val IDLE_RING_ALPHA = 0.35f

/**
 * Fokusring am angetippten Punkt und daneben ein senkrechter Belichtungsregler (Sonne).
 *
 * Der Regler verstellt die **Belichtungskorrektur** — dieselbe wie im Pro-Panel, es gibt nur
 * einen Wert. Gemessen wird dabei am angetippten Punkt: "diese Stelle soll heller/dunkler
 * werden" statt "das ganze Bild". Nach oben = heller, wie bei jeder Kamera-App.
 *
 * Nach [ACTIVE_MS] ohne Beruehrung tritt alles zurueck: der Regler verschwindet, der Ring
 * bleibt blass stehen — der Punkt ist ja weiter eingerastet, und der Sucher soll frei sein.
 *
 * @param exposure Bereich der Korrektur, oder `null`, wenn sie gerade nichts bewirkt
 *  (Objektiv ohne Korrektur, oder ISO/Verschlusszeit stehen im Pro-Modus auf manuell). Dann
 *  gibt es nur den Ring.
 */
@Composable
fun FocusIndicator(
    point: FocusPoint,
    state: FocusState,
    exposure: ExposureCompensation?,
    exposureIndex: Int,
    onExposureChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Jede Beruehrung (neuer Tipp, Ziehen am Regler) zaehlt die Ruhezeit neu an.
    var interaction by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf(true) }
    LaunchedEffect(point.id, interaction) {
        active = true
        delay(ACTIVE_MS)
        active = false
    }

    // Kurzes "Zuschnappen" bei jedem neuen Tipp.
    val snap = remember { Animatable(1f) }
    LaunchedEffect(point.id) {
        snap.snapTo(1.35f)
        snap.animateTo(1f, tween(220))
    }

    val ringColor by animateColorAsState(
        if (state == FocusState.FOCUSED) ACCENT else Color.White,
        animationSpec = tween(150),
    )
    val ringAlpha by animateFloatAsState(if (active) 1f else IDLE_RING_ALPHA, tween(400))
    val sliderAlpha by animateFloatAsState(if (active) 1f else 0f, tween(400))

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val boxWidth = with(density) { maxWidth.toPx() }
        val boxHeight = with(density) { maxHeight.toPx() }
        val ring = with(density) { RING_SIZE.toPx() }
        val track = with(density) { TRACK_HEIGHT.toPx() }
        val gap = with(density) { TRACK_GAP.toPx() }
        val sun = with(density) { SUN_SIZE.toPx() }

        val cx = point.x * boxWidth
        val cy = point.y * boxHeight

        Canvas(
            modifier = Modifier
                .offset { IntOffset((cx - ring / 2).roundToInt(), (cy - ring / 2).roundToInt()) }
                .size(RING_SIZE)
                .graphicsLayer {
                    scaleX = snap.value
                    scaleY = snap.value
                    alpha = ringAlpha
                },
        ) {
            val stroke = 1.5.dp.toPx()
            // Dunkler Saum wie beim Drittel-Raster, damit der Ring auch auf hellem Grund steht.
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = size.minDimension / 2 - stroke, style = Stroke(stroke * 2.5f))
            drawCircle(ringColor, radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
        }

        if (exposure != null && sliderAlpha > 0f) {
            val onLeft = sliderOnLeft(point.x)
            val trackX = if (onLeft) cx - ring / 2 - gap else cx + ring / 2 + gap
            // Senkrecht am Ring ausrichten, aber nie aus dem Vorschau-Rahmen ragen.
            val trackTop = (cy - track / 2).coerceIn(0f, (boxHeight - track).coerceAtLeast(0f))
            val fraction = exposureFraction(exposureIndex, exposure)
            val sunY = trackTop + (1f - fraction) * track
            val currentIndex by rememberUpdatedState(exposureIndex)
            val currentExposure by rememberUpdatedState(exposure)

            // Ziehflaeche: breiter als die Linie, damit der Daumen sie trifft.
            Box(
                modifier = Modifier
                    .offset { IntOffset((trackX - sun).roundToInt(), trackTop.roundToInt()) }
                    .size(SUN_SIZE * 2, TRACK_HEIGHT)
                    .graphicsLayer { alpha = sliderAlpha }
                    // Ein Tipp auf den Regler ist kein neuer Fokuspunkt — sonst sprange der
                    // Ring beim Verfehlen der Sonne um ein paar Millimeter weg.
                    .pointerInput(Unit) { detectTapGestures { interaction++ } }
                    .pointerInput(point.id) {
                        var startIndex = 0
                        var dragged = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                startIndex = currentIndex
                                dragged = 0f
                                interaction++
                            },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                dragged += dy
                                val next = evIndexForDrag(startIndex, dragged, track, currentExposure)
                                if (next != currentIndex) onExposureChange(next)
                                interaction++
                            },
                        )
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val x = size.width / 2
                    val sunCenterY = sunY - trackTop
                    val lineColor = Color.White.copy(alpha = 0.8f)
                    val lineStroke = 1.5.dp.toPx()
                    // Linie oberhalb und unterhalb der Sonne, mit Luecke um sie herum.
                    if (sunCenterY - sun * 0.7f > 0f) {
                        drawLine(lineColor, Offset(x, 0f), Offset(x, sunCenterY - sun * 0.7f), lineStroke)
                    }
                    if (sunCenterY + sun * 0.7f < size.height) {
                        drawLine(lineColor, Offset(x, sunCenterY + sun * 0.7f), Offset(x, size.height), lineStroke)
                    }
                    drawSun(Offset(x, sunCenterY), sun, if (exposureIndex != 0) ACCENT else Color.White)
                }
            }

            // Wert neben der Sonne, nur wenn er nicht 0 ist — "±0" waere nur Rauschen.
            if (exposureIndex != 0) {
                val label = formatEv(exposureIndex * exposure.stepEv)
                Text(
                    text = label,
                    color = ACCENT,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .offset {
                            val labelX = if (onLeft) trackX - sun * 3.2f else trackX + sun * 0.9f
                            IntOffset(labelX.roundToInt(), (sunY - sun / 2).roundToInt())
                        }
                        .graphicsLayer { alpha = sliderAlpha },
                )
            }
        }
    }
}

/** Sonnensymbol: Kreis mit acht Strahlen. */
private fun DrawScope.drawSun(center: Offset, size: Float, color: Color) {
    val stroke = size * 0.09f
    val core = size * 0.2f
    drawCircle(color, radius = core, center = center, style = Stroke(stroke))
    for (i in 0 until 8) {
        val angle = i * kotlin.math.PI / 4
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        drawLine(
            color,
            Offset(center.x + dx * core * 1.6f, center.y + dy * core * 1.6f),
            Offset(center.x + dx * size * 0.45f, center.y + dy * size * 0.45f),
            stroke,
        )
    }
}

/**
 * Neuer Korrektur-Index nach einem senkrechten Zug um [dragPx] Pixel (negativ = nach oben).
 *
 * Die ganze Laenge der Leiste ([trackPx]) entspricht dem ganzen Bereich — so ist der Regler
 * auf jedem Geraet gleich "schnell", egal ob es ±2 oder ±4 Blendenstufen erlaubt. Nach oben
 * ziehen = heller = hoeherer Index.
 */
internal fun evIndexForDrag(startIndex: Int, dragPx: Float, trackPx: Float, range: ExposureCompensation): Int {
    val span = range.maxIndex - range.minIndex
    val delta = -dragPx / trackPx * span
    return (startIndex + delta).roundToInt().coerceIn(range.minIndex, range.maxIndex)
}

/** Position der Sonne auf der Leiste: 0 = unten (dunkelste), 1 = oben (hellste Korrektur). */
internal fun exposureFraction(index: Int, range: ExposureCompensation): Float {
    val span = range.maxIndex - range.minIndex
    if (span <= 0) return 0.5f
    return (index - range.minIndex).toFloat() / span
}

/**
 * Der Regler sitzt rechts vom Ring — ausser der Punkt liegt so weit rechts, dass er aus dem
 * Bild ragen wuerde. Dann links.
 */
internal fun sliderOnLeft(x: Float): Boolean = x > 0.75f
