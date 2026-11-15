import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// « Épure Notifications » : petit module facultatif qui lit les notifications et les transmet
// à l'écran d'accueil Épure. Il est séparé du launcher car Google Play Protect bloque
// l'installation hors Play Store des apps capables de lire les notifications : ainsi,
// seul ce module (installé une fois) est concerné, pas les mises à jour du launcher.
plugins {
    alias(libs.plugins.android.application)
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "fr.epure.notifications"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.epure.notifications"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Même clé que le launcher : obligatoire pour que les deux apps puissent échanger.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
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
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
}
