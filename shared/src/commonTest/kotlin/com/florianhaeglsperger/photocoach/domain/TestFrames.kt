package com.florianhaeglsperger.photocoach.domain

import com.florianhaeglsperger.photocoach.domain.model.FaceRect
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis
import com.florianhaeglsperger.photocoach.domain.model.SaliencyPoint

/**
 * Bausteine, um `FrameAnalysis`-Situationen fuer Regel-Tests zu beschreiben.
 *
 * Der Sinn: eine Regel in `domain/rules/` ist eine pure Funktion von [FrameAnalysis] auf
 * einen Hinweis. Sie laesst sich damit vollstaendig ohne Kamera, ohne Emulator und in
 * Millisekunden testen — man beschreibt die Situation, prueft das Ergebnis. Diese Helfer
 * sorgen dafuer, dass in einem Test nur das steht, worum es in dem Test geht.
 *
 * Statt:
 * ```
 * FrameAnalysis(null, listOf(SaliencyPoint(0.33f, 0.5f, 1f)), emptyList(), 0L)
 * ```
 * lieber:
 * ```
 * frame(saliency = listOf(subjectAt(THIRD_LEFT, 0.5f)))
 * ```
 */

/** Die vier Drittel-Linien in normierten Koordinaten. */
const val THIRD_LEFT = 1f / 3f
const val THIRD_RIGHT = 2f / 3f
const val THIRD_TOP = 1f / 3f
const val THIRD_BOTTOM = 2f / 3f

/** Bildmitte. */
const val CENTER = 0.5f

/**
 * Baut ein [FrameAnalysis]. Alle Felder haben neutrale Vorgaben — im Test wird nur
 * benannt, was fuer den Test relevant ist.
 */
fun frame(
    tiltDegrees: Float? = null,
    saliency: List<SaliencyPoint> = emptyList(),
    faces: List<FaceRect> = emptyList(),
    timestampMs: Long = 0L,
): FrameAnalysis = FrameAnalysis(
    horizonTiltDegrees = tiltDegrees,
    saliencyRegions = saliency,
    faces = faces,
    timestampMs = timestampMs,
)

/** Ein Aufmerksamkeits-Schwerpunkt an einer bestimmten Stelle. */
fun subjectAt(x: Float, y: Float, weight: Float = 1f): SaliencyPoint =
    SaliencyPoint(x = x, y = y, weight = weight)

/**
 * Ein Gesicht, beschrieben ueber Mittelpunkt und Groesse statt ueber vier Kanten —
 * so denkt man beim Formulieren eines Testfalls ("Gesicht oben links, klein").
 */
fun faceAt(
    centerX: Float,
    centerY: Float,
    size: Float = 0.2f,
): FaceRect = FaceRect(
    left = centerX - size / 2f,
    top = centerY - size / 2f,
    right = centerX + size / 2f,
    bottom = centerY + size / 2f,
)
