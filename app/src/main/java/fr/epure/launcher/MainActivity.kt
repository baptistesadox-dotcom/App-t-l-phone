package fr.epure.launcher

import android.Manifest
import android.app.ActivityOptions
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import fr.epure.launcher.data.apps.AppShortcut
import fr.epure.launcher.data.calendar.AgendaEvent
import fr.epure.launcher.data.config.HostedWidget
import fr.epure.launcher.data.config.LauncherConfig
import fr.epure.launcher.data.config.addHostedWidget
import fr.epure.launcher.data.config.removeHostedWidget
import fr.epure.shared.NotificationPreview
import fr.epure.launcher.ui.AppEntry
import fr.epure.launcher.ui.LauncherActions
import fr.epure.launcher.ui.LauncherScreen
import fr.epure.launcher.ui.LauncherUiController
import fr.epure.launcher.ui.LauncherViewModel
import fr.epure.launcher.ui.common.LocalIconProvider
import fr.epure.launcher.ui.common.LoaderIconProvider
import fr.epure.launcher.ui.theme.LauncherTheme
import fr.epure.launcher.ui.theme.LocalLauncherLook
import fr.epure.launcher.ui.widgets.HostedWidgetFrame
import fr.epure.launcher.ui.widgets.rememberNow
import fr.epure.launcher.widgets.LauncherWidgetHost
import fr.epure.launcher.widgets.WidgetProviderEntry
import fr.epure.launcher.widgets.WidgetProviders
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** L'écran d'accueil (activité « HOME »). */
class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private val ui = LauncherUiController()
    private val container by lazy { appContainer }

    private lateinit var widgetHost: LauncherWidgetHost
    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }

    /** Widget en cours d'ajout (en attente d'autorisation ou de configuration). */
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    /** Vrai si le fond d'écran est clair (texte sombre conseillé). Android 12+ uniquement. */
    private val wallpaperPrefersDarkText = mutableStateOf(false)
    private var colorsListener: WallpaperManager.OnColorsChangedListener? = null

    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(this, R.string.agenda_permission_denied, Toast.LENGTH_LONG).show()
        viewModel.refreshAgenda()
    }

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingWidgetId
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return@registerForActivityResult
        if (result.resultCode == RESULT_OK) configureOrAddWidget(id) else cancelPendingWidget()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Barres système transparentes, sans voile : le fond d'écran va jusqu'aux bords.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        pendingWidgetId = savedInstanceState?.getInt(STATE_PENDING_WIDGET, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        widgetHost = LauncherWidgetHost(this)
        observeWallpaperColors()
        cleanUpOrphanWidgets()
        handleIntent(intent)

        setContent {
            val config by viewModel.config.collectAsStateWithLifecycle()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val drawer by viewModel.drawer.collectAsStateWithLifecycle()
            val query by viewModel.query.collectAsStateWithLifecycle()
            val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
            val weather by viewModel.weather.collectAsStateWithLifecycle()
            val agenda by viewModel.agenda.collectAsStateWithLifecycle()
            val notifications by viewModel.notifications.collectAsStateWithLifecycle()
            val iconGeneration by container.iconLoader.generation.collectAsStateWithLifecycle()
            val darkText by wallpaperPrefersDarkText

            val currentConfig = config ?: return@setContent
            val currentState = state ?: return@setContent
            val iconPack = currentConfig.appearance.iconPack
            val iconProvider = remember(iconPack, iconGeneration) { LoaderIconProvider(container.iconLoader, iconPack) }
            val now = rememberNow()

            LauncherTheme(appearance = currentConfig.appearance, wallpaperPrefersDarkText = darkText) {
                val look = LocalLauncherLook.current
                val view = LocalView.current
                // Icônes de la barre d'état lisibles sur le fond (accueil) ou sur la liste.
                val darkIcons = if (ui.drawerOpen) !look.isDark else look.onWallpaper.luminance() < 0.5f
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = darkIcons
                        isAppearanceLightNavigationBars = darkIcons
                    }
                }
                CompositionLocalProvider(LocalIconProvider provides iconProvider) {
                    LauncherScreen(
                        state = currentState,
                        drawer = drawer,
                        query = query,
                        selectedTab = selectedTab,
                        weather = weather,
                        agenda = agenda,
                        notifications = notifications,
                        now = now,
                        use24Hour = DateFormat.is24HourFormat(this),
                        actions = actions,
                        ui = ui,
                        hostedWidget = { widget ->
                            HostedWidgetFrame(
                                widget = widget,
                                host = widgetHost,
                                onLongPress = { ui.widgetMenu = widget },
                                onRemove = { actions.removeWidget(widget) },
                            )
                        },
                        onConfigureWeather = { openSettings(SettingsActivity.PAGE_WIDGETS) },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { widgetHost.startListening() }
            .onFailure { Log.w(TAG, "Hôte de widgets indisponible", it) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onHomeVisible()
    }

    override fun onStop() {
        super.onStop()
        runCatching { widgetHost.stopListening() }
        // Au retour, on retrouve l'accueil (et non la liste des apps restée ouverte).
        if (!isChangingConfigurations) {
            ui.goHome()
            viewModel.resetDrawer()
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            colorsListener?.let { WallpaperManager.getInstance(this).removeOnColorsChangedListener(it) }
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PENDING_WIDGET, pendingWidgetId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when {
            intent == null -> Unit
            // Bouton « Accueil » : on referme tout.
            intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME) -> {
                ui.goHome()
                viewModel.resetDrawer()
            }
            intent.action == ACTION_ADD_WIDGET -> {
                ui.goHome()
                ui.widgetPickerOpen = true
            }
        }
    }

    // ----- Couleurs du fond d'écran -----

    private fun observeWallpaperColors() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
        val manager = WallpaperManager.getInstance(this)
        updateWallpaperHint(runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull())
        val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) updateWallpaperHint(colors)
        }
        runCatching { manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper())) }
            .onSuccess { colorsListener = listener }
    }

    private fun updateWallpaperHint(colors: WallpaperColors?) {
        wallpaperPrefersDarkText.value = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && colors != null &&
            (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
    }

    // ----- Widgets Android -----

    /** Supprime les widgets alloués mais jamais ajoutés (ajout interrompu). */
    private fun cleanUpOrphanWidgets() {
        lifecycleScope.launch {
            val config = viewModel.config.filterNotNull().first()
            val known = config.widgets.hosted.mapTo(HashSet()) { it.appWidgetId }
            runCatching { widgetHost.appWidgetIds }.getOrNull()
                ?.filter { it !in known && it != pendingWidgetId }
                ?.forEach { widgetHost.deleteAppWidgetId(it) }
        }
    }

    private fun startAddWidget(entry: WidgetProviderEntry) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        val bound = runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(id, entry.info.profile, entry.info.provider, null)
        }.getOrDefault(false)
        if (bound) {
            configureOrAddWidget(id)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, entry.info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, entry.info.profile)
            runCatching { bindWidget.launch(intent) }.onFailure { cancelPendingWidget() }
        }
    }

    private fun configureOrAddWidget(id: Int) {
        val info = appWidgetManager.getAppWidgetInfo(id)
        if (info == null) {
            cancelPendingWidget()
            return
        }
        val configurationOptional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        if (info.configure != null && !configurationOptional) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_CONFIGURE_WIDGET, null)
            } catch (e: Exception) {
                Log.w(TAG, "Configuration du widget impossible", e)
                cancelPendingWidget()
                Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show()
            }
        } else {
            finishAddWidget(id, info)
        }
    }

    @Deprecated("Requis par AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            val id = pendingWidgetId
            val info = appWidgetManager.getAppWidgetInfo(id)
            if (resultCode == RESULT_OK && info != null) finishAddWidget(id, info) else cancelPendingWidget()
            return
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun finishAddWidget(id: Int, info: AppWidgetProviderInfo) {
        val density = resources.displayMetrics.density
        val height = WidgetProviders.defaultHeightDp(
            WidgetProviderEntry(info, "", "", (info.minWidth / density).toInt(), (info.minHeight / density).toInt()),
        )
        viewModel.updateConfig { it.addHostedWidget(id, height) }
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private fun cancelPendingWidget() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            runCatching { widgetHost.deleteAppWidgetId(pendingWidgetId) }
        }
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private fun openSettings(page: String? = null) {
        val intent = Intent(this, SettingsActivity::class.java)
        if (page != null) intent.putExtra(SettingsActivity.EXTRA_PAGE, page)
        startActivity(intent)
    }

    // ----- Actions de l'interface -----

    private val actions = object : LauncherActions {
        override fun launch(entry: AppEntry, bounds: Rect?) {
            val view = window.decorView
            val options = bounds?.let {
                ActivityOptions.makeScaleUpAnimation(view, it.left.toInt(), it.top.toInt(), it.width.toInt(), it.height.toInt()).toBundle()
            }
            val source = bounds?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
            if (!container.appsRepository.launch(entry.app, source, options)) {
                showMessage(getString(R.string.error_launch, entry.label))
            }
        }

        override fun openAppInfo(entry: AppEntry) {
            container.appsRepository.openAppDetails(entry.app)
        }

        override fun uninstall(entry: AppEntry) {
            SystemActions.start(this@MainActivity, container.appsRepository.uninstallIntent(entry.app), getString(R.string.error_no_app))
        }

        override suspend fun shortcutsFor(entry: AppEntry): List<AppShortcut> = viewModel.shortcutsFor(entry)

        override fun launchShortcut(shortcut: AppShortcut) {
            if (!container.appsRepository.startShortcut(shortcut, null)) {
                showMessage(getString(R.string.error_launch, shortcut.label))
            }
        }

        override fun updateConfig(transform: (LauncherConfig) -> LauncherConfig) = viewModel.updateConfig(transform)
        override fun setQuery(query: String) = viewModel.setQuery(query)
        override fun selectTab(folderId: String?) = viewModel.selectTab(folderId)
        override fun resetDrawer() = viewModel.resetDrawer()
        override fun openSettings() = this@MainActivity.openSettings()
        override fun openWallpaperPicker() = SystemActions.openWallpaperPicker(this@MainActivity)
        override fun expandNotifications() = SystemActions.expandNotifications(this@MainActivity)
        override fun webSearch(query: String) = SystemActions.webSearch(this@MainActivity, query)
        override fun openClock() {
            SystemActions.openClock(this@MainActivity)
        }
        override fun openCalendar() = SystemActions.openCalendar(this@MainActivity)
        override fun openEvent(event: AgendaEvent) = SystemActions.openEvent(this@MainActivity, event)
        override fun requestCalendarPermission() = calendarPermission.launch(Manifest.permission.READ_CALENDAR)
        override fun refreshWeather() = viewModel.refreshWeather(force = false)
        override fun addTodo(text: String) = viewModel.addTodo(text)
        override fun toggleTodo(id: String) = viewModel.toggleTodo(id)
        override fun editTodo(id: String, text: String) = viewModel.editTodo(id, text)
        override fun removeTodo(id: String) = viewModel.removeTodo(id)
        override fun clearDoneTodos() = viewModel.clearDoneTodos()
        override fun listWidgetProviders(): List<WidgetProviderEntry> = WidgetProviders.list(this@MainActivity)
        override fun addWidget(provider: WidgetProviderEntry) = startAddWidget(provider)

        override fun removeWidget(widget: HostedWidget) {
            runCatching { widgetHost.deleteAppWidgetId(widget.appWidgetId) }
            viewModel.updateConfig { it.removeHostedWidget(widget.appWidgetId) }
        }

        override fun showMessage(message: String) {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }

        override fun openNotification(preview: NotificationPreview) {
            if (!container.notificationHub.open(preview, this@MainActivity)) {
                // Notification expirée : on ouvre simplement l'app.
                viewModel.state.value?.allApps
                    ?.firstOrNull { it.app.packageName == preview.packageName && it.app.user == preview.user }
                    ?.let { launch(it, null) }
            }
        }

        override fun dismissNotification(preview: NotificationPreview) = container.notificationHub.dismiss(preview)

    }

    companion object {
        private const val TAG = "MainActivity"
        private const val STATE_PENDING_WIDGET = "pendingWidget"
        private const val REQUEST_CONFIGURE_WIDGET = 42
        const val ACTION_ADD_WIDGET = "fr.epure.launcher.action.ADD_WIDGET"
    }
}
