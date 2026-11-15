package fr.epure.launcher.data.notifications

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import fr.epure.shared.AppUserKey
import fr.epure.shared.NotificationParser
import fr.epure.shared.NotificationPreview
import fr.epure.shared.NotificationsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** État du module « Épure Notifications ». */
enum class NotificationModuleStatus {
    /** Module pas installé : pas de notifications sur l'accueil. */
    NOT_INSTALLED,

    /** Module installé, mais l'accès aux notifications n'est pas encore autorisé. */
    NO_ACCESS,

    /** Tout fonctionne. */
    ACTIVE,
}

/**
 * Notifications en cours, regroupées par app. Elles viennent du module séparé
 * « Épure Notifications » (le launcher lui-même ne lit pas les notifications, pour
 * que Google Play Protect laisse installer ses mises à jour).
 */
class NotificationHub(private val context: Context) {

    private val _byApp = MutableStateFlow<Map<AppUserKey, List<NotificationPreview>>>(emptyMap())
    val byApp: StateFlow<Map<AppUserKey, List<NotificationPreview>>> = _byApp.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != NotificationsContract.ACTION_SNAPSHOT) return
            val connected = intent.getBooleanExtra(NotificationsContract.EXTRA_CONNECTED, false)
            val bundles = IntentCompat.getParcelableArrayListExtra(
                intent, NotificationsContract.EXTRA_NOTIFICATIONS, Bundle::class.java,
            ).orEmpty()
            _connected.value = connected
            _byApp.value = if (connected) {
                NotificationParser.group(bundles.mapNotNull(NotificationsContract::fromBundle))
            } else {
                emptyMap()
            }
        }
    }

    init {
        // Seul un expéditeur signé avec la même clé (le module) peut envoyer ces données.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(NotificationsContract.ACTION_SNAPSHOT),
            NotificationsContract.PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
        requestSnapshot()
    }

    fun isModuleInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(NotificationsContract.MODULE_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun status(): NotificationModuleStatus = when {
        !isModuleInstalled() -> NotificationModuleStatus.NOT_INSTALLED
        !NotificationManagerCompat.getEnabledListenerPackages(context).contains(NotificationsContract.MODULE_PACKAGE) ->
            NotificationModuleStatus.NO_ACCESS
        else -> NotificationModuleStatus.ACTIVE
    }

    /** Demande la liste actuelle au module (au démarrage, au retour sur l'accueil…). */
    fun requestSnapshot() {
        if (!isModuleInstalled()) {
            _connected.value = false
            _byApp.value = emptyMap()
            return
        }
        send(Intent(NotificationsContract.ACTION_REQUEST))
    }

    private fun send(intent: Intent) {
        intent.setPackage(NotificationsContract.MODULE_PACKAGE).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        runCatching { context.sendBroadcast(intent, NotificationsContract.PERMISSION) }
            .onFailure { Log.w(TAG, "Module de notifications injoignable", it) }
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
        // Retrait immédiat à l'écran ; le module confirmera avec la nouvelle liste.
        _byApp.value = _byApp.value
            .mapValues { (_, list) -> list.filterNot { it.key == preview.key } }
            .filterValues { it.isNotEmpty() }
        send(Intent(NotificationsContract.ACTION_DISMISS).putExtra(NotificationsContract.EXTRA_KEY, preview.key))
    }

    /** Réglage Android où l'on autorise le module à lire les notifications. */
    fun accessSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                NotificationsContract.listenerComponent.flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    /** Le service « double appui pour verrouiller » du module est-il activé ? */
    fun isLockServiceEnabled(): Boolean = NotificationsContract.isLockServiceEnabled(
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
    )

    /** Demande au module de verrouiller l'écran ; false si le service n'est pas activé. */
    fun lockScreen(): Boolean {
        if (!isLockServiceEnabled()) return false
        send(Intent(NotificationsContract.ACTION_LOCK))
        return true
    }

    /**
     * Réglage d'accessibilité où l'on active le verrouillage : la fiche du service quand le
     * téléphone l'ouvre aux autres apps, sinon la liste (voir SettingsActivity).
     */
    fun lockSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, NotificationsContract.lockServiceComponent.flattenToString())
        } else {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }

    /** Infos de l'app du module (pour « Autoriser les paramètres restreints »). */
    fun moduleDetailsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", NotificationsContract.MODULE_PACKAGE, null))

    private companion object {
        const val TAG = "Notifications"
    }
}
