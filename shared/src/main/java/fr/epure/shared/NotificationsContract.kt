package fr.epure.shared

import android.app.PendingIntent
import android.content.ComponentName
import android.os.Bundle
import android.os.UserHandle
import androidx.core.os.BundleCompat

/**
 * Échange entre l'écran d'accueil Épure et le module « Épure Notifications ».
 *
 * Le module lit les notifications et envoie la liste au launcher ; le launcher lui demande
 * la liste ou d'effacer une notification. Tout passe par des diffusions limitées aux deux apps :
 * la permission [PERMISSION] est de niveau « signature », donc seule une app signée avec
 * la même clé peut envoyer ou recevoir ces données.
 */
object NotificationsContract {
    const val LAUNCHER_PACKAGE = "fr.epure.launcher"
    const val MODULE_PACKAGE = "fr.epure.notifications"
    const val MODULE_LISTENER = "fr.epure.notifications.ModuleListenerService"
    const val PERMISSION = "fr.epure.permission.NOTIFICATIONS"

    /** Module → launcher : liste à jour des notifications (et état de l'accès). */
    const val ACTION_SNAPSHOT = "fr.epure.action.NOTIFICATIONS_SNAPSHOT"

    /** Launcher → module : envoie-moi la liste actuelle. */
    const val ACTION_REQUEST = "fr.epure.action.NOTIFICATIONS_REQUEST"

    /** Launcher → module : efface cette notification. */
    const val ACTION_DISMISS = "fr.epure.action.NOTIFICATIONS_DISMISS"

    /**
     * Module → launcher : sessions de lecture en cours (musique, podcasts…), de la plus
     * récente à la plus ancienne. Le launcher s'en sert pour afficher le lecteur et le
     * piloter (lecture, pause, piste suivante).
     */
    const val ACTION_MEDIA = "fr.epure.action.MEDIA_SESSIONS"

    /** Launcher → module : verrouille l'écran (double appui sur l'accueil). */
    const val ACTION_LOCK = "fr.epure.action.LOCK_SCREEN"

    /** Service d'accessibilité du module qui verrouille l'écran. */
    const val LOCK_SERVICE = "fr.epure.notifications.LockService"

    const val EXTRA_CONNECTED = "connected"
    const val EXTRA_NOTIFICATIONS = "notifications"
    const val EXTRA_KEY = "key"
    const val EXTRA_MEDIA_TOKENS = "tokens"

    val listenerComponent: ComponentName get() = ComponentName(MODULE_PACKAGE, MODULE_LISTENER)

    val lockServiceComponent: ComponentName get() = ComponentName(MODULE_PACKAGE, LOCK_SERVICE)

    /**
     * Le service de verrouillage est-il activé dans les réglages d'accessibilité ?
     * [enabledServices] : valeur de `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.
     */
    fun isLockServiceEnabled(enabledServices: String?): Boolean {
        val wanted = lockServiceComponent
        return enabledServices.orEmpty().split(':').any { ComponentName.unflattenFromString(it.trim()) == wanted }
    }

    fun toBundle(preview: NotificationPreview): Bundle = Bundle().apply {
        putString("key", preview.key)
        putString("package", preview.packageName)
        putParcelable("user", preview.user)
        putLong("time", preview.postTime)
        putString("title", preview.title)
        putString("text", preview.text)
        putParcelable("intent", preview.contentIntent)
        putBoolean("clearable", preview.clearable)
        putBoolean("autoCancel", preview.autoCancel)
        putString("group", preview.groupKey)
        putBoolean("summary", preview.isGroupSummary)
    }

    /** Relit une notification reçue ; null si le paquet est incomplet. */
    fun fromBundle(bundle: Bundle): NotificationPreview? {
        val key = bundle.getString("key") ?: return null
        val packageName = bundle.getString("package") ?: return null
        val user = BundleCompat.getParcelable(bundle, "user", UserHandle::class.java) ?: return null
        return NotificationPreview(
            key = key,
            packageName = packageName,
            user = user,
            postTime = bundle.getLong("time"),
            title = bundle.getString("title").orEmpty(),
            text = bundle.getString("text").orEmpty(),
            contentIntent = BundleCompat.getParcelable(bundle, "intent", PendingIntent::class.java),
            clearable = bundle.getBoolean("clearable"),
            autoCancel = bundle.getBoolean("autoCancel"),
            groupKey = bundle.getString("group"),
            isGroupSummary = bundle.getBoolean("summary"),
        )
    }
}
