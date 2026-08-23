// Root-Build: bewusst leer. Alle Plugins werden mit `apply false` deklariert,
// damit sie im richtigen Modul (shared/androidApp) mit der korrekten Version
// angewendet werden koennen, ohne dass Gradle sie hier schon ausfuehrt.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}
