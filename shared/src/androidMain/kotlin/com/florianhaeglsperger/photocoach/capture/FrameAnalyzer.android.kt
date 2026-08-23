package com.florianhaeglsperger.photocoach.capture

import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis

/**
 * Platzhalter fuer Phase 0. Ab Phase 1 wird das hier durch androidx.camera.core.ImageProxy
 * ersetzt (dann muss androidx.camera:camera-core als Dependency in shared/build.gradle.kts
 * ergaenzt werden) — siehe CLAUDE.md "Planned architecture".
 */
actual class CameraFrame

/**
 * Android-Implementierung. Phase 0: liefert nur Dummy-Daten, damit der Datenfluss
 * Kamera -> FrameAnalyzer -> UI durchgaengig steht. Ab Phase 1 hier CameraX ImageAnalysis
 * + ML Kit (Gesichter) + ein kleines TFLite-Saliency-Modell + Sensor-Fusion fuer den
 * Horizont anbinden (siehe CLAUDE.md "Planned architecture").
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
