// Fichier de build racine : déclare les plugins utilisés par le module :app.
plugins {
    alias(libs.plugins.android.application) apply false
    // Fixe la version du compilateur Kotlin utilisée par le support Kotlin intégré d'AGP 9.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.roborazzi) apply false
}
