package fr.epure.launcher.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Icônes au trait fin (tracés Tabler), construites à la demande et gardées en mémoire. */
object LineIcons {

    /**
     * Épaisseur du trait à l'écran, la même quelle que soit la taille de l'icône :
     * une grande icône garde un trait fin (Tabler dessine en 2 sur une grille de 24).
     */
    val STROKE: Dp = 1.8.dp

    private val cache = HashMap<Pair<String, Int>, ImageVector>()

    fun has(name: String): Boolean = LineIconData.paths.containsKey(name)

    /**
     * Icône blanche (à teinter à l'affichage) pour une taille donnée, ou null si le nom
     * est inconnu.
     */
    fun vector(name: String, size: Dp): ImageVector? {
        // Épaisseur dans la grille 24×24, arrondie au centième pour limiter le cache.
        val hundredths = (STROKE.value * 24f / size.value.coerceAtLeast(1f) * 100f).roundToInt().coerceIn(40, 240)
        return synchronized(cache) {
            val key = name to hundredths
            cache[key] ?: build(name, hundredths / 100f)?.also { cache[key] = it }
        }
    }

    private fun build(name: String, stroke: Float): ImageVector? {
        val paths = LineIconData.paths[name] ?: return null
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        paths.forEach { data ->
            builder.addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
