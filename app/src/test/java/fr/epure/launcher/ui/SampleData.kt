package fr.epure.launcher.ui

import android.content.ComponentName
import android.os.Process
import fr.epure.launcher.data.apps.InstalledApp
import fr.epure.launcher.data.calendar.AgendaEvent
import fr.epure.launcher.data.config.LauncherConfig
import fr.epure.launcher.data.config.WeatherLocation
import fr.epure.launcher.data.config.addFavorite
import fr.epure.launcher.data.config.addTodo
import fr.epure.launcher.data.config.createFolder
import fr.epure.launcher.data.config.toggleTodo
import fr.epure.launcher.data.config.updateWidgets
import fr.epure.shared.AppUserKey
import fr.epure.shared.NotificationParser
import fr.epure.shared.NotificationPreview
import fr.epure.launcher.domain.DayForecast
import fr.epure.launcher.domain.LineIconMatcher
import fr.epure.launcher.domain.WeatherReport
import java.time.LocalDateTime
import java.time.ZoneId

/** Données d'exemple pour les tests (apps, configuration, météo, agenda). */
object SampleData {

    /** Vrais noms de paquets : les icônes au trait sont choisies comme sur un téléphone. */
    private val packages = mapOf(
        "Agenda" to "com.google.android.calendar",
        "Appareil photo" to "com.google.android.GoogleCamera",
        "Banque" to "fr.creditagricole.androidapp",
        "Calculatrice" to "com.google.android.calculator",
        "Chrome" to "com.android.chrome",
        "Deezer" to "deezer.android.app",
        "Discord" to "com.discord",
        "Drive" to "com.google.android.apps.docs",
        "Gmail" to "com.google.android.gm",
        "Google Maps" to "com.google.android.apps.maps",
        "Horloge" to "com.google.android.deskclock",
        "Instagram" to "com.instagram.android",
        "Keep" to "com.google.android.keep",
        "LinkedIn" to "com.linkedin.android",
        "Messages" to "com.google.android.apps.messaging",
        "Météo" to "fr.meteo",
        "Netflix" to "com.netflix.mediaclient",
        "Notion" to "notion.id",
        "Paramètres" to "com.android.settings",
        "Photos" to "com.google.android.apps.photos",
        "Play Store" to "com.android.vending",
        "Signal" to "org.thoughtcrime.securesms",
        "Snapchat" to "com.snapchat.android",
        "Spotify" to "com.spotify.music",
        "Téléphone" to "com.google.android.dialer",
        "Waze" to "com.waze",
        "WhatsApp" to "com.whatsapp",
        "YouTube" to "com.google.android.youtube",
        "Zoom" to "us.zoom.videomeetings",
        "1Password" to "com.onepassword.android",
        "Claude" to "com.anthropic.claude",
        "Uber" to "com.ubercab",
        "Vinted" to "com.vinted",
    )

    fun app(label: String, pkg: String = packages[label] ?: ("com.exemple." + label.lowercase().filter { it.isLetterOrDigit() }), category: Int = -1) =
        InstalledApp(
            key = "$pkg/$pkg.Main",
            packageName = pkg,
            component = ComponentName(pkg, "$pkg.Main"),
            user = Process.myUserHandle(),
            label = label,
            category = category,
            isWorkProfile = false,
            lineIcon = LineIconMatcher.match(pkg, category),
        )

    val apps: List<InstalledApp> = listOf(
        "Agenda", "Appareil photo", "Banque", "Calculatrice", "Chrome", "Deezer", "Discord", "Drive",
        "Gmail", "Google Maps", "Horloge", "Instagram", "Keep", "LinkedIn", "Messages", "Météo",
        "Netflix", "Notion", "Paramètres", "Photos", "Signal", "Snapchat", "Spotify", "Téléphone",
        "Waze", "WhatsApp", "YouTube", "Zoom", "1Password", "Claude", "Uber", "Vinted", "Play Store",
    ).map { app(it) }

    fun key(label: String) = apps.first { it.label == label }.key

