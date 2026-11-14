package fr.epure.shared

import android.app.Notification
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationsTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val user = Process.myUserHandle()

    private fun parse(notification: Notification, ongoing: Boolean = false, key: String = "k", group: String? = "g:$key") =
        NotificationParser.parse(key, "com.exemple.app", user, 1_000L, notification, ongoing, clearable = true, groupKey = group)

    @Test
    fun `age court en francais`() {
        val now = 10_000_000L
        assertEquals("à l’instant", NotificationFormat.ago(now - 20_000, now))
        assertEquals("19 min", NotificationFormat.ago(now - 19 * 60_000, now))
        assertEquals("2 h", NotificationFormat.ago(now - 125 * 60_000, now))
        assertEquals("3 j", NotificationFormat.ago(now - 3L * 24 * 3600_000, now))
        assertEquals("à l’instant", NotificationFormat.ago(now + 60_000, now))
    }

    @Test
    fun `notification simple`() {
        val n = NotificationCompat.Builder(context, "canal")
            .setContentTitle("Colis livré")
            .setContentText("Votre commande\nest arrivée")
            .setAutoCancel(true)
            .build()
        val preview = parse(n)!!
        assertEquals("Colis livré", preview.title)
        assertEquals("Votre commande est arrivée", preview.text)
        assertTrue(preview.autoCancel)
    }

    @Test
    fun `conversation de groupe avec expediteur`() {
        val me = Person.Builder().setName("Moi").build()
        val altair = Person.Builder().setName("Altaïr").build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle("Arkange • #médias")
            .setGroupConversation(true)
            .addMessage("Salut", 1L, altair)
            .addMessage("A envoyé une image", 2L, altair)
        val n = NotificationCompat.Builder(context, "canal").setStyle(style).build()
        val preview = parse(n)!!
        assertEquals("Arkange • #médias", preview.title)
        assertEquals("Altaïr : A envoyé une image", preview.text)
    }

    @Test
    fun `conversation a deux sans repetition du nom`() {
        val me = Person.Builder().setName("Moi").build()
        val lea = Person.Builder().setName("Léa").build()
        val style = NotificationCompat.MessagingStyle(me).addMessage("On se voit à 18 h ?", 1L, lea)
        val n = NotificationCompat.Builder(context, "canal").setContentTitle("Léa").setStyle(style).build()
        val preview = parse(n)!!
        assertEquals("Léa", preview.title)
        assertEquals("On se voit à 18 h ?", preview.text)
    }

    @Test
    fun `notifications permanentes et vides ignorees`() {
        val music = NotificationCompat.Builder(context, "canal").setContentTitle("Lecture en cours").build()
        assertNull(parse(music, ongoing = true))
        val empty = NotificationCompat.Builder(context, "canal").build()
        assertNull(parse(empty))
    }

    @Test
    fun `resume de groupe ignore quand ses messages sont la, regroupement par app`() {
        val base = NotificationPreview("a", "com.app", user, 1L, "t", "x", null, true, false, "grp", false)
        val newer = base.copy(key = "b", postTime = 5L)
        val summary = base.copy(key = "s", isGroupSummary = true, postTime = 9L)
        val lonelySummary = base.copy(key = "s2", groupKey = "autre", isGroupSummary = true, postTime = 3L)
        val other = base.copy(key = "c", packageName = "com.autre", groupKey = "g2")
        val grouped = NotificationParser.group(listOf(base, newer, summary, lonelySummary, other))
        assertEquals(listOf("b", "s2", "a"), grouped[AppUserKey("com.app", user)]!!.map { it.key })
        assertEquals(listOf("c"), grouped[AppUserKey("com.autre", user)]!!.map { it.key })
    }

    @Test
    fun `echange entre le module et le launcher sans perte`() {
        val preview = NotificationPreview(
            key = "0|com.discord|42|null|10001",
            packageName = "com.discord",
            user = user,
            postTime = 123_456L,
            title = "Arkange • #médias",
            text = "Altaïr : a envoyé une image",
            contentIntent = null,
            clearable = true,
            autoCancel = true,
            groupKey = "0|com.discord|g:messages",
            isGroupSummary = false,
        )
        assertEquals(preview, NotificationsContract.fromBundle(NotificationsContract.toBundle(preview)))
        // Un paquet incomplet est ignoré plutôt que de planter l'écran d'accueil.
        assertNull(NotificationsContract.fromBundle(android.os.Bundle()))
    }
}
