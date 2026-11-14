package fr.epure.launcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.epure.launcher.appContainer
import fr.epure.launcher.data.apps.AppShortcut
import fr.epure.launcher.data.calendar.AgendaEvent
import fr.epure.launcher.data.config.LauncherConfig
import fr.epure.launcher.data.config.WeatherLocation
import fr.epure.launcher.data.config.addTodo
import fr.epure.launcher.data.config.clearDoneTodos
import fr.epure.launcher.data.config.editTodo
import fr.epure.launcher.data.config.removeTodo
import fr.epure.launcher.data.config.toggleTodo
import fr.epure.launcher.data.notifications.AppUserKey
import fr.epure.launcher.data.notifications.NotificationPreview
import fr.epure.launcher.data.weather.WeatherRepository
import fr.epure.launcher.domain.WeatherReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** État du widget météo ([location] : lieu auquel correspond [report]). */
data class WeatherState(
    val report: WeatherReport? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val location: WeatherLocation? = null,
)

/** État du widget agenda. */
data class AgendaState(val hasPermission: Boolean = false, val events: List<AgendaEvent> = emptyList())

/** Logique de l'écran d'accueil : données affichées et actions qui modifient la configuration. */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val configRepository = container.configRepository
    private val appsRepository = container.appsRepository

    val config: StateFlow<LauncherConfig?> =
        configRepository.config.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** null pendant le chargement initial (fraction de seconde au démarrage). */
    val state: StateFlow<LauncherState?> =
        combine(config.filterNotNull(), appsRepository.apps.filterNotNull()) { config, apps ->
            buildLauncherState(config, apps)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Dossier affiché en onglet dans la liste des apps (null = toutes les apps). */
    private val _selectedTab = MutableStateFlow<String?>(null)
    val selectedTab: StateFlow<String?> = _selectedTab.asStateFlow()

    val drawer: StateFlow<DrawerList> =
        combine(state.filterNotNull(), _query, _selectedTab) { state, query, tab ->
            val source = tab?.let { id -> state.folderById(id)?.apps } ?: state.apps
            buildDrawerList(source, query)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, DrawerList.EMPTY)

    /** Notifications en cours, par app (vide tant que l'accès n'est pas autorisé). */
    val notifications: StateFlow<Map<AppUserKey, List<NotificationPreview>>> = container.notificationHub.byApp

    private val _weather = MutableStateFlow(WeatherState())
    val weather: StateFlow<WeatherState> = _weather.asStateFlow()

    private val _agenda = MutableStateFlow(AgendaState())
    val agenda: StateFlow<AgendaState> = _agenda.asStateFlow()

    private var weatherJob: Job? = null

    init {
        seedDefaultFavorites()
        // Recharge la météo et l'agenda quand leurs réglages changent.
        viewModelScope.launch {
            config.filterNotNull()
                .map { it.widgets.showWeather to it.widgets.weatherLocation }
                .distinctUntilChanged()
                .collect { refreshWeather(force = false) }
        }
        viewModelScope.launch {
            config.filterNotNull()
                .map { it.widgets.showAgenda to it.widgets.agendaDays }
                .distinctUntilChanged()
                .collect { refreshAgenda() }
        }
        viewModelScope.launch {
            container.calendarRepository.changes().collect { refreshAgenda() }
        }
    }

    /** Au tout premier lancement, place quelques apps essentielles sur l'accueil. */
    private fun seedDefaultFavorites() {
        viewModelScope.launch {
            val initial = config.filterNotNull().first()
            if (initial.onboardingDone) return@launch
            val apps = appsRepository.apps.filterNotNull().first()
            val suggestions = withContext(Dispatchers.IO) { appsRepository.suggestedFavorites(apps) }
            configRepository.update { current ->
                if (current.onboardingDone) current
                else current.copy(
                    onboardingDone = true,
                    home = if (current.home.isEmpty()) suggestions.map { fr.epure.launcher.data.config.HomeItem.App(it) } else current.home,
                )
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun selectTab(folderId: String?) {
        _selectedTab.value = folderId
    }

    /** Remet la liste des apps à zéro (fermeture du tiroir, retour à l'accueil). */
    fun resetDrawer() {
        _query.value = ""
        _selectedTab.value = null
    }

    fun updateConfig(transform: (LauncherConfig) -> LauncherConfig) {
        viewModelScope.launch { configRepository.update(transform) }
    }

    // ----- Tâches -----

    fun addTodo(text: String) = updateConfig { it.addTodo(UUID.randomUUID().toString(), text) }
    fun toggleTodo(id: String) = updateConfig { it.toggleTodo(id) }
    fun editTodo(id: String, text: String) = updateConfig { it.editTodo(id, text) }
    fun removeTodo(id: String) = updateConfig { it.removeTodo(id) }
    fun clearDoneTodos() = updateConfig { it.clearDoneTodos() }

    // ----- Raccourcis d'apps -----

    suspend fun shortcutsFor(entry: AppEntry): List<AppShortcut> =
        withContext(Dispatchers.IO) { appsRepository.shortcutsFor(entry.app) }

    // ----- Météo et agenda -----

    /** Rafraîchit la météo si elle a plus de 30 minutes (ou tout de suite si [force]). */
    fun refreshWeather(force: Boolean) {
        val widgets = config.value?.widgets ?: return
        val location = widgets.weatherLocation
        if (!widgets.showWeather || location == null) {
            weatherJob?.cancel()
            _weather.value = WeatherState()
            return
        }
        // Une météo d'un autre lieu (ville changée) ne compte pas.
        val current = _weather.value.takeIf { it.location == location } ?: WeatherState(location = location)
        val age = current.report?.let { System.currentTimeMillis() - it.fetchedAtMillis } ?: Long.MAX_VALUE
        if (!force && (current.loading || age < WeatherRepository.REFRESH_AFTER_MILLIS)) return
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            val repo = container.weatherRepository
            val cached = current.report ?: repo.cached(location)
            _weather.value = WeatherState(report = cached, loading = true, location = location)
            val fresh = repo.forecast(location)
            _weather.value = WeatherState(
                report = fresh ?: cached,
                loading = false,
                failed = fresh == null && cached == null,
                location = location,
            )
        }
    }

    fun refreshAgenda() {
        val widgets = config.value?.widgets ?: return
        viewModelScope.launch {
            val repo = container.calendarRepository
            val permission = repo.hasPermission()
            val events = if (widgets.showAgenda && permission) repo.upcoming(widgets.agendaDays) else emptyList()
            _agenda.value = AgendaState(hasPermission = permission, events = events)
        }
    }

    /** Appelé quand l'accueil redevient visible. */
    fun onHomeVisible() {
        appsRepository.refreshIfLocaleChanged()
        container.notificationHub.ensureBound()
        refreshWeather(force = false)
        refreshAgenda()
    }
}
