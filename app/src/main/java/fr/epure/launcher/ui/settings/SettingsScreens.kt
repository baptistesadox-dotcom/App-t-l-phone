package fr.epure.launcher.ui.settings

import android.appwidget.AppWidgetManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.epure.launcher.R
import fr.epure.launcher.data.config.Appearance
import fr.epure.launcher.data.config.FontChoice
import fr.epure.launcher.data.config.HomeItem
import fr.epure.launcher.data.config.HomeStyle
import fr.epure.launcher.data.config.IconStyle
import fr.epure.launcher.data.config.HomeTextColor
import fr.epure.launcher.data.config.LauncherConfig
import fr.epure.launcher.data.config.ScrollerSide
import fr.epure.launcher.data.config.ScrollerSpeed
import fr.epure.launcher.data.config.ThemeMode
import fr.epure.launcher.data.config.WeatherLocation
import fr.epure.launcher.data.config.WidgetsConfig
import fr.epure.launcher.data.config.addFavorite
import fr.epure.launcher.data.config.hideApp
import fr.epure.launcher.data.config.moveFolder
import fr.epure.launcher.data.config.moveHomeItem
import fr.epure.launcher.data.config.removeHomeItemAt
import fr.epure.launcher.data.config.removeHostedWidget
import fr.epure.launcher.data.config.renameApp
import fr.epure.launcher.data.config.unhideApp
import fr.epure.launcher.data.config.updateAppearance
import fr.epure.launcher.data.config.updateWidgets
import fr.epure.launcher.data.icons.IconPackInfo
import fr.epure.launcher.data.notifications.NotificationModuleStatus
import fr.epure.launcher.ui.FolderEditorRequest
import fr.epure.launcher.ui.LauncherState
import fr.epure.launcher.ui.NoOpActions
import fr.epure.launcher.ui.common.AppIcon
import fr.epure.launcher.ui.sheets.AppChecklist
import fr.epure.launcher.ui.sheets.FolderEditorDialog
import fr.epure.launcher.ui.sheets.FullScreenDialog
import fr.epure.launcher.ui.theme.AccentPalette
import fr.epure.launcher.ui.theme.Fonts
import fr.epure.launcher.ui.wallpaper.TiltSensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SettingsPage { MAIN, WIDGETS, FAVORITES, APPEARANCE, FOLDERS, APPS, BACKUP, ABOUT }

/** Actions qui demandent l'activité (permissions, sélecteurs de fichiers…). */
interface SettingsCallbacks {
    fun requestDefaultLauncher()
    fun requestCalendarPermission()
    fun hasCalendarPermission(): Boolean
    fun pickFont()
    fun exportBackup()
    fun importBackup()
    fun addAndroidWidget()
    fun removeAndroidWidget(appWidgetId: Int)
    fun openWallpaperPicker()
    fun openUrl(url: String)
    fun showMessage(message: String)
    fun notificationStatus(): NotificationModuleStatus
    fun openNotificationAccess()
    fun openNotificationModuleDetails()
    fun applyRivageWallpaper()
    fun pickHomeImage()
    fun applyHomeImageToSystem()
    fun hasContactsPermission(): Boolean
    fun requestContactsPermission()
    fun isLockServiceEnabled(): Boolean
    /** Le module installé contient-il le service de verrouillage (version 1.1 ou plus) ? */
    fun moduleSupportsLock(): Boolean
    fun openLockSettings()
}

/** Racine des réglages avec une navigation simple entre pages. */
@Composable
fun SettingsApp(
    vm: SettingsViewModel,
    config: LauncherConfig,
    state: LauncherState?,
    startPage: SettingsPage,
    isDefaultLauncher: Boolean,
    versionName: String,
    callbacks: SettingsCallbacks,
    onFinish: () -> Unit,
) {
    var stack by rememberSaveable { mutableStateOf(listOf(startPage.name)) }
    // Une page disparue d'une ancienne version (ex. « Thème ») ramène à l'accueil des réglages.
    val page = SettingsPage.entries.firstOrNull { it.name == stack.last() } ?: SettingsPage.MAIN
    val navigate: (SettingsPage) -> Unit = { stack = stack + it.name }
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) else onFinish() }
    BackHandler(enabled = stack.size > 1) { back() }

    UpdateConfirmationEffect(vm.updates)
    when (page) {
        SettingsPage.MAIN -> MainPage(vm, isDefaultLauncher, callbacks, navigate, onFinish)
        SettingsPage.WIDGETS -> WidgetsPage(vm, config, callbacks, back)
        SettingsPage.FAVORITES -> FavoritesPage(vm, config, state, back)
        SettingsPage.APPEARANCE -> AppearancePage(vm, config, callbacks, back)
        SettingsPage.FOLDERS -> FoldersPage(vm, state, callbacks, back)
        SettingsPage.APPS -> AppsPage(vm, state, back)
        SettingsPage.BACKUP -> BackupPage(callbacks, back)
        SettingsPage.ABOUT -> AboutPage(vm, versionName, callbacks, back)
    }
}

