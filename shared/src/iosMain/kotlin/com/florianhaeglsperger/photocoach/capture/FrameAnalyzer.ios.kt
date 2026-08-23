package com.florianhaeglsperger.photocoach.capture

import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis

/**
 * Platzhalter fuer Phase 0, analog zu FrameAnalyzer.android.kt. Ab Phase 1 wird das hier
 * durch einen echten Frame-Typ ersetzt (z.B. ein Wrapper um CMSampleBuffer aus
 * AVCaptureVideoDataOutput) — siehe CLAUDE.md "Planned architecture".
 */
actual class CameraFrame

/**
 * iOS-Implementierung. Phase 0: liefert nur Dummy-Daten, damit der Datenfluss
 * Kamera -> FrameAnalyzer -> UI durchgaengig steht. Ab Phase 1 hier AVFoundation
 * (AVCaptureSession) + Vision (VNDetectHorizonRequest, VNGenerateAttentionBasedSaliency-
 * ImageRequest, VNDetectFaceRectanglesRequest) anbinden (siehe CLAUDE.md "Planned architecture").
 */
actual class FrameAnalyzer actual constructor() {
    actual fun analyze(frame: CameraFrame): FrameAnalysis =
        FrameAnalysis(
            horizonTiltDegrees = null,
            saliencyRegions = emptyList(),
            faces = emptyList(),
            timestampMs = 0L,
        )
}
