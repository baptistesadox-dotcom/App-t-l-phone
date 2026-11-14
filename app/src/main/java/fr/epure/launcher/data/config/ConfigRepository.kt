package fr.epure.launcher.data.config

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** Format JSON de la configuration (fichier interne et sauvegardes). */
object ConfigJson {
    private val compact = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }
    private val pretty = Json(from = compact) { prettyPrint = true }

    fun encode(config: LauncherConfig, prettyPrint: Boolean = false): String =
        (if (prettyPrint) pretty else compact).encodeToString(LauncherConfig.serializer(), config)

    /**
     * Lit une configuration (et la met à jour si elle vient d'une version précédente).
     * @throws SerializationException si le texte n'est pas une configuration valide.
     */
    fun decode(text: String): LauncherConfig =
        compact.decodeFromString(LauncherConfig.serializer(), text).migrated()
}

private object LauncherConfigSerializer : Serializer<LauncherConfig> {
    override val defaultValue: LauncherConfig = LauncherConfig()

    override suspend fun readFrom(input: InputStream): LauncherConfig {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return defaultValue
        return try {
            ConfigJson.decode(text)
        } catch (e: SerializationException) {
            throw CorruptionException("Configuration illisible", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Configuration illisible", e)
        }
    }

    override suspend fun writeTo(t: LauncherConfig, output: OutputStream) {
        output.write(ConfigJson.encode(t).encodeToByteArray())
    }
}

/** Accès unique à la configuration enregistrée (une seule instance par processus). */
class ConfigRepository(context: Context, scope: CoroutineScope) {
    private val store: DataStore<LauncherConfig> = DataStoreFactory.create(
        serializer = LauncherConfigSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { LauncherConfig() },
        scope = scope,
        produceFile = { context.applicationContext.dataStoreFile("launcher_config.json") },
    )

    val config: Flow<LauncherConfig> = store.data

    suspend fun current(): LauncherConfig = store.data.first()

    suspend fun update(transform: (LauncherConfig) -> LauncherConfig) {
        store.updateData { transform(it) }
    }
}
