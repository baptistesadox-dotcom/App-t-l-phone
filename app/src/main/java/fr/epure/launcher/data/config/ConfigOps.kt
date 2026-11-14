package fr.epure.launcher.data.config

/*
 * Modifications de la configuration, sous forme de fonctions pures :
 * elles renvoient une nouvelle configuration sans rien modifier en place,
 * ce qui les rend faciles à tester et sûres à appliquer dans DataStore.
 */

const val MIN_WIDGET_HEIGHT_DP = 60
const val MAX_WIDGET_HEIGHT_DP = 480
const val DEFAULT_FOLDER_NAME = "Dossier"

// ----- Favoris de l'accueil -----

fun LauncherConfig.isFavorite(key: String): Boolean =
    home.any { it is HomeItem.App && it.key == key }

fun LauncherConfig.addFavorite(key: String): LauncherConfig =
    if (isFavorite(key)) this else copy(home = home + HomeItem.App(key))

fun LauncherConfig.removeFavorite(key: String): LauncherConfig =
    copy(home = home.filterNot { it is HomeItem.App && it.key == key })

fun LauncherConfig.toggleFavorite(key: String): LauncherConfig =
    if (isFavorite(key)) removeFavorite(key) else addFavorite(key)

/** Déplace l'élément d'accueil [from] à la position [to]. */
fun LauncherConfig.moveHomeItem(from: Int, to: Int): LauncherConfig {
    if (from !in home.indices || to !in home.indices || from == to) return this
    val list = home.toMutableList()
    val item = list.removeAt(from)
    list.add(to, item)
    return copy(home = list)
}

fun LauncherConfig.removeHomeItemAt(index: Int): LauncherConfig =
    if (index !in home.indices) this else copy(home = home.filterIndexed { i, _ -> i != index })

fun LauncherConfig.isFolderOnHome(folderId: String): Boolean =
    home.any { it is HomeItem.FolderRef && it.folderId == folderId }

fun LauncherConfig.addFolderToHome(folderId: String): LauncherConfig =
    if (isFolderOnHome(folderId) || folders.none { it.id == folderId }) this
    else copy(home = home + HomeItem.FolderRef(folderId))

fun LauncherConfig.removeFolderFromHome(folderId: String): LauncherConfig =
    copy(home = home.filterNot { it is HomeItem.FolderRef && it.folderId == folderId })

// ----- Dossiers -----

fun LauncherConfig.createFolder(
    id: String,
    name: String,
    apps: List<String> = emptyList(),
    onHome: Boolean = false,
): LauncherConfig {
    val folder = Folder(id = id, name = name.trim().ifEmpty { DEFAULT_FOLDER_NAME }, apps = apps.distinct())
    val withFolder = copy(folders = folders + folder)
    return if (onHome) withFolder.addFolderToHome(id) else withFolder
}

fun LauncherConfig.updateFolder(id: String, transform: (Folder) -> Folder): LauncherConfig =
    copy(folders = folders.map { if (it.id == id) transform(it) else it })

fun LauncherConfig.renameFolder(id: String, name: String): LauncherConfig =
    updateFolder(id) { folder -> folder.copy(name = name.trim().ifEmpty { folder.name }) }

fun LauncherConfig.setFolderApps(id: String, apps: List<String>): LauncherConfig =
    updateFolder(id) { it.copy(apps = apps.distinct()) }

fun LauncherConfig.addAppToFolder(id: String, key: String): LauncherConfig =
    updateFolder(id) { if (key in it.apps) it else it.copy(apps = it.apps + key) }

fun LauncherConfig.removeAppFromFolder(id: String, key: String): LauncherConfig =
    updateFolder(id) { it.copy(apps = it.apps - key) }

fun LauncherConfig.setFolderShowAsTab(id: String, show: Boolean): LauncherConfig =
    updateFolder(id) { it.copy(showAsTab = show) }

/** Supprime le dossier (les apps restent installées) et son raccourci sur l'accueil. */
fun LauncherConfig.deleteFolder(id: String): LauncherConfig = copy(
    folders = folders.filterNot { it.id == id },
    home = home.filterNot { it is HomeItem.FolderRef && it.folderId == id },
)

fun LauncherConfig.moveFolder(id: String, delta: Int): LauncherConfig {
    val from = folders.indexOfFirst { it.id == id }
    val to = from + delta
    if (from < 0 || to !in folders.indices) return this
    val list = folders.toMutableList()
    list.add(to, list.removeAt(from))
    return copy(folders = list)
}

