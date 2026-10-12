package com.florianhaeglsperger.photocoach.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.florianhaeglsperger.photocoach.capture.FrameAnalyzer
import com.florianhaeglsperger.photocoach.capture.STILL_ANALYSIS_MAX_DIMENSION
import com.florianhaeglsperger.photocoach.domain.scoring.CompositionAssessment
import com.florianhaeglsperger.photocoach.domain.scoring.CompositionScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Kantenlaenge, mit der die Vorschau geladen wird — reicht fuer den Kreis bei 3x-Dichte. */
const val THUMBNAIL_MAX_DIMENSION = 256

/**
 * Obergrenze fuer die Vollbild-Ansicht. Deckt 12-MP-Fotos (4000 px) in voller Aufloesung ab;
 * nur noch groessere Sensoren werden heruntergerechnet, damit das Bitmap zeichenbar bleibt.
 */
const val FULL_MAX_DIMENSION = 4096

/** Dauer des Ein-/Ausblendens der Bedienelemente per Tipp. */
private const val CONTROLS_FADE_MS = 180

private const val OPEN_DURATION_MS = 320

/** Loeschen: so lange schrumpft und verblasst das Foto, so lange waechst das naechste herein. */
private const val VANISH_OUT_MS = 220
private const val VANISH_IN_MS = 260
private const val VANISH_SHRINK = 0.12f

/** Anheben beim Loeschen: Dauer, Abstand zum Panel, Eckenradius, Panel-Hoehe vor dem Messen. */
private const val LIFT_MS = 260
private val LIFT_GAP = 16.dp
private val LIFT_TOP = 56.dp
private val LIFT_TOP_SIDEWAYS = 32.dp
private val LIFT_CORNER = 14.dp
private val LIFT_SHEET_FALLBACK = 220.dp

/** Wohin und wie gross das Foto beim Anheben ([liftedPlacement]). */
internal data class LiftedPlacement(val scale: Float, val offsetX: Float, val offsetY: Float)

/**
 * Das Foto beim Loeschen-Panel so verkleinern und verschieben, dass es **ganz** in [free]
 * steht — dem Bereich neben bzw. ueber dem Panel. Das Panel soll es nicht verdecken, darum
 * geht es ja. Nie vergroessern (Faktor hoechstens [LIFT_MAX_SCALE]), damit auch ein Foto,
 * das ohnehin schon passt, sichtbar "angehoben" wird.
 */
internal fun liftedPlacement(image: Rect, free: Rect): LiftedPlacement {
    val scale = minOf(
        LIFT_MAX_SCALE,
        free.width.coerceAtLeast(1f) / image.width,
        free.height.coerceAtLeast(1f) / image.height,
    ).coerceAtLeast(0.2f)
    // graphicsLayer skaliert um die Mitte des Fotos: neue Mitte = Mitte des freien Bereichs.
    return LiftedPlacement(
        scale = scale,
        offsetX = free.center.x - image.center.x,
        offsetY = free.center.y - image.center.y,
    )
}

private const val LIFT_MAX_SCALE = 0.92f
private const val CLOSE_DURATION_MS = 260

/** Ab dieser Vergroesserung zeigt Doppeltippen wieder das ganze Bild. */
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val MAX_ZOOM = 6f

/** Ab diesem Anteil der Bildschirmhoehe schliesst das Wegziehen auch ohne Schwung. */
private const val DISMISS_DISTANCE_FRACTION = 0.15f

/** Ab dieser Geschwindigkeit nach unten schliesst schon ein kurzes Schnippen. */
private val DISMISS_FLING_VELOCITY = 1000.dp

/** So weit schrumpft das Bild hoechstens, waehrend es weggezogen wird. */
private const val DISMISS_MAX_SHRINK = 0.3f

/**
 * Aufloesung der Nachbarseiten beim Blaettern: scharf auf jedem Handy-Bildschirm, aber nur
 * ein Bruchteil des Speichers eines vollen Fotos. Voll geladen wird erst die aktuelle Seite.
 */
const val PAGE_MAX_DIMENSION = 1600

/** Abstand zwischen zwei Seiten beim Blaettern — wie der Steg zwischen Dias. */
private val PAGE_GAP = 16.dp

/** Ab diesem Anteil der Breite rastet das Blaettern auch ohne Schwung ein. */
private const val PAGE_DISTANCE_FRACTION = 0.2f

/** Ab dieser Geschwindigkeit seitwaerts reicht schon ein kurzes Schnippen. */
private val PAGE_FLING_VELOCITY = 600.dp

private const val PAGE_SETTLE_MS = 220