@Composable
private fun MainPage(
    vm: SettingsViewModel,
    isDefaultLauncher: Boolean,
    callbacks: SettingsCallbacks,
    navigate: (SettingsPage) -> Unit,
    onFinish: () -> Unit,
) {
    SettingsScaffold(title = stringResource(R.string.settings_title), onBack = onFinish) {
        if (!isDefaultLauncher) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.default_launcher_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.default_launcher_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = callbacks::requestDefaultLauncher) {
                        Text(stringResource(R.string.default_launcher_button))
                    }
                }
            }
        }
        val updateState by vm.updates.state.collectAsStateWithLifecycle()
        UpdateBanner(updateState) { navigate(SettingsPage.ABOUT) }
        SettingsItem(stringResource(R.string.settings_widgets), stringResource(R.string.settings_widgets_sub), Icons.Outlined.Widgets, onClick = { navigate(SettingsPage.WIDGETS) })
        SettingsItem(stringResource(R.string.settings_favorites), stringResource(R.string.settings_favorites_sub), Icons.Outlined.Star, onClick = { navigate(SettingsPage.FAVORITES) })
        SettingsItem(stringResource(R.string.settings_appearance), stringResource(R.string.settings_appearance_sub), Icons.Outlined.Palette, onClick = { navigate(SettingsPage.APPEARANCE) })
        SettingsItem(stringResource(R.string.settings_folders), stringResource(R.string.settings_folders_sub), Icons.Outlined.Folder, onClick = { navigate(SettingsPage.FOLDERS) })
        SettingsItem(stringResource(R.string.settings_apps), stringResource(R.string.settings_apps_sub), Icons.Outlined.VisibilityOff, onClick = { navigate(SettingsPage.APPS) })
        SettingsItem(stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_sub), Icons.Outlined.Backup, onClick = { navigate(SettingsPage.BACKUP) })
        SettingsItem(stringResource(R.string.settings_about), null, Icons.Outlined.Info, onClick = { navigate(SettingsPage.ABOUT) })
    }
}

// ----- Accueil et widgets -----

