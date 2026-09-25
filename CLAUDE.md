# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Pflegehinweis (verbindlich)

Diese Datei nach jeder abgeschlossenen Aufgabe aktualisieren — neuer Stand unter
"Repository status" ergänzen, nicht nur im Kopf behalten oder nur in `Planung/Progress.md`
festhalten. Neu entdeckte Bugs/Lücken gehören ebenfalls sofort hier rein (siehe bestehende
"Bekannte Luecke"-Einträge als Vorbild), auch wenn sie noch nicht behoben sind.

## Plattform-Fokus: Android (iOS pausiert, 22.09.2026)

**Neue Funktionalität wird nur auf Android gebaut.** Begründung und Bedingungen für das
Wiederaufnehmen: `Planung/Plan-zur-Umsetzung.md` Abschnitt 0.

Was das fuer die Arbeit hier heisst:

- **Keine neuen iOS-Features.** `AVCaptureSession`, Preview und Aufnahme auf iOS bleiben
  liegen. `FrameAnalyzer.ios.kt` ist gebaut, aber nicht angeschlossen und inhaltlich
  unverifiziert — nicht darauf aufbauen.
- **Der iOS-Code muss weiter kompilieren.** Aendert sich etwas in `commonMain` (neues Feld
  in `FrameAnalysis`, neue Methode im `expect`), das iOS-`actual` mitziehen. Das kostet
  jetzt Minuten und spaeter Tage.
- **Die `expect`/`actual`-Naht und `commonMain` bleiben plattformneutral.** Sie sind der
  Grund, warum die Pause billig ist — Logik und UI gehoeren weiterhin dorthin, nicht nach
  `androidMain`.
- **Vor dem Commit `./gradlew :shared:allTests`** (schliesst die iOS-Simulator-Tests ein).
  Im Alltag reicht `:shared:testDebugUnitTest`, das spart den iOS-Compile.

## Repository status

Phase 0 (Projekt-Setup) ist angelegt: KMP-Gerueststruktur (`shared/`, `androidApp/`),
`FrameAnalyzer` als `expect`/`actual` mit Dummy-Implementierung auf Android.

**Kamera-Vorschau (Android) steht.** CameraX `Preview`-Use-Case ueber die
`expect`/`actual`-Naht `ui/CameraPreview.kt` (commonMain) → `ui/CameraPreview.android.kt`
(androidMain), Statusdarstellung geteilt in `ui/CameraScreen.kt`. Verifiziert im Emulator:
Berechtigungsdialog, Kamera bindet, Live-Bild sichtbar.

**Foto-Aufnahme (Android) steht — Phase-0-DoD fuer Android damit erfuellt.**
`ImageCapture`-Use-Case (`CAPTURE_MODE_MINIMIZE_LATENCY`) im selben `actual`; Speichern
ueber den MediaStore nach `Pictures/PhotoCoach`, also in die Galerie des Nutzers und nicht
in den App-Ordner — die Fotos sollen eine Deinstallation ueberleben und teilbar sein.
Ausloeser und Rueckmeldung liegen in `CameraScreen` (commonMain), gelten also spaeter fuer
iOS unveraendert.

Design-Prinzip der Naht: **jeder `CameraState` traegt die Aktionen, die in ihm moeglich
sind** — `PermissionRequired.requestPermission`, `Running.takePhoto`. Die Mechanik ist
plattformspezifisch, Texte/Layout/Buttons liegen in commonMain.

Im Emulator verifiziert: 1 Tap = 1 Foto, Datei im MediaStore vorhanden, drei schnelle Taps
erzeugen nur eine Aufnahme (Guard ueber `capturing`, Button wird sichtbar ausgegraut).

Bekannte Luecke: `imageCapture.targetRotation` wird nur beim Binden gesetzt. Dreht der
Nutzer das Geraet waehrend die App laeuft, wird das Foto falsch herum gespeichert — braucht
einen `OrientationEventListener`.

**Datenfluss Kamera -> FrameAnalyzer -> UI steht (Android).** `ImageAnalysis`-Use-Case
(`STRATEGY_KEEP_ONLY_LATEST`, eigener Executor, auf ~10 Hz gedrosselt) liegt in
`ui/CameraPreview.android.kt` und schickt jedes Frame als `CameraFrame` — jetzt ein duenner
Wrapper um `ImageProxy` — durch den `FrameAnalyzer`. Ergebnis geht ueber den neuen
`onAnalysis`-Callback der `CameraPreview`-Naht auf dem Main-Thread nach oben.
Der Analyzer selbst bleibt bewusst ein Dummy: er uebernimmt nur den echten Frame-Zeitstempel,
`horizonTiltDegrees`/`saliencyRegions`/`faces` bleiben leer, bis Phase 1 sie befuellt.
`CameraScreen` zeigt das als provisorisches `AnalysisDebugBadge` (Frame-Zaehler +
Zeitstempel) — faellt weg, sobald dort das echte ScoreOverlay haengt.
Im Emulator verifiziert: Zaehler laeuft hoch, Zeitstempel wandert mit, kein Crash.