/** Wie stark das Bild am Anfang/Ende der Reihe noch nachgibt (Anteil der Fingerbewegung). */
private const val EDGE_RESISTANCE = 0.3f

/**
 * Runde Vorschau des zuletzt aufgenommenen Fotos, links vom Ausloeser.
 *
 * Meldet ihre Lage auf dem Bildschirm ueber [onBounds] — von dort waechst der
 * [PhotoViewer] beim Oeffnen heraus. Kommt ein neues Foto dazu, "ploppt" sie kurz auf: so
 * sieht man aus dem Augenwinkel, dass die Aufnahme angekommen ist.
 */
@Composable
fun PhotoThumbnail(
    bitmap: ImageBitmap,
    onClick: () -> Unit,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(bitmap) {
        pop.snapTo(0.6f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    Image(
        bitmap = bitmap,
        contentDescription = "Letztes Foto öffnen",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(52.dp)
            .scale(pop.value)
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            .clip(CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .clickable(onClick = onClick),
    )
}

/**
 * Vollbild-Ansicht der App-Fotos in voller Aufloesung — geoeffnet auf [photo], mit Wischen
 * nach links/rechts durch alle aelteren und neueren Aufnahmen der App.
 *
 * **Uebergang:** Das Bild waechst aus der runden Vorschau ([origin]) an seine Vollbild-
 * Position; dabei oeffnet sich der Kreis zum Rechteck und der Hintergrund blendet nach
 * Schwarz. Beim Schliessen dasselbe rueckwaerts — aber nur fuer das Foto, das die Vorschau
 * auch zeigt. Wurde zu einem anderen geblaettert, waere das Zurueckschrumpfen in einen Kreis
 * mit fremdem Inhalt irritierend; dann blendet es zur Bildmitte hin aus.
 *
 * Der Trick, der das ohne Sprung macht: das Bild wird waehrend des ganzen Uebergangs mit
 * `ContentScale.Crop` in ein Rechteck gezeichnet, das zwischen Vorschau-Kreis und dem
 * Vollbild-Rechteck interpoliert. Am Ende hat das Rechteck exakt das Seitenverhaeltnis des
 * Fotos — dort ist "Crop" dasselbe wie "ganzes Bild", es fehlt also nichts.
 *
 * **Blaettern:** Neuestes Foto zuerst; der Finger nach links holt das naechstaeltere herein
 * (wie in jeder Galerie). Der Wechsel rastet ein, wenn weit genug gezogen oder geschnippt
 * wurde ([pageStep]); am Anfang und Ende der Reihe gibt das Bild nur gummiartig nach.
 * Vergroessert verschiebt derselbe Wisch den Ausschnitt statt zu blaettern.
 *
 * **Volle Qualitaet und Speicher:** Nur die aktuelle Seite wird in voller Aufloesung
 * gehalten. Die beiden Nachbarn liegen in Bildschirmaufloesung bereit
 * ([PAGE_MAX_DIMENSION]), damit sie beim Wischen sofort da sind — drei volle 12-MP-Bitmaps
 * waeren rund 150 MB. Bis die volle Aufloesung dekodiert ist, steht die kleinere Fassung an
 * ihrer Stelle, nichts wartet.
 *
 * **Loeschen:** Papierkorb in der Aktionsleiste unten, nach Rueckfrage im
 * [DeleteConfirmSheet]; das Foto schrumpft und verblasst, dann waechst das naechste herein. Danach steht das naechstaeltere
 * Foto da (gibt es keines, das neuere); war es das letzte, schliesst die Ansicht. Ueber
 * [onDeleted] erfaehrt der Aufrufer das neueste verbliebene Foto, damit die Vorschau im
 * Sucher nicht auf ein geloeschtes Bild zeigt.
 *
 * **Bewertung:** Score und Hinweis wie im Sucher ([ScoreOverlay]) ueber der Aktionsleiste —
 * aus derselben Analyse ([FrameAnalyzer.analyzeStill]) und demselben [CompositionScorer].
 * Ohne Horizont: dessen Neigung kommt im Sucher aus dem Lagesensor und ist im Foto nicht
 * gespeichert.
 *
 * **Teilen und Info:** Teilen in der Aktionsleiste unten ([ViewerActionBar]), Info oben
 * rechts ([InfoButton]). Info zeigt die Aufnahmedaten des angezeigten Fotos
 * ([PhotoInfoSheet], hebt das Foto an wie die Loeschen-Rueckfrage) und bleibt beim Blaettern
 * offen.
 *
 * **Schliessen:** X oben links, Zurueck-Taste, oder nach unten wegziehen (siehe
 * [shouldDismiss]); Wegziehen nur bei nicht vergroessertem Bild.
 */
@Composable
fun PhotoViewer(
    photo: CapturedPhoto,
    preview: ImageBitmap?,
    origin: Rect?,
    library: PhotoLibrary,
    onDeleted: (newest: CapturedPhoto?) -> Unit,
    onClosed: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val zoom = remember { Animatable(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    // Wie weit das Bild gerade nach unten weggezogen ist (Pixel). Bei 0 liegt es ruhig.
    var dismissY by remember { mutableFloatStateOf(0f) }
    // Horizontale Verschiebung beim Blaettern (Pixel), negativ = Richtung aeltere Fotos.
    val pageDrag = remember { Animatable(0f) }
    var windowOffset by remember { mutableStateOf(Offset.Zero) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Bis die Liste geladen ist, besteht sie nur aus dem geoeffneten Foto — die Ansicht
    // steht damit sofort, Blaettern kommt einen Augenblick spaeter dazu.
    var photos by remember { mutableStateOf(listOf(photo)) }
    var current by remember { mutableStateOf(photo) }
    val index = photos.indexOf(current).coerceAtLeast(0)

    val pageImages = remember { mutableStateMapOf<CapturedPhoto, ImageBitmap>() }
    val failed = remember { mutableStateMapOf<CapturedPhoto, Boolean>() }
    var full by remember { mutableStateOf<Pair<CapturedPhoto, ImageBitmap>?>(null) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(OPEN_DURATION_MS, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(library) {
        val all = library.all()
        // Das geoeffnete Foto muss drin sein, auch wenn der MediaStore es (noch) nicht listet.
        photos = if (photo in all) all else listOf(photo) + all
    }
    LaunchedEffect(current) {
        val loaded = library.load(current, FULL_MAX_DIMENSION)
        if (loaded != null) full = current to loaded else failed[current] = true
    }
    LaunchedEffect(index, photos) {
        // Nachbarn vorladen, alles ausserhalb von zwei Seiten Abstand wieder freigeben.
        val keep = photos.subList((index - 2).coerceAtLeast(0), (index + 3).coerceAtMost(photos.size))
        pageImages.keys.filter { it !in keep }.forEach { pageImages.remove(it) }
        for (i in listOf(index + 1, index - 1)) {
            val neighbour = photos.getOrNull(i) ?: continue
            if (neighbour in pageImages || failed[neighbour] == true) continue
            val loaded = library.load(neighbour, PAGE_MAX_DIMENSION)
            if (loaded != null) pageImages[neighbour] = loaded else failed[neighbour] = true
        }
    }

    fun bitmapFor(p: CapturedPhoto): ImageBitmap? =
        full?.takeIf { it.first == p }?.second
            ?: pageImages[p]
            ?: preview.takeIf { p == photo }

    // Ein Tipp aufs Bild blendet alle Bedienelemente aus (Bild pur), der naechste wieder ein.
    var controlsVisible by remember { mutableStateOf(true) }
    val chromeAlpha by animateFloatAsState(if (controlsVisible) 1f else 0f, tween(CONTROLS_FADE_MS))
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }
    var shareFailed by remember { mutableStateOf(false) }

    // Info-Panel ([PhotoInfoSheet]): bleibt beim Blaettern offen und laedt dann die Daten
    // des neuen Fotos.
    var infoOpen by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<Pair<CapturedPhoto, PhotoDetails?>?>(null) }
    LaunchedEffect(current, infoOpen) {
        if (infoOpen && details?.first != current) details = current to library.details(current)
    }
    // Bewertung wie im Sucher: dieselbe FrameAnalysis, derselbe CompositionScorer, dasselbe
    // ScoreOverlay. Pro Foto einmal gerechnet und gemerkt — beim Zurueckblaettern steht sie
    // sofort. Schluessel fehlt = laeuft noch; Wert null = Foto nicht lesbar, keine Leiste.
    val analyzer = remember { FrameAnalyzer() }
    DisposableEffect(analyzer) { onDispose { analyzer.close() } }
    val assessments = remember { mutableStateMapOf<CapturedPhoto, CompositionAssessment?>() }
    LaunchedEffect(current) {
        val photoToRate = current
        if (photoToRate in assessments) return@LaunchedEffect
        val image = library.load(photoToRate, STILL_ANALYSIS_MAX_DIMENSION)
        assessments[photoToRate] = image?.let {
            // Eigener Scorer pro Foto: der im Sucher haelt Zustand ueber Frames (Drittel-
            // Hysterese), den es zwischen zwei unabhaengigen Fotos nicht geben darf.
            withContext(Dispatchers.Default) { CompositionScorer().assess(analyzer.analyzeStill(it)) }
        }
    }

    var closeAfterDelete by remember { mutableStateOf(false) }
    // 0 = Foto steht normal, 1 = verschwunden. Traegt das Ausblenden beim Loeschen.
    val vanish = remember { Animatable(0f) }
    // 0 = Foto an seinem Platz, 1 = "angehoben": verkleinert ueber dem Loeschen-Panel. So
    // steht das Foto, um das es geht, ganz sichtbar ueber der Frage — und das Loeschen
    // setzt genau diese Bewegung fort (weiter schrumpfen, verblassen).
    val lift = remember { Animatable(0f) }
    var deleting by remember { mutableStateOf(false) }
    var sheetSize by remember { mutableStateOf(IntSize.Zero) }
    // Beide Panels (Loeschen, Info) heben das Foto auf dieselbe Weise an.
    val sheetOpen = confirmDelete || infoOpen
    LaunchedEffect(sheetOpen) {
        if (sheetOpen) {
            // Ein vergroesserter Ausschnitt wuerde beim Anheben nur halb zu sehen sein.
            launch { zoom.animateTo(1f, tween(LIFT_MS)) }
            pan = Offset.Zero
            lift.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        } else if (!deleting) {
            lift.animateTo(0f, tween(LIFT_MS, easing = FastOutSlowInEasing))
        }
    }

    fun deleteCurrent() {
        val doomed = current
        deleting = true
        scope.launch {
            if (!library.delete(doomed)) {
                deleteFailed = true
                deleting = false
                lift.animateTo(0f, tween(LIFT_MS, easing = FastOutSlowInEasing))
                return@launch
            }
            deleteFailed = false
            // Das geloeschte Foto schrumpft und verblasst, erst dann rueckt das naechste nach
            // (und waechst mit derselben Bewegung herein). Ein harter Wechsel liess offen, ob
            // ueberhaupt etwas passiert war — man sah ja wieder ein Foto.
            vanish.animateTo(1f, tween(VANISH_OUT_MS, easing = FastOutSlowInEasing))
            val remaining = photos - doomed
            pageImages.remove(doomed)
            failed.remove(doomed)
            if (full?.first == doomed) full = null
            onDeleted(remaining.firstOrNull())
            if (remaining.isEmpty()) {
                closeAfterDelete = true
            } else {
                // An derselben Stelle weiter: das naechstaeltere Foto rueckt nach, am Ende
                // der Reihe das neuere.
                current = remaining[index.coerceAtMost(remaining.lastIndex)]
                photos = remaining
                zoom.snapTo(1f)
                pan = Offset.Zero
                // Das naechste Foto waechst an seinem normalen Platz herein, nicht angehoben.
                lift.snapTo(0f)
                deleting = false
                vanish.animateTo(0f, tween(VANISH_IN_MS, easing = FastOutSlowInEasing))
            }
        }
    }

    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                // Erst zurueck auf das ganze Bild, sonst schrumpft ein Ausschnitt in den Kreis.
                launch { zoom.animateTo(1f, tween(CLOSE_DURATION_MS)) }
                launch { pageDrag.animateTo(0f, tween(CLOSE_DURATION_MS)) }
                // Von dort, wo der Finger das Bild losgelassen hat, zurueck — nicht erst nach
                // oben in die Mitte springen.
                launch {
                    animate(dismissY, 0f, animationSpec = tween(CLOSE_DURATION_MS)) { value, _ ->
                        dismissY = value
                    }
                }
                pan = Offset.Zero
                progress.animateTo(0f, tween(CLOSE_DURATION_MS, easing = FastOutSlowInEasing))
                onClosed()
            }
        }
    }
    LaunchedEffect(closeAfterDelete) { if (closeAfterDelete) close() }
    PlatformBackHandler(enabled = true, onBack = close)

    /**
     * Blaettern, waehrend ein Panel offen ist (Info): die Seitenleiste mit Nachbarn steht
     * dann nicht zur Verfuegung, das Foto ist ja angehoben. Stattdessen dieselbe kurze Geste
     * wie beim Loeschen — Foto schrumpft und verblasst, das naechste waechst angehoben herein.
     */
    fun stepWhileLifted(step: Int) {
        val target = photos.getOrNull(photos.indexOf(current) + step) ?: return
        scope.launch {
            vanish.animateTo(1f, tween(VANISH_OUT_MS / 2, easing = FastOutSlowInEasing))
            current = target
            zoom.snapTo(1f)
            pan = Offset.Zero
            vanish.animateTo(0f, tween(VANISH_IN_MS, easing = FastOutSlowInEasing))
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { windowOffset = it.positionInWindow() },
    ) {
        // Bedienelemente: per Tipp ausblendbar (chromeAlpha) und waehrend der Loeschen-Frage
        // ausgeblendet — dann sind sie nicht bedienbar und lenken vom Foto ab. Nach dem
        // Loeschen kommen sie zusammen mit dem naechsten Foto zurueck (vanish), statt
        // schlagartig vor dem noch dunklen Bild zu stehen.
        val chrome = chromeAlpha * (1f - max(lift.value, vanish.value))
        val density = LocalDensity.current
        val container = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }

        // Wohin das Foto beim Loeschen-Panel ausweicht: darueber (hoch) oder links daneben
        // (quer). Bevor das Panel einmal vermessen ist, mit einer Schaetzung.
        val sideways = container.width > container.height
        val liftFreeArea = with(density) {
            val gap = LIFT_GAP.toPx()
            if (sideways) {
                val sheetWidth = if (sheetSize.width > 0) sheetSize.width.toFloat() else SHEET_SIDE_WIDTH.toPx()
                Rect(gap, LIFT_TOP_SIDEWAYS.toPx(), container.width - sheetWidth - SHEET_SIDE_MARGIN.toPx() - gap, container.height - gap)
            } else {
                // Nicht TOP_CONTROLS_CLEARANCE: die Knoepfe oben sind waehrend der Frage
                // ausgeblendet, ihr Platz gehoert dem Foto.
                val sheetHeight = if (sheetSize.height > 0) sheetSize.height.toFloat() else LIFT_SHEET_FALLBACK.toPx()
                Rect(0f, LIFT_TOP.toPx(), container.width, container.height - sheetHeight - gap)
            }
        }
        val pageStride = container.width + with(density) { PAGE_GAP.toPx() }
        val currentBitmap = bitmapFor(current)
        val target = currentBitmap?.let { fittedRect(container, it.width.toFloat() / it.height) }
        val dismissFling = with(density) { DISMISS_FLING_VELOCITY.toPx() }
        val pageFling = with(density) { PAGE_FLING_VELOCITY.toPx() }

        // 0 = Bild liegt ruhig, 1 = halbe Bildschirmhoehe weggezogen. Steuert, wie weit der
        // Sucher dahinter schon wieder durchscheint und wie stark das Bild schrumpft.
        val dragFraction = (dismissY / (container.height / 2f)).coerceIn(0f, 1f)
        val hasNewer = index > 0
        val hasOlder = index < photos.lastIndex

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = progress.value * (1f - 0.85f * dragFraction)))
                // Die Gesten haengen an dieser festen Vollbild-Flaeche und nicht am Bild:
                // das Bild bewegt sich unter dem Finger mit, und in *seinen* Koordinaten
                // gemessen schrumpfte jede Bewegung um genau diese Mitbewegung — Ziehen und
                // Verschieben ruckelten und liefen dem Finger hinterher.
                .pointerInput(target, container, index, photos.size) {
                    if (target == null) return@pointerInput
                    viewerGestures(
                        isClosing = { closing },
                        zoom = { zoom.value },
                        onTransform = { centroid, panChange, zoomChange ->
                            val newZoom = (zoom.value * zoomChange).coerceIn(1f, MAX_ZOOM)
                            val zoomed = panForZoomAround(pan, centroid - target.center, newZoom / zoom.value)
                            scope.launch { zoom.snapTo(newZoom) }
                            pan = clampPan(zoomed + panChange, target, container, newZoom)
                        },
                        onDismissDrag = { dy -> dismissY = (dismissY + dy).coerceAtLeast(0f) },
                        onDismissEnd = { velocityY ->
                            if (shouldDismiss(dismissY, velocityY, container.height, dismissFling)) {
                                close()
                            } else {
                                scope.launch {
                                    animate(dismissY, 0f, animationSpec = spring()) { value, _ -> dismissY = value }
                                }
                            }
                        },
                        onPageDrag = { dx ->
                            val next = pageDrag.value + dx
                            // Am Rand der Reihe nur gummiartig nachgeben: man spuert, dass es
                            // dort nicht weitergeht, statt gegen eine Wand zu wischen.
                            val atEdge = (next > 0f && !hasNewer) || (next < 0f && !hasOlder)
                            scope.launch { pageDrag.snapTo(pageDrag.value + if (atEdge) dx * EDGE_RESISTANCE else dx) }
                        },
                        onPageEnd = { velocityX ->
                            val step = pageStep(pageDrag.value, velocityX, container.width, pageFling, hasNewer, hasOlder)
                            scope.launch {
                                if (step == 0) {
                                    pageDrag.animateTo(0f, spring())
                                } else {
                                    pageDrag.animateTo(-step * pageStride, tween(PAGE_SETTLE_MS, easing = FastOutSlowInEasing))
                                    current = photos[index + step]
                                    pageDrag.snapTo(0f)
                                    zoom.snapTo(1f)
                                    pan = Offset.Zero
                                }
                            }
                        },
                    )
                }
                // Doppeltippen vergroessert, ein einzelner Tipp blendet die Bedienelemente
                // aus bzw. wieder ein; Tipps landen hier und gehen nicht zum Sucher darunter
                // durch.
                .pointerInput(target) {
                    if (target == null) return@pointerInput
                    detectTapGestures(
                        onTap = { if (!closing) controlsVisible = !controlsVisible },
                        onDoubleTap = { tap ->
                            if (closing) return@detectTapGestures
                            val zoomIn = zoom.value < DOUBLE_TAP_ZOOM * 0.9f
                            scope.launch {
                                zoom.animateTo(if (zoomIn) DOUBLE_TAP_ZOOM else 1f, tween(220))
                            }
                            // Die angetippte Stelle bleibt unter dem Finger, statt dass immer
                            // zur Bildmitte vergroessert wird.
                            pan = if (zoomIn) {
                                clampPan(
                                    panForZoomAround(Offset.Zero, tap - target.center, DOUBLE_TAP_ZOOM),
                                    target,
                                    container,
                                    DOUBLE_TAP_ZOOM,
                                )
                            } else {
                                Offset.Zero
                            }
                        },
                    )
                },
        ) {
            for (i in (index - 1).coerceAtLeast(0)..(index + 1).coerceAtMost(photos.lastIndex)) {
                val pagePhoto = photos[i]
                val shiftX = (i - index) * pageStride + pageDrag.value
                val bitmap = bitmapFor(pagePhoto)

                if (bitmap == null) {
                    if (failed[pagePhoto] == true) {
                        Text(
                            text = "Foto nicht mehr verfügbar",
                            color = Color.White.copy(alpha = progress.value),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .graphicsLayer { translationX = shiftX },
                        )
                    }
                    continue
                }

                val pageTarget = fittedRect(container, bitmap.width.toFloat() / bitmap.height)
                if (i != index) {
                    // Nachbarseite: ruht auf ihrer Vollbild-Position, nur seitlich versetzt.
                    PageImage(
                        bitmap = bitmap,
                        rect = pageTarget,
                        cornerPx = 0f,
                        modifier = Modifier.graphicsLayer {
                            translationX = shiftX
                            alpha = progress.value
                        },
                    )
                    continue
                }

                // Aktuelle Seite: traegt Uebergang, Zoom, Verschieben und Wegziehen.
                val thumbnailRect = origin?.takeIf { pagePhoto == photo }?.translate(-windowOffset)
                val growsFromThumbnail = thumbnailRect != null
                val start = thumbnailRect ?: pageTarget.scaledAroundCenter(0.85f)
                val rect = lerp(start, pageTarget, progress.value)
                val dragScale = 1f - DISMISS_MAX_SHRINK * dragFraction
                val lifted = liftedPlacement(image = pageTarget, free = liftFreeArea)
                val liftCornerPx = with(density) { LIFT_CORNER.toPx() } / lifted.scale
                PageImage(
                    bitmap = bitmap,
                    rect = rect,
                    cornerPx = max(
                        if (growsFromThumbnail) (start.minDimension / 2f) * (1f - progress.value) else 0f,
                        liftCornerPx * lift.value,
                    ),
                    modifier = Modifier.graphicsLayer {
                        val vanishScale = 1f - VANISH_SHRINK * vanish.value
                        val liftScale = 1f - (1f - lifted.scale) * lift.value
                        scaleX = zoom.value * dragScale * vanishScale * liftScale
                        scaleY = zoom.value * dragScale * vanishScale * liftScale
                        translationX = pan.x + shiftX + lifted.offsetX * lift.value
                        translationY = pan.y + dismissY + lifted.offsetY * lift.value
                        // Aus dem Kreis heraus blendet das Bild nur teilweise ein — das wirkt
                        // weicher als ein hartes Erscheinen. Ohne Kreis ganz.
                        alpha = (if (growsFromThumbnail) 0.4f + 0.6f * progress.value else progress.value) *
                            (1f - vanish.value)
                    },
                )
            }
        }

        // Ausgeblendete Elemente werden gar nicht erst gezeichnet — Alpha 0 allein liesse
        // sie weiter antippbar.
        if (chrome > 0.01f) {
            if (photos.size > 1) {
                Text(
                    text = "${index + 1} / ${photos.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 58.dp)
                        .graphicsLayer { alpha = progress.value * (1f - dragFraction) * chrome },
                )
            }

            val controlsAlpha = progress.value * (1f - dragFraction) * chrome

            InfoButton(
                open = infoOpen,
                onClick = { infoOpen = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 48.dp)
                    .graphicsLayer { alpha = controlsAlpha },
            )

            if (current in assessments) {
                assessments[current]?.let { assessment ->
                    ScoreOverlay(
                        verdict = assessment.verdict,
                        score = assessment.score,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = ACTION_BAR_CLEARANCE)
                            .graphicsLayer { alpha = controlsAlpha },
                    )
                }
            } else {
                PhotoScorePending(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = ACTION_BAR_CLEARANCE)
                        .graphicsLayer { alpha = controlsAlpha },
                )
            }

            ViewerActionBar(
                onShare = {
                    deleteFailed = false
                    shareFailed = !library.share(current)
                },
                onDelete = { deleteFailed = false; shareFailed = false; confirmDelete = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .graphicsLayer { alpha = controlsAlpha },
            )

        }

        if (deleteFailed || shareFailed) {
            Text(
                text = if (deleteFailed) "Löschen nicht möglich" else "Teilen nicht möglich",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = SCORE_BAR_CLEARANCE),
            )
        }

        if (chrome > 0.01f) {
            CloseButton(
                onClick = close,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 48.dp)
                    .graphicsLayer { alpha = progress.value * (1f - dragFraction) * chrome },
            )
        }

        // Zuletzt, damit es ueber allem liegt — auch ueber dem X oben links.
        DeleteConfirmSheet(
            visible = confirmDelete,
            sideways = sideways,
            onSizeChanged = { sheetSize = it },
            onConfirm = { confirmDelete = false; deleteCurrent() },
            onDismiss = { confirmDelete = false },
        )
        PhotoInfoSheet(
            visible = infoOpen,
            sideways = sideways,
            details = details?.takeIf { it.first == current }?.second,
            loading = details?.first != current,
            position = if (photos.size > 1) "${index + 1} / ${photos.size}" else null,
            onSizeChanged = { sheetSize = it },
            onSwipe = ::stepWhileLifted,
            onDismiss = { infoOpen = false },
        )
    }
}

