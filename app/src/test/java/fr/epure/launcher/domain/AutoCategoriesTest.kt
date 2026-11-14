package fr.epure.launcher.domain

import fr.epure.launcher.data.icons.AppFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoCategoriesTest {

    @Test
    fun `regroupement par categorie, au moins deux apps`() {
        val apps = listOf(
            Triple("k1", "com.whatsapp", -1),          // connue : Messages
            Triple("k2", "org.telegram.messenger", -1), // connue : Messages
            Triple("k3", "com.example.game", 0),        // catégorie Jeu déclarée
            Triple("k4", "com.example.music", 1),       // seule de sa catégorie : ignorée
            Triple("k5", "com.example.other", -1),      // pas de catégorie
            Triple("k6", "com.example.puzzle", 0),
        )
        val groups = AutoCategories.group(apps)
        assertEquals(mapOf("Messages" to listOf("k1", "k2"), "Jeux" to listOf("k3", "k6")), groups)
        assertEquals(listOf("Messages", "Jeux"), groups.keys.toList())
    }

    @Test
    fun `categorie d une app`() {
        assertEquals(AutoCategory.VIDEO, AutoCategories.categoryOf("com.google.android.youtube", -1))
        assertEquals(AutoCategory.MAPS, AutoCategories.categoryOf("x", 6))
        assertNull(AutoCategories.categoryOf("x", -1))
    }

    @Test
    fun `lecture des composants des packs d icones`() {
        assertEquals("com.whatsapp" to "com.whatsapp.Main", AppFilter.parseComponent("ComponentInfo{com.whatsapp/com.whatsapp.Main}"))
        assertEquals("com.app" to "com.app.ui.Home", AppFilter.parseComponent("ComponentInfo{com.app/.ui.Home}"))
        assertNull(AppFilter.parseComponent("ComponentInfo{}"))
        assertNull(AppFilter.parseComponent(":LAUNCHER_ACTION_APP_DRAWER"))
    }
}