Wichtig fuer Phase 1: das `ImageProxy` in `CameraFrame` ist nur waehrend des
`analyze()`-Aufrufs gueltig — der Aufrufer schliesst es danach. Nichts daraus ueber den
Aufruf hinaus festhalten.

Berechtigungen werden in `shared/src/androidMain/AndroidManifest.xml` deklariert (nicht in
androidApp) — dort liegt der Kamera-Code. Diese Datei haelt den Berechtigungs-Satz bewusst
klein, was zum on-device-Versprechen der App gehoert:
- `ACCESS_NETWORK_STATE` (kommt transitiv ueber `camera-view` → `androidx.media3`) wird per
  `tools:node="remove"` entfernt
- `WRITE_EXTERNAL_STORAGE` ist auf `maxSdkVersion=28` begrenzt (ab API 29 schreibt der
  MediaStore ohne Berechtigung)
- `READ_EXTERNAL_STORAGE` fuegt der Manifest-Merger automatisch hinzu, sobald WRITE
  deklariert ist — und zwar OHNE Grenze. Deshalb hier explizit ebenfalls auf
  `maxSdkVersion=28` gesetzt, sonst stuende auf allen Geraeten ein Zugriff auf die
  gesamte Fotobibliothek im Play-Store-Eintrag.

Ergebnis nach dem Merge: auf Geraeten ab API 29 fordert die App nur noch CAMERA an. Wer
hier Dependencies ergaenzt, sollte das gemergte Manifest gegenpruefen
(`androidApp/build/intermediates/merged_manifest/debug/.../AndroidManifest.xml`).

**iOS-Targets sind aktiv, App laeuft im Simulator** (verifiziert: iPhone 17, iOS 26.5).
`CameraPreview.ios.kt` prueft die Kamera-Berechtigung echt (Systemdialog getestet), erkennt
mangels Kamera-Hardware im Simulator korrekt "keine Kamera gefunden" — `AVCaptureSession`/
Preview/Aufnahme folgen erst mit einem echten iPhone zum Testen (Phase 1). Details, inkl.
dreier echter Bugs, die dabei auftraten und wie sie behoben wurden (Compose Multiplatform
1.11.1 KLIB-Resolver-Bug, iosX64 nicht mehr veroeffentlicht, PlistSanityCheck-Crash ohne
`CADisableMinimumFrameDurationOnPhone`), stehen in `shared/src/iosMain/README.md`.

**Tests.** `./gradlew :shared:allTests` — laeuft auf Android UND im iOS-Simulator.
- `shared/src/commonTest/.../domain/TestFrames.kt` — Fixtures, um `FrameAnalysis`-Situationen
  zu beschreiben (`frame(...)`, `faceAt(...)`, `subjectAt(...)`, Konstanten `THIRD_LEFT` etc.).
  **Regeln in `domain/rules/` gehoeren hierueber getestet, nicht am Emulator** — sie sind pure
  Funktionen von `FrameAnalysis` auf einen Hinweis.
- `shared/src/commonTest/.../domain/rules/HorizonRuleTest.kt` und `PortraitFramingRuleTest.kt`
  — erste zwei Regeln aus Plan 3.2 (`domain/rules/Rule.kt` fuer den gemeinsamen Contract).
  Beide gruen auf Android und iOS-Simulator (7/7 bzw. 9/9). `DEBUG_RULES` in
  `CameraScreen.kt` sammelt alle zutreffenden Hinweise fuers `AnalysisDebugBadge` ein — neue
  Regel = ein Eintrag dort, kein Copy-Paste-UI-Code — bis dort das echte ScoreOverlay
  (Plan 3.4) haengt.
- `shared/src/androidUnitTest/.../capture/HorizonSensorTest.kt` — Winkel-Mathematik der
  Horizont-Erkennung. Achtung bei Aenderungen: `Display.getRotation()` meldet die Drehung der
  Grafik, nicht des Geraets, beides ist gegenlaeufig. Die Konvention steht als Kommentar im
  Test — sie war beim Schreiben schon einmal vertauscht.
