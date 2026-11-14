package fr.epure.launcher.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.compose.ui.text.font.FontFamily
import fr.epure.launcher.data.config.FontChoice
import java.io.File

/** Polices proposées : celles du système, plus une police importée par l'utilisateur. */
object Fonts {

    /** Nom affiché de chaque choix de police. */
    fun label(choice: FontChoice): String = when (choice) {
        FontChoice.SYSTEM -> "Système"
        FontChoice.LIGHT -> "Fine"
        FontChoice.CONDENSED -> "Condensée"
        FontChoice.SERIF -> "Classique (avec empattements)"
        FontChoice.MONO -> "Machine à écrire"
        FontChoice.CASUAL -> "Décontractée"
        FontChoice.CURSIVE -> "Manuscrite"
        FontChoice.CUSTOM -> "Ma police"
    }

    fun family(choice: FontChoice, customFontPath: String?): FontFamily = when (choice) {
        FontChoice.SYSTEM -> FontFamily.Default
        FontChoice.LIGHT -> systemFamily("sans-serif-light")
        FontChoice.CONDENSED -> systemFamily("sans-serif-condensed")
        FontChoice.SERIF -> FontFamily.Serif
        FontChoice.MONO -> FontFamily.Monospace
        FontChoice.CASUAL -> systemFamily("casual")
        FontChoice.CURSIVE -> FontFamily.Cursive
        FontChoice.CUSTOM -> customFontPath?.let(::fileFamily) ?: FontFamily.Default
    }

    private fun systemFamily(name: String): FontFamily =
        FontFamily(Typeface.create(name, Typeface.NORMAL))

    private val fileCache = HashMap<String, FontFamily>()

    private fun fileFamily(path: String): FontFamily? = synchronized(fileCache) {
        fileCache[path] ?: runCatching {
            val file = File(path)
            if (!file.exists()) null else FontFamily(Typeface.createFromFile(file))
        }.onFailure { Log.w("Fonts", "Police illisible : $path", it) }
            .getOrNull()
            ?.also { fileCache[path] = it }
    }

    /**
     * Copie un fichier .ttf/.otf choisi par l'utilisateur dans le dossier privé de l'app
     * et vérifie qu'il s'agit bien d'une police. Renvoie le chemin du fichier copié.
     */
    fun importFont(context: Context, uri: Uri): String? {
        val dir = File(context.filesDir, "fonts").apply { mkdirs() }
        val target = File(dir, "police-${System.currentTimeMillis()}.ttf")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            val valid = Typeface.Builder(target).build() != null
            if (!valid) {
                target.delete()
                null
            } else {
                // On ne garde que la dernière police importée.
                dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
                synchronized(fileCache) { fileCache.clear() }
                target.absolutePath
            }
        } catch (e: Exception) {
            Log.w("Fonts", "Import de police impossible", e)
            target.delete()
            null
        }
    }
}
