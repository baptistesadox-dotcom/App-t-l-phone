package fr.epure.launcher.data.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Version du format de configuration (voir [migrated]). */
const val CURRENT_SCHEMA_VERSION = 2

/**
 * Toute la configuration de l'utilisateur, enregistrée en JSON.
 *
 * Chaque champ a une valeur par défaut : une sauvegarde créée par une ancienne
 * version reste lisible quand on ajoute de nouveaux réglages.
 *
 * Les apps sont identifiées par une « clé » stable (voir [fr.epure.launcher.data.apps.AppKeys]).
 */
@Serializable
data class LauncherConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /** Vrai une fois les favoris par défaut proposés au premier lancement. */
    val onboardingDone: Boolean = false,
    /** Éléments de l'accueil, dans l'ordre d'affichage (apps ou dossiers). */
    val home: List<HomeItem> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
    /** Noms personnalisés : clé d'app → nom affiché. */
    val customLabels: Map<String, String> = emptyMap(),
    val appearance: Appearance = Appearance(),
    val widgets: WidgetsConfig = WidgetsConfig(),
    val todos: List<TodoItem> = emptyList(),
    val gestures: Gestures = Gestures(),
    val notifications: NotificationsConfig = NotificationsConfig(),
)

/** Un élément de l'écran d'accueil : une app ou un dossier. */
@Serializable
sealed interface HomeItem {
    @Serializable
    @SerialName("app")
    data class App(val key: String) : HomeItem

    @Serializable
    @SerialName("folder")
    data class FolderRef(val folderId: String) : HomeItem
}

@Serializable
data class Folder(
    val id: String,
    val name: String,
    val apps: List<String> = emptyList(),
    /** Affiche le dossier comme onglet en haut de la liste des apps. */
    val showAsTab: Boolean = true,
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Couleur du texte posé sur le fond d'écran. */
@Serializable
enum class HomeTextColor { AUTO, LIGHT, DARK }

/** Polices : NUNITO, OUTFIT, MANROPE et PLAYFAIR sont incluses dans l'app, les autres viennent d'Android. */
@Serializable
enum class FontChoice { NUNITO, OUTFIT, MANROPE, PLAYFAIR, SYSTEM, LIGHT, CONDENSED, SERIF, MONO, CASUAL, CURSIVE, CUSTOM }

/** Côté de l'écran où se trouve l'alphabet. */
@Serializable
enum class ScrollerSide { END, START }

@Serializable
data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Couleurs Material You tirées du fond d'écran (Android 12+). */
    val dynamicColor: Boolean = true,
    /** Couleur d'accent ARGB, utilisée quand les couleurs dynamiques sont désactivées. */
    val accentColor: Long = 0xFF8AB4F8,
    val homeTextColor: HomeTextColor = HomeTextColor.AUTO,
    val font: FontChoice = FontChoice.NUNITO,
    /** Chemin du fichier de police importé (dossier privé de l'app). */
    val customFontPath: String? = null,
    val textScale: Float = 1f,
    val showIcons: Boolean = true,
    val iconSizeDp: Int = 38,
    /** Nom de paquet du pack d'icônes choisi, ou null pour les icônes d'origine. */
    val iconPack: String? = null,
    /** Assombrissement du fond d'écran, de 0 (aucun) à 1 (noir). */
    val wallpaperDim: Float = 0.1f,
    val scrollerSide: ScrollerSide = ScrollerSide.END,
    val showScrollerOnHome: Boolean = true,
)

@Serializable
data class WidgetsConfig(
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showWeather: Boolean = false,
    val weatherLocation: WeatherLocation? = null,
    val showAgenda: Boolean = false,
    /** Nombre de jours d'événements affichés (aujourd'hui inclus). */
    val agendaDays: Int = 2,
    val showTodo: Boolean = true,
    /** Widgets Android ajoutés par l'utilisateur, dans l'ordre d'affichage. */
    val hosted: List<HostedWidget> = emptyList(),
)

@Serializable
data class WeatherLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val region: String? = null,
)

@Serializable
data class HostedWidget(val appWidgetId: Int, val heightDp: Int)

@Serializable
data class TodoItem(val id: String, val text: String, val done: Boolean = false)

/** Notifications affichées sous le nom des apps (nécessite l'accès aux notifications). */
@Serializable
data class NotificationsConfig(
    /** Aperçu de la dernière notification sous les apps de l'accueil. */
    val showOnHome: Boolean = true,
    /** Pastille à côté des apps qui ont des notifications, dans la liste. */
    val showDots: Boolean = true,
)

@Serializable
data class Gestures(
    /** Glisser vers le bas sur l'accueil ouvre les notifications. */
    val swipeDownNotifications: Boolean = true,
)
