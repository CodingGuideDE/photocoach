plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.florianhaeglsperger.photocoach.android"
    compileSdk = 36  // siehe shared/build.gradle.kts

    defaultConfig {
        applicationId = "com.florianhaeglsperger.photocoach"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        debug {
            // Nur arm64: MediaPipe bringt native Bibliotheken fuer vier Architekturen mit
            // (~48 MB). Emulator (sdk_gphone64_arm64) und Testgeraet sind beide arm64; die
            // uebrigen drei liessen das Debug-APK auf 130 MB wachsen, und der Emulator hatte
            // dafuer keinen Platz mehr. Release bleibt unbeschraenkt (Play liefert ohnehin
            // pro Geraet nur die passende Architektur aus).
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // MediaPipe liest Modelle direkt aus dem APK (Memory-Mapping) — das geht nur, wenn sie
    // unkomprimiert drinliegen. Muss hier im App-Modul stehen: im shared-Modul (Bibliothek)
    // wirkt die Einstellung nicht auf das Packen des APKs.
    androidResources {
        noCompress += "task"
    }
}

kotlin {
    // Muss zu android.compileOptions oben passen, sonst bricht compileDebugKotlin ab
    // ("Inconsistent JVM Target Compatibility") - der hier laufende JBR liefert Java 21,
    // das Android-Target soll aber auf 17 kompilieren.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
