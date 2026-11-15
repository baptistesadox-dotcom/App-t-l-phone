package fr.epure.launcher.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.epure.launcher.R
import fr.epure.launcher.appContainer
import fr.epure.launcher.data.config.ConfigJson
import fr.epure.launcher.data.config.LauncherConfig
import fr.epure.launcher.data.config.WeatherLocation
import fr.epure.launcher.data.config.mergeGeneratedFolders
import fr.epure.launcher.data.config.sanitizedForImport
import fr.epure.launcher.data.config.withHomeImage
import fr.epure.launcher.data.icons.IconPackInfo
import fr.epure.launcher.domain.AutoCategories
import fr.epure.launcher.ui.LauncherState
import fr.epure.launcher.ui.buildLauncherState
import fr.epure.launcher.ui.theme.Fonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Données et actions des réglages. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val configRepository = container.configRepository

    val config: StateFlow<LauncherConfig?> =
        configRepository.config.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<LauncherState?> =
        combine(config.filterNotNull(), container.appsRepository.apps.filterNotNull()) { config, apps ->
            buildLauncherState(config, apps)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun update(transform: (LauncherConfig) -> LauncherConfig) {
        viewModelScope.launch { configRepository.update(transform) }
    }

    suspend fun iconPacks(): List<IconPackInfo> = withContext(Dispatchers.IO) { container.iconPacks.installedPacks() }

    /** Recherche de ville pour la météo. Renvoie null si le réseau est indisponible. */
    suspend fun searchCity(query: String): List<WeatherLocation>? =
        runCatching { container.weatherRepository.searchLocations(query) }.getOrNull()

    /** Range les apps dans des dossiers par catégorie. Renvoie le nombre de dossiers concernés. */
    suspend fun autoCategorize(): Int {
        val state = this.state.filterNotNull().first()
        val groups = AutoCategories.group(state.apps.map { Triple(it.key, it.app.packageName, it.app.category) })
        if (groups.isNotEmpty()) {
            configRepository.update { it.mergeGeneratedFolders(groups) { UUID.randomUUID().toString() } }
        }
        return groups.size
    }

    /** Importe une police ; renvoie false si le fichier n'est pas une police valide. */
    suspend fun importFont(uri: Uri): Boolean {
        val path = withContext(Dispatchers.IO) { Fonts.importFont(getApplication(), uri) } ?: return false
        configRepository.update {
            it.copy(appearance = it.appearance.copy(font = fr.epure.launcher.data.config.FontChoice.CUSTOM, customFontPath = path))
        }
        return true
    }

    suspend fun exportTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val json = ConfigJson.encode(configRepository.current(), prettyPrint = true)
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(json.encodeToByteArray())
            } ?: error("Fichier inaccessible")
        }.isSuccess
    }

    /** Lit une sauvegarde et la valide, sans l'appliquer. */
    suspend fun readBackup(uri: Uri): LauncherConfig? = withContext(Dispatchers.IO) {
        runCatching {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: error("Fichier inaccessible")
            ConfigJson.decode(text).sanitizedForImport()
        }.getOrNull()
    }

    fun applyBackup(config: LauncherConfig) {
        // Les widgets Android déjà posés et l'image de l'accueil de ce téléphone restent en place.
        update { current ->
            config.copy(
                widgets = config.widgets.copy(hosted = current.widgets.hosted),
                appearance = config.appearance.copy(
                    homeImagePath = current.appearance.homeImagePath,
                    homeImageWallpaperId = current.appearance.homeImageWallpaperId,
                ),
            )
        }
    }

    // ----- Image de l'accueil (effet parallaxe) -----

    private val homeImages get() = container.homeImages

    /** Mises à jour d'Épure et du module. */
    val updates get() = container.updates

    /** Importe une image de la galerie comme image de l'accueil ; renvoie false si elle est illisible. */
    suspend fun importHomeImage(uri: Uri): Boolean {
        val path = withContext(Dispatchers.IO) { homeImages.import(uri) } ?: return false
        setHomeImage(path, withContext(Dispatchers.IO) { homeImages.systemWallpaperId() })
        return true
    }

    /**
     * Fond d'écran Rivage : image de l'accueil (avec l'effet parallaxe) et fond d'écran du
     * téléphone (écran verrouillé compris). Renvoie false en cas d'échec.
     */
    suspend fun useRivageWallpaper(): Boolean {
        val path = withContext(Dispatchers.IO) { homeImages.importResource(R.drawable.rivage_wallpaper) } ?: return false
        val id = withContext(Dispatchers.IO) {
            homeImages.applyResourceAsSystemWallpaper(R.drawable.rivage_wallpaper) ?: homeImages.systemWallpaperId()
        }
        setHomeImage(path, id)
        return true
    }

    /** Met l'image de l'accueil en fond d'écran du téléphone (écran verrouillé, couleurs). */
    suspend fun applyHomeImageToSystem(): Boolean {
        val path = configRepository.current().appearance.homeImagePath ?: return false
        val id = withContext(Dispatchers.IO) { homeImages.applyAsSystemWallpaper(path) } ?: return false
        configRepository.update { it.withHomeImage(path, id) }
        return true
    }

    /** Revient au fond d'écran du téléphone. */
    fun removeHomeImage() {
        viewModelScope.launch {
            configRepository.update { it.withHomeImage(null, null) }
            withContext(Dispatchers.IO) { homeImages.cleanUp(keep = null) }
        }
    }

    private suspend fun setHomeImage(path: String, systemWallpaperId: Int?) {
        configRepository.update { it.withHomeImage(path, systemWallpaperId) }
        withContext(Dispatchers.IO) { homeImages.cleanUp(keep = path) }
    }
}
