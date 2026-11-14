package fr.epure.launcher.data.notifications

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Notifications en cours, regroupées par app. Alimenté par [NotificationsService]
 * une fois que l'utilisateur a autorisé l'accès aux notifications.
 */
class NotificationHub(private val context: Context, private val scope: CoroutineScope) {

    private val _byApp = MutableStateFlow<Map<AppUserKey, List<NotificationPreview>>>(emptyMap())
    val byApp: StateFlow<Map<AppUserKey, List<NotificationPreview>>> = _byApp.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile
    private var service: NotificationListenerService? = null
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (request in refreshRequests) {
                load()
                // Regroupe les rafales (une app qui met à jour plusieurs notifications d'un coup).
                delay(150)
            }
        }
    }

    /** Vrai si l'utilisateur a donné l'accès aux notifications à Épure. */
    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    internal fun attach(service: NotificationListenerService) {
        this.service = service
        _connected.value = true
        requestRefresh()
    }

    internal fun detach(service: NotificationListenerService) {
        if (this.service !== service) return
        this.service = null
        _connected.value = false
        _byApp.value = emptyMap()
    }

    fun requestRefresh() {
        refreshRequests.trySend(Unit)
    }

    private fun load() {
        val current = service ?: return
        val active = try {
            current.activeNotifications
        } catch (e: Exception) {
            Log.w(TAG, "Notifications illisibles", e)
            null
        } ?: return
        _byApp.value = NotificationParser.group(
            active.filter { it.packageName != context.packageName }.mapNotNull { NotificationParser.parse(it) },
        )
    }

    /** Si l'accès est accordé mais que le service a été arrêté par le système, on le relance. */
    fun ensureBound() {
        if (_connected.value || !hasAccess()) return
        runCatching { NotificationListenerService.requestRebind(ComponentName(context, NotificationsService::class.java)) }
    }

    /** Ouvre la notification comme depuis le volet. Renvoie false si elle n'est plus valable. */
    fun open(preview: NotificationPreview, launcher: Context): Boolean {
        val intent = preview.contentIntent ?: return false
        return try {
            val options = ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // L'écran d'accueil est au premier plan : il autorise l'ouverture de l'app.
                    @Suppress("DEPRECATION")
                    setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
            }.toBundle()
            intent.send(launcher, 0, null, null, null, null, options)
            if (preview.autoCancel) dismiss(preview)
            true
        } catch (e: PendingIntent.CanceledException) {
            Log.i(TAG, "Notification expirée : ${preview.key}", e)
            false
        }
    }

    fun dismiss(preview: NotificationPreview) {
        if (!preview.clearable) return
        runCatching { service?.cancelNotification(preview.key) }
        requestRefresh()
    }

    /** Écran des réglages Android où l'on autorise l'accès aux notifications. */
    fun accessSettingsIntent(): Intent {
        val component = ComponentName(context, NotificationsService::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
    }

    private companion object {
        const val TAG = "Notifications"
    }
}
