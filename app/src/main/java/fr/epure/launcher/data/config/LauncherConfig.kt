package fr.epure.launcher.data.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Version du format de configuration (voir [migrated]). */
const val CURRENT_SCHEMA_VERSION = 4

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

/**
 * Polices : NUNITO, OUTFIT, MANROPE, MONTSERRAT et PLAYFAIR sont incluses dans l'app,
 * les autres viennent d'Android. (Enregistrées par leur nom : l'ordre peut changer.)
 */
@Serializable
enum class FontChoice { NUNITO, OUTFIT, MANROPE, MONTSERRAT, PLAYFAIR, SYSTEM, LIGHT, CONDENSED, SERIF, MONO, CASUAL, CURSIVE, CUSTOM }

/** Style des icônes d'apps. */
@Serializable
enum class IconStyle {
    /** Icônes blanches au trait fin, toutes du même style. */
    LINE,

    /** Icônes d'origine des apps (ou du pack d'icônes choisi). */
    ORIGINAL,
}

/** Mise en page de l'écran d'accueil. */
@Serializable
enum class HomeStyle {
    /** Grande typographie en haut (« PASSE UN BON DIMANCHE »), apps en liste compacte en bas. */
    TYPOGRAPHIC,

    /** Heure au-dessus d'une liste d'apps aérée, au centre de l'écran. */
    LIST,
}

/** Côté de l'écran où se trouve l'alphabet. */
@Serializable
enum class ScrollerSide { END, START }

/**
 * Vitesse de défilement de l'alphabet : écart entre les lettres (donc distance à parcourir
 * du doigt pour changer de lettre) et façon dont la liste rejoint la lettre.
 */
@Serializable
enum class ScrollerSpeed {
    /** Lettres très espacées, la liste glisse jusqu'à la lettre. */
    SLOW,

    /** Lettres espacées, la liste glisse jusqu'à la lettre. */
    NORMAL,

    /** Lettres serrées, la liste saute d'un coup (comportement de la 0.4.0). */
    FAST,
}

/**
 * Apparence. Par défaut : le style Épure (liste aérée, police ronde, couleurs Material You)
 * avec les icônes au trait. Chaque réglage se change séparément dans Réglages › Apparence.
 */
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
    val iconSizeDp: Int = DEFAULT_ICON_SIZE_DP,
    val iconStyle: IconStyle = IconStyle.LINE,
    val homeStyle: HomeStyle = HomeStyle.LIST,
    /** Nom de paquet du pack d'icônes choisi, ou null pour les icônes d'origine. */
    val iconPack: String? = null,
    /** Assombrissement du fond d'écran, de 0 (aucun) à 1 (noir). */
    val wallpaperDim: Float = 0.1f,
    val scrollerSide: ScrollerSide = ScrollerSide.END,
    val showScrollerOnHome: Boolean = true,
    val scrollerSpeed: ScrollerSpeed = ScrollerSpeed.NORMAL,
    /**
     * Image de l'accueil choisie dans Épure (copie dans le dossier privé de l'app), ou null
     * pour laisser voir le fond d'écran du téléphone.
     */
    val homeImagePath: String? = null,
    /**
     * Identifiant du fond d'écran du téléphone quand l'image a été choisie : s'il change
     * (nouveau fond choisi ailleurs), l'image de l'accueil s'efface pour le laisser voir.
     */
    val homeImageWallpaperId: Int? = null,
    /** Effet parallaxe : l'image de l'accueil glisse quand on incline le téléphone. */
    val parallax: Boolean = true,
    /** Intensité de l'effet parallaxe, de 0.2 à 1. */
    val parallaxStrength: Float = 0.6f,
)

/** Taille des icônes par défaut, en dp. */
const val DEFAULT_ICON_SIZE_DP = 38

@Serializable
data class WidgetsConfig(
    /** Grande salutation (« PASSE UN BON DIMANCHE »), sur l'accueil typographique seulement. */
    val showGreeting: Boolean = true,
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
