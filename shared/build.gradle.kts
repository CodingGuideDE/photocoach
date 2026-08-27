plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // Xcode ist jetzt installiert (siehe shared/src/iosMain/README.md fuer den Stand).
    // iosX64 (Intel-Simulator) bewusst weggelassen: Compose Multiplatform 1.11.1
    // veroeffentlicht dafuer keine Artefakte mehr, und dieser Mac ist Apple Silicon.
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            // compose.components.resources bewusst nicht eingebunden: wird bisher nirgends
            // verwendet (keine Bild-/Font-Ressourcen im Projekt) und loeste beim
            // iOS-Compile einen KLIB-Resolver-Fehler aus. Bei Bedarf spaeter wieder rein.
        }

        androidMain.dependencies {
            // CameraX fuer die Live-Vorschau (Phase 0/1). Bewusst hier im shared-Modul
            // und nicht in androidApp: der plattformspezifische Kamera-Code gehoert
            // hinter die expect/actual-Naht, damit androidApp ein duenner Host bleibt.
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)

            // Fuer die Laufzeit-Berechtigungsabfrage (rememberLauncherForActivityResult)
            implementation(libs.androidx.activity.compose)
            // ContextCompat.getMainExecutor
            implementation(libs.androidx.core.ktx)

            // Gesichtserkennung fuer FrameAnalysis.faces (Phase 1). Das Modell ist im
            // Artefakt gebuendelt, laeuft on-device und laedt nichts nach.
            implementation(libs.mlkit.face.detection)
        }

        androidUnitTest.dependencies {
            // Fuer Tests von androidMain-Code, der ohne Geraet auskommt (z.B. die
            // Winkel-Mathematik in HorizonSensor.kt).
            implementation(kotlin("test"))
        }

        commonTest.dependencies {
            // Regeln in domain/ sind pure Funktionen von FrameAnalysis auf einen Hinweis —
            // genau das, was sich ohne Kamera und ohne Emulator testen laesst. Ausfuehren
            // mit: ./gradlew :shared:allTests
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.florianhaeglsperger.photocoach.shared"
    // compileSdk 36 ist Pflicht ab CameraX 1.6 — betrifft nur, gegen welche APIs
    // kompiliert wird. targetSdk (= Laufzeitverhalten) bleibt bewusst auf 35.
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