@Composable
private fun WidgetsPage(vm: SettingsViewModel, config: LauncherConfig, callbacks: SettingsCallbacks, back: () -> Unit) {
    val widgets = config.widgets
    val context = LocalContext.current
    var citySearch by remember { mutableStateOf(false) }
    fun update(transform: (WidgetsConfig) -> WidgetsConfig) = vm.update { it.updateWidgets(transform) }

    SettingsScaffold(title = stringResource(R.string.settings_widgets), onBack = back) {
        SettingsSection(stringResource(R.string.section_clock))
        SettingsSwitch(stringResource(R.string.widget_clock), widgets.showClock, { v -> update { it.copy(showClock = v) } })
        SettingsSwitch(stringResource(R.string.widget_date), widgets.showDate, { v -> update { it.copy(showDate = v) } })

        SettingsSection(stringResource(R.string.section_weather))
        SettingsSwitch(stringResource(R.string.widget_weather), widgets.showWeather, { v ->
            update { it.copy(showWeather = v) }
            if (v && widgets.weatherLocation == null) citySearch = true
        })
        SettingsItem(
            title = stringResource(R.string.weather_city),
            subtitle = widgets.weatherLocation?.let { listOfNotNull(it.name, it.region).joinToString(" · ") }
                ?: stringResource(R.string.weather_city_none),
            icon = Icons.Outlined.LocationOn,
            onClick = { citySearch = true },
        )

        SettingsSection(stringResource(R.string.section_agenda))
        SettingsSwitch(
            title = stringResource(R.string.widget_agenda),
            subtitle = if (widgets.showAgenda && !callbacks.hasCalendarPermission()) stringResource(R.string.agenda_permission_needed) else null,
            checked = widgets.showAgenda,
            onChange = { v ->
                update { it.copy(showAgenda = v) }
                if (v && !callbacks.hasCalendarPermission()) callbacks.requestCalendarPermission()
            },
        )
        if (widgets.showAgenda) {
            SettingsSlider(
                title = stringResource(R.string.agenda_days),
                value = widgets.agendaDays.toFloat(),
                range = 1f..7f,
                steps = 5,
                valueLabel = { days -> pluralStringResourceSafe(context, R.plurals.days, days.roundToInt()) },
                onChange = { v -> update { it.copy(agendaDays = v.roundToInt()) } },
            )
        }

        SettingsSection(stringResource(R.string.section_todo))
        SettingsSwitch(stringResource(R.string.widget_todo), widgets.showTodo, { v -> update { it.copy(showTodo = v) } })

        SettingsSection(stringResource(R.string.section_android_widgets))
        if (widgets.hosted.isEmpty()) SettingsNote(stringResource(R.string.android_widgets_none))
        widgets.hosted.forEach { hosted ->
            val label = remember(hosted.appWidgetId) {
                val manager = AppWidgetManager.getInstance(context)
                manager.getAppWidgetInfo(hosted.appWidgetId)?.loadLabel(context.packageManager)
            } ?: stringResource(R.string.widget_missing)
            SettingsItem(
                title = label,
                subtitle = stringResource(R.string.widget_height, hosted.heightDp),
                icon = Icons.Outlined.Widgets,
                trailing = {
                    SmallIconButton(Icons.Outlined.Delete, stringResource(R.string.widget_remove)) {
                        callbacks.removeAndroidWidget(hosted.appWidgetId)
                        vm.update { it.removeHostedWidget(hosted.appWidgetId) }
                    }
                },
            )
        }
        ButtonRow {
            OutlinedButton(onClick = callbacks::addAndroidWidget) { Text(stringResource(R.string.home_menu_add_widget)) }
        }

        SettingsSection(stringResource(R.string.section_notifications))
        var status by remember { mutableStateOf(callbacks.notificationStatus()) }
        // Relu au retour des réglages Android ou après l'installation du module.
        LifecycleResumeEffect(Unit) {
            status = callbacks.notificationStatus()
            onPauseOrDispose { }
        }
        when (status) {
            NotificationModuleStatus.NOT_INSTALLED -> {
                SettingsNote(stringResource(R.string.notif_module_missing))
                SettingsNote(stringResource(R.string.notif_module_install_steps))
                ModuleInstallButton(vm.updates)
            }
            NotificationModuleStatus.NO_ACCESS -> {
                SettingsNote(stringResource(R.string.notif_module_no_access))
                ButtonRow {
                    OutlinedButton(onClick = callbacks::openNotificationAccess) { Text(stringResource(R.string.notif_grant)) }
                }
                SettingsNote(stringResource(R.string.notif_restricted_help))
                ButtonRow {
                    TextButton(onClick = callbacks::openNotificationModuleDetails) { Text(stringResource(R.string.notif_open_app_info)) }
                }
            }
            NotificationModuleStatus.ACTIVE -> SettingsNote(stringResource(R.string.notif_module_active))
        }
        SettingsSwitch(
            title = stringResource(R.string.notif_show_home),
            checked = config.notifications.showOnHome,
            enabled = status == NotificationModuleStatus.ACTIVE,
            onChange = { v -> vm.update { it.copy(notifications = it.notifications.copy(showOnHome = v)) } },
        )
        SettingsSwitch(
            title = stringResource(R.string.notif_dots),
            checked = config.notifications.showDots,
            enabled = status == NotificationModuleStatus.ACTIVE,
            onChange = { v -> vm.update { it.copy(notifications = it.notifications.copy(showDots = v)) } },
        )

        SettingsSection(stringResource(R.string.section_media))
        SettingsSwitch(
            title = stringResource(R.string.widget_media),
            subtitle = stringResource(R.string.widget_media_sub),
            checked = widgets.showMedia,
            onChange = { v -> update { it.copy(showMedia = v) } },
        )

        SettingsSection(stringResource(R.string.section_search))
        val search = config.search
        var contactsAllowed by remember { mutableStateOf(callbacks.hasContactsPermission()) }
        LifecycleResumeEffect(Unit) {
            contactsAllowed = callbacks.hasContactsPermission()
            onPauseOrDispose { }
        }
        SettingsSwitch(
            title = stringResource(R.string.search_option_contacts),
            subtitle = stringResource(
                if (search.contacts && !contactsAllowed) R.string.search_option_contacts_missing else R.string.search_option_contacts_sub,
            ),
            checked = search.contacts,
            onChange = { v ->
                vm.update { it.copy(search = it.search.copy(contacts = v)) }
                if (v && !contactsAllowed) callbacks.requestContactsPermission()
            },
        )
        SettingsSwitch(
            title = stringResource(R.string.search_option_calculator),
            subtitle = stringResource(R.string.search_option_calculator_sub),
            checked = search.calculator,
            onChange = { v -> vm.update { it.copy(search = it.search.copy(calculator = v)) } },
        )
        SettingsSwitch(
            title = stringResource(R.string.search_option_settings),
            subtitle = stringResource(R.string.search_option_settings_sub),
            checked = search.systemSettings,
            onChange = { v -> vm.update { it.copy(search = it.search.copy(systemSettings = v)) } },
        )

        SettingsSection(stringResource(R.string.section_gestures))
        SettingsSwitch(
            title = stringResource(R.string.gesture_swipe_down),
            checked = config.gestures.swipeDownNotifications,
            onChange = { v -> vm.update { it.copy(gestures = it.gestures.copy(swipeDownNotifications = v)) } },
        )
        SettingsSwitch(
            title = stringResource(R.string.gesture_double_tap_lock),
            subtitle = stringResource(R.string.gesture_double_tap_lock_sub),
            checked = config.gestures.doubleTapToLock,
            onChange = { v -> vm.update { it.copy(gestures = it.gestures.copy(doubleTapToLock = v)) } },
        )
        if (config.gestures.doubleTapToLock) {
            var lockReady by remember { mutableStateOf(callbacks.isLockServiceEnabled()) }
            LifecycleResumeEffect(Unit) {
                lockReady = callbacks.isLockServiceEnabled()
                onPauseOrDispose { }
            }
            when {
                lockReady -> SettingsNote(stringResource(R.string.lock_ready))
                status == NotificationModuleStatus.NOT_INSTALLED || !callbacks.moduleSupportsLock() ->
                    SettingsNote(stringResource(R.string.lock_needs_module))
                else -> {
                    SettingsNote(stringResource(R.string.lock_needs_service))
                    ButtonRow {
                        OutlinedButton(onClick = callbacks::openLockSettings) { Text(stringResource(R.string.lock_enable)) }
                        TextButton(onClick = callbacks::openNotificationModuleDetails) { Text(stringResource(R.string.notif_open_app_info)) }
                    }
                }
            }
        }
        SettingsNote(stringResource(R.string.gesture_note))
    }

    if (citySearch) {
        CitySearchDialog(
            vm = vm,
            onPick = { location -> update { it.copy(weatherLocation = location, showWeather = true) } },
            onDismiss = { citySearch = false },
        )
    }
}

