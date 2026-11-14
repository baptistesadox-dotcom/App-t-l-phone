package fr.epure.launcher.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** Une app lançable (une activité « LAUNCHER »), pour un profil utilisateur donné. */
data class InstalledApp(
    val key: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    /** Nom d'origine de l'app. */
    val label: String,
    /** Valeur de ApplicationInfo.category (-1 si non déclarée). */
    val category: Int,
    /** Vrai pour les apps du profil professionnel. */
    val isWorkProfile: Boolean,
    /** Infos système, utilisées pour charger l'icône (null dans les tests). */
    val info: LauncherActivityInfo? = null,
)

object AppKeys {
    /**
     * Clé stable d'une app : « paquet/classe », suivie de « #numéro » pour les
     * apps d'un autre profil (professionnel), afin de distinguer les doublons.
     */
    fun of(component: ComponentName, userSerial: Long, isMainUser: Boolean): String {
        val base = component.flattenToString()
        return if (isMainUser) base else "$base#$userSerial"
    }

    fun packageOf(key: String): String = key.substringBefore('/')
}

/** Raccourci d'app (ex. « Nouveau message »), disponible quand Épure est le launcher par défaut. */
data class AppShortcut(val id: String, val label: String, val info: ShortcutInfo)

/**
 * Liste des apps installées, tenue à jour automatiquement quand une app
 * est installée, mise à jour ou désinstallée.
 */
class AppsRepository(private val context: Context, private val scope: CoroutineScope) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    private val _apps = MutableStateFlow<List<InstalledApp>?>(null)

    /** null tant que la première lecture n'est pas terminée. */
    val apps: StateFlow<List<InstalledApp>?> = _apps.asStateFlow()

    @Volatile
    private var loadedLocale: Locale? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
        override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) = refresh()
        override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: UserHandle?) = refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        scope.launch {
            for (request in refreshRequests) {
                _apps.value = loadApps()
                // Regroupe les rafales d'événements (mise à jour de plusieurs apps).
                delay(250)
            }
        }
        refresh()
    }

    fun refresh() {
        refreshRequests.trySend(Unit)
    }

    /** Recharge si la langue a changé depuis la dernière lecture (les noms d'apps en dépendent). */
    fun refreshIfLocaleChanged() {
        val current = context.resources.configuration.locales[0]
        if (loadedLocale != null && loadedLocale != current) refresh()
    }

    private fun loadApps(): List<InstalledApp> {
        loadedLocale = context.resources.configuration.locales[0]
        val mainUser = Process.myUserHandle()
        return userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            val isMain = user == mainUser
            runCatching { launcherApps.getActivityList(null, user) }
                .onFailure { Log.w(TAG, "Impossible de lister les apps du profil $serial", it) }
                .getOrDefault(emptyList())
                .map { info ->
                    InstalledApp(
                        key = AppKeys.of(info.componentName, serial, isMain),
                        packageName = info.componentName.packageName,
                        component = info.componentName,
                        user = user,
                        label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                        category = info.applicationInfo.category,
                        isWorkProfile = !isMain,
                        info = info,
                    )
                }
        }
    }

    /** Lance l'app. Renvoie false si elle n'a pas pu être ouverte (désinstallée, profil en pause…). */
    fun launch(app: InstalledApp, sourceBounds: Rect?, options: Bundle?): Boolean = try {
        launcherApps.startMainActivity(app.component, app.user, sourceBounds, options)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Lancement impossible : ${app.key}", e)
        false
    }

    fun openAppDetails(app: InstalledApp): Boolean = try {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Infos de l'app indisponibles : ${app.key}", e)
        false
    }

    fun uninstallIntent(app: InstalledApp): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Raccourcis de l'app ; liste vide si Épure n'est pas le launcher par défaut. */
    fun shortcutsFor(app: InstalledApp): List<AppShortcut> {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setActivity(app.component)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
            )
        return runCatching { launcherApps.getShortcuts(query, app.user).orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.isEnabled }
            .sortedWith(compareBy<ShortcutInfo>({ if (it.isDynamic) 1 else 0 }, { it.rank }))
            .take(MAX_SHORTCUTS)
            .map { info ->
                AppShortcut(
                    id = info.id,
                    label = (info.shortLabel ?: info.longLabel ?: info.id).toString(),
                    info = info,
                )
            }
    }

    fun startShortcut(shortcut: AppShortcut, sourceBounds: Rect?): Boolean = try {
        launcherApps.startShortcut(shortcut.info, sourceBounds, null)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Raccourci indisponible : ${shortcut.id}", e)
        false
    }

    fun shortcutIcon(shortcut: AppShortcut, density: Int) =
        runCatching { launcherApps.getShortcutIconDrawable(shortcut.info, density) }.getOrNull()

    /**
     * Favoris proposés au premier lancement : téléphone, messages, appareil photo,
     * navigateur et galerie par défaut, s'ils existent.
     */
    fun suggestedFavorites(apps: List<InstalledApp>): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.wikipedia.org")),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
        )
        val personalApps = apps.filter { !it.isWorkProfile }
        return intents.mapNotNull { intent ->
            val pkg = runCatching {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }.getOrNull()
            pkg?.takeIf { it != "android" }?.let { p -> personalApps.firstOrNull { it.packageName == p }?.key }
        }.distinct()
    }

    private companion object {
        const val TAG = "AppsRepository"
        const val MAX_SHORTCUTS = 4
    }
}
