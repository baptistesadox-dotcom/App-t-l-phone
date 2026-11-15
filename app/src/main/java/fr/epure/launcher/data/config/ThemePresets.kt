package fr.epure.launcher.data.config

/**
 * Thèmes prêts à l'emploi. Un thème ne change que l'apparence : favoris, dossiers,
 * apps masquées et widgets restent tels quels.
 */
object ThemePresets {

    data class Preset(val id: String, val name: String, val description: String, val appearance: Appearance)

    /** Noir et blanc, icônes au trait, grande typographie (inspiré d'une configuration KWGT). */
    val RIVAGE = Preset(
        id = "rivage",
        name = "Rivage",
        description = "Noir et blanc, icônes au trait, grande typographie",
        appearance = Appearance(
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            accentColor = 0xFFFFFFFF,
            homeTextColor = HomeTextColor.LIGHT,
            font = FontChoice.MONTSERRAT,
            textScale = 1f,
            showIcons = true,
            iconSizeDp = 28,
            iconStyle = IconStyle.LINE,
            homeStyle = HomeStyle.TYPOGRAPHIC,
            wallpaperDim = 0.2f,
            scrollerSide = ScrollerSide.END,
            showScrollerOnHome = false,
        ),
    )

    /** Le style de la version 0.2 : liste aérée, icônes d'origine, police ronde. */
    val EPURE = Preset(
        id = "epure",
        name = "Épure",
        description = "Liste aérée au centre, icônes d'origine, police ronde",
        appearance = Appearance(
            themeMode = ThemeMode.SYSTEM,
            dynamicColor = true,
            accentColor = 0xFF8AB4F8,
            homeTextColor = HomeTextColor.AUTO,
            font = FontChoice.NUNITO,
            textScale = 1f,
            showIcons = true,
            iconSizeDp = 38,
            iconStyle = IconStyle.ORIGINAL,
            homeStyle = HomeStyle.LIST,
            wallpaperDim = 0.1f,
            scrollerSide = ScrollerSide.END,
            showScrollerOnHome = true,
        ),
    )

    val all: List<Preset> = listOf(RIVAGE, EPURE)

    /** Applique le thème en gardant la police importée et le pack d'icônes de l'utilisateur. */
    fun apply(config: LauncherConfig, preset: Preset): LauncherConfig = config.copy(
        appearance = preset.appearance.copy(
            customFontPath = config.appearance.customFontPath,
            iconPack = config.appearance.iconPack,
        ),
    )

    /** Thème actuellement appliqué tel quel, ou null si l'apparence a été personnalisée. */
    fun current(appearance: Appearance): Preset? = all.firstOrNull {
        it.appearance.copy(customFontPath = appearance.customFontPath, iconPack = appearance.iconPack) == appearance
    }
}
