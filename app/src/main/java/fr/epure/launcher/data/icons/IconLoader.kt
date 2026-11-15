package fr.epure.launcher.data.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import fr.epure.launcher.data.apps.AppShortcut
import fr.epure.launcher.data.apps.AppsRepository
import fr.epure.launcher.data.apps.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Charge les icônes des apps (pack d'icônes choisi, sinon icône d'origine)
 * et les garde en mémoire pour un défilement fluide.
 */
class IconLoader(
    private val context: Context,
    private val iconPacks: IconPackManager,
    private val appsRepository: AppsRepository,
) {
    private val cache = object : LruCache<String, ImageBitmap>(cacheSizeBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun cacheKey(appKey: String, sizePx: Int, iconPack: String?) = "$appKey|$sizePx|${iconPack.orEmpty()}"

    fun cached(app: InstalledApp, sizePx: Int, iconPack: String?): ImageBitmap? =
        cache.get(cacheKey(app.key, sizePx, iconPack))

    suspend fun load(app: InstalledApp, sizePx: Int, iconPack: String?): ImageBitmap? {
        val key = cacheKey(app.key, sizePx, iconPack)
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val density = context.resources.displayMetrics.densityDpi
            val fromPack = iconPack?.let { iconPacks.drawableFor(it, app.component, density) }
                ?.let { if (app.isWorkProfile) context.packageManager.getUserBadgedIcon(it, app.user) else it }
            val drawable = fromPack ?: runCatching { app.info?.getBadgedIcon(density) }.getOrNull()
            drawable?.toImageBitmap(sizePx)?.also { cache.put(key, it) }
        }
    }

    /** Apps déjà vérifiées qui n'ont pas de version monochrome de leur icône. */
    private val withoutMonochrome = HashSet<String>()

    /**
     * Version monochrome de l'icône (calque prévu par l'app pour les icônes à thème
     * d'Android 13+), en blanc, agrandie pour occuper toute la taille demandée.
     */
    suspend fun loadMonochrome(app: InstalledApp, sizePx: Int): ImageBitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val key = "mono|" + cacheKey(app.key, sizePx, null)
        cache.get(key)?.let { return it }
        if (synchronized(withoutMonochrome) { app.key in withoutMonochrome }) return null
        return withContext(Dispatchers.IO) {
            val density = context.resources.displayMetrics.densityDpi
            val adaptive = runCatching { app.info?.getIcon(density) }.getOrNull() as? AdaptiveIconDrawable
            val mono = adaptive?.monochrome?.mutate()
            if (mono == null) {
                synchronized(withoutMonochrome) { withoutMonochrome += app.key }
                return@withContext null
            }
            runCatching {
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                // Le calque fait 108 dp dont 72 visibles : on le déborde d'un quart de chaque côté.
                val inset = sizePx / 4
                mono.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
                mono.setTint(android.graphics.Color.WHITE)
                mono.draw(Canvas(bitmap))
                bitmap.asImageBitmap()
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }

    suspend fun loadShortcutIcon(shortcut: AppShortcut, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val density = context.resources.displayMetrics.densityDpi
        appsRepository.shortcutIcon(shortcut, density)?.toImageBitmap(sizePx)
    }

    private val _generation = MutableStateFlow(0)

    /** Change à chaque vidage du cache : l'interface recharge alors les icônes affichées. */
    val generation: StateFlow<Int> = _generation.asStateFlow()

    /** À appeler quand une app est installée ou mise à jour (son icône a pu changer). */
    fun clear() {
        cache.evictAll()
        synchronized(withoutMonochrome) { withoutMonochrome.clear() }
        _generation.value++
    }

    private fun Drawable.toImageBitmap(sizePx: Int): ImageBitmap? = runCatching {
        toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
    }.getOrNull()

    private companion object {
        /** Un huitième de la mémoire disponible pour l'app, au maximum 32 Mo. */
        fun cacheSizeBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(32L * 1024 * 1024).toInt()
    }
}
