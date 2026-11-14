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
import fr.epure.launcher.domain.DayForecast
import fr.epure.launcher.domain.WeatherReport
import java.time.LocalDateTime
import java.time.ZoneId

/** Données d'exemple pour les tests (apps, configuration, météo, agenda). */
object SampleData {

    fun app(label: String, pkg: String = "com.exemple." + label.lowercase().filter { it.isLetterOrDigit() }, category: Int = -1) =
        InstalledApp(
            key = "$pkg/$pkg.Main",
            packageName = pkg,
            component = ComponentName(pkg, "$pkg.Main"),
            user = Process.myUserHandle(),
            label = label,
            category = category,
            isWorkProfile = false,
        )

    val apps: List<InstalledApp> = listOf(
        "Agenda", "Appareil photo", "Banque", "Calculatrice", "Chrome", "Deezer", "Discord", "Drive",
        "Gmail", "Google Maps", "Horloge", "Instagram", "Keep", "LinkedIn", "Messages", "Météo",
        "Netflix", "Notion", "Paramètres", "Photos", "Signal", "Snapchat", "Spotify", "Téléphone",
        "Waze", "WhatsApp", "YouTube", "Zoom", "1Password",
    ).map { app(it) }

    fun key(label: String) = apps.first { it.label == label }.key

    val config: LauncherConfig = LauncherConfig(onboardingDone = true)
        .addFavorite(key("Téléphone"))
        .addFavorite(key("Messages"))
        .createFolder("social", "Réseaux", listOf(key("Instagram"), key("WhatsApp"), key("Snapchat"), key("Signal"), key("Discord")), onHome = true)
        .addFavorite(key("Spotify"))
        .addFavorite(key("Appareil photo"))
        .addFavorite(key("Chrome"))
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
        ),
    )

    val agenda = AgendaState(
        hasPermission = true,
        events = listOf(
            AgendaEvent(1, "Café avec Camille", at("2026-11-14T11:00"), at("2026-11-14T12:00"), allDay = false, color = 0xFF7FD1C7.toInt()),
            AgendaEvent(2, "Match de foot", at("2026-11-15T15:30"), at("2026-11-15T17:00"), allDay = false, color = 0xFFF2C14E.toInt()),
        ),
    )
}