/** Ein Foto, mit `Crop` in [rect] gezeichnet und mit [cornerPx] abgerundet. */
@Composable
private fun PageImage(bitmap: ImageBitmap, rect: Rect, cornerPx: Float, modifier: Modifier) {
    val density = LocalDensity.current
    Image(
        bitmap = bitmap,
        contentDescription = "Foto",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .then(modifier)
            .clip(RoundedCornerShape(with(density) { cornerPx.toDp() })),
    )
}

/**
 * Eine Geste in der Vollbild-Ansicht. Drei Moeglichkeiten:
 *  - **Wegziehen** — ein Finger nach unten, Bild nicht vergroessert
 *  - **Blaettern** — ein Finger seitwaerts, Bild nicht vergroessert
 *  - **Zoomen/Verschieben** — alles andere
 *
 * Welche, entscheidet sich einmal pro Geste, sobald der Finger die Beruehrungs-Toleranz
 * verlassen hat — und bleibt dann so. Sonst kippte ein leicht schraeger Wisch auf halbem
 * Weg von einer Bewegung in die andere.
 *
 * Warum nicht `detectTransformGestures` bzw. ein `HorizontalPager`: Ersteres meldet kein
 * Ende der Geste, das Wegziehen und Blaettern aber brauchen, um beim Loslassen zu
 * entscheiden. Ein Pager haette mit Zoom und Wegziehen um dieselben Wische konkurriert.
 */
