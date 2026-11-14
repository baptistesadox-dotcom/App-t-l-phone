package fr.epure.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppSearchTest {

    @Test
    fun `normalisation retire accents, ligatures et majuscules`() {
        assertEquals("editeur oeuvres", normalizeForSearch("  Éditeur Œuvres "))
        assertEquals("francais", normalizeForSearch("Français"))
        assertEquals("strasse", normalizeForSearch("Straße"))
    }

    @Test
    fun `section par premiere lettre, chiffres et symboles dans #`() {
        assertEquals("E", sectionOf("Écran"))
        assertEquals("C", sectionOf("ça marche"))
        assertEquals("#", sectionOf("7-Zip"))
        assertEquals("#", sectionOf("@Home"))
        assertEquals("#", sectionOf(""))
    }

    @Test
    fun `tri alphabetique francais, # en premier`() {
        val sorted = sortByLabel(listOf("Zoom", "éditeur", "Agenda", "1Password", "Banque", "Echo"), Locale.FRANCE) { it }
        assertEquals(listOf("1Password", "Agenda", "Banque", "Echo", "éditeur", "Zoom"), sorted)
    }

    @Test
    fun `recherche par debut, mot, initiales et sans accents`() {
        val apps = listOf("Google Maps", "Messages", "Météo", "Gmail", "Paramètres", "WhatsApp")
        // Les noms qui commencent par « me » d'abord, puis ceux qui le contiennent.
        assertEquals(listOf("Messages", "Météo", "Paramètres"), AppSearch.search(apps, "me", { it }))
        assertEquals("Météo", AppSearch.search(apps, "meteo", { it }).first())
        assertEquals(listOf("Google Maps"), AppSearch.search(apps, "maps", { it }))
        assertEquals(listOf("Gmail", "Google Maps"), AppSearch.search(apps, "gma", { it }))
        assertTrue(AppSearch.search(apps, "parametres", { it }).contains("Paramètres"))
        assertTrue(AppSearch.search(apps, "   ", { it }).isEmpty())
    }

    @Test
    fun `le debut du nom passe avant les initiales`() {
        val apps = listOf("Google Maps", "Gmail")
        // « gm » : Gmail commence par « gm », Google Maps seulement par ses initiales.
        assertEquals(listOf("Gmail", "Google Maps"), AppSearch.search(apps, "gm", { it }))
    }

    @Test
    fun `le nom d origine reste cherchable apres renommage`() {
        data class App(val label: String, val original: String)
        val apps = listOf(App("Banque", "Crédit Mutuel"), App("Musique", "Spotify"))
        val found = AppSearch.search(apps, "spot", { it.label }, { it.original })
        assertEquals(listOf(App("Musique", "Spotify")), found)
    }
}
