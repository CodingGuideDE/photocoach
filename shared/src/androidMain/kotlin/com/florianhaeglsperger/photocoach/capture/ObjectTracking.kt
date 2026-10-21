package com.florianhaeglsperger.photocoach.capture

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Verfolgt ein angetipptes Objekt ueber die Kamerabilder — die einfachste moegliche Form:
 * jedes Mal, wenn das Modell frei ist, das neueste Bild segmentieren, und zwar am
 * **Schwerpunkt des zuletzt erkannten Objekts** ("Re-Prompting"). Bewegt sich Kamera oder
 * Objekt langsam genug, liegt der alte Schwerpunkt noch im Objekt, und das Modell findet es
 * wieder.
 *
 * Jede Erkennung geht durch das [TrackingGate]: verliert der Punkt das Objekt und das Modell
 * markiert stattdessen den Hintergrund (oft fast das ganze Bild), wird das verworfen, der alte
 * Rahmen bleibt stehen und es wird an der alten Stelle weitergesucht; erst nach mehreren
 * Fehlversuchen in Folge gilt das Objekt als verloren.
 *
 * Noch ohne Gyro-Vorhersage oder Zwischen-Tracker (siehe CLAUDE.md "Tipp-Segmentierung").
 *
 * Threads: [start]/[stop] vom Main-Thread, [offerFrame] vom Analyse-Thread, die Erkennung
 * selbst auf dem Segmentierungs-Thread. [generation] trennt Ergebnisse einer alten Auswahl
 * von der aktuellen.
 */
internal class ObjectTracking {

    /** Wo als Naechstes segmentiert wird (normiert, Vorschau-Koordinaten), oder `null` = aus. */
    @Volatile
    private var prompt: Pair<Float, Float>? = null

    /** Zaehlt jede neue Auswahl und jedes Aufheben hoch; nur der Main-Thread schreibt. */
    @Volatile
    var generation: Int = 0
        private set

    private val busy = AtomicBoolean(false)

    // Nur auf dem Segmentierungs-Thread benutzt. Statt es bei start()/stop() vom Main-Thread
    // aus zurueckzusetzen (Wettlauf mit einer laufenden Erkennung), setzt es sich selbst
    // zurueck, sobald es eine neue Generation sieht.
    private val gate = TrackingGate()
    private var gateGeneration = -1

    fun start(x: Float, y: Float) {
        generation++
        prompt = x to y
    }

    fun stop() {
        generation++
        prompt = null
    }

    /**
     * Bietet ein Kamerabild an. Ist eine Auswahl aktiv und das Modell frei, wird das Bild
     * kopiert (das [ImageProxy] ist nur waehrend dieses Aufrufs gueltig) und auf [executor]
     * segmentiert; sonst passiert nichts.
     */
    fun offerFrame(
        imageProxy: ImageProxy,
        mirrored: Boolean,
        executor: Executor,
        segmenter: ObjectSegmenter,
        deliver: (generation: Int, selection: ObjectSelection) -> Unit,
    ) {
        val point = prompt ?: return
        if (!busy.compareAndSet(false, true)) return
        val gen = generation
        val bitmap = try {
            imageProxy.toUprightBitmap(mirrored)
        } catch (_: Exception) {
            busy.set(false)
            return
        }
        try {
            executor.execute {
                try {
                    val result = segmenter.segment(bitmap, point.first, point.second)
                    if (result == null) {
                        if (generation == gen) prompt = null
                        deliver(gen, ObjectSelection.Unavailable(segmenter.unavailableReason ?: "Segmentierung nicht verfügbar"))
                        return@execute
                    }
                    // Ergebnis einer inzwischen abgeloesten Auswahl: weder pruefen noch melden.
                    if (generation != gen) return@execute
                    if (gateGeneration != gen) {
                        gate.reset()
                        gateGeneration = gen
                    }
                    when (val decision = gate.judge(result.obj)) {
                        is TrackDecision.Accept -> {
                            // Weiter am Schwerpunkt des (plausiblen) Objekts.
                            prompt = decision.obj.centerX to decision.obj.centerY
                            deliver(gen, ObjectSelection.Found(decision.obj, result.inferenceMs))
                        }
                        // Verworfen: nichts melden — der zuletzt gute Rahmen bleibt stehen, und
                        // der Punkt bleibt, wo er war (alter Schwerpunkt bzw. der Tipp selbst).
                        is TrackDecision.Hold -> Unit
                        is TrackDecision.Lose -> {
                            prompt = null
                            deliver(gen, ObjectSelection.Lost(result.inferenceMs))
                        }
                    }
                } finally {
                    bitmap.recycle()
                    busy.set(false)
                }
            }
        } catch (_: RejectedExecutionException) {
            // Executor schon heruntergefahren (Bildschirm verlassen).
            bitmap.recycle()
            busy.set(false)
        }
    }
}

/**
 * Das Kamerabild als Bitmap, so gedreht und gespiegelt, wie die Vorschau es zeigt — dann
 * gelten die Tipp-Koordinaten unveraendert, und das Ergebnis passt direkt auf die Vorschau.
 */
private fun ImageProxy.toUprightBitmap(mirrored: Boolean): Bitmap {
    val raw = toBitmap()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0 && !mirrored) return raw
    val matrix = Matrix().apply {
        postRotate(rotation.toFloat())
        if (mirrored) postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
        if (it !== raw) raw.recycle()
    }
}