private suspend fun PointerInputScope.viewerGestures(
    isClosing: () -> Boolean,
    zoom: () -> Float,
    onTransform: (centroid: Offset, pan: Offset, zoomChange: Float) -> Unit,
    onDismissDrag: (dy: Float) -> Unit,
    onDismissEnd: (velocityY: Float) -> Unit,
    onPageDrag: (dx: Float) -> Unit,
    onPageEnd: (velocityX: Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var mode = GestureMode.UNDECIDED
        var travelled = Offset.Zero

        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.none { it.pressed }) break
            if (isClosing()) continue

            event.changes.firstOrNull { it.id == down.id }?.let { tracker.addPosition(it.uptimeMillis, it.position) }
            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            val fingers = event.changes.count { it.pressed }

            if (mode == GestureMode.UNDECIDED) {
                travelled += panChange
                val unzoomed = zoom() <= 1.01f
                mode = when {
                    fingers > 1 || zoomChange != 1f -> GestureMode.TRANSFORM
                    travelled.getDistance() < viewConfiguration.touchSlop -> GestureMode.UNDECIDED
                    unzoomed && travelled.y > abs(travelled.x) -> GestureMode.DISMISS
                    unzoomed && abs(travelled.x) > abs(travelled.y) -> GestureMode.PAGE
                    else -> GestureMode.TRANSFORM
                }
            }

            when (mode) {
                GestureMode.DISMISS -> onDismissDrag(panChange.y)
                GestureMode.PAGE -> onPageDrag(panChange.x)
                GestureMode.TRANSFORM -> onTransform(event.calculateCentroid(), panChange, zoomChange)
                GestureMode.UNDECIDED -> Unit
            }
            if (mode != GestureMode.UNDECIDED) event.changes.forEach { it.consume() }
        }

        when (mode) {
            GestureMode.DISMISS -> onDismissEnd(tracker.calculateVelocity().y)
            GestureMode.PAGE -> onPageEnd(tracker.calculateVelocity().x)
            else -> Unit
        }
    }
}

