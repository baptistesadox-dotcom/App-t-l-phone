package fr.epure.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import fr.epure.launcher.data.calendar.AgendaEvent
import fr.epure.launcher.domain.SystemShortcut

/** Petites actions système (ouvrir l'horloge, l'agenda, les notifications…). */
object SystemActions {
    private const val TAG = "SystemActions"

    /** Lance [intent] ; affiche [fallbackMessage] si aucune app ne sait l'ouvrir. */
    fun start(context: Context, intent: Intent, fallbackMessage: String? = null): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        fallbackMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Action refusée : $intent", e)
        fallbackMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        false
    }

    /**
     * Déroule le volet des notifications. Android ne propose pas d'API publique pour cela :
     * on utilise la méthode qu'emploient les launchers, sans effet si elle est indisponible.
     */
    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context) {
        try {
            val service = context.getSystemService("statusbar") ?: return
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        } catch (e: Exception) {
            Log.i(TAG, "Volet des notifications indisponible", e)
        }
    }

    fun openClock(context: Context) =
        start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS), context.getString(R.string.error_no_app))

    fun openCalendar(context: Context, timeMillis: Long = System.currentTimeMillis()) {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(timeMillis.toString()).build()
        start(context, Intent(Intent.ACTION_VIEW).setData(uri), context.getString(R.string.error_no_app))
    }

    fun openEvent(context: Context, event: AgendaEvent) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
        start(context, intent, context.getString(R.string.error_no_app))
    }

    /** Réglages de Play Protect (ou, à défaut, le Play Store). */
    fun openPlayProtect(context: Context) {
        if (start(context, Intent("com.google.android.gms.settings.VERIFY_APPS_SETTINGS"))) return
        val store = context.packageManager.getLaunchIntentForPackage("com.android.vending")
        if (store != null) start(context, store, context.getString(R.string.error_no_app))
    }

    /** Ouvre un réglage d'Android trouvé par la recherche (ou un réglage plus général). */
    fun openShortcut(context: Context, shortcut: SystemShortcut): Boolean {
        val action = shortcut.action ?: return false
        if (action == Intent.ACTION_SET_WALLPAPER) {
            openWallpaperPicker(context)
            return true
        }
        return start(context, Intent(action)) ||
            (shortcut.fallbackAction?.let { start(context, Intent(it)) } ?: false) ||
            start(context, Intent(Settings.ACTION_SETTINGS), context.getString(R.string.error_no_app))
    }

    /** Ouvre le clavier du téléphone avec le numéro (l'appel part quand on appuie sur Appeler). */
    fun dial(context: Context, number: String) =
        start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)), context.getString(R.string.error_no_app))

    fun sms(context: Context, number: String) =
        start(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)), context.getString(R.string.error_no_app))

    fun webSearch(context: Context, query: String) {
        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        if (!start(context, search)) {
            val url = "https://www.google.com/search?q=" + Uri.encode(query)
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), context.getString(R.string.error_no_app))
        }
    }

    fun openWallpaperPicker(context: Context) {
        val chooser = Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), context.getString(R.string.home_menu_wallpaper))
        start(context, chooser, context.getString(R.string.error_no_app))
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        start(context, intent)
    }

    /** Vrai si Épure est l'écran d'accueil par défaut. */
    fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** Intent qui demande à l'utilisateur de choisir Épure comme écran d'accueil. */
    fun defaultLauncherIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }
}
