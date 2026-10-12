package com.florianhaeglsperger.photocoach.capture

import androidx.compose.ui.graphics.ImageBitmap
import com.florianhaeglsperger.photocoach.domain.model.FrameAnalysis

/** Ein einzelnes Kamera-Frame, plattformspezifisch repraesentiert (z.B. Bitmap/ImageProxy vs. CVPixelBuffer). */
expect class CameraFrame

/**
 * Analysiert ein Kamera-Frame und liefert die fuer die Komposition relevanten Daten
 * (Horizont, Saliency, Gesichter). Jede Plattform bringt ihre eigene `actual`-Implementierung
 * mit; die domain-/UI-Logik in commonMain kennt nur diesen Contract, nie die konkreten
 * Vision-APIs dahinter (Phase 0: beide `actual`-Implementierungen liefern noch Dummy-Daten).
 */
expect class FrameAnalyzer() {
    fun analyze(frame: CameraFrame): FrameAnalysis

    /**
     * Analysiert ein gespeichertes Foto — fuer die Bewertung in der Vollbild-Ansicht.
     *
     * [image] muss aufrecht stehen (so, wie es angezeigt wird) und sollte etwa
     * [STILL_ANALYSIS_MAX_DIMENSION] gross sein: dieselbe Groessenordnung wie die Frames im
     * Sucher, damit dieselben Regeln dasselbe Urteil faellen.
     *
     * `horizonTiltDegrees` ist hier immer `null`. Im Sucher kommt die Neigung aus dem
     * Lagesensor (Android) — fuer ein Foto muesste sie bei der Aufnahme mitgespeichert worden
     * sein (Planung/Foto-Bearbeitung.md 4.3). Die Horizont-Regel bewertet ein Foto deshalb
     * nicht, statt etwas zu raten.
     *
     * Blockiert (Android: ML Kit) — nicht auf dem Main-Thread aufrufen.
     */
    fun analyzeStill(image: ImageBitmap): FrameAnalysis

    /**
     * Gibt plattformspezifische Ressourcen frei (Android: den ML-Kit-Detektor).
     *
     * Muss aufgerufen werden, wenn der Analyzer nicht mehr gebraucht wird — sonst bleiben
     * Modelle im Speicher. Danach darf [analyze] nicht mehr aufgerufen werden.
     */
    fun close()
}
