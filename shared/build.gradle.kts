import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Code commun à l'écran d'accueil et au module « Épure Notifications » :
// lecture des notifications et format d'échange entre les deux apps.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "fr.epure.shared"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
