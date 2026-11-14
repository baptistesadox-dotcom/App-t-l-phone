package fr.epure.shared

import android.app.Notification
import android.app.PendingIntent
import android.os.Bundle
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/** Identifie une app pour un profil utilisateur (perso ou pro). */
data class AppUserKey(val packageName: String, val user: UserHandle)

/** Notification prête à afficher sous le nom d'une app. */
data class NotificationPreview(
    val key: String,
    val packageName: String,
    val user: UserHandle,
    val postTime: Long,
    /** Titre : nom de la conversation ou de l'expéditeur. */
    val title: String,
    /** Texte : dernier message (« Léa : On se voit à 18 h ? ») ou contenu. */
    val text: String,
    val contentIntent: PendingIntent?,
    val clearable: Boolean,
    val autoCancel: Boolean,
    val groupKey: String?,
    val isGroupSummary: Boolean,
) {
    val appKey: AppUserKey get() = AppUserKey(packageName, user)
}

/** Textes relatifs aux notifications (sans dépendance Android, testables). */
object NotificationFormat {

    /** Âge d'une notification : « à l’instant », « 19 min », « 2 h », « 3 j ». */
    fun ago(postTime: Long, now: Long): String {
        val minutes = (now - postTime).coerceAtLeast(0) / 60_000
        return when {
            minutes < 1 -> "à l’instant"
            minutes < 60 -> "$minutes min"
            minutes < 24 * 60 -> "${minutes / 60} h"
            else -> "${minutes / (24 * 60)} j"
        }
    }
}

/** Lecture des notifications Android. */
object NotificationParser {

    fun parse(sbn: StatusBarNotification): NotificationPreview? = parse(
        key = sbn.key,
        packageName = sbn.packageName,
        user = sbn.user,
        postTime = sbn.postTime,
        notification = sbn.notification,
        ongoing = sbn.isOngoing,
        clearable = sbn.isClearable,
        groupKey = sbn.groupKey,
    )

    /**
     * Garde les notifications utiles (messages, alertes) et ignore les notifications
     * permanentes (musique, téléchargements, services en cours…).
     */
    fun parse(
        key: String,
        packageName: String,
        user: UserHandle,
        postTime: Long,
        notification: Notification,
        ongoing: Boolean,
        clearable: Boolean,
        groupKey: String?,
    ): NotificationPreview? {
        val flags = notification.flags
        if (ongoing || flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return null
        val extras: Bundle = notification.extras ?: Bundle.EMPTY

        val messaging = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        }.getOrNull()
        val lastMessage = messaging?.messages?.lastOrNull()
        val conversationTitle = messaging?.conversationTitle?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        val title = (
            conversationTitle
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)
            )?.toString()?.trim().orEmpty()

        val text = if (lastMessage != null) {
            val body = lastMessage.text?.toString()?.trim().orEmpty()
            val sender = lastMessage.person?.name?.toString()?.trim()
            // Dans un groupe, on précise qui a écrit ; en tête-à-tête, le titre suffit.
            val isGroup = messaging.isGroupConversation || conversationTitle != null
            if (isGroup && !sender.isNullOrEmpty() && sender != title) "$sender : $body" else body
        } else {
            (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
                ?.toString()?.trim().orEmpty()
        }.replace('\n', ' ')

        if (title.isEmpty() && text.isEmpty()) return null
        return NotificationPreview(
            key = key,
            packageName = packageName,
            user = user,
            postTime = postTime,
            title = title,
            text = text,
            contentIntent = notification.contentIntent,
            clearable = clearable,
            autoCancel = flags and Notification.FLAG_AUTO_CANCEL != 0,
            groupKey = groupKey,
            isGroupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )
    }

    /**
     * Regroupe les notifications par app, de la plus récente à la plus ancienne.
     * Le résumé d'un groupe est ignoré quand les notifications du groupe sont présentes.
     */
    fun group(previews: List<NotificationPreview>): Map<AppUserKey, List<NotificationPreview>> {
        val groupsWithChildren = previews
            .filter { !it.isGroupSummary && it.groupKey != null }
            .mapTo(HashSet()) { it.groupKey }
        return previews
            .filterNot { it.isGroupSummary && it.groupKey in groupsWithChildren }
            .groupBy { it.appKey }
            .mapValues { (_, list) -> list.sortedByDescending { it.postTime } }
    }
}