private fun pluralStringResourceSafe(context: android.content.Context, id: Int, count: Int): String =
    context.resources.getQuantityString(id, count, count)

@Composable
private fun CitySearchDialog(vm: SettingsViewModel, onPick: (WeatherLocation) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<WeatherLocation>?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun search() {
        if (query.isBlank()) return
        loading = true
        scope.launch {
            val found = vm.searchCity(query)
            failed = found == null
            results = found.orEmpty()
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weather_city_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.weather_city_hint)) },
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { search() }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    failed -> Text(stringResource(R.string.weather_city_error), color = MaterialTheme.colorScheme.error)
                    results?.isEmpty() == true -> Text(stringResource(R.string.weather_city_no_result))
                    else -> Column(
                        Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        results.orEmpty().forEach { location ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPick(location)
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(location.name, style = MaterialTheme.typography.bodyLarge)
                                location.region?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::search) { Text(stringResource(R.string.action_search)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ----- Favoris -----

@Composable
private fun FavoritesPage(vm: SettingsViewModel, config: LauncherConfig, state: LauncherState?, back: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    SettingsScaffold(title = stringResource(R.string.settings_favorites), onBack = back) {
        SettingsNote(stringResource(R.string.favorites_note))
        config.home.forEachIndexed { index, item ->
            val (title, leading) = when (item) {
                is HomeItem.App -> {
                    val entry = state?.appByKey(item.key)
                    (entry?.label ?: item.key) to entry
                }
                is HomeItem.FolderRef -> (state?.folderById(item.folderId)?.name ?: "?") to null
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            ) {
                if (leading != null) {
                    AppIcon(app = leading.app, label = leading.label, size = 32.dp)
                } else {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                SmallIconButton(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.action_move_up), enabled = index > 0) {
                    vm.update { it.moveHomeItem(index, index - 1) }
                }
                SmallIconButton(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.action_move_down), enabled = index < config.home.lastIndex) {
                    vm.update { it.moveHomeItem(index, index + 1) }
                }
                SmallIconButton(Icons.Outlined.Delete, stringResource(R.string.action_remove)) {
                    vm.update { it.removeHomeItemAt(index) }
                }
            }
        }
        ButtonRow {
            OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.favorites_add)) }
        }
    }
    if (picking && state != null) {
        val initial = remember { config.home.filterIsInstance<HomeItem.App>().map { it.key }.toSet() }
        var selected by remember { mutableStateOf(initial) }
        FullScreenDialog(
            title = stringResource(R.string.favorites_add),
            onDismiss = { picking = false },
            action = {
                TextButton(onClick = {
                    vm.update { config ->
                        val removed = initial - selected
                        val kept = config.copy(home = config.home.filterNot { it is HomeItem.App && it.key in removed })
                        state.apps.map { it.key }.filter { it in selected && it !in initial }
                            .fold(kept) { acc, key -> acc.addFavorite(key) }
                    }
                    picking = false
                }) { Text(stringResource(R.string.action_save)) }
            },
        ) {
            AppChecklist(
                apps = state.apps,
                selected = selected,
                onToggle = { key -> selected = if (key in selected) selected - key else selected + key },
            )
        }
    }
}

