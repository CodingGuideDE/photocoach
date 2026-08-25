package com.florianhaeglsperger.photocoach.capture

import androidx.camera.core.ImageProxy
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis

/**
 * Android-Frame: haelt das rohe [ImageProxy] aus dem CameraX-`ImageAnalysis`-Use-Case.
 *
 * Bewusst ein duenner Wrapper statt einer eigenen Kopie der Bilddaten — ein Frame pro
 * Analyse zu kopieren waere bei 5-10 Hz unnoetiger Aufwand. Dafuer gilt: das [ImageProxy]
 * ist nur waehrend des `analyze()`-Aufrufs gueltig, danach schliesst der Aufrufer es
 * wieder (siehe CameraPreview.android.kt). Nichts aus diesem Objekt darf also ueber den
 * Aufruf hinaus festgehalten werden.
 */
actual class CameraFrame(internal val image: ImageProxy)

/**
 * Android-Implementierung. Phase 0: fuehrt noch keine Bildanalyse durch, uebernimmt aber
 * bereits den echten Zeitstempel des Frames — damit ist der Datenfluss
 * Kamera -> FrameAnalyzer -> UI vollstaendig und nachweisbar, ohne Analyse-Ergebnisse
 * zu erfinden. Die uebrigen Felder bleiben deshalb bewusst leer/null.
 *
 * Ab Phase 1 kommt hier hinein: ML Kit Face Detection (`faces`), ein kleines
 * TFLite-Saliency-Modell (`saliencyRegions`) und `SensorManager`-Fusion fuer den
 * Horizont (`horizonTiltDegrees`) — siehe Plan-zur-Umsetzung.md Abschnitt 3.3.
 */
actual class FrameAnalyzer actual constructor() {
    actual fun analyze(frame: CameraFrame): FrameAnalysis =
        FrameAnalysis(
            horizonTiltDegrees = null,
            saliencyRegions = emptyList(),
            faces = emptyList(),
            // imageInfo.timestamp ist in Nanosekunden seit Boot.
            timestampMs = frame.image.imageInfo.timestamp / 1_000_000L,
        )
}
