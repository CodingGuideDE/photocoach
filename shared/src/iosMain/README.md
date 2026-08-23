# iosMain — noch nicht aktiv

Dieses Verzeichnis ist als Platzhalter fuer den kommenden iOS-`actual`-Sourceset
angelegt (`FrameAnalyzer.ios.kt`, AVFoundation + Vision), ist aber noch nicht Teil
des Gradle-Builds.

Grund: iOS-Targets (`iosX64`, `iosArm64`, `iosSimulatorArm64`) brauchen ein volles
`Xcode.app` (nicht nur die Command Line Tools) fuer das cinterop gegen die Apple-
Plattform-Header. Auf diesem Rechner ist aktuell nur `Xcode.app`s CLT-Ersatz aktiv.

**Sobald volles Xcode installiert ist:**

1. In `shared/build.gradle.kts` im `kotlin { }`-Block ergaenzen:
   ```kotlin
   iosX64()
   iosArm64()
   iosSimulatorArm64()
   ```
2. `FrameAnalyzer.ios.kt` hier anlegen (analog zu
   `shared/src/androidMain/kotlin/.../FrameAnalyzer.android.kt`).
3. `iosApp/` (Xcode-Projekt) aufsetzen — dafuer XcodeGen (`project.yml`) statt eines
   handgeschriebenen `.xcodeproj` verwenden, siehe Plan-zur-Umsetzung.md.
