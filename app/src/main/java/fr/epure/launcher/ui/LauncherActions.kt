package fr.epure.launcher.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import fr.epure.launcher.data.apps.AppShortcut
import fr.epure.launcher.data.calendar.AgendaEvent
import fr.epure.launcher.data.config.HostedWidget
import fr.epure.launcher.data.config.LauncherConfig
import fr.epure.shared.NotificationPreview
import fr.epure.launcher.widgets.WidgetProviderEntry

/**
 * Actions déclenchées par l'interface. L'activité fournit la vraie implémentation
 * (lancement d'apps, permissions…) ; les tests d'affichage utilisent [NoOpActions].
 */
interface LauncherActions {
    fun launch(entry: AppEntry, bounds: Rect?)
    fun openAppInfo(entry: AppEntry)
    fun uninstall(entry: AppEntry)
    suspend fun shortcutsFor(entry: AppEntry): List<AppShortcut>
    fun launchShortcut(shortcut: AppShortcut)
    fun updateConfig(transform: (LauncherConfig) -> LauncherConfig)
    fun setQuery(query: String)
    fun selectTab(folderId: String?)
    fun resetDrawer()
    fun openSettings()
    fun openWallpaperPicker()
    fun expandNotifications()
    fun webSearch(query: String)
    fun openClock()
    fun openCalendar()
    fun openEvent(event: AgendaEvent)
    fun requestCalendarPermission()
    fun refreshWeather()
    fun addTodo(text: String)
    fun toggleTodo(id: String)
    fun editTodo(id: String, text: String)
    fun removeTodo(id: String)
    fun clearDoneTodos()
    fun listWidgetProviders(): List<WidgetProviderEntry>
    fun addWidget(provider: WidgetProviderEntry)
    fun removeWidget(widget: HostedWidget)
    fun showMessage(message: String)
    fun openNotification(preview: NotificationPreview)
    fun dismissNotification(preview: NotificationPreview)

}

open class NoOpActions : LauncherActions {
    override fun launch(entry: AppEntry, bounds: Rect?) = Unit
    override fun openAppInfo(entry: AppEntry) = Unit
    override fun uninstall(entry: AppEntry) = Unit
    override suspend fun shortcutsFor(entry: AppEntry): List<AppShortcut> = emptyList()
    override fun launchShortcut(shortcut: AppShortcut) = Unit
    override fun updateConfig(transform: (LauncherConfig) -> LauncherConfig) = Unit
    override fun setQuery(query: String) = Unit
    override fun selectTab(folderId: String?) = Unit
    override fun resetDrawer() = Unit
    override fun openSettings() = Unit
    override fun openWallpaperPicker() = Unit
    override fun expandNotifications() = Unit
    override fun webSearch(query: String) = Unit
    override fun openClock() = Unit
    override fun openCalendar() = Unit
    override fun openEvent(event: AgendaEvent) = Unit
    override fun requestCalendarPermission() = Unit
    override fun refreshWeather() = Unit
    override fun addTodo(text: String) = Unit
    override fun toggleTodo(id: String) = Unit
    override fun editTodo(id: String, text: String) = Unit
    override fun removeTodo(id: String) = Unit
    override fun clearDoneTodos() = Unit
    override fun listWidgetProviders(): List<WidgetProviderEntry> = emptyList()
    override fun addWidget(provider: WidgetProviderEntry) = Unit
    override fun removeWidget(widget: HostedWidget) = Unit
    override fun showMessage(message: String) = Unit
    override fun openNotification(preview: NotificationPreview) = Unit
    override fun dismissNotification(preview: NotificationPreview) = Unit
}

/** D'où vient l'appui long sur une app (pour proposer les bonnes actions). */
sealed interface AppMenuOrigin {
    data object Home : AppMenuOrigin
    data object Drawer : AppMenuOrigin
    data class Folder(val folderId: String) : AppMenuOrigin
}

data class AppMenuRequest(val entry: AppEntry, val origin: AppMenuOrigin)

/** Édition d'un dossier existant ([folderId]) ou création d'un nouveau (avec [initialApps]). */
data class FolderEditorRequest(
    val folderId: String?,
    val initialApps: List<String> = emptyList(),
    val addToHome: Boolean = false,
)

/** État d'affichage temporaire de l'accueil (tiroir ouvert, menus…). */
@Stable
class LauncherUiController(drawerInitiallyOpen: Boolean = false) {
    var drawerOpen by mutableStateOf(drawerInitiallyOpen)
    var expandedFolderId by mutableStateOf<String?>(null)
    var appMenu by mutableStateOf<AppMenuRequest?>(null)
    var folderMenu by mutableStateOf<FolderEntry?>(null)
    var renameApp by mutableStateOf<AppEntry?>(null)
    var addToFolder by mutableStateOf<AppEntry?>(null)
    var folderEditor by mutableStateOf<FolderEditorRequest?>(null)
    var homeMenuOpen by mutableStateOf(false)
    var widgetPickerOpen by mutableStateOf(false)
    var widgetMenu by mutableStateOf<HostedWidget?>(null)

    /** App dont toutes les notifications sont dépliées sur l'accueil (clé d'app). */
    var expandedNotifications by mutableStateOf<String?>(null)

    /** Lettre actuellement touchée sur l'alphabet (null si aucune). */
    var scrollerLetter by mutableStateOf<String?>(null)

    /** Hauteur du doigt sur l'alphabet (pixels, repère de la fenêtre). */
    var scrollerY by mutableStateOf(0f)

    /** Incrémenté à chaque appui sur « Accueil », pour remonter en haut des listes. */
    var homeResetCount by mutableStateOf(0)

    val hasOverlay: Boolean
        get() = appMenu != null || folderMenu != null || renameApp != null || addToFolder != null ||
            folderEditor != null || homeMenuOpen || widgetPickerOpen || widgetMenu != null

    fun closeOverlays() {
        appMenu = null
        folderMenu = null
        renameApp = null
        addToFolder = null
        folderEditor = null
        homeMenuOpen = false
        widgetPickerOpen = false
        widgetMenu = null
    }

    /** Retour à l'état de repos : accueil, sans menu ni dossier ouvert. */
    fun goHome() {
        closeOverlays()
        drawerOpen = false
        expandedFolderId = null
        expandedNotifications = null
        scrollerLetter = null
        homeResetCount++
    }
}
