package com.florianhaeglsperger.photocoach.capture

import kotlin.math.max
import kotlin.math.min

/**
 * Das angetippte Objekt, wie die Tipp-Segmentierung es erkannt hat. Alle Koordinaten
 * normiert (0..1) auf das **Vorschaubild**, also so, wie der Nutzer es sieht (bei der
 * Frontkamera gespiegelt).
 */
data class SegmentedObject(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Schwerpunkt der Objektflaeche — liegt sicher *im* Objekt, anders als die Rahmenmitte. */
    val centerX: Float,
    val centerY: Float,
    /** Anteil des Bildes, den das Objekt bedeckt (0..1). */
    val areaFraction: Float,
)

/** Zustand der Tipp-Auswahl, wie die Plattform ihn nach oben meldet. */
sealed interface ObjectSelection {
    /** Angetippt, die erste Erkennung laeuft noch. */
    data class Searching(val x: Float, val y: Float) : ObjectSelection

    /** Erkannt. [inferenceMs] = Dauer der letzten Erkennung (Testhilfe: wie schnell ist das Modell?). */
    data class Found(val obj: SegmentedObject, val inferenceMs: Long) : ObjectSelection

    /** Unter dem Tipp (bzw. dem nachgefuehrten Punkt) ist nichts mehr erkennbar. */
    data class Lost(val inferenceMs: Long) : ObjectSelection

    /** Segmentierung nicht moeglich (Modell fehlt, Plattform ohne Unterstuetzung). */
    data class Unavailable(val reason: String) : ObjectSelection
}

/** Ab diesem Konfidenzwert gilt ein Maskenpixel als "gehoert zum Objekt". */
const val MASK_THRESHOLD = 0.5f

/**
 * Objekte unter diesem Flaechenanteil gelten als nicht erkannt — einzelne Pixel, die das
 * Modell zufaellig ueber die Schwelle hebt, sind kein Objekt.
 */
const val MIN_OBJECT_AREA = 0.002f

/**
 * Wertet eine Konfidenzmaske (zeilenweise, Werte 0..1) aus: die **zusammenhaengende** Flaeche
 * ueber [threshold], die den Punkt ([seedX], [seedY], normiert) enthaelt — oder, falls der
 * Punkt selbst knapp daneben liegt, die naechstgelegene.
 *
 * Warum nicht einfach alle Pixel ueber der Schwelle: Das Modell markiert gelegentlich
 * einzelne Flecken anderswo im Bild (Model Card: "may segment multiple objects ... located
 * very closely"). Ein solcher Fleck am Bildrand wuerde den Rahmen ueber das halbe Bild
 * aufziehen. Gemeint ist das Objekt *unter dem Finger*.
 *
 * `null`, wenn nichts Zusammenhaengendes ab [MIN_OBJECT_AREA] gefunden wird.
 */
fun regionAt(
    mask: FloatArray,
    width: Int,
    height: Int,
    seedX: Float,
    seedY: Float,
    threshold: Float = MASK_THRESHOLD,
): SegmentedObject? {
    require(mask.size >= width * height) { "Maske passt nicht zu $width x $height" }
    val sx = (seedX * width).toInt().coerceIn(0, width - 1)
    val sy = (seedY * height).toInt().coerceIn(0, height - 1)
    val start = nearestForeground(mask, width, height, sx, sy, threshold) ?: return null

    // Flood-Fill (4er-Nachbarschaft) mit eigenem Stapel — Rekursion liefe bei grossen
    // Objekten (hunderttausende Pixel) in einen Stack Overflow.
    val visited = BooleanArray(width * height)
    val stack = IntArray(width * height)
    var top = 0
    stack[top++] = start
    visited[start] = true
    var count = 0L
    var sumX = 0L
    var sumY = 0L
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    while (top > 0) {
        val i = stack[--top]
        val x = i % width
        val y = i / width
        count++
        sumX += x
        sumY += y
        minX = min(minX, x)
        maxX = max(maxX, x)
        minY = min(minY, y)
        maxY = max(maxY, y)
        if (x > 0) top = push(i - 1, mask, threshold, visited, stack, top)
        if (x < width - 1) top = push(i + 1, mask, threshold, visited, stack, top)
        if (y > 0) top = push(i - width, mask, threshold, visited, stack, top)
        if (y < height - 1) top = push(i + width, mask, threshold, visited, stack, top)
    }

    val area = count.toFloat() / (width * height)
    if (area < MIN_OBJECT_AREA) return null
    return SegmentedObject(
        left = minX.toFloat() / width,
        top = minY.toFloat() / height,
        right = (maxX + 1).toFloat() / width,
        bottom = (maxY + 1).toFloat() / height,
        centerX = (sumX.toFloat() / count + 0.5f) / width,
        centerY = (sumY.toFloat() / count + 0.5f) / height,
        areaFraction = area,
    )
}

private fun push(i: Int, mask: FloatArray, threshold: Float, visited: BooleanArray, stack: IntArray, top: Int): Int {
    if (visited[i] || mask[i] < threshold) return top
    visited[i] = true
    stack[top] = i
    return top + 1
}

/**
 * Der Vordergrundpixel, der dem Punkt am naechsten liegt — gesucht in wachsenden Quadraten
 * bis zu [SEED_SEARCH_RADIUS] des Bildes. Ein Tipp knapp neben eine duenne Kante soll das
 * Objekt trotzdem treffen; ein Tipp ins Leere soll *kein* weit entferntes Objekt waehlen.
 */
private fun nearestForeground(mask: FloatArray, width: Int, height: Int, sx: Int, sy: Int, threshold: Float): Int? {
    val maxR = (max(width, height) * SEED_SEARCH_RADIUS).toInt().coerceAtLeast(1)
    for (r in 0..maxR) {
        for (y in (sy - r)..(sy + r)) {
            if (y !in 0 until height) continue
            for (x in (sx - r)..(sx + r)) {
                if (x !in 0 until width) continue
                // Nur der Rand des Quadrats — das Innere wurde mit kleinerem r schon geprueft.
                if (r > 0 && y != sy - r && y != sy + r && x != sx - r && x != sx + r) continue
                if (mask[y * width + x] >= threshold) return y * width + x
            }
        }
    }
    return null
}

private const val SEED_SEARCH_RADIUS = 0.05f
