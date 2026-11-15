package fr.epure.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class RivageTest {

    @Test
    fun `icones au trait des apps connues, des roles et des mots-cles`() {
        assertEquals("brand-chrome", LineIconMatcher.match("com.android.chrome"))
        assertEquals("brand-instagram", LineIconMatcher.match("com.instagram.android"))
        assertEquals("brand-google-play", LineIconMatcher.match("com.android.vending"))
        // Rôle système détecté sur le téléphone (ex. l'appli SMS par défaut d'un fabricant).
        assertEquals("message-circle", LineIconMatcher.match("com.fabricant.sms", roleIcon = "message-circle"))
        // Une app connue garde son logo même si elle joue un rôle (Chrome = navigateur par défaut).
        assertEquals("brand-chrome", LineIconMatcher.match("com.android.chrome", roleIcon = "world"))
        // Mots-clés : segment entier, ou mot long n'importe où.
        assertEquals("camera", LineIconMatcher.match("com.sec.android.app.camera"))
        assertEquals("clock", LineIconMatcher.match("com.sec.android.app.clockpackage"))
        assertEquals("building-bank", LineIconMatcher.match("fr.creditagricole.androidapp"))
        // « bus » ne doit pas s'appliquer à « business ».
        assertNull(LineIconMatcher.match("com.exemple.business"))
        // Catégorie déclarée (6 = cartes).
        assertEquals("map", LineIconMatcher.match("com.exemple.inconnue", declaredCategory = 6))
        assertNull(LineIconMatcher.match("com.exemple.inconnue"))
    }

    @Test
    fun `toutes les icones choisies existent dans le jeu d icones`() {
        val names = listOf(
            "com.android.chrome", "com.whatsapp", "com.spotify.music", "com.android.settings",
            "com.google.android.apps.photos", "com.anthropic.claude", "com.vinted",
        ).map { LineIconMatcher.match(it) }
        names.forEach { name -> assert(name != null && fr.epure.launcher.ui.icons.LineIconData.paths.containsKey(name)) { "icône manquante : $name" } }
    }

    @Test
    fun `salutation du jour`() {
        val lines = Greeting.forDay(DayOfWeek.SUNDAY)
        assertEquals(listOf("PASSE", "UN", "BON", "DIMANCHE"), listOf(lines.first, lines.second, lines.highlight, lines.last))
        assertEquals("JEUDI", Greeting.forDay(DayOfWeek.THURSDAY).last)
    }

    @Test
    fun `phrase meteo en majuscules`() {
        val report = WeatherReport(11.6, 0, isDay = true, days = emptyList(), fetchedAtMillis = 0, humidity = 61, windKmh = 4)
        assertEquals("MÉTÉO À LYON : ENSOLEILLÉ, 12°. VENT À 4 KM/H, HUMIDITÉ 61 %.", WeatherCodes.sentence("Lyon", report))
        val sparse = report.copy(humidity = null, windKmh = null, code = 3)
        assertEquals("MÉTÉO À NEW YORK : COUVERT, 12°.", WeatherCodes.sentence("New York", sparse))
    }
}
