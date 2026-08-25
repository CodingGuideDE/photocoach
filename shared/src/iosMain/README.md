# iosMain — Phase 0, laeuft im Simulator

Xcode ist installiert, die iOS-Targets sind aktiv, die App laeuft im iOS-Simulator
(verifiziert: `iPhone 17`, iOS 26.5).

- `FrameAnalyzer.ios.kt` — Dummy-`actual`, analog zu androidMain (Phase 1: echte Analyse)
- `ui/CameraPreview.ios.kt` — `actual`-Implementierung mit echtem Berechtigungs-Flow
  (`AVCaptureDevice.requestAccessForMediaType`, System-Dialog getestet) und echter
  Geraete-Erkennung. **Kein `AVCaptureSession`/Preview/Aufnahme** — der iOS-Simulator hat
  keine Kamera-Hardware (`AVCaptureDevice.defaultDeviceWithMediaType` liefert dort immer
  `null`), die Session-Logik liesse sich also nur blind schreiben, nicht verifizieren. Das
  kommt zusammen mit dem ersten Test auf einem echten iPhone (Phase 1) — dann analog zu
  `CameraPreview.android.kt` mit `AVCaptureSession`, `AVCaptureVideoPreviewLayer` (ueber
  `UIKitView`), `AVCaptureVideoDataOutput` fuer die Frame-Analyse und `AVCapturePhotoOutput`
  fuers Ausloesen, Speichern via `PHPhotoLibrary`.
- `ui/MainViewController.kt` — Bruecke, die `App()` (die gemeinsame Compose-UI) als
  `UIViewController` exportiert, den `iosApp/ContentView.swift` einhaengt

`iosApp/` (per XcodeGen aus `project.yml` generiert, `.xcodeproj` selbst nicht eingecheckt):

- `cd iosApp && xcodegen generate` — erzeugt/aktualisiert `iosApp.xcodeproj`
- Danach `open iosApp.xcodeproj`, Simulator als Ziel waehlen, Run. Der erste Build-Schritt
  ruft automatisch `./gradlew :shared:embedAndSignAppleFrameworkForXcode` auf (siehe
  `preBuildScripts` in `project.yml`), kein separater manueller Gradle-Aufruf noetig.

## Zwei echte Bugs beim ersten Aufsetzen (beide durch echten Build gefunden, nicht geraten)

1. **Compose Multiplatform 1.11.1 hat einen KLIB-Resolver-Bug beim iOS-Compile**
   (`KLIB resolver: Could not find "..."`, trifft `org.jetbrains.compose.animation:animation-core`
   und andere transitive Artefakte — vermutlich eine Metadaten-Inkonsistenz im neuen
   AndroidX-Convergence-Dependency-Graph, siehe JetBrains/compose-multiplatform#CMP-7959 fuer
   das gleiche Fehlerbild bei anderer Artefakt-Kombination). Fix: Downgrade auf 1.10.3
   (`gradle/libs.versions.toml`). Android-Build laeuft mit 1.10.3 weiterhin einwandfrei.
2. **`iosX64` (Intel-Simulator) wird von Compose Multiplatform 1.11.1 nicht mehr
   veroeffentlicht** — auf diesem Apple-Silicon-Mac ohnehin nicht gebraucht, deshalb nur
   `iosArm64()` + `iosSimulatorArm64()` aktiv.
3. **Compose Multiplatform crasht ab 1.7 absichtlich beim Start ohne
   `CADisableMinimumFrameDurationOnPhone` in der Info.plist** (PlistSanityCheck — verhindert,
   dass die UI auf ProMotion-Displays unbemerkt auf 60 Hz gedrosselt laeuft). Xcodes
   `INFOPLIST_KEY_*`-Mechanismus kennt nur eine feste Liste von Apple-Standardkeys und
   verwirft eigene/CoreAnimation-Keys wie diesen stillschweigend — deshalb in `project.yml`
   ueber XcodeGens `info.properties`-Block gesetzt (schreibt eine echte Info.plist), nicht
   ueber `INFOPLIST_KEY_CADisableMinimumFrameDurationOnPhone`.

Alle drei Fixes stehen mit Begruendung als Kommentar direkt an der jeweiligen Stelle
(`gradle/libs.versions.toml`, `shared/build.gradle.kts`, `iosApp/project.yml`).