// ----- Apparence -----

@Composable
private fun AppearancePage(vm: SettingsViewModel, config: LauncherConfig, callbacks: SettingsCallbacks, back: () -> Unit) {
    val appearance = config.appearance
    val context = LocalContext.current
    var choosingPack by remember { mutableStateOf(false) }
    var confirmWallpaper by remember { mutableStateOf(false) }
    fun update(transform: (Appearance) -> Appearance) = vm.update { it.updateAppearance(transform) }

    SettingsScaffold(title = stringResource(R.string.settings_appearance), onBack = back) {
        SettingsNote(stringResource(R.string.appearance_note))

        SettingsSection(stringResource(R.string.section_layout))
        SettingsChoice(
            title = null,
            options = listOf(
                HomeStyle.LIST to stringResource(R.string.home_style_list),
                HomeStyle.TYPOGRAPHIC to stringResource(R.string.home_style_typo),
            ),
            selected = appearance.homeStyle,
            onSelect = { v -> update { it.copy(homeStyle = v) } },
        )
        SettingsNote(
            stringResource(
                if (appearance.homeStyle == HomeStyle.TYPOGRAPHIC) R.string.home_style_typo_sub else R.string.home_style_list_sub,
            ),
        )
        if (appearance.homeStyle == HomeStyle.TYPOGRAPHIC) {
            SettingsSwitch(
                title = stringResource(R.string.widget_greeting),
                subtitle = stringResource(R.string.widget_greeting_sub),
                checked = config.widgets.showGreeting,
                onChange = { v -> vm.update { c -> c.updateWidgets { it.copy(showGreeting = v) } } },
            )
        }

        SettingsSection(stringResource(R.string.section_icons))
        SettingsChoice(
            title = stringResource(R.string.icon_style),
            options = listOf(
                IconStyle.LINE to stringResource(R.string.icon_style_line),
                IconStyle.ORIGINAL to stringResource(R.string.icon_style_original),
            ),
            selected = appearance.iconStyle,
            onSelect = { v -> update { it.copy(iconStyle = v) } },
        )
        SettingsSwitch(stringResource(R.string.show_icons), appearance.showIcons, { v -> update { it.copy(showIcons = v) } })
        if (appearance.showIcons) {
            SettingsSlider(
                title = stringResource(R.string.icon_size),
                value = appearance.iconSizeDp.toFloat(),
                range = 24f..56f,
                steps = 7,
                valueLabel = { "${it.roundToInt()} dp" },
                onChange = { v -> update { it.copy(iconSizeDp = v.roundToInt()) } },
            )
        }
        val packsUsable = appearance.iconStyle == IconStyle.ORIGINAL
        SettingsItem(
            title = stringResource(R.string.icon_pack),
            subtitle = if (packsUsable) appearance.iconPack ?: stringResource(R.string.icon_pack_none) else stringResource(R.string.icon_pack_line_note),
            icon = Icons.Outlined.Apps,
            enabled = packsUsable,
            onClick = { choosingPack = true },
        )

        SettingsSection(stringResource(R.string.section_wallpaper))
        val homeImagePath = appearance.homeImagePath
        SettingsItem(
            title = stringResource(R.string.home_image),
            subtitle = stringResource(if (homeImagePath == null) R.string.home_image_none else R.string.home_image_set),
            icon = Icons.Outlined.Image,
            onClick = callbacks::pickHomeImage,
            trailing = homeImagePath?.let { path -> { HomeImageThumbnail(path) } },
        )
        if (homeImagePath == null) {
            SettingsNote(stringResource(R.string.parallax_note))
            ButtonRow {
                OutlinedButton(onClick = callbacks::pickHomeImage) { Text(stringResource(R.string.home_image_pick)) }
            }
        } else {
            val hasSensor = remember { TiltSensor.isAvailable(context) }
            SettingsSwitch(
                title = stringResource(R.string.parallax),
                subtitle = stringResource(if (hasSensor) R.string.parallax_sub else R.string.parallax_no_sensor),
                checked = appearance.parallax && hasSensor,
                enabled = hasSensor,
                onChange = { v -> update { it.copy(parallax = v) } },
            )
            if (appearance.parallax && hasSensor) {
                SettingsSlider(
                    title = stringResource(R.string.parallax_strength),
                    value = appearance.parallaxStrength,
                    range = 0.2f..1f,
                    steps = 7,
                    valueLabel = { "${(it * 100).roundToInt()} %" },
                    onChange = { v -> update { it.copy(parallaxStrength = v) } },
                )
            }
            SettingsItem(
                title = stringResource(R.string.home_image_to_system),
                subtitle = stringResource(R.string.home_image_to_system_sub),
                icon = Icons.Outlined.Lock,
                onClick = callbacks::applyHomeImageToSystem,
            )
            SettingsItem(
                title = stringResource(R.string.home_image_remove),
                subtitle = stringResource(R.string.home_image_remove_sub),
                icon = Icons.Outlined.Delete,
                onClick = vm::removeHomeImage,
            )
        }
        SettingsItem(
            title = stringResource(R.string.system_wallpaper),
            subtitle = stringResource(R.string.system_wallpaper_sub),
            icon = Icons.Outlined.Wallpaper,
            onClick = callbacks::openWallpaperPicker,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Image(
                painter = painterResource(R.drawable.rivage_wallpaper),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 64.dp, height = 128.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.rivage_wallpaper), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.rivage_wallpaper_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { confirmWallpaper = true }) { Text(stringResource(R.string.rivage_wallpaper_apply)) }
            }
        }
        SettingsChoice(
            title = stringResource(R.string.home_text_color),
            options = listOf(
                HomeTextColor.AUTO to stringResource(R.string.home_text_auto),
                HomeTextColor.LIGHT to stringResource(R.string.home_text_light),
                HomeTextColor.DARK to stringResource(R.string.home_text_dark),
            ),
            selected = appearance.homeTextColor,
            onSelect = { v -> update { it.copy(homeTextColor = v) } },
        )
        SettingsSlider(
            title = stringResource(R.string.wallpaper_dim),
            value = appearance.wallpaperDim,
            range = 0f..0.8f,
            valueLabel = { "${(it * 100).roundToInt()} %" },
            onChange = { v -> update { it.copy(wallpaperDim = v) } },
        )

        SettingsSection(stringResource(R.string.section_theme))
        SettingsChoice(
            title = null,
            options = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                ThemeMode.LIGHT to stringResource(R.string.theme_light),
                ThemeMode.DARK to stringResource(R.string.theme_dark),
            ),
            selected = appearance.themeMode,
            onSelect = { v -> update { it.copy(themeMode = v) } },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingsSwitch(
                title = stringResource(R.string.dynamic_color),
                subtitle = stringResource(R.string.dynamic_color_sub),
                checked = appearance.dynamicColor,
                onChange = { v -> update { it.copy(dynamicColor = v) } },
            )
        }
        if (!appearance.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Text(
                stringResource(R.string.accent_color),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            ) {
                AccentPalette.forEach { color ->
                    val selected = appearance.accentColor == color
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                            .clickable { update { it.copy(accentColor = color) } },
                    ) {
                        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color(0xFF15161A))
                    }
                }
            }
        }

        SettingsSection(stringResource(R.string.section_font))
        FontChoice.entries.filter { it != FontChoice.CUSTOM || appearance.customFontPath != null }.forEach { choice ->
            val family = remember(choice, appearance.customFontPath) { Fonts.family(choice, appearance.customFontPath) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { update { it.copy(font = choice) } }
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            ) {
                RadioButton(selected = appearance.font == choice, onClick = { update { it.copy(font = choice) } })
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Fonts.label(choice),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = family),
                )
            }
        }
        ButtonRow {
            OutlinedButton(onClick = callbacks::pickFont) {
                Icon(Icons.Outlined.FontDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.font_import))
            }
        }
        SettingsSlider(
            title = stringResource(R.string.text_size),
            value = appearance.textScale,
            range = 0.8f..1.4f,
            steps = 5,
            valueLabel = { "${(it * 100).roundToInt()} %" },
            onChange = { v -> update { it.copy(textScale = v) } },
        )

        SettingsSection(stringResource(R.string.section_alphabet))
        SettingsChoice(
            title = null,
            options = listOf(
                ScrollerSide.END to stringResource(R.string.alphabet_right),
                ScrollerSide.START to stringResource(R.string.alphabet_left),
            ),
            selected = appearance.scrollerSide,
            onSelect = { v -> update { it.copy(scrollerSide = v) } },
        )
        SettingsChoice(
            title = stringResource(R.string.alphabet_speed),
            options = listOf(
                ScrollerSpeed.SLOW to stringResource(R.string.alphabet_speed_slow),
                ScrollerSpeed.NORMAL to stringResource(R.string.alphabet_speed_normal),
                ScrollerSpeed.FAST to stringResource(R.string.alphabet_speed_fast),
            ),
            selected = appearance.scrollerSpeed,
            onSelect = { v -> update { it.copy(scrollerSpeed = v) } },
        )
        SettingsNote(stringResource(R.string.alphabet_speed_note))
        SettingsSwitch(
            title = stringResource(R.string.alphabet_on_home),
            checked = appearance.showScrollerOnHome,
            onChange = { v -> update { it.copy(showScrollerOnHome = v) } },
        )
    }

    if (confirmWallpaper) {
        AlertDialog(
            onDismissRequest = { confirmWallpaper = false },
            title = { Text(stringResource(R.string.rivage_wallpaper_confirm_title)) },
            text = { Text(stringResource(R.string.rivage_wallpaper_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWallpaper = false
                    callbacks.applyRivageWallpaper()
                }) { Text(stringResource(R.string.rivage_wallpaper_apply)) }
            },
            dismissButton = { TextButton(onClick = { confirmWallpaper = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (choosingPack) {
        IconPackDialog(
            vm = vm,
            current = appearance.iconPack,
            onPick = { pack -> update { it.copy(iconPack = pack) } },
            onDismiss = { choosingPack = false },
        )
    }
}

/** Aperçu de l'image de l'accueil (lue en petit, sans bloquer l'affichage). */
@Composable
private fun HomeImageThumbnail(path: String) {
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap()
            }.getOrNull()
        }
    }
    val image = thumbnail
    Box(
        Modifier
            .size(width = 36.dp, height = 64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun IconPackDialog(vm: SettingsViewModel, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val packs by produceState<List<IconPackInfo>?>(initialValue = null) { value = vm.iconPacks() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.icon_pack)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val options = listOf<IconPackInfo?>(null) + packs.orEmpty()
                options.forEach { pack ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(pack?.packageName)
                                onDismiss()
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = current == pack?.packageName, onClick = {
                            onPick(pack?.packageName)
                            onDismiss()
                        })
                        Text(pack?.label ?: stringResource(R.string.icon_pack_none))
                    }
                }
                if (packs == null) CircularProgressIndicator(Modifier.padding(16.dp))
                if (packs?.isEmpty() == true) {
                    Text(
                        stringResource(R.string.icon_pack_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

// ----- Dossiers -----

@Composable
private fun FoldersPage(vm: SettingsViewModel, state: LauncherState?, callbacks: SettingsCallbacks, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editor by remember { mutableStateOf<FolderEditorRequest?>(null) }
    val context = LocalContext.current
    SettingsScaffold(title = stringResource(R.string.settings_folders), onBack = back) {
        SettingsNote(stringResource(R.string.folders_note))
        state?.folders?.forEachIndexed { index, folder ->
            SettingsItem(
                title = folder.name,
                subtitle = pluralStringResource(R.plurals.folder_count, folder.apps.size, folder.apps.size) +
                    if (folder.folder.showAsTab) " · " + stringResource(R.string.folder_tab_flag) else "",
                icon = Icons.Outlined.Folder,
                onClick = { editor = FolderEditorRequest(folderId = folder.id) },
                trailing = {
                    Row {
                        SmallIconButton(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.action_move_up), enabled = index > 0) {
                            vm.update { it.moveFolder(folder.id, -1) }
                        }
                        SmallIconButton(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.action_move_down), enabled = index < state.folders.lastIndex) {
                            vm.update { it.moveFolder(folder.id, 1) }
                        }
                    }
                },
            )
        }
        ButtonRow {
            OutlinedButton(onClick = { editor = FolderEditorRequest(folderId = null) }) {
                Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_menu_new_folder))
            }
        }
        SettingsSection(stringResource(R.string.section_auto_folders))
        SettingsNote(stringResource(R.string.auto_folders_note))
        ButtonRow {
            OutlinedButton(onClick = {
                scope.launch {
                    val count = vm.autoCategorize()
                    callbacks.showMessage(
                        if (count == 0) context.getString(R.string.auto_folders_none)
                        else context.resources.getQuantityString(R.plurals.auto_folders_done, count, count),
                    )
                }
            }) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.auto_folders_button))
            }
        }
    }
    val request = editor
    if (request != null && state != null) {
        val actions = remember(vm) {
            object : NoOpActions() {
                override fun updateConfig(transform: (LauncherConfig) -> LauncherConfig) = vm.update(transform)
            }
        }
        FolderEditorDialog(request = request, state = state, actions = actions, onDismiss = { editor = null })
    }
}

