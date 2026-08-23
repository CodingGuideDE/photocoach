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

    // iOS-Targets bewusst noch nicht aktiviert: dafuer wird ein volles Xcode.app
    // (nicht nur die Command Line Tools) benoetigt, um die Apple-Plattform-Header
    // fuers cinterop aufzuloesen. Sobald Xcode installiert ist, hier ergaenzen:
    //   iosX64(); iosArm64(); iosSimulatorArm64()
    // und einen entsprechenden iosMain-Sourceset unter src/iosMain anlegen.

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
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
        }
    }
}

android {
    namespace = "com.florianhaeglsperger.photocoach.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
