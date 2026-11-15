package fr.epure.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.epure.launcher.data.config.Appearance
import fr.epure.launcher.data.config.HomeStyle
import fr.epure.launcher.data.config.HomeTextColor
import fr.epure.launcher.data.config.IconStyle
import fr.epure.launcher.data.config.ScrollerSide
import fr.epure.launcher.data.config.ThemeMode

/** Couleurs d'accent proposées quand Material You est désactivé. */
val AccentPalette: List<Long> = listOf(
    0xFF8AB4F8, // bleu
    0xFF7FD1C7, // turquoise
    0xFF9CD67D, // vert
    0xFFF2C14E, // or
    0xFFF59E6C, // corail
    0xFFF28BA8, // rose
    0xFFC3A6FF, // lavande
    0xFFE6E1D8, // ivoire
)

/** Réglages visuels propres au launcher, accessibles partout dans l'interface. */
@Immutable
data class LauncherLook(
    val showIcons: Boolean,
    val iconSize: Dp,
    val textScale: Float,
    /** Couleur du texte posé sur le fond d'écran. */
    val onWallpaper: Color,
    val onWallpaperMuted: Color,
    val textShadow: Shadow?,
    val wallpaperDim: Float,
    val scrollerSide: ScrollerSide,
    val showScrollerOnHome: Boolean,
    val isDark: Boolean,
    val iconStyle: IconStyle = IconStyle.ORIGINAL,
    val homeStyle: HomeStyle = HomeStyle.LIST,
) {
    /** Style d'un texte posé sur le fond d'écran (avec une ombre légère si le texte est clair). */
    fun TextStyle.onWallpaper(muted: Boolean = false): TextStyle =
        copy(color = if (muted) onWallpaperMuted else onWallpaper, shadow = textShadow)
}

val LocalLauncherLook = staticCompositionLocalOf {
    LauncherLook(
        showIcons = true,
        iconSize = 36.dp,
        textScale = 1f,
        onWallpaper = Color.White,
        onWallpaperMuted = Color.White.copy(alpha = 0.72f),
        textShadow = null,
        wallpaperDim = 0f,
        scrollerSide = ScrollerSide.END,
        showScrollerOnHome = true,
        isDark = true,
    )
}

fun isDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private fun accentScheme(accent: Color, dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF101114),
            primaryContainer = lerp(accent, Color(0xFF15171B), 0.72f),
            onPrimaryContainer = lerp(accent, Color.White, 0.6f),
            secondary = accent,
            secondaryContainer = lerp(accent, Color(0xFF15171B), 0.8f),
            onSecondaryContainer = lerp(accent, Color.White, 0.7f),
            tertiary = accent,
            background = Color(0xFF0F1013),
            onBackground = Color(0xFFE8E8EC),
            surface = Color(0xFF15171B),
            onSurface = Color(0xFFE8E8EC),
            surfaceVariant = Color(0xFF24272D),
            onSurfaceVariant = Color(0xFFBFC2CA),
            surfaceContainerLowest = Color(0xFF0B0C0E),
            surfaceContainerLow = Color(0xFF17191D),
            surfaceContainer = Color(0xFF1B1D22),
            surfaceContainerHigh = Color(0xFF22252A),
            surfaceContainerHighest = Color(0xFF2B2E34),
            outline = Color(0xFF6E727A),
            outlineVariant = Color(0xFF3A3D44),
        )
    } else {
        val strong = lerp(accent, Color.Black, 0.45f)
        lightColorScheme(
            primary = strong,
            onPrimary = Color.White,
            primaryContainer = lerp(accent, Color.White, 0.6f),
            onPrimaryContainer = lerp(accent, Color.Black, 0.7f),
            secondary = strong,
            secondaryContainer = lerp(accent, Color.White, 0.7f),
            onSecondaryContainer = lerp(accent, Color.Black, 0.7f),
            tertiary = strong,
            background = Color(0xFFF7F7F9),
            onBackground = Color(0xFF1A1B1F),
            surface = Color(0xFFFBFBFD),
            onSurface = Color(0xFF1A1B1F),
            surfaceVariant = Color(0xFFE6E7EC),
            onSurfaceVariant = Color(0xFF45474E),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF4F4F7),
            surfaceContainer = Color(0xFFEFEFF3),
            surfaceContainerHigh = Color(0xFFE9E9EE),
            surfaceContainerHighest = Color(0xFFE3E3E9),
            outline = Color(0xFF777A82),
            outlineVariant = Color(0xFFC7C8CE),
        )
    }

private fun Typography.withFont(family: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/**
 * Thème du launcher, entièrement piloté par les réglages d'apparence.
 *
 * @param wallpaperPrefersDarkText vrai si le fond d'écran est clair (texte sombre conseillé).
 */
@Composable
fun LauncherTheme(
    appearance: Appearance,
    wallpaperPrefersDarkText: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = isDarkTheme(appearance.themeMode, isSystemInDarkTheme())
    val colorScheme = when {
        appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> accentScheme(Color(appearance.accentColor), dark)
    }
    val fontFamily = remember(appearance.font, appearance.customFontPath) {
        Fonts.family(appearance.font, appearance.customFontPath)
    }
    val typography = remember(fontFamily) { Typography().withFont(fontFamily) }

    val lightText = when (appearance.homeTextColor) {
        HomeTextColor.LIGHT -> true
        HomeTextColor.DARK -> false
        // Un fond assombri appelle toujours un texte clair.
        HomeTextColor.AUTO -> !wallpaperPrefersDarkText || appearance.wallpaperDim >= 0.35f
    }
    val look = LauncherLook(
        showIcons = appearance.showIcons,
        iconSize = appearance.iconSizeDp.coerceIn(20, 64).dp,
        textScale = appearance.textScale.coerceIn(0.7f, 1.6f),
        onWallpaper = if (lightText) Color.White else Color(0xFF15161A),
        onWallpaperMuted = if (lightText) Color.White.copy(alpha = 0.74f) else Color(0xFF15161A).copy(alpha = 0.7f),
        textShadow = if (lightText) Shadow(Color.Black.copy(alpha = 0.35f), blurRadius = 6f) else null,
        wallpaperDim = appearance.wallpaperDim.coerceIn(0f, 0.9f),
        scrollerSide = appearance.scrollerSide,
        showScrollerOnHome = appearance.showScrollerOnHome,
        isDark = dark,
        iconStyle = appearance.iconStyle,
        homeStyle = appearance.homeStyle,
    )
    MaterialTheme(colorScheme = colorScheme, typography = typography) {
        CompositionLocalProvider(LocalLauncherLook provides look, content = content)
    }
}
