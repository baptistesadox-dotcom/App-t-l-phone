// Miroir officiel de Maven Central hébergé par Google : utilisé en premier car Maven Central
// limite parfois le débit (erreurs HTTP 429) sur les machines de build partagées.
// Les bibliothèques Android (androidx, com.android…) viennent directement du dépôt Google.
fun RepositoryHandler.mavenCentralMirror() {
    maven("https://maven-central.storage-download.googleapis.com/maven2") {
        name = "MavenCentralGoogleMirror"
        content {
            excludeGroupByRegex("androidx.*")
            excludeGroupByRegex("com\\.android.*")
            excludeGroupByRegex("com\\.google\\.android.*")
        }
    }
}

pluginManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2") {
            name = "MavenCentralGoogleMirror"
            content {
                excludeGroupByRegex("androidx.*")
                excludeGroupByRegex("com\\.android.*")
                excludeGroupByRegex("com\\.google\\.android.*")
            }
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentralMirror()
        google()
        mavenCentral()
    }
}

rootProject.name = "Epure"
include(":app", ":shared", ":notifications")
