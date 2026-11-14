package fr.epure.launcher.ui

import fr.epure.launcher.data.config.hideApp
import fr.epure.launcher.data.config.renameApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LauncherStateTest {

    private val apps = SampleData.apps

    @Test
    fun `etat de l accueil avec dossiers, noms et apps masquees`() {
        val config = SampleData.config
            .renameApp(SampleData.key("Chrome"), "Internet")
            .hideApp(SampleData.key("Snapchat"))
        val state = buildLauncherState(config, apps, Locale.FRANCE)

        // L'app masquée disparaît de la liste et de son dossier.
        assertFalse(state.apps.any { it.label == "Snapchat" })
        assertTrue(state.allApps.any { it.label == "Snapchat" && it.isHidden })
        assertEquals(listOf("Discord", "Instagram", "Signal", "WhatsApp"), state.folderById("social")!!.apps.map { it.label })

        // Le nom personnalisé est utilisé partout, y compris pour la lettre de section.
        val internet = state.apps.first { it.key == SampleData.key("Chrome") }
        assertEquals("Internet", internet.label)
        assertEquals("I", internet.section)
        assertTrue(internet.isRenamed)

        // Accueil : dans l'ordre de la configuration.
        assertEquals(
            listOf("app:Téléphone", "app:Messages", "folder:Réseaux", "app:Spotify", "app:Appareil photo", "app:Internet"),
            state.home.map {
                when (it) {
                    is HomeEntry.App -> "app:${it.entry.label}"
                    is HomeEntry.FolderItem -> "folder:${it.folder.name}"
                }
            },
        )
        assertEquals(listOf("Réseaux", "Travail"), state.tabs.map { it.name })
    }

    @Test
    fun `liste par lettres puis recherche par pertinence`() {
        val state = buildLauncherState(SampleData.config, apps, Locale.FRANCE)
        val drawer = buildDrawerList(state.apps, "")
        assertFalse(drawer.isSearch)
        assertEquals("#", drawer.letters.first())
        assertEquals(apps.size, drawer.rows.count { it is DrawerRow.Item })
        // Chaque lettre pointe sur son en-tête.
        drawer.letterIndex.forEach { (letter, index) ->
            assertEquals(DrawerRow.Header(letter), drawer.rows[index])
        }
        // « Météo » est rangée avec les M, après « Messages ».
        val mApps = drawer.rows.dropWhile { it != DrawerRow.Header("M") }.drop(1)
            .takeWhile { it is DrawerRow.Item }.map { (it as DrawerRow.Item).entry.label }
        assertEquals(listOf("Messages", "Météo"), mApps)

        val search = buildDrawerList(state.apps, "sp")
        assertTrue(search.isSearch)
        assertEquals("Spotify", (search.rows.first() as DrawerRow.Item).entry.label)
    }
}
