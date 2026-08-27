package com.florianhaeglsperger.photocoach.capture

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
     * Gibt plattformspezifische Ressourcen frei (Android: den ML-Kit-Detektor).
     *
     * Muss aufgerufen werden, wenn der Analyzer nicht mehr gebraucht wird — sonst bleiben
     * Modelle im Speicher. Danach darf [analyze] nicht mehr aufgerufen werden.
     */
    fun close()
}