// ----- Apps masquées et renommées -----

@Composable
private fun AppsPage(vm: SettingsViewModel, state: LauncherState?, back: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    SettingsScaffold(title = stringResource(R.string.settings_apps), onBack = back) {
        SettingsSection(stringResource(R.string.section_hidden))
        val hidden = state?.allApps?.filter { it.isHidden }.orEmpty()
        if (hidden.isEmpty()) SettingsNote(stringResource(R.string.hidden_none))
        hidden.forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            ) {
                AppIcon(app = entry.app, label = entry.label, size = 32.dp)
                Spacer(Modifier.width(16.dp))
                Text(entry.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.update { it.unhideApp(entry.key) } }) {
                    Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_show))
                }
            }
        }
        ButtonRow {
            OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.hide_apps)) }
        }

        SettingsSection(stringResource(R.string.section_renamed))
        val renamed = state?.allApps?.filter { it.isRenamed }.orEmpty()
        if (renamed.isEmpty()) SettingsNote(stringResource(R.string.renamed_none))
        renamed.forEach { entry ->
            SettingsItem(
                title = entry.label,
                subtitle = stringResource(R.string.rename_original, entry.originalLabel),
                icon = Icons.AutoMirrored.Outlined.Label,
                trailing = {
                    SmallIconButton(Icons.Outlined.Restore, stringResource(R.string.rename_reset)) {
                        vm.update { it.renameApp(entry.key, null) }
                    }
                },
            )
        }
        SettingsNote(stringResource(R.string.renamed_note))
    }
    if (picking && state != null) {
        val initial = remember { state.allApps.filter { it.isHidden }.map { it.key }.toSet() }
        var selected by remember { mutableStateOf(initial) }
        FullScreenDialog(
            title = stringResource(R.string.hide_apps),
            onDismiss = { picking = false },
            action = {
                TextButton(onClick = {
                    vm.update { config ->
                        val unhidden = (initial - selected).fold(config) { acc, key -> acc.unhideApp(key) }
                        (selected - initial).fold(unhidden) { acc, key -> acc.hideApp(key) }
                    }
                    picking = false
                }) { Text(stringResource(R.string.action_save)) }
            },
        ) {
            AppChecklist(
                apps = state.allApps,
                selected = selected,
                onToggle = { key -> selected = if (key in selected) selected - key else selected + key },
            )
        }
    }
}

