package com.florianhaeglsperger.photocoach.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Flaeche der Panels — eine Spur heller als die Panels im Sucher, damit sie sich abheben. */
internal val SHEET_SURFACE = Color(0xFF1C1C1E)

/** Querformat: Breite des Panels und sein Abstand zum rechten Rand. */
internal val SHEET_SIDE_WIDTH = 360.dp
internal val SHEET_SIDE_MARGIN = 16.dp

/** Ab dieser Zugstrecke (nach unten bzw. rechts) schliesst das Panel beim Loslassen. */
private val DISMISS_DRAG = 96.dp

/** Ab dieser Wischstrecke neben dem Panel wird geblaettert statt abgebrochen. */
private val SWIPE_DISTANCE = 48.dp

/**
 * Gemeinsamer Rahmen fuer die Panels der Vollbild-Ansicht ([DeleteConfirmSheet],
 * [PhotoInfoSheet]): faehrt unten aus der Aktionsleiste hoch, im Querformat ([sideways])
 * rechts angedockt. Der Viewer hebt waehrenddessen das Foto in den freien Bereich daneben
 * an — dafuer meldet [onSizeChanged] die Groesse.
 *
 * **Keine Abdunklung:** das Foto ist der Gegenstand und soll hell bleiben. Der Bereich
 * neben dem Panel faengt trotzdem Beruehrungen ab: ein Tipp schliesst ([onDismiss]), ein
 * seitlicher Wisch blaettert ([onSwipe], +1 = aelteres Foto), falls angegeben.
 *
 * Schliessen ausserdem: wegziehen (nach unten bzw. rechts) und Zurueck.
 */
@Composable
internal fun BoxScope.ViewerSheet(
    visible: Boolean,
    sideways: Boolean,
    onSizeChanged: (IntSize) -> Unit,
    onDismiss: () -> Unit,
    onSwipe: ((step: Int) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PlatformBackHandler(enabled = visible, onBack = onDismiss)

    val density = LocalDensity.current
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(180)),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val swipePx = SWIPE_DISTANCE.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var travel = Offset.Zero
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.id == down.id) travel += change.position - change.previousPosition
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                        val swipe = currentOnSwipe
                        when {
                            swipe != null && abs(travel.x) > swipePx && abs(travel.x) > abs(travel.y) ->
                                // Finger nach links holt — wie beim Blaettern sonst — das aeltere Foto.
                                swipe(if (travel.x < 0) 1 else -1)
                            travel.getDistance() < viewConfiguration.touchSlop -> currentOnDismiss()
                        }
                    }
                },
        )
    }

    val dragOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible) { if (visible) dragOffset.snapTo(0f) }

    val slideSpec = spring<IntOffset>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    AnimatedVisibility(
        visible = visible,
        enter = (if (sideways) slideInHorizontally(slideSpec) { it } else slideInVertically(slideSpec) { it }) +
            fadeIn(tween(120)),
        exit = (if (sideways) slideOutHorizontally(tween(200)) { it } else slideOutVertically(tween(200)) { it }) +
            fadeOut(tween(200)),
        modifier = if (sideways) {
            Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = SHEET_SIDE_MARGIN)
        } else {
            Modifier.align(Alignment.BottomCenter)
        },
    ) {
        Column(
            modifier = (if (sideways) Modifier.width(SHEET_SIDE_WIDTH) else Modifier.widthIn(max = 560.dp).fillMaxWidth())
                .onSizeChanged(onSizeChanged)
                .graphicsLayer {
                    if (sideways) translationX = dragOffset.value else translationY = dragOffset.value
                }
                .clip(if (sideways) RoundedCornerShape(24.dp) else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(SHEET_SURFACE)
                // Tipps auf das Panel selbst gehen nicht an den Bereich dahinter durch.
                .pointerInput(Unit) { detectTapGestures { } }
                .draggable(
                    orientation = if (sideways) Orientation.Horizontal else Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f)) }
                    },
                    onDragStopped = { velocity ->
                        if (dragOffset.value > with(density) { DISMISS_DRAG.toPx() } || velocity > 1500f) {
                            onDismiss()
                        } else {
                            dragOffset.animateTo(0f, spring())
                        }
                    },
                )
                .then(if (sideways) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                // Wechselt der Inhalt seine Hoehe (Info: erst Ladeanzeige, dann Daten), waechst
                // das Panel weich mit — und das angehobene Foto gleitet mit, statt zu springen.
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                .padding(start = 20.dp, end = 20.dp, top = if (sideways) 22.dp else 10.dp, bottom = 8.dp),
        ) {
            if (!sideways) {
                // Griff — zeigt, dass sich das Panel wegziehen laesst.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.22f)),
                )
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}