private enum class GestureMode { UNDECIDED, DISMISS, PAGE, TRANSFORM }

/**
 * Schliesst das Wegziehen das Foto? Ja, wenn es weit genug gezogen wurde — oder kurz, aber
 * schwungvoll nach unten geschnippt. Ein kleiner, langsamer Zug federt zurueck.
 */
internal fun shouldDismiss(
    dragY: Float,
    velocityY: Float,
    containerHeight: Float,
    flingVelocity: Float,
): Boolean = dragY > containerHeight * DISMISS_DISTANCE_FRACTION ||
    (velocityY > flingVelocity && dragY > 0f)

/**
 * Wohin rastet das Blaettern beim Loslassen ein?
 *
 * @return `+1` = eine Seite weiter zum aelteren Foto (Finger nach links gezogen), `-1` =
 *  zum neueren, `0` = zurueck auf die aktuelle Seite.
 *
 * Einrasten, wenn weit genug gezogen ([PAGE_DISTANCE_FRACTION] der Breite) oder in dieselbe
 * Richtung geschnippt — und nur, wenn es in der Richtung ueberhaupt ein Foto gibt.
 */
internal fun pageStep(
    dragX: Float,
    velocityX: Float,
    pageWidth: Float,
    flingVelocity: Float,
    hasNewer: Boolean,
    hasOlder: Boolean,
): Int {
    val towardsOlder = dragX < -pageWidth * PAGE_DISTANCE_FRACTION || (velocityX < -flingVelocity && dragX < 0f)
    val towardsNewer = dragX > pageWidth * PAGE_DISTANCE_FRACTION || (velocityX > flingVelocity && dragX > 0f)
    return when {
        towardsOlder && hasOlder -> 1
        towardsNewer && hasNewer -> -1
        else -> 0
    }
}