- Die vormals bekannt roten `FrameAnalyzerTest`-Faelle zur Saliency-Position auf
  `iosSimulatorArm64` sind behoben (durch Entfernen der im Simulator nicht verifizierbaren
  Positions-Assertions, siehe Kommentar dort und `iosMain/README.md`) — alle Tests gruen.

**Stand `FrameAnalysis`:** `faces` (Android: ML Kit, iOS: Vision) und `horizonTiltDegrees`
(Android: Schwerkraft-Sensor, iOS: `VNDetectHorizonRequest`) sind auf beiden Plattformen
echt. Vorzeichen-Konvention (positiv = rechts tiefer) steht verbindlich am Feld in
`FrameAnalysis.kt` — fuer iOS aus der Vision-Rotationsrichtung hergeleitet, aber **noch
nicht an einem echten Geraet verifiziert**. `saliencyRegions`: auf Android noch leer
(offene Modell-Entscheidung, Plan 11), auf iOS ueber
`VNGenerateAttentionBasedSaliencyImageRequest` echt (siehe die zwei bekannt roten Tests
oben).

`FrameAnalyzer` hat jetzt ein `close()` (gibt den ML-Kit-Detektor frei), aufgerufen aus dem
`DisposableEffect` in `CameraPreview.android.kt`.

**Noch nicht auf Geraet/Emulator verifiziert:** Gesichtserkennung und Neigungswert im
laufenden Betrieb. Build und Unit-Tests sind gruen, aber das Debug-Badge (zeigt jetzt
Neigung + Gesichter-Anzahl) wurde noch nicht live gesehen — der Emulator hing beim Testen.

**ScoreOverlay (Plan 3.4) steht.** Schmale Leiste am oberen Bildrand, Symbol + Text,
immer nur *ein* Hinweis. Kein Overlay ueber dem Motiv — der Sucher bleibt frei.

Kette: `FrameAnalysis` → `HintSelector` (waehlt einen Hinweis) → `HintStabilizer`
(Mindestanzeigezeit) → `ScoreOverlay`.

- `domain/scoring/HintSelector` — Rangfolge nach **Reparierbarkeit**, nicht Auffaelligkeit:
  Portraet-Rahmung (angeschnittener Kopf ist unwiederbringlich) → Horizont (notfalls durch
  Drehen/Beschneiden zu retten) → Drittel-Regel (weichste, bewusst verletzbar).
  Setzt den `ThirdsTargetTracker` zurueck, wenn kein Motiv mehr erkannt wird.
- `domain/scoring/HintStabilizer` — ein neuer Hinweis (auch "keiner") uebernimmt erst nach
  1,8 s. Ohne das wechselt der Text bei 10 Hz Analyse bis zu zehnmal pro Sekunde.
- Beide in `domain/`, nicht in der UI: es sind Entscheidungen darueber *was* gezeigt wird,
  und so ohne Compose testbar.

Das ist der erste Teil dessen, was der Plan `CompositionScorer` nennt. Der 0-100-Score
fehlt weiterhin — dafuer muessten die Regeln melden, *wie stark* sie verletzt sind, und sie
liefern bisher nur "Hinweis oder nicht".

Das `AnalysisDebugBadge` zeigt jetzt nur noch Rohdaten (Frames, Neigung, Gesichter,
Saliency) — Hinweise gehoeren ins ScoreOverlay. Es faellt weg, sobald der Geraetetest
(Plan 3.5) bestaetigt hat, dass die Daten stimmen.

Im Emulator verifiziert: Ruhezustand ("Komposition passt", gruen) und Warnzustand
(Horizont-Hinweis, orange) rendern korrekt, kein Crash.

**Feldtest-Ruestzeug (Plan 3.5), temporaer.**
- `ui/ThirdsGrid` — Drittel-Raster mit hervorgehobenen Schnittpunkten, ueber
  `GridToggle` (oben rechts) ein-/ausblendbar, standardmaessig AN. Ohne sichtbare Linien
  laesst sich nicht beurteilen, ob ein Hinweis stimmt.
- `diagnostics/FieldLog` — schreibt jede Hinweis-Aenderung und jede Aufnahme mit Uhrzeit
  nach `/sdcard/Android/data/com.florianhaeglsperger.photocoach/files/feldtest.log`.
  Abholen: `adb pull <Pfad>`. Datei statt Logcat, weil das Geraet beim Test nicht am
  Rechner haengt. **Faellt weg, sobald der Feldtest ausgewertet ist.**

