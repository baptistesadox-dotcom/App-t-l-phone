package fr.epure.launcher.domain

/**
 * Raccourci vers un réglage d'Android (ou une action rapide) trouvable dans la recherche.
 *
 * @param action action d'intent Android ouvrant le réglage ; null pour une action gérée par
 *   Épure lui-même (lampe torche).
 * @param fallbackAction réglage plus général si le premier n'existe pas sur le téléphone.
 */
data class SystemShortcut(
    val id: String,
    val title: String,
    val icon: String,
    val keywords: List<String>,
    val action: String?,
    val fallbackAction: String? = null,
)

/** Réglages d'Android proposés par la recherche (« wifi », « batterie », « lampe »…). */
object SystemShortcuts {

    const val TORCH = "torch"

    private const val SETTINGS = "android.settings.SETTINGS"

    val all: List<SystemShortcut> = listOf(
        SystemShortcut(TORCH, "Lampe torche", "bulb", listOf("lampe", "torche", "flash", "lumiere"), action = null),
        SystemShortcut("wifi", "Wi-Fi", "wifi", listOf("wifi", "wi-fi", "internet", "reseau", "box"), "android.settings.WIFI_SETTINGS"),
        SystemShortcut("bluetooth", "Bluetooth", "bluetooth", listOf("bluetooth", "casque", "ecouteurs", "enceinte", "appairer"), "android.settings.BLUETOOTH_SETTINGS"),
        SystemShortcut(
            "mobile", "Données mobiles", "antenna-bars-5", listOf("donnees mobiles", "4g", "5g", "reseau mobile", "sim", "operateur", "itinerance"),
            "android.settings.DATA_ROAMING_SETTINGS", fallbackAction = "android.settings.WIRELESS_SETTINGS",
        ),
        SystemShortcut("hotspot", "Partage de connexion", "access-point", listOf("partage de connexion", "point d'acces", "hotspot", "modem"), "android.settings.TETHER_SETTINGS", fallbackAction = "android.settings.WIRELESS_SETTINGS"),
        SystemShortcut("airplane", "Mode avion", "plane", listOf("mode avion", "avion"), "android.settings.AIRPLANE_MODE_SETTINGS"),
        SystemShortcut("data_usage", "Consommation des données", "database", listOf("consommation", "donnees", "forfait"), "android.settings.DATA_USAGE_SETTINGS", fallbackAction = "android.settings.WIRELESS_SETTINGS"),
        SystemShortcut("display", "Affichage", "brightness-half", listOf("affichage", "luminosite", "ecran", "mode sombre", "theme sombre", "veille"), "android.settings.DISPLAY_SETTINGS"),
        SystemShortcut("sound", "Son et vibreur", "volume", listOf("son", "volume", "sonnerie", "vibreur", "vibration", "silencieux"), "android.settings.SOUND_SETTINGS"),
        SystemShortcut("dnd", "Ne pas déranger", "bell-off", listOf("ne pas deranger", "deranger", "silence", "concentration"), "android.settings.ZEN_MODE_SETTINGS", fallbackAction = "android.settings.SOUND_SETTINGS"),
        SystemShortcut("notifications", "Notifications", "bell", listOf("notifications", "alertes"), "android.settings.NOTIFICATION_SETTINGS", fallbackAction = SETTINGS),
        SystemShortcut("battery", "Batterie", "battery", listOf("batterie", "autonomie", "economiseur", "charge"), "android.intent.action.POWER_USAGE_SUMMARY", fallbackAction = "android.settings.BATTERY_SAVER_SETTINGS"),
        SystemShortcut("apps", "Applications", "apps", listOf("applications", "applis", "apps", "autorisations"), "android.settings.MANAGE_APPLICATIONS_SETTINGS"),
        SystemShortcut("default_apps", "Applications par défaut", "apps", listOf("par defaut", "ecran d'accueil", "launcher", "navigateur par defaut"), "android.settings.MANAGE_DEFAULT_APPS_SETTINGS", fallbackAction = "android.settings.MANAGE_APPLICATIONS_SETTINGS"),
        SystemShortcut("storage", "Stockage", "device-sd-card", listOf("stockage", "memoire", "espace", "carte sd"), "android.settings.INTERNAL_STORAGE_SETTINGS"),
        SystemShortcut("location", "Localisation", "map-pin", listOf("localisation", "gps", "position"), "android.settings.LOCATION_SOURCE_SETTINGS"),
        SystemShortcut("security", "Sécurité", "shield-lock", listOf("securite", "verrouillage", "empreinte", "visage", "code pin", "mot de passe"), "android.settings.SECURITY_SETTINGS"),
        SystemShortcut("privacy", "Confidentialité", "lock", listOf("confidentialite", "vie privee"), "android.settings.PRIVACY_SETTINGS", fallbackAction = "android.settings.SECURITY_SETTINGS"),
        SystemShortcut("accounts", "Comptes", "user-circle", listOf("comptes", "compte google", "synchronisation"), "android.settings.SYNC_SETTINGS"),
        SystemShortcut("date", "Date et heure", "clock", listOf("date", "heure", "fuseau horaire"), "android.settings.DATE_SETTINGS"),
        SystemShortcut("language", "Langues", "language", listOf("langue", "langues", "traduction"), "android.settings.LOCALE_SETTINGS"),
        SystemShortcut("keyboard", "Clavier", "keyboard", listOf("clavier", "saisie", "dictionnaire"), "android.settings.INPUT_METHOD_SETTINGS"),
        SystemShortcut("accessibility", "Accessibilité", "accessible", listOf("accessibilite", "taille du texte", "talkback"), "android.settings.ACCESSIBILITY_SETTINGS"),
        SystemShortcut("nfc", "NFC et paiement sans contact", "nfc", listOf("nfc", "sans contact", "paiement"), "android.settings.NFC_SETTINGS", fallbackAction = "android.settings.WIRELESS_SETTINGS"),
        SystemShortcut("cast", "Caster l'écran", "cast", listOf("caster", "diffuser", "chromecast", "projeter"), "android.settings.CAST_SETTINGS"),
        SystemShortcut("vpn", "VPN", "shield", listOf("vpn"), "android.settings.VPN_SETTINGS"),
        SystemShortcut("wallpaper", "Fond d'écran Android", "photo", listOf("fond d'ecran", "wallpaper"), "android.intent.action.SET_WALLPAPER"),
        SystemShortcut("developer", "Options pour les développeurs", "code", listOf("developpeur", "options pour les developpeurs", "debogage"), "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"),
        SystemShortcut("about", "À propos du téléphone", "info-circle", listOf("a propos", "version d'android", "numero de serie", "modele"), "android.settings.DEVICE_INFO_SETTINGS"),
        SystemShortcut("system_update", "Mise à jour du système", "refresh", listOf("mise a jour", "mise a jour systeme", "android"), "android.settings.SYSTEM_UPDATE_SETTINGS", fallbackAction = "android.settings.DEVICE_INFO_SETTINGS"),
    )

    /**
     * Réglages correspondant à la recherche (au moins deux lettres) : le titre ou un mot-clé
     * commence par le texte tapé, ou un de leurs mots commence par lui.
     */
    fun search(query: String, limit: Int = 3): List<SystemShortcut> {
        val q = normalizeForSearch(query.replace('’', '\'')).trim()
        if (q.length < 2) return emptyList()
        return all
            .mapNotNull { shortcut -> score(shortcut, q)?.let { shortcut to it } }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun score(shortcut: SystemShortcut, q: String): Int? {
        val title = normalizeForSearch(shortcut.title)
        val keywords = shortcut.keywords.map(::normalizeForSearch)
        return when {
            title.startsWith(q) -> 4
            keywords.any { it.startsWith(q) } -> 3
            (listOf(title) + keywords).any { text -> text.split(' ', '-', '\'').any { it.startsWith(q) } } -> 2
            q.length >= 4 && (listOf(title) + keywords).any { it.contains(q) } -> 1
            else -> null
        }
    }
}
