package fr.epure.launcher.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigOpsTest {

    private val base = LauncherConfig()

    @Test
    fun `favoris ajoutes une seule fois, retires et deplaces`() {
        val config = base.addFavorite("a").addFavorite("b").addFavorite("a").addFavorite("c")
        assertEquals(listOf("a", "b", "c"), config.home.map { (it as HomeItem.App).key })
        assertFalse(config.toggleFavorite("b").isFavorite("b"))
        val moved = config.moveHomeItem(2, 0)
        assertEquals(listOf("c", "a", "b"), moved.home.map { (it as HomeItem.App).key })
        // Déplacement hors limites : rien ne change.
        assertEquals(config, config.moveHomeItem(0, 5))
    }

    @Test
    fun `supprimer un dossier retire aussi son raccourci d accueil`() {
        val config = base
            .createFolder("f1", "  Réseaux ", listOf("x", "y", "x"), onHome = true)
            .addFavorite("z")
        assertEquals("Réseaux", config.folders.single().name)
        assertEquals(listOf("x", "y"), config.folders.single().apps)
        assertTrue(config.isFolderOnHome("f1"))

        val deleted = config.deleteFolder("f1")
        assertTrue(deleted.folders.isEmpty())
        assertEquals(listOf<HomeItem>(HomeItem.App("z")), deleted.home)
    }

    @Test
    fun `nom de dossier vide remplace par un nom par defaut ou conserve`() {
        val created = base.createFolder("f", "   ")
        assertEquals(DEFAULT_FOLDER_NAME, created.folders.single().name)
        assertEquals(DEFAULT_FOLDER_NAME, created.renameFolder("f", " ").folders.single().name)
        assertEquals("Jeux", created.renameFolder("f", "Jeux").folders.single().name)
    }

    @Test
    fun `ajout et retrait d apps dans un dossier`() {
        val config = base.createFolder("f", "Travail")
            .addAppToFolder("f", "mail")
            .addAppToFolder("f", "mail")
            .addAppToFolder("f", "agenda")
        assertEquals(listOf("mail", "agenda"), config.folders.single().apps)
        assertEquals(listOf("agenda"), config.removeAppFromFolder("f", "mail").folders.single().apps)
    }

    @Test
    fun `on ne peut pas epingler un dossier inexistant`() {
        assertEquals(base, base.addFolderToHome("inconnu"))
    }

    @Test
    fun `dossiers generes fusionnes avec les dossiers existants`() {
        var counter = 0
        val config = base.createFolder("f", "Jeux", listOf("g1"))
            .mergeGeneratedFolders(mapOf("jeux" to listOf("g2"), "Vidéo" to listOf("v1", "v2"))) { "new${counter++}" }
        assertEquals(2, config.folders.size)
        assertEquals(listOf("g1", "g2"), config.folders.first { it.id == "f" }.apps)
        assertEquals(listOf("v1", "v2"), config.folders.first { it.id == "new0" }.apps)
    }

    @Test
    fun `masquer une app la retire des favoris`() {
        val config = base.addFavorite("a").hideApp("a")
        assertTrue("a" in config.hiddenApps)
        assertFalse(config.isFavorite("a"))
        assertFalse("a" in config.unhideApp("a").hiddenApps)
    }

    @Test
    fun `renommer puis revenir au nom d origine`() {
        val renamed = base.renameApp("a", "  Banque ")
        assertEquals("Banque", renamed.customLabels["a"])
        assertNull(renamed.renameApp("a", "").customLabels["a"])
        assertNull(renamed.renameApp("a", null).customLabels["a"])
    }

    @Test
    fun `taches ajoutees, cochees, modifiees et nettoyees`() {
        val config = base.addTodo("1", " Pain ").addTodo("2", "   ").addTodo("3", "Courrier")
        assertEquals(listOf("Pain", "Courrier"), config.todos.map { it.text })
        val done = config.toggleTodo("1")
        assertTrue(done.todos.first().done)
        assertEquals(listOf("Courrier"), done.clearDoneTodos().todos.map { it.text })
        assertEquals("Lettre", config.editTodo("3", "Lettre").todos.last().text)
        // Un texte vide supprime la tâche.
        assertEquals(1, config.editTodo("3", " ").todos.size)
    }

    @Test
    fun `hauteur des widgets bornee et ordre modifiable`() {
        val config = base.addHostedWidget(1, 10).addHostedWidget(2, 5000)
        assertEquals(MIN_WIDGET_HEIGHT_DP, config.widgets.hosted[0].heightDp)
        assertEquals(MAX_WIDGET_HEIGHT_DP, config.widgets.hosted[1].heightDp)
        assertEquals(listOf(2, 1), config.moveHostedWidget(2, -1).widgets.hosted.map { it.appWidgetId })
        assertEquals(config, config.moveHostedWidget(1, -1))
        assertEquals(listOf(2), config.removeHostedWidget(1).widgets.hosted.map { it.appWidgetId })
    }

    @Test
    fun `migration vers v3 applique le theme Rivage sans toucher aux favoris`() {
        val old = LauncherConfig(schemaVersion = 1)
            .addFavorite("a")
            .createFolder("f", "Jeux", listOf("g"))
            .updateAppearance { it.copy(font = FontChoice.SYSTEM, iconSizeDp = 36, iconPack = "pack.icones", customFontPath = "/x.ttf") }
        val migrated = old.migrated()
        assertEquals(CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(old.home, migrated.home)
        assertEquals(old.folders, migrated.folders)
        assertEquals(ThemePresets.RIVAGE, ThemePresets.current(migrated.appearance))
        // Le pack d'icônes et la police importée sont conservés pour plus tard.
        assertEquals("pack.icones", migrated.appearance.iconPack)
        assertEquals("/x.ttf", migrated.appearance.customFontPath)

        // Déjà à jour : rien ne change.
        val current = LauncherConfig().updateAppearance { it.copy(font = FontChoice.SERIF) }
        assertEquals(current, current.migrated())
    }

    @Test
    fun `themes prets a l emploi`() {
        assertEquals(ThemePresets.RIVAGE, ThemePresets.current(LauncherConfig().appearance))
        val epure = ThemePresets.apply(LauncherConfig(), ThemePresets.EPURE)
        assertEquals(ThemePresets.EPURE, ThemePresets.current(epure.appearance))
        assertEquals(IconStyle.ORIGINAL, epure.appearance.iconStyle)
        assertEquals(HomeStyle.LIST, epure.appearance.homeStyle)
        // Un réglage modifié à la main : plus aucun thème « tel quel ».
        assertNull(ThemePresets.current(epure.appearance.copy(textScale = 1.3f)))
    }

    @Test
    fun `import sans widgets Android ni police importee`() {
        val config = base.addHostedWidget(7, 120)
            .updateAppearance { it.copy(font = FontChoice.CUSTOM, customFontPath = "/data/x.ttf") }
            .sanitizedForImport()
        assertTrue(config.widgets.hosted.isEmpty())
        assertEquals(FontChoice.MONTSERRAT, config.appearance.font)
        assertNull(config.appearance.customFontPath)
        assertTrue(config.onboardingDone)
    }
}