/**
 * Neue Verschiebung, wenn um den Faktor [zoomFactor] um einen Punkt vergroessert wird, der
 * [focusFromCenter] von der Bildmitte entfernt liegt — so, dass dieser Punkt unter dem
 * Finger stehen bleibt.
 *
 * Herleitung: Ein Bildpunkt erscheint bei `mitte + abstand * zoom + pan`. Soll er nach dem
 * Zoomen an derselben Stelle bleiben, ergibt sich
 * `neuesPan = fokus * (1 - faktor) + pan * faktor`.
 */
internal fun panForZoomAround(pan: Offset, focusFromCenter: Offset, zoomFactor: Float): Offset =
    focusFromCenter * (1f - zoomFactor) + pan * zoomFactor

/** Groesstes Rechteck mit [aspect] (Breite/Hoehe), das mittig in [container] passt. */
internal fun fittedRect(container: Size, aspect: Float): Rect {
    val width = min(container.width, container.height * aspect)
    val height = width / aspect
    val left = (container.width - width) / 2f
    val top = (container.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/**
 * Begrenzt die Verschiebung so, dass beim Vergroessern kein schwarzer Rand ins Bild
 * gezogen werden kann — die Bildkante stoesst hoechstens an die Bildschirmkante.
 */
internal fun clampPan(pan: Offset, image: Rect, container: Size, zoom: Float): Offset {
    val maxX = max(0f, (image.width * zoom - container.width) / 2f)
    val maxY = max(0f, (image.height * zoom - container.height) / 2f)
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

private fun Rect.scaledAroundCenter(factor: Float): Rect {
    val c = center
    val w = width * factor / 2f
    val h = height * factor / 2f
    return Rect(c.x - w, c.y - h, c.x + w, c.y + h)
}

/** Schliessen-Knopf: ein X auf dunklem Kreis, von Hand gezeichnet wie die anderen Symbole. */
@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val stroke = size.width * 0.14f
            drawLine(Color.White, Offset.Zero, Offset(size.width, size.height), stroke)
            drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), stroke)
        }
    }
}
