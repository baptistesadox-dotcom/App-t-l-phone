package fr.epure.launcher.domain

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val WORD_SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

/** Section utilisée pour tout ce qui ne commence pas par une lettre (chiffres, symboles…). */
const val OTHER_SECTION = "#"

/** Toutes les sections possibles, dans l'ordre de l'alphabet latéral. */
val ALL_SECTIONS: List<String> = listOf(OTHER_SECTION) + ('A'..'Z').map { it.toString() }

/**
 * Met un texte sous une forme comparable : minuscules, sans accents ni ligatures.
 * « Éditeur Œuvres » → « editeur oeuvres ».
 */
fun normalizeForSearch(text: String): String {
    val expanded = text
        .replace("œ", "oe").replace("Œ", "OE")
        .replace("æ", "ae").replace("Æ", "AE")
        .replace("ß", "ss")
    return Normalizer.normalize(expanded, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .trim()
}

/** Lettre de section d'un nom d'app : « Écran » → « E », « 7-Zip » → « # ». */
fun sectionOf(label: String): String {
    val first = normalizeForSearch(label).firstOrNull { !it.isWhitespace() }
        ?: return OTHER_SECTION
    val upper = first.uppercaseChar()
    return if (upper in 'A'..'Z') upper.toString() else OTHER_SECTION
}

private fun sectionRank(section: String): Int =
    if (section == OTHER_SECTION) 0 else section[0] - 'A' + 1

/**
 * Trie par section (# puis A → Z) puis selon l'ordre alphabétique de la langue
 * (« Éditeur » est rangé avec les E, les majuscules n'influencent pas l'ordre).
 */
fun <T> sortByLabel(items: List<T>, locale: Locale = Locale.getDefault(), label: (T) -> String): List<T> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    return items.sortedWith { a, b ->
        val la = label(a)
        val lb = label(b)
        val bySection = sectionRank(sectionOf(la)) - sectionRank(sectionOf(lb))
        if (bySection != 0) bySection else collator.compare(la, lb)
    }
}

/** Recherche d'apps tolérante : sans accents, par début de mot, par initiales. */
object AppSearch {

    /** Score de pertinence (0 = ne correspond pas). */
    fun score(query: String, label: String, alternativeLabel: String? = null): Int {
        val q = normalizeForSearch(query)
        if (q.isEmpty()) return 0
        val main = scoreNormalized(q, normalizeForSearch(label))
        val alt = alternativeLabel?.let { scoreNormalized(q, normalizeForSearch(it)) - 1 } ?: 0
        return maxOf(main, alt, 0)
    }

    private fun scoreNormalized(q: String, l: String): Int {
        if (l.isEmpty()) return 0
        val words = l.split(WORD_SEPARATORS).filter { it.isNotEmpty() }
        val compact = words.joinToString("")
        return when {
            l == q -> 100
            l.startsWith(q) -> 80
            words.any { it.startsWith(q) } -> 60
            compact.startsWith(q.replace(" ", "")) -> 55
            l.contains(q) -> 40
            q.length >= 2 && words.map { it.first() }.joinToString("").startsWith(q) -> 30
            q.length >= 3 && isSubsequence(q.replace(" ", ""), compact) -> 10
            else -> 0
        }
    }

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        var i = 0
        for (c in haystack) {
            if (i < needle.length && needle[i] == c) i++
        }
        return i == needle.length
    }

    /** Résultats triés du plus pertinent au moins pertinent, puis par ordre alphabétique. */
    fun <T> search(
        items: List<T>,
        query: String,
        label: (T) -> String,
        alternativeLabel: (T) -> String? = { null },
    ): List<T> {
        if (normalizeForSearch(query).isEmpty()) return emptyList()
        return items
            .map { it to score(query, label(it), alternativeLabel(it)) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<T, Int>> { it.second }
                    .thenBy { normalizeForSearch(label(it.first)) },
            )
            .map { it.first }
    }
}