// ----- Sauvegarde -----

@Composable
private fun BackupPage(callbacks: SettingsCallbacks, back: () -> Unit) {
    SettingsScaffold(title = stringResource(R.string.settings_backup), onBack = back) {
        SettingsNote(stringResource(R.string.backup_note))
        SettingsItem(stringResource(R.string.backup_export), stringResource(R.string.backup_export_sub), Icons.Outlined.Upload, onClick = callbacks::exportBackup)
        SettingsItem(stringResource(R.string.backup_import), stringResource(R.string.backup_import_sub), Icons.Outlined.Download, onClick = callbacks::importBackup)
    }
}

// ----- À propos -----

@Composable
private fun AboutPage(vm: SettingsViewModel, versionName: String, callbacks: SettingsCallbacks, back: () -> Unit) {
    var showLicenses by remember { mutableStateOf(false) }
    val context = LocalContext.current
    if (showLicenses) {
        val text = remember {
            runCatching {
                context.resources.openRawResource(R.raw.licence_polices).bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.about_fonts)) },
            text = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    SettingsScaffold(title = stringResource(R.string.settings_about), onBack = back) {
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light)
            Text(stringResource(R.string.about_version, versionName), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyLarge)
        }
        UpdatesSection(vm.updates)
        SettingsSection(stringResource(R.string.section_credits))
        SettingsItem(
            title = stringResource(R.string.about_weather_credit),
            subtitle = "open-meteo.com · CC BY 4.0",
            icon = Icons.Outlined.Home,
            onClick = { callbacks.openUrl("https://open-meteo.com/") },
        )
        SettingsItem(
            title = stringResource(R.string.about_fonts),
            subtitle = "Montserrat, Nunito, Outfit, Manrope, Playfair (SIL OFL) · Tabler Icons (MIT)",
            icon = Icons.Outlined.FontDownload,
            onClick = { showLicenses = true },
        )
    }
}