**Rotation.** `OrientationEventListener` in `CameraPreview.android.kt` haelt jetzt beides
aktuell — mit einer Unterscheidung, die leicht untergeht:
- `imageCapture.targetRotation` folgt der **physischen** Geraetelage (Querformat-Foto auch
  bei gesperrter Bildschirmdrehung) — abgeleitet ueber `toSurfaceRotation()`, unit-getestet.
- `horizonSensor.displayRotation` folgt der **Bildschirm**-Drehung, direkt vom Display
  gelesen. Der Nutzer beurteilt "gerade" an dem, was er sieht.
Die beiden zu verwechseln faellt im Hochformat nicht auf, im Querformat sofort.

**Objektiv-Umschalter.** `LensToggle` rechts neben dem Ausloeser, nur sichtbar wenn das
Geraet beide Kameras hat. `CameraState.Running` traegt `lensFacing`, `canSwitchLens` und
`switchLens` — gleiche Linie wie die uebrigen Zustaende: jeder traegt die Aktionen, die in
ihm moeglich sind. CameraX kann das Objektiv nicht im Betrieb wechseln, deshalb bindet der
`DisposableEffect` bei Aenderung neu (Key `desiredLens`).

**Wichtig dabei — Frontkamera-Spiegelung:** Die *Vorschau* der Frontkamera ist gespiegelt
(macht jede Kamera-App so), die *Analyse-Frames* sind es nicht — die kommen roh vom Sensor.
Ohne Korrektur zeigt jeder Hinweis beim Selfie in die falsche Richtung. `CameraFrame` traegt
deshalb ein `mirrored`-Flag, und `analyze()` spiegelt die Gesichter ueber
`FaceRect.mirroredHorizontally()` (commonMain, 7 Unit-Tests).

Nicht gespiegelt wird die **Horizont-Neigung**: die misst die physische Geraetelage, und
die aendert sich nicht dadurch, welche Kamera aktiv ist.

Offen und bewusst so gelassen: Das *gespeicherte* Selfie ist nicht gespiegelt (CameraX-
Standard, so kennt man es von jeder Kamera-App) — es unterscheidet sich also spiegelbildlich
von der Vorschau, auf die sich die Hinweise bezogen. Fuer die Beurteilung der Komposition
ist das unkritisch (Drittel-Punkte bilden sich auf Drittel-Punkte ab), sollte aber nach dem
Feldtest bewusst entschieden werden.

**Build/Run (Android):**
- `./gradlew :androidApp:assembleDebug` — Debug-APK bauen
- Im Android Studio Projekt oeffnen (nutzt dessen gebuendeltes JBR) oder lokal:
  `JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew ...`
  (kein separates System-JDK auf diesem Rechner installiert)
- Emulator "Medium_Phone_API_36.0" ist als AVD vorhanden

**Build/Run (iOS):**
- `cd iosApp && xcodegen generate && open iosApp.xcodeproj` — Simulator als Ziel waehlen, Run
  (baut `shared.framework` automatisch mit, siehe `preBuildScripts` in `project.yml`)
- Oder per CLI: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator
  -destination 'platform=iOS Simulator,name=<Geraetename>' -derivedDataPath iosApp/build build`
- `xcodegen` ist per Homebrew installiert; `iosApp.xcodeproj` selbst ist NICHT eingecheckt
  (generiertes Artefakt, siehe `.gitignore`)

Versionen (Stand 2026-08, siehe `gradle/libs.versions.toml`): Kotlin 2.2.20, AGP 8.11.1,
Compose Multiplatform 1.10.3 (bewusst nicht 1.11.1, siehe iosMain/README.md), Gradle 8.13,
compileSdk 36, targetSdk 35, minSdk 26, CameraX 1.6.1, iOS-Deploymenttarget 14.0.
compileSdk musste fuer CameraX 1.6 von 35 auf 36 — targetSdk bleibt bewusst auf 35, das ist
eine getrennte Entscheidung (Laufzeitverhalten).

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
- **Text-based coaching** — proactive, concrete short instructions while framing
  ("Person etwas weiter rechts, naeher rankommen") instead of only a score or an overlay
  the user has to interpret. Driven by the same `FrameAnalysis` data as the overlay.
  (Speech/haptic output for blind/low-vision users was considered and deliberately
  deprioritised — see Planung/Umsetzbarkeit.md 6.1.)

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
3. Text-based coaching — short, actionable in-viewfinder hints derived from the same
   `FrameAnalysis` data (template-based, no model needed).
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
