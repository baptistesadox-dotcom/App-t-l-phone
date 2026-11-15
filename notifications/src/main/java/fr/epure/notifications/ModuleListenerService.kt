package fr.epure.notifications

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import fr.epure.shared.NotificationParser
import fr.epure.shared.NotificationPreview
import fr.epure.shared.NotificationsContract

/** Envoi des notifications à l'écran d'accueil Épure (et à lui seul). */
object ModuleBridge {
    private const val TAG = "EpureNotifications"

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun sendSnapshot(context: Context, connected: Boolean, previews: List<NotificationPreview>) {
        val intent = Intent(NotificationsContract.ACTION_SNAPSHOT)
            .setPackage(NotificationsContract.LAUNCHER_PACKAGE)
            .putExtra(NotificationsContract.EXTRA_CONNECTED, connected)
            .putParcelableArrayListExtra(
                NotificationsContract.EXTRA_NOTIFICATIONS,
                ArrayList(previews.map(NotificationsContract::toBundle)),
            )
        runCatching { context.sendBroadcast(intent, NotificationsContract.PERMISSION) }
            .onFailure { Log.w(TAG, "Envoi au launcher impossible", it) }
    }

    /** Envoie au launcher les sessions de lecture en cours (vide si aucune). */
    fun sendMediaSessions(context: Context, tokens: List<MediaSession.Token>) {
        val intent = Intent(NotificationsContract.ACTION_MEDIA)
            .setPackage(NotificationsContract.LAUNCHER_PACKAGE)
            .putParcelableArrayListExtra(NotificationsContract.EXTRA_MEDIA_TOKENS, ArrayList(tokens))
        runCatching { context.sendBroadcast(intent, NotificationsContract.PERMISSION) }
            .onFailure { Log.w(TAG, "Envoi des lecteurs au launcher impossible", it) }
    }

    fun accessSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                NotificationsContract.listenerComponent.flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
}

/**
 * Lecteur de notifications, lancé par Android une fois l'accès autorisé.
 * À chaque changement, il envoie la liste des notifications utiles au launcher.
 */
class ModuleListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val publishRunnable = Runnable { publish() }

    /** Sessions de lecture : leur liste change quand une app commence ou arrête de jouer. */
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        ModuleBridge.sendMediaSessions(this, controllers.orEmpty().map { it.sessionToken })
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        runCatching {
            getSystemService(MediaSessionManager::class.java)
                ?.addOnActiveSessionsChangedListener(sessionsListener, ComponentName(this, ModuleListenerService::class.java), handler)
        }.onFailure { Log.w(TAG, "Sessions de lecture inaccessibles", it) }
        publish()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        runCatching { getSystemService(MediaSessionManager::class.java)?.removeOnActiveSessionsChangedListener(sessionsListener) }
        ModuleBridge.sendSnapshot(this, connected = false, previews = emptyList())
        ModuleBridge.sendMediaSessions(this, emptyList())
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = schedule()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = schedule()

    override fun onDestroy() {
        handler.removeCallbacks(publishRunnable)
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Regroupe les rafales (une app qui met à jour plusieurs notifications d'un coup). */
    private fun schedule() {
        handler.removeCallbacks(publishRunnable)
        handler.postDelayed(publishRunnable, 150)
    }

    fun publish() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val previews = active
            .filter { it.packageName != packageName && it.packageName != NotificationsContract.LAUNCHER_PACKAGE }
            .mapNotNull { NotificationParser.parse(it) }
        ModuleBridge.sendSnapshot(this, connected = true, previews = previews)
        publishMedia()
    }

    private fun publishMedia() {
        val sessions = runCatching {
            getSystemService(MediaSessionManager::class.java)
                ?.getActiveSessions(ComponentName(this, ModuleListenerService::class.java))
        }.getOrNull().orEmpty()
        ModuleBridge.sendMediaSessions(this, sessions.map { it.sessionToken })
    }

    fun dismiss(key: String) {
        runCatching { cancelNotification(key) }
    }

    companion object {
        private const val TAG = "EpureNotifications"

        @Volatile
        var instance: ModuleListenerService? = null
    }
}

/** Demandes du launcher (protégées par la permission « signature »). */
class ModuleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationsContract.ACTION_REQUEST -> {
                val service = ModuleListenerService.instance
                when {
                    service != null -> service.publish()
                    ModuleBridge.hasAccess(context) ->
                        // Accès accordé mais lecteur arrêté : Android le relance, il enverra la liste.
                        runCatching { NotificationListenerService.requestRebind(NotificationsContract.listenerComponent) }
                    else -> ModuleBridge.sendSnapshot(context, connected = false, previews = emptyList())
                }
            }
            NotificationsContract.ACTION_DISMISS ->
                intent.getStringExtra(NotificationsContract.EXTRA_KEY)?.let { ModuleListenerService.instance?.dismiss(it) }
            NotificationsContract.ACTION_LOCK -> LockService.instance?.lock()
        }
    }
}
