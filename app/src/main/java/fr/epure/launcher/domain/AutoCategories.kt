package fr.epure.launcher.domain

/**
 * Catégories proposées pour ranger automatiquement les apps dans des dossiers.
 * Elles viennent de la catégorie déclarée par chaque app (Android 8+), complétée
 * par une petite liste d'apps très courantes qui ne la déclarent pas.
 */
enum class AutoCategory(val folderName: String) {
    SOCIAL("Réseaux sociaux"),
    MESSAGING("Messages"),
    AUDIO("Musique & audio"),
    VIDEO("Vidéo"),
    PHOTO("Photo"),
    GAMES("Jeux"),
    NEWS("Actualités"),
    MAPS("Cartes & transports"),
    PRODUCTIVITY("Travail"),
    SHOPPING("Shopping"),
    FINANCE("Banque & finances"),
    ACCESSIBILITY("Accessibilité"),
}

object AutoCategories {

    // Valeurs de ApplicationInfo.CATEGORY_* (constantes stables du SDK Android).
    private const val CATEGORY_GAME = 0
    private const val CATEGORY_AUDIO = 1
    private const val CATEGORY_VIDEO = 2
    private const val CATEGORY_IMAGE = 3
    private const val CATEGORY_SOCIAL = 4
    private const val CATEGORY_NEWS = 5
    private const val CATEGORY_MAPS = 6
    private const val CATEGORY_PRODUCTIVITY = 7
    private const val CATEGORY_ACCESSIBILITY = 8

    private val knownPackages: Map<String, AutoCategory> = mapOf(
        "com.whatsapp" to AutoCategory.MESSAGING,
        "com.whatsapp.w4b" to AutoCategory.MESSAGING,
        "org.telegram.messenger" to AutoCategory.MESSAGING,
        "org.thoughtcrime.securesms" to AutoCategory.MESSAGING,
        "com.facebook.orca" to AutoCategory.MESSAGING,
        "com.google.android.apps.messaging" to AutoCategory.MESSAGING,
        "com.samsung.android.messaging" to AutoCategory.MESSAGING,
        "com.discord" to AutoCategory.MESSAGING,
        "com.instagram.android" to AutoCategory.SOCIAL,
        "com.instagram.barcelona" to AutoCategory.SOCIAL,
        "com.facebook.katana" to AutoCategory.SOCIAL,
        "com.snapchat.android" to AutoCategory.SOCIAL,
        "com.zhiliaoapp.musically" to AutoCategory.SOCIAL,
        "com.ss.android.ugc.trill" to AutoCategory.SOCIAL,
        "com.twitter.android" to AutoCategory.SOCIAL,
        "com.bereal.ft" to AutoCategory.SOCIAL,
        "com.linkedin.android" to AutoCategory.SOCIAL,
        "com.reddit.frontpage" to AutoCategory.SOCIAL,
        "com.pinterest" to AutoCategory.SOCIAL,
        "com.spotify.music" to AutoCategory.AUDIO,
        "deezer.android.app" to AutoCategory.AUDIO,
        "com.google.android.apps.youtube.music" to AutoCategory.AUDIO,
        "com.soundcloud.android" to AutoCategory.AUDIO,
        "com.google.android.youtube" to AutoCategory.VIDEO,
        "com.netflix.mediaclient" to AutoCategory.VIDEO,
        "com.disney.disneyplus" to AutoCategory.VIDEO,
        "com.amazon.avod.thirdpartyclient" to AutoCategory.VIDEO,
        "tv.twitch.android.app" to AutoCategory.VIDEO,
        "com.canal.android.canal" to AutoCategory.VIDEO,
        "com.google.android.apps.photos" to AutoCategory.PHOTO,
        "com.google.android.apps.maps" to AutoCategory.MAPS,
        "com.waze" to AutoCategory.MAPS,
        "com.ubercab" to AutoCategory.MAPS,
        "com.sncf.fusion" to AutoCategory.MAPS,
        "com.citymapper.app.release" to AutoCategory.MAPS,
        "com.google.android.gm" to AutoCategory.PRODUCTIVITY,
        "com.microsoft.office.outlook" to AutoCategory.PRODUCTIVITY,
        "com.google.android.apps.docs" to AutoCategory.PRODUCTIVITY,
        "com.google.android.keep" to AutoCategory.PRODUCTIVITY,
        "com.notion.id" to AutoCategory.PRODUCTIVITY,
        "com.Slack" to AutoCategory.PRODUCTIVITY,
        "com.microsoft.teams" to AutoCategory.PRODUCTIVITY,
        "com.amazon.mShop.android.shopping" to AutoCategory.SHOPPING,
        "com.vinted" to AutoCategory.SHOPPING,
        "fr.leboncoin" to AutoCategory.SHOPPING,
        "com.paypal.android.p2pmobile" to AutoCategory.FINANCE,
        "com.revolut.revolut" to AutoCategory.FINANCE,
        "de.number26.android" to AutoCategory.FINANCE,
        "com.boursorama.android.clients" to AutoCategory.FINANCE,
        "com.lydia" to AutoCategory.FINANCE,
    )

    fun categoryOf(packageName: String, declaredCategory: Int): AutoCategory? =
        knownPackages[packageName] ?: when (declaredCategory) {
            CATEGORY_GAME -> AutoCategory.GAMES
            CATEGORY_AUDIO -> AutoCategory.AUDIO
            CATEGORY_VIDEO -> AutoCategory.VIDEO
            CATEGORY_IMAGE -> AutoCategory.PHOTO
            CATEGORY_SOCIAL -> AutoCategory.SOCIAL
            CATEGORY_NEWS -> AutoCategory.NEWS
            CATEGORY_MAPS -> AutoCategory.MAPS
            CATEGORY_PRODUCTIVITY -> AutoCategory.PRODUCTIVITY
            CATEGORY_ACCESSIBILITY -> AutoCategory.ACCESSIBILITY
            else -> null
        }

    /**
     * Regroupe les apps par catégorie. Seules les catégories contenant au moins
     * [minAppsPerFolder] apps sont gardées, pour éviter les dossiers d'une seule app.
     *
     * @param apps triplets (clé d'app, nom de paquet, catégorie déclarée)
     * @return nom du dossier → clés des apps, dans l'ordre des catégories
     */
    fun group(apps: List<Triple<String, String, Int>>, minAppsPerFolder: Int = 2): Map<String, List<String>> {
        val byCategory = apps
            .mapNotNull { (key, pkg, declared) -> categoryOf(pkg, declared)?.let { it to key } }
            .groupBy({ it.first }, { it.second })
        return AutoCategory.entries
            .mapNotNull { category ->
                byCategory[category]?.takeIf { it.size >= minAppsPerFolder }?.let { category.folderName to it }
            }
            .toMap()
    }
}
