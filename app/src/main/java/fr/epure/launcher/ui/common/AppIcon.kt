package fr.epure.launcher.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.epure.launcher.data.apps.AppShortcut
import fr.epure.launcher.data.apps.InstalledApp
import fr.epure.launcher.data.config.IconStyle
import fr.epure.launcher.data.icons.IconLoader
import fr.epure.launcher.domain.normalizeForSearch
import fr.epure.launcher.ui.icons.LineIcons
import fr.epure.launcher.ui.theme.LocalLauncherLook
import kotlin.math.abs

/** Source des icônes : le vrai chargeur dans l'app, une version factice dans les tests. */
interface IconProvider {
    fun cached(app: InstalledApp, sizePx: Int): ImageBitmap?
    suspend fun load(app: InstalledApp, sizePx: Int): ImageBitmap?
    suspend fun loadShortcut(shortcut: AppShortcut, sizePx: Int): ImageBitmap?

    /** Icône monochrome blanche (Android 13+), pour le style « au trait ». */
    suspend fun loadMonochrome(app: InstalledApp, sizePx: Int): ImageBitmap? = null
}

/** Icônes réelles, avec le pack d'icônes choisi (null = icônes d'origine). */
class LoaderIconProvider(private val loader: IconLoader, private val iconPack: String?) : IconProvider {
    override fun cached(app: InstalledApp, sizePx: Int) = loader.cached(app, sizePx, iconPack)
    override suspend fun load(app: InstalledApp, sizePx: Int) = loader.load(app, sizePx, iconPack)
    override suspend fun loadShortcut(shortcut: AppShortcut, sizePx: Int) = loader.loadShortcutIcon(shortcut, sizePx)
    override suspend fun loadMonochrome(app: InstalledApp, sizePx: Int) = loader.loadMonochrome(app, sizePx)
}

object NoIconProvider : IconProvider {
    override fun cached(app: InstalledApp, sizePx: Int): ImageBitmap? = null
    override suspend fun load(app: InstalledApp, sizePx: Int): ImageBitmap? = null
    override suspend fun loadShortcut(shortcut: AppShortcut, sizePx: Int): ImageBitmap? = null
}

val LocalIconProvider = staticCompositionLocalOf<IconProvider> { NoIconProvider }

/**
 * Icône d'une app, selon le style choisi : au trait (blanche, de la couleur du texte)
 * ou d'origine.
 */
@Composable
fun AppIcon(app: InstalledApp, label: String, size: Dp, modifier: Modifier = Modifier) {
    if (LocalLauncherLook.current.iconStyle == IconStyle.LINE) {
        LineAppIcon(app = app, label = label, size = size, modifier = modifier)
    } else {
        OriginalAppIcon(app = app, label = label, size = size, modifier = modifier)
    }
}

/** Icône au trait : tracé dédié, sinon icône monochrome de l'app, sinon initiale cerclée. */
@Composable
private fun LineAppIcon(app: InstalledApp, label: String, size: Dp, modifier: Modifier) {
    val tint = LocalContentColor.current
    val vector = remember(app.lineIcon, size) { app.lineIcon?.let { LineIcons.vector(it, size) } }
    if (vector != null) {
        Icon(imageVector = vector, contentDescription = null, tint = tint, modifier = modifier.size(size))
        return
    }
    val provider = LocalIconProvider.current
    val sizePx = with(LocalDensity.current) { (size * 0.9f).roundToPx() }
    val monochrome by produceState<ImageBitmap?>(initialValue = null, provider, app.key, sizePx) {
        value = provider.loadMonochrome(app, sizePx)
    }
    val image = monochrome
    if (image != null) {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            Image(bitmap = image, contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(size * 0.9f))
        }
    } else {
        LetterCircle(label = label, size = size, color = tint, modifier = modifier)
    }
}

/** Initiale dans un cercle au trait fin (icône au trait par défaut). */
@Composable
fun LetterCircle(label: String, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val initial = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .padding(size * 0.06f)
            .border(width = (size.value / 18f).coerceAtLeast(1f).dp, color = color, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initial, color = color, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.38f).sp)
    }
}

@Composable
private fun OriginalAppIcon(app: InstalledApp, label: String, size: Dp, modifier: Modifier) {
    val provider = LocalIconProvider.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val initial = remember(provider, app.key, sizePx) { provider.cached(app, sizePx) }
    val bitmap by produceState(initialValue = initial, provider, app.key, sizePx) {
        value = provider.load(app, sizePx) ?: value
    }
    val image = bitmap
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = modifier.size(size))
    } else {
        LetterAvatar(label = label, size = size, modifier = modifier)
    }
}

@Composable
fun ShortcutIcon(shortcut: AppShortcut, size: Dp, modifier: Modifier = Modifier) {
    val provider = LocalIconProvider.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, provider, shortcut.id, sizePx) {
        value = provider.loadShortcut(shortcut, sizePx)
    }
    val image = bitmap
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = modifier.size(size))
    } else {
        LetterAvatar(label = shortcut.label, size = size, modifier = modifier)
    }
}

/** Pastille colorée avec l'initiale, quand aucune icône n'est disponible. */
@Composable
fun LetterAvatar(label: String, size: Dp, modifier: Modifier = Modifier) {
    val initial = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val color = remember(label) { avatarColor(label) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.45f).sp,
        )
    }
}

private val AvatarColors = listOf(
    Color(0xFF5C7CFA), Color(0xFF20A39E), Color(0xFF7C4DFF), Color(0xFFEF6C57),
    Color(0xFF2E9D5B), Color(0xFFD9A21B), Color(0xFFE5578E), Color(0xFF4F8EC9),
)

private fun avatarColor(label: String): Color =
    AvatarColors[abs(normalizeForSearch(label).hashCode()) % AvatarColors.size]
