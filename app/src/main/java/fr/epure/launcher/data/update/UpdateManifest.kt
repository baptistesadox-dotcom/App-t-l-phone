package fr.epure.launcher.data.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** Une version publiée d'une des deux apps (launcher ou module de notifications). */
@Serializable
data class UpdateEntry(
    val versionCode: Long,
    val versionName: String,
    /** Adresse de l'APK (HTTPS). */
    val url: String,
    /** Empreinte SHA-256 de l'APK, en hexadécimal : vérifiée après le téléchargement. */
    val sha256: String,
    /** Taille en octets (pour la barre de progression). */
    val size: Long = 0,
    /** Nouveautés, en une ou deux phrases. */
    val notes: String = "",
)

/**
 * Fichier `updates/latest.json` du dépôt : dernière version de chaque app.
 * Android refuse toute mise à jour signée avec une autre clé : un fichier modifié ne
 * pourrait pas installer autre chose qu'Épure.
 */
@Serializable
data class UpdateManifest(
    val launcher: UpdateEntry? = null,
    val module: UpdateEntry? = null,
)

/** App concernée par une mise à jour. */
enum class UpdateTarget(val packageName: String, val fileName: String) {
    LAUNCHER("fr.epure.launcher", "Epure"),
    MODULE("fr.epure.notifications", "Epure-Notifications"),
}

data class AvailableUpdate(val target: UpdateTarget, val entry: UpdateEntry)

object UpdateManifests {

    private val json = Json { ignoreUnknownKeys = true }

    /** Lit le fichier publié ; null s'il est illisible ou incomplet. */
    fun parse(text: String): UpdateManifest? = runCatching { json.decodeFromString<UpdateManifest>(text) }
        .getOrNull()
        ?.takeIf { manifest -> listOfNotNull(manifest.launcher, manifest.module).all(::isValid) }

    private fun isValid(entry: UpdateEntry): Boolean =
        entry.versionCode > 0 &&
            entry.url.startsWith("https://") &&
            entry.sha256.length == 64 &&
            entry.sha256.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    /**
     * Mises à jour à proposer : versions plus récentes que celles installées. Le module
     * n'est proposé ici que s'il est déjà installé ([moduleVersion] non nul).
     */
    fun available(manifest: UpdateManifest, launcherVersion: Long, moduleVersion: Long?): List<AvailableUpdate> = buildList {
        manifest.launcher?.takeIf { it.versionCode > launcherVersion }?.let { add(AvailableUpdate(UpdateTarget.LAUNCHER, it)) }
        if (moduleVersion != null) {
            manifest.module?.takeIf { it.versionCode > moduleVersion }?.let { add(AvailableUpdate(UpdateTarget.MODULE, it)) }
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
