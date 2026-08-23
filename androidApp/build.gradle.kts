plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.florianhaeglsperger.photocoach.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.florianhaeglsperger.photocoach"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
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
