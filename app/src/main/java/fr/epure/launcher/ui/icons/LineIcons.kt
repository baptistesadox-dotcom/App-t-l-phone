package fr.epure.launcher.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Icônes au trait fin (tracés Tabler), construites à la demande et gardées en mémoire. */
object LineIcons {

    /** Épaisseur du trait dans la grille 24×24 (Tabler dessine en 2 ; un peu plus fin ici). */
    private const val STROKE = 1.6f

    private val cache = HashMap<String, ImageVector>()

    fun has(name: String): Boolean = LineIconData.paths.containsKey(name)

    /** Icône blanche (à teinter à l'affichage), ou null si le nom est inconnu. */
    fun vector(name: String): ImageVector? = synchronized(cache) {
        cache[name] ?: build(name)?.also { cache[name] = it }
    }

    private fun build(name: String): ImageVector? {
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
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
