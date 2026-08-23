# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository status

Phase 0 (Projekt-Setup) ist angelegt: KMP-Gerueststruktur (`shared/`, `androidApp/`),
Compose-Multiplatform-UI-Platzhalter, `FrameAnalyzer` als `expect`/`actual` mit Dummy-
Implementierung auf Android. Die App laesst sich im Android-Emulator starten, zeigt aber
noch keine echte Kamera-Vorschau (folgt in Phase 1). iOS-Targets sind noch nicht aktiv
(siehe `shared/src/iosMain/README.md`) — dafuer wird ein volles Xcode.app benoetigt, auf
diesem Rechner ist bisher nur die Command-Line-Tools-Variante installiert.

**Build/Run (Android):**
- `./gradlew :androidApp:assembleDebug` — Debug-APK bauen
- Im Android Studio Projekt oeffnen (nutzt dessen gebuendeltes JBR) oder lokal:
  `JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew ...`
  (kein separates System-JDK auf diesem Rechner installiert)
- Emulator "Medium_Phone_API_36.0" ist als AVD vorhanden

Versionen (Stand 2026-08, siehe `gradle/libs.versions.toml`): Kotlin 2.2.20, AGP 8.11.1,
Compose Multiplatform 1.11.1, Gradle 8.13, compileSdk/targetSdk 35, minSdk 26.

- `Planung/Umsetzbarkeit.md` — feasibility research: competitor landscape (GudoCam,
  ComposeAI, SnapFrame, LiveCompose, etc.), which platform APIs cover which features,
  and a gap analysis (section 6) identifying underserved niches.
- `Planung/Plan-zur-Umsetzung.md` — the implementation plan derived from that research:
  tech stack decision, module structure, and a phased roadmap (Phase 0–8) with a
  Definition of Done per phase.
- `Planung/ML-Architektur.md` — follow-up ML research refining the plan (supervised
  approach instead of RL for Phase 6, CADB over AVA/NIMA as the composition dataset,
  on-device rationale).

Both documents are in German; keep planning/status updates to them in German to stay
consistent, unless the user asks otherwise.

## Project concept

An iOS + Android app that gives **live composition/framing feedback** while a user is
framing a shot (rule of thirds, horizon tilt, dead space, portrait framing), with a
post-capture aesthetic score and gallery. The two features chosen to differentiate from
existing competitor apps (per the gap analysis) are:

- **Explainable overlay** — visualize the actual saliency map / detected edges the
  scoring is based on, not just a bare score.
- **Accessibility coaching** — spoken/haptic composition guidance for blind/low-vision
  users, driven by the same underlying frame-analysis data as the visual overlay.

## Planned architecture

**Stack:** Kotlin Multiplatform (KMP) + Compose Multiplatform, targeting iOS and Android
from one UI/logic codebase. Camera capture and ML/vision inference are implemented
natively per platform (no cross-platform vision library abstracts iOS Vision framework
and Android CameraX/ML Kit well enough) and funneled into one shared data model.

**Module layout (planned):**

```
photocoach/
├── shared/                 # KMP module
│   ├── commonMain/
│   │   ├── domain/         # Pure Kotlin: model/, rules/, scoring/, coaching/
│   │   ├── capture/        # FrameAnalyzer.kt — expect class
│   │   └── ui/             # Compose UI: CameraScreen, GalleryScreen, ScoreOverlay
│   ├── iosMain/capture/    # FrameAnalyzer.ios.kt — actual, AVFoundation + Vision
│   └── androidMain/capture/# FrameAnalyzer.android.kt — actual, CameraX + ML Kit + TFLite
├── iosApp/                 # thin iOS host (Xcode project)
└── androidApp/             # thin Android host
```

**Core cross-platform contract** (keeps platform-specific vision code out of shared logic):

```kotlin
// commonMain
expect class FrameAnalyzer {
    fun analyze(frame: CameraFrame): FrameAnalysis
}

data class FrameAnalysis(
    val horizonTiltDegrees: Float?,
    val saliencyRegions: List<SaliencyPoint>,  // normalized 0.0–1.0
    val faces: List<FaceRect>,
    val timestampMs: Long
)
```

All composition rules, scoring, and coaching-text logic in `domain/` operate only on
`FrameAnalysis` and never touch platform APIs directly — each platform's `FrameAnalyzer`
implementation (iOS: `VNDetectHorizonRequest`, `VNGenerateAttentionBasedSaliencyImageRequest`,
`VNDetectFaceRectanglesRequest`; Android: CameraX + ML Kit + a small TFLite saliency model +
sensor fusion for horizon) is the only place that varies per platform. This is the seam to
preserve when adding features: new composition rules go in `domain/rules/`, new sensing
capability goes behind `FrameAnalyzer`.

## Roadmap phases (see Planung/Plan-zur-Umsetzung.md for full detail)

0. Project setup — KMP scaffold, dummy `FrameAnalyzer`, basic camera preview both platforms.
1. MVP — rule-based feedback (rule of thirds, horizon, dead space, portrait framing) live
   in-viewfinder, both platforms.
2. Explainable overlay + post-capture aesthetic score (NIMA-style model → CoreML + LiteRT).
3. Accessibility — audio/haptic coaching layer on the same `FrameAnalysis` data, tested
   with real blind/low-vision users.
4. Active directional suggestions via heuristics (saliency → nearest third-point vector).
5. Story/sequence coach for content creators (shot-type variety, color consistency across
   a marked photo series).
6. Optional: own ML model for directional suggestions (only if Phase 4 heuristics prove
   insufficient) — study github.com/LiveCompose/LiveCapture (MIT) as reference.
7. Optional: social duel/comparison mode (backend-heavy, deliberately late).
8. Polish & release (both app stores).

Open technical decisions that should be resolved before Phase 1 work starts (see
Planung/Plan-zur-Umsetzung.md §11): which Android saliency model to use, which
aesthetic-score model to build/port for Phase 2, minimum supported OS versions, and the
on-device-vs-cloud privacy stance.
