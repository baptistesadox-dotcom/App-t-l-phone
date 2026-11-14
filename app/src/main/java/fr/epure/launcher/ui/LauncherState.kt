package fr.epure.launcher.ui

import androidx.compose.runtime.Immutable
import fr.epure.launcher.data.apps.InstalledApp
import fr.epure.launcher.data.config.Folder
import fr.epure.launcher.data.config.HomeItem
import fr.epure.launcher.data.config.LauncherConfig
import fr.epure.launcher.domain.AppSearch
import fr.epure.launcher.domain.sectionOf
import fr.epure.launcher.domain.sortByLabel
import java.util.Locale

/** Une app telle qu'elle est affichée (avec son éventuel nom personnalisé). */
@Immutable
data class AppEntry(
    val app: InstalledApp,
    val label: String,
    val section: String,
    val isFavorite: Boolean,
    val isHidden: Boolean,
) {
    val key: String get() = app.key
    val originalLabel: String get() = app.label
    val isRenamed: Boolean get() = label != app.label
}

@Immutable
data class FolderEntry(val folder: Folder, val apps: List<AppEntry>) {
    val id: String get() = folder.id
    val name: String get() = folder.name
}

/** Élément de l'accueil prêt à afficher. */
@Immutable
sealed interface HomeEntry {
    val key: String

    data class App(val entry: AppEntry) : HomeEntry {
        override val key: String get() = "app:${entry.key}"
    }

    data class FolderItem(val folder: FolderEntry) : HomeEntry {
        override val key: String get() = "folder:${folder.id}"
    }
}

/** Tout ce qu'il faut pour dessiner le launcher. */
@Immutable
data class LauncherState(
    val config: LauncherConfig,
    /** Apps visibles (non masquées), triées. */
    val apps: List<AppEntry>,
    /** Toutes les apps, y compris masquées (pour les réglages). */
    val allApps: List<AppEntry>,
    val home: List<HomeEntry>,
    val folders: List<FolderEntry>,
) {
    /** Dossiers affichés comme onglets dans la liste des apps. */
    val tabs: List<FolderEntry> get() = folders.filter { it.folder.showAsTab && it.apps.isNotEmpty() }

    fun appByKey(key: String): AppEntry? = allApps.firstOrNull { it.key == key }
    fun folderById(id: String): FolderEntry? = folders.firstOrNull { it.id == id }
}

fun buildLauncherState(
    config: LauncherConfig,
    installed: List<InstalledApp>,
    locale: Locale = Locale.getDefault(),
): LauncherState {
    val favoriteKeys = config.home.mapNotNullTo(HashSet()) { (it as? HomeItem.App)?.key }
    val entries = installed.map { app ->
        val label = config.customLabels[app.key]?.takeIf { it.isNotBlank() } ?: app.label
        AppEntry(
            app = app,
            label = label,
            section = sectionOf(label),
            isFavorite = app.key in favoriteKeys,
            isHidden = app.key in config.hiddenApps,
        )
    }
    val sorted = sortByLabel(entries, locale) { it.label }
    val byKey = sorted.associateBy { it.key }
    val visible = sorted.filterNot { it.isHidden }
    val folders = config.folders.map { folder ->
        val apps = folder.apps.mapNotNull { byKey[it] }.filterNot { it.isHidden }
        FolderEntry(folder, sortByLabel(apps, locale) { it.label })
    }
    val folderById = folders.associateBy { it.id }
    val home = config.home.mapNotNull { item ->
        when (item) {
            is HomeItem.App -> byKey[item.key]?.takeUnless { it.isHidden }?.let { HomeEntry.App(it) }
            is HomeItem.FolderRef -> folderById[item.folderId]?.let { HomeEntry.FolderItem(it) }
        }
    }
    return LauncherState(config, visible, sorted, home, folders)
}

/** Ligne de la liste des apps : en-tête de lettre ou app. */
@Immutable
sealed interface DrawerRow {
    val key: String

    data class Header(val letter: String) : DrawerRow {
        override val key: String get() = "header:$letter"
    }

    data class Item(val entry: AppEntry) : DrawerRow {
        override val key: String get() = "app:${entry.key}"
    }
}

@Immutable
data class DrawerList(
    val rows: List<DrawerRow>,
    /** Lettre → position de son en-tête dans [rows]. */
    val letterIndex: Map<String, Int>,
    val isSearch: Boolean,
    val resultCount: Int,
) {
    val letters: List<String> get() = letterIndex.keys.toList()

    companion object {
        val EMPTY = DrawerList(emptyList(), emptyMap(), isSearch = false, resultCount = 0)
    }
}

/**
 * Construit la liste affichée dans le tiroir d'apps : par lettre quand on ne cherche rien,
 * par pertinence pendant une recherche.
 */
fun buildDrawerList(apps: List<AppEntry>, query: String): DrawerList {
    if (query.isNotBlank()) {
        val results = AppSearch.search(
            items = apps,
            query = query,
            label = { it.label },
            alternativeLabel = { if (it.isRenamed) it.originalLabel else null },
        )
        return DrawerList(results.map { DrawerRow.Item(it) }, emptyMap(), isSearch = true, resultCount = results.size)
    }
    val rows = ArrayList<DrawerRow>(apps.size + 27)
    val index = LinkedHashMap<String, Int>()
    var current: String? = null
    for (app in apps) {
        if (app.section != current) {
            current = app.section
            index[app.section] = rows.size
            rows += DrawerRow.Header(app.section)
        }
        rows += DrawerRow.Item(app)
    }
    return DrawerList(rows, index, isSearch = false, resultCount = apps.size)
}
