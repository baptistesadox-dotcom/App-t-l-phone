package fr.epure.launcher.data.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Image de l'accueil prête à afficher. */
class HomeImage(val bitmap: ImageBitmap, val prefersDarkText: Boolean)

/**
 * Image de l'accueil choisie par l'utilisateur. Elle est copiée, redimensionnée à la taille
 * de l'écran, dans le dossier privé de l'app : Épure l'affiche lui-même, ce qui permet
 * l'effet parallaxe.
 */
class HomeImageStore(context: Context) {

    private val appContext = context.applicationContext
    private val dir: File get() = File(appContext.filesDir, "accueil").apply { mkdirs() }

    /** Moment (horloge système) jusqu'auquel un changement du fond d'écran vient d'Épure lui-même. */
    @Volatile
    private var applyingUntil = 0L

    /** Vrai pendant qu'Épure change lui-même le fond d'écran du téléphone. */
    val isApplyingSystemWallpaper: Boolean get() = SystemClock.elapsedRealtime() < applyingUntil

    /** Copie une image choisie dans la galerie ; renvoie son chemin, ou null si elle est illisible. */
    fun import(uri: Uri): String? = save { decode(uri) }

    /** Copie une image fournie avec l'app (fond d'écran Rivage). */
    fun importResource(@DrawableRes id: Int): String? = save {
        BitmapFactory.decodeResource(appContext.resources, id)
    }

    /** Charge l'image à afficher, ou null si le fichier a disparu. */
    fun load(path: String): HomeImage? = runCatching {
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        bitmap.prepareToDraw()
        HomeImage(bitmap.asImageBitmap(), prefersDarkText = averageLuminance(bitmap) > 0.6f)
    }.onFailure { Log.w(TAG, "Image de l'accueil illisible", it) }.getOrNull()

    /** Supprime les anciennes images (on garde [keep], l'image actuelle). */
    fun cleanUp(keep: String?) {
        dir.listFiles()?.filter { it.absolutePath != keep }?.forEach { it.delete() }
    }

    /** Identifiant du fond d'écran actuel du téléphone (il change à chaque nouveau fond). */
    fun systemWallpaperId(): Int? =
        runCatching { WallpaperManager.getInstance(appContext).getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrNull()

    /**
     * Met l'image en fond d'écran du téléphone (écran d'accueil et écran verrouillé) ;
     * renvoie l'identifiant du nouveau fond, ou null en cas d'échec.
     */
    fun applyAsSystemWallpaper(path: String): Int? = applying {
        File(path).inputStream().use { input -> setSystemWallpaper(input) }
    }

    /** Met une image fournie avec l'app en fond d'écran du téléphone. */
    fun applyResourceAsSystemWallpaper(@DrawableRes id: Int): Int? = applying {
        appContext.resources.openRawResource(id).use { input -> setSystemWallpaper(input) }
    }

    private fun setSystemWallpaper(input: InputStream): Int =
        WallpaperManager.getInstance(appContext)
            .setStream(input, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)

    private fun applying(block: () -> Int): Int? {
        applyingUntil = SystemClock.elapsedRealtime() + 60_000L
        return try {
            runCatching(block)
                .onFailure { Log.w(TAG, "Fond d'écran non modifié", it) }
                .getOrNull()
                // L'identifiant relu fait foi : c'est lui que l'accueil comparera ensuite.
                ?.let { returned -> systemWallpaperId() ?: returned.takeIf { it > 0 } }
        } finally {
            // Laisse le temps à l'accueil d'apprendre le nouvel identifiant.
            applyingUntil = SystemClock.elapsedRealtime() + 3_000L
        }
    }

    private fun save(decode: () -> Bitmap?): String? = runCatching {
        val bitmap = cropToScreen(decode() ?: return null)
        val file = File(dir, "image-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        file.absolutePath
    }.onFailure { Log.w(TAG, "Image non importée", it) }.getOrNull()

    /**
     * Taille utile : l'écran (en portrait), un peu agrandi pour l'effet parallaxe.
     * Les images plus petites ne sont pas agrandies.
     */
    private fun targetSize(): Pair<Int, Int> {
        val metrics = appContext.resources.displayMetrics
        val short = min(metrics.widthPixels, metrics.heightPixels)
        val long = max(metrics.widthPixels, metrics.heightPixels)
        return (short * 1.25f).roundToInt() to (long * 1.25f).roundToInt()
    }

    /** Échelle qui garde juste assez de pixels pour couvrir [targetSize] (jamais plus de 1). */
    private fun scaleFor(width: Int, height: Int): Float {
        val (targetW, targetH) = targetSize()
        return min(1f, max(targetW.toFloat() / width, targetH.toFloat() / height))
    }

    private fun decode(uri: Uri): Bitmap? {
        val resolver = appContext.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder respecte l'orientation des photos (EXIF) et lit le HEIF.
            val source = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = scaleFor(info.size.width, info.size.height)
                val width = (info.size.width * scale).roundToInt().coerceAtLeast(1)
                val height = (info.size.height * scale).roundToInt().coerceAtLeast(1)
                if (scale < 1f) decoder.setTargetSize(width, height)
                // Seule la partie visible est gardée en mémoire (photo en paysage…).
                decoder.crop = screenCrop(width, height)
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val scale = scaleFor(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (sample * 2 <= (1f / scale)) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val remaining = scaleFor(decoded.width, decoded.height)
        if (remaining >= 0.99f) return decoded
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * remaining).roundToInt().coerceAtLeast(1),
            (decoded.height * remaining).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    /** Partie centrale d'une image [width]×[height], aux proportions de l'écran. */
    private fun screenCrop(width: Int, height: Int): Rect {
        val (targetW, targetH) = targetSize()
        val ratio = targetW.toFloat() / targetH
        val cropW = min(width, (height * ratio).roundToInt()).coerceAtLeast(1)
        val cropH = min(height, (width / ratio).roundToInt()).coerceAtLeast(1)
        val left = (width - cropW) / 2
        val top = (height - cropH) / 2
        return Rect(left, top, left + cropW, top + cropH)
    }

    /**
     * Garde la partie centrale de l'image, aux proportions de l'écran : c'est la seule
     * visible, et une photo en paysage pèserait sinon bien plus lourd en mémoire.
     */
    private fun cropToScreen(bitmap: Bitmap): Bitmap {
        val crop = screenCrop(bitmap.width, bitmap.height)
        if (crop.width() >= bitmap.width - 1 && crop.height() >= bitmap.height - 1) return bitmap
        val cropped = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
        if (cropped !== bitmap) bitmap.recycle()
        return cropped
    }

    private fun averageLuminance(bitmap: Bitmap): Float {
        val small = Bitmap.createScaledBitmap(bitmap, 9, 16, true)
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        if (small !== bitmap) small.recycle()
        return pixels.sumOf { c ->
            val r = (c shr 16 and 0xFF) / 255.0
            val g = (c shr 8 and 0xFF) / 255.0
            val b = (c and 0xFF) / 255.0
            0.2126 * r + 0.7152 * g + 0.0722 * b
        }.toFloat() / pixels.size
    }

    private companion object {
        const val TAG = "HomeImageStore"
    }
}
