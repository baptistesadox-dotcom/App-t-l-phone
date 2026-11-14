package fr.epure.launcher.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigJsonTest {

    @Test
    fun `aller-retour JSON complet`() {
        val config = LauncherConfig()
            .addFavorite("com.a/com.a.Main")
            .createFolder("f", "Réseaux", listOf("com.b/com.b.Main"), onHome = true)
            .hideApp("com.c/com.c.Main")
            .renameApp("com.a/com.a.Main", "Banque")
            .addTodo("t", "Appeler Léa")
            .addHostedWidget(12, 160)
            .updateAppearance { it.copy(themeMode = ThemeMode.DARK, accentColor = 0xFFF28BA8, textScale = 1.2f) }
            .updateWidgets { it.copy(showWeather = true, weatherLocation = WeatherLocation("Paris", 48.85, 2.35, "France")) }

        val decoded = ConfigJson.decode(ConfigJson.encode(config))
        assertEquals(config, decoded)
        assertEquals(config, ConfigJson.decode(ConfigJson.encode(config, prettyPrint = true)))
    }

    @Test
    fun `les elements d accueil sont types`() {
        val json = ConfigJson.encode(LauncherConfig().addFavorite("x").createFolder("f", "Jeux", onHome = true))
        assertTrue(json.contains("\"type\":\"app\""))
        assertTrue(json.contains("\"type\":\"folder\""))
    }

    @Test
    fun `une configuration de la version 0_1 est mise a jour a la lecture`() {
        val v1 = ConfigJson.encode(LauncherConfig(schemaVersion = 1).updateAppearance { it.copy(font = FontChoice.SYSTEM) })
        val read = ConfigJson.decode(v1)
        assertEquals(CURRENT_SCHEMA_VERSION, read.schemaVersion)
        assertEquals(FontChoice.NUNITO, read.appearance.font)
    }

    @Test
    fun `une ancienne sauvegarde incomplete reste lisible`() {
        val old = """{"home":[{"type":"app","key":"k"}],"champInconnu":42,"appearance":{"themeMode":"MODE_FUTUR"}}"""
        val config = ConfigJson.decode(old)
        assertEquals(listOf<HomeItem>(HomeItem.App("k")), config.home)
        // Valeur inconnue : on revient à la valeur par défaut.
        assertEquals(ThemeMode.SYSTEM, config.appearance.themeMode)
        assertEquals(WidgetsConfig(), config.widgets)
    }
}