    val config: LauncherConfig = LauncherConfig(onboardingDone = true)
        .addFavorite(key("WhatsApp"))
        .addFavorite(key("Instagram"))
        .addFavorite(key("Discord"))
        .addFavorite(key("Messages"))
        .createFolder("social", "Réseaux", listOf(key("Snapchat"), key("Signal"), key("LinkedIn"), key("YouTube")), onHome = true)
        .addFavorite(key("Appareil photo"))
        .addFavorite(key("Photos"))
        .createFolder("travail", "Travail", listOf(key("Gmail"), key("Notion"), key("Drive"), key("Zoom"), key("LinkedIn")))
        .addTodo("1", "Acheter du pain")
        .addTodo("2", "Appeler Léa pour samedi")
        .addTodo("3", "Réserver le train")
        .toggleTodo("3")
        .updateWidgets {
            it.copy(
                showWeather = true,
                weatherLocation = WeatherLocation("Lyon", 45.75, 4.85, "Auvergne-Rhône-Alpes, France"),
                showAgenda = true,
            )
        }

    /** Configuration pour l'option « accueil typographique » (mêmes apps que la vidéo d'inspiration). */
    val rivageConfig: LauncherConfig = LauncherConfig(onboardingDone = true)
        .addFavorite(key("Chrome"))
        .addFavorite(key("Instagram"))
        .addFavorite(key("Play Store"))
        .addFavorite(key("Messages"))
        .addFavorite(key("Photos"))
        .addFavorite(key("Paramètres"))
        .createFolder("travail", "Travail", listOf(key("Gmail"), key("Notion"), key("Drive"), key("Zoom"), key("LinkedIn")))
        .updateWidgets {
            it.copy(
                showWeather = true,
                weatherLocation = WeatherLocation("Lyon", 45.75, 4.85, "Auvergne-Rhône-Alpes, France"),
                showAgenda = false,
                showTodo = false,
            )
        }

    private val zone = ZoneId.systemDefault()
    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    /** Samedi 14 novembre 2026, 9 h 41. */
    val now: Long = at("2026-11-14T09:41")

    val weather = WeatherState(
        report = WeatherReport(
            temperature = 12.4,
            code = 2,
            isDay = true,
            days = listOf(
                DayForecast("2026-11-14", 2, 6.0, 13.0),
                DayForecast("2026-11-15", 61, 7.0, 11.0),
                DayForecast("2026-11-16", 3, 5.0, 10.0),
                DayForecast("2026-11-17", 0, 3.0, 12.0),
            ),
            fetchedAtMillis = now,
            humidity = 61,
            windKmh = 4,
        ),
    )

    /** Notifications d'exemple (une conversation de groupe Discord, deux messages WhatsApp). */
    val notifications: Map<AppUserKey, List<NotificationPreview>> = NotificationParser.group(
        listOf(
            preview("discord", "com.discord", "Arkange Star Citizen • #médias", "Altaïr : a envoyé une image", minutesAgo = 19),
            preview("wa1", "com.whatsapp", "Léa", "On se voit à 18 h ?", minutesAgo = 3),
            preview("wa2", "com.whatsapp", "Famille", "Maman : N’oublie pas le gâteau pour dimanche 🎂", minutesAgo = 42),
        ),
    )

    private fun preview(key: String, pkg: String, title: String, text: String, minutesAgo: Int) = NotificationPreview(
        key = key,
        packageName = pkg,
        user = Process.myUserHandle(),
        postTime = now - minutesAgo * 60_000L,
        title = title,
        text = text,
        contentIntent = null,
        clearable = true,
        autoCancel = true,
        groupKey = key,
        isGroupSummary = false,
    )

    val agenda = AgendaState(
        hasPermission = true,
        events = listOf(
            AgendaEvent(1, "Café avec Camille", at("2026-11-14T11:00"), at("2026-11-14T12:00"), allDay = false, color = 0xFF7FD1C7.toInt()),
            AgendaEvent(2, "Match de foot", at("2026-11-15T15:30"), at("2026-11-15T17:00"), allDay = false, color = 0xFFF2C14E.toInt()),
        ),
    )
}
