package com.florianhaeglsperger.photocoach.domain.model

/**
 * Ergebnis einer einzelnen Frame-Analyse. Wird von [com.florianhaeglsperger.photocoach.capture.FrameAnalyzer]
 * produziert und ist der einzige Kontaktpunkt zwischen den plattformspezifischen
 * Vision-Implementierungen (iOS: Vision framework, Android: CameraX + ML Kit) und der
 * gemeinsamen domain-/UI-Logik. Siehe CLAUDE.md "Core cross-platform contract".
 */
data class FrameAnalysis(
    val horizonTiltDegrees: Float?,
    val saliencyRegions: List<SaliencyPoint>,
    val faces: List<FaceRect>,
    val timestampMs: Long,
)

/** Normalisierter Punkt (0.0–1.0) im Kamerabild mit Aufmerksamkeits-Gewicht. */
data class SaliencyPoint(
    val x: Float,
    val y: Float,
    val weight: Float,
)

/** Normalisiertes Rechteck (0.0–1.0) fuer ein erkanntes Gesicht. */
data class FaceRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