/**
 * Ajoute des dossiers générés automatiquement ([name] → apps).
 * Si un dossier du même nom existe déjà, les apps y sont ajoutées.
 */
fun LauncherConfig.mergeGeneratedFolders(
    generated: Map<String, List<String>>,
    newId: () -> String,
): LauncherConfig {
    var result = this
    for ((name, apps) in generated) {
        val existing = result.folders.firstOrNull { it.name.equals(name, ignoreCase = true) }
        result = if (existing != null) {
            result.setFolderApps(existing.id, existing.apps + apps)
        } else {
            result.createFolder(newId(), name, apps)
        }
    }
    return result
}

// ----- Apps masquées et renommées -----

fun LauncherConfig.hideApp(key: String): LauncherConfig =
    copy(hiddenApps = hiddenApps + key).removeFavorite(key)

fun LauncherConfig.unhideApp(key: String): LauncherConfig =
    copy(hiddenApps = hiddenApps - key)

/** Un nom vide efface le nom personnalisé et revient au nom d'origine. */
fun LauncherConfig.renameApp(key: String, label: String?): LauncherConfig {
    val clean = label?.trim().orEmpty()
    return if (clean.isEmpty()) copy(customLabels = customLabels - key)
    else copy(customLabels = customLabels + (key to clean))
}

// ----- Liste de tâches -----

fun LauncherConfig.addTodo(id: String, text: String): LauncherConfig {
    val clean = text.trim()
    return if (clean.isEmpty()) this else copy(todos = todos + TodoItem(id, clean))
}

fun LauncherConfig.toggleTodo(id: String): LauncherConfig =
    copy(todos = todos.map { if (it.id == id) it.copy(done = !it.done) else it })

fun LauncherConfig.editTodo(id: String, text: String): LauncherConfig {
    val clean = text.trim()
    return if (clean.isEmpty()) removeTodo(id)
    else copy(todos = todos.map { if (it.id == id) it.copy(text = clean) else it })
}

fun LauncherConfig.removeTodo(id: String): LauncherConfig =
    copy(todos = todos.filterNot { it.id == id })

fun LauncherConfig.clearDoneTodos(): LauncherConfig =
    copy(todos = todos.filterNot { it.done })

// ----- Widgets Android -----

fun LauncherConfig.updateWidgets(transform: (WidgetsConfig) -> WidgetsConfig): LauncherConfig =
    copy(widgets = transform(widgets))

fun LauncherConfig.addHostedWidget(appWidgetId: Int, heightDp: Int): LauncherConfig =
    updateWidgets {
        it.copy(hosted = it.hosted + HostedWidget(appWidgetId, heightDp.coerceIn(MIN_WIDGET_HEIGHT_DP, MAX_WIDGET_HEIGHT_DP)))
    }

fun LauncherConfig.removeHostedWidget(appWidgetId: Int): LauncherConfig =
    updateWidgets { w -> w.copy(hosted = w.hosted.filterNot { it.appWidgetId == appWidgetId }) }

fun LauncherConfig.resizeHostedWidget(appWidgetId: Int, heightDp: Int): LauncherConfig =
    updateWidgets { w ->
        w.copy(hosted = w.hosted.map {
            if (it.appWidgetId == appWidgetId) it.copy(heightDp = heightDp.coerceIn(MIN_WIDGET_HEIGHT_DP, MAX_WIDGET_HEIGHT_DP)) else it
        })
    }

fun LauncherConfig.moveHostedWidget(appWidgetId: Int, delta: Int): LauncherConfig {
    val from = widgets.hosted.indexOfFirst { it.appWidgetId == appWidgetId }
    val to = from + delta
    if (from < 0 || to !in widgets.hosted.indices) return this
    val list = widgets.hosted.toMutableList()
    list.add(to, list.removeAt(from))
    return updateWidgets { it.copy(hosted = list) }
}

// ----- Apparence -----

fun LauncherConfig.updateAppearance(transform: (Appearance) -> Appearance): LauncherConfig =
    copy(appearance = transform(appearance))

// ----- Import / export -----

/**
 * Prépare une configuration importée depuis une sauvegarde : les widgets Android
 * sont liés à l'appareil d'origine et ne peuvent pas être restaurés.
 */
fun LauncherConfig.sanitizedForImport(): LauncherConfig = copy(
    onboardingDone = true,
    widgets = widgets.copy(hosted = emptyList()),
    appearance = appearance.copy(customFontPath = null).let {
        if (it.font == FontChoice.CUSTOM) it.copy(font = FontChoice.SYSTEM) else it
    },
)
