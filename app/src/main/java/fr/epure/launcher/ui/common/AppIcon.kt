package fr.epure.launcher.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import fr.epure.launcher.data.apps.AppShortcut
import fr.epure.launcher.data.apps.InstalledApp
import fr.epure.launcher.data.icons.IconLoader
import fr.epure.launcher.domain.normalizeForSearch
import kotlin.math.abs

/** Source des icônes : le vrai chargeur dans l'app, une version factice dans les tests. */
interface IconProvider {
    fun cached(app: InstalledApp, sizePx: Int): ImageBitmap?
    suspend fun load(app: InstalledApp, sizePx: Int): ImageBitmap?
    suspend fun loadShortcut(shortcut: AppShortcut, sizePx: Int): ImageBitmap?
}

/** Icônes réelles, avec le pack d'icônes choisi (null = icônes d'origine). */
class LoaderIconProvider(private val loader: IconLoader, private val iconPack: String?) : IconProvider {
    override fun cached(app: InstalledApp, sizePx: Int) = loader.cached(app, sizePx, iconPack)
    override suspend fun load(app: InstalledApp, sizePx: Int) = loader.load(app, sizePx, iconPack)
    override suspend fun loadShortcut(shortcut: AppShortcut, sizePx: Int) = loader.loadShortcutIcon(shortcut, sizePx)
}

object NoIconProvider : IconProvider {
    override fun cached(app: InstalledApp, sizePx: Int): ImageBitmap? = null
    override suspend fun load(app: InstalledApp, sizePx: Int): ImageBitmap? = null
    override suspend fun loadShortcut(shortcut: AppShortcut, sizePx: Int): ImageBitmap? = null
}

val LocalIconProvider = staticCompositionLocalOf<IconProvider> { NoIconProvider }

@Composable
fun AppIcon(app: InstalledApp, label: String, size: Dp, modifier: Modifier = Modifier) {
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
