package fr.epure.launcher.data.icons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/** Un pack d'icônes installé (format compatible Nova / ADW / Lawnchair…). */
data class IconPackInfo(val packageName: String, val label: String)

/** Lecture de « appfilter.xml », le fichier qui associe chaque app à son icône dans le pack. */
object AppFilter {

    /**
     * « ComponentInfo{com.whatsapp/com.whatsapp.Main} » → (« com.whatsapp », « com.whatsapp.Main »).
     * Les noms de classe relatifs (« .Main ») sont complétés avec le nom du paquet.
     */
    fun parseComponent(raw: String): Pair<String, String>? {
        val inner = raw.substringAfter('{', "").substringBefore('}', "").trim()
        if (inner.isEmpty() || '/' !in inner) return null
        val pkg = inner.substringBefore('/').trim()
        var cls = inner.substringAfter('/').trim()
        if (pkg.isEmpty() || cls.isEmpty()) return null
        if (cls.startsWith(".")) cls = pkg + cls
        return pkg to cls
    }

    /** Lit toutes les balises « item » du fichier : « paquet/classe » → nom de l'image. */
    fun parse(parser: XmlPullParser): Map<String, String> {
        val result = HashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                val parsed = component?.let(::parseComponent)
                if (parsed != null && !drawable.isNullOrBlank()) {
                    result.putIfAbsent("${parsed.first}/${parsed.second}", drawable)
                }
            }
            event = parser.next()
        }
        return result
    }
}

/** Détecte les packs d'icônes installés et fournit leurs icônes. */
class IconPackManager(private val context: Context) {

    private class LoadedPack(
        val packageName: String,
        val resources: Resources,
        val byComponent: Map<String, String>,
        val byPackage: Map<String, String>,
    )

    @Volatile
    private var loaded: LoadedPack? = null

    fun installedPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val packages = PACK_INTENTS
            .flatMap { intent -> runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName }
            .distinct()
        return packages.mapNotNull { pkg ->
            runCatching {
                IconPackInfo(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString())
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
    }

    /** Icône du pack pour cette app, ou null si le pack n'en propose pas. */
    fun drawableFor(packName: String, component: ComponentName, density: Int): Drawable? {
        val pack = pack(packName) ?: return null
        val name = pack.byComponent[component.flattenToString()]
            ?: pack.byPackage[component.packageName]
            ?: return null
        return drawable(pack, name, density)
    }

    @Synchronized
    private fun pack(packageName: String): LoadedPack? {
        loaded?.takeIf { it.packageName == packageName }?.let { return it }
        val pack = load(packageName)
        loaded = pack
        return pack
    }

    @SuppressLint("DiscouragedApi") // Les packs d'icônes exposent leurs images par leur nom.
    private fun load(packageName: String): LoadedPack? = try {
        val res = context.packageManager.getResourcesForApplication(packageName)
        val xmlId = res.getIdentifier("appfilter", "xml", packageName)
        val mapping = if (xmlId != 0) {
            res.getXml(xmlId).use { AppFilter.parse(it) }
        } else {
            res.assets.open("appfilter.xml").use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, null)
                AppFilter.parse(parser)
            }
        }
        val byPackage = HashMap<String, String>()
        mapping.forEach { (component, drawable) -> byPackage.putIfAbsent(component.substringBefore('/'), drawable) }
        LoadedPack(packageName, res, mapping, byPackage)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(TAG, "Pack d'icônes absent : $packageName", e)
        null
    } catch (e: Exception) {
        Log.w(TAG, "Pack d'icônes illisible : $packageName", e)
        null
    }

    @SuppressLint("DiscouragedApi")
    private fun drawable(pack: LoadedPack, name: String, density: Int): Drawable? {
        val res = pack.resources
        val id = res.getIdentifier(name, "drawable", pack.packageName).takeIf { it != 0 }
            ?: res.getIdentifier(name, "mipmap", pack.packageName).takeIf { it != 0 }
            ?: return null
        return runCatching { res.getDrawableForDensity(id, density, null) }.getOrNull()
    }

    private companion object {
        const val TAG = "IconPacks"
        val PACK_INTENTS = listOf(
            Intent("org.adw.launcher.THEMES"),
            Intent("com.novalauncher.THEME"),
            Intent("com.teslacoilsw.launcher.THEME"),
            Intent("com.gau.go.launcherex.theme"),
            Intent("com.fede.launcher.THEME_ICONPACK"),
            Intent("com.dlto.atom.launcher.THEME"),
            Intent(Intent.ACTION_MAIN).addCategory("com.anddoes.launcher.THEME"),
        )
    }
}
