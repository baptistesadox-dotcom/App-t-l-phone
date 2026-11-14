package fr.epure.launcher.data.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import fr.epure.launcher.appContainer

/**
 * Service lancé par Android quand l'accès aux notifications est autorisé.
 * Il ne fait que prévenir [NotificationHub] des changements : rien n'est enregistré
 * ni envoyé ailleurs.
 */
class NotificationsService : NotificationListenerService() {

    private val hub: NotificationHub get() = appContainer.notificationHub

    override fun onListenerConnected() {
        super.onListenerConnected()
        hub.attach(this)
    }

    override fun onListenerDisconnected() {
        hub.detach(this)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        hub.requestRefresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        hub.requestRefresh()
    }
}
