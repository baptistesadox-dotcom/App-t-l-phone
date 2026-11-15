package fr.epure.launcher.domain

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Inclinaison du téléphone pour l'effet parallaxe, à partir du gyroscope ou, à défaut,
 * de la gravité.
 *
 * On mesure de combien le téléphone a tourné depuis sa « position de repos », qui suit
 * lentement la position réelle : on tient le téléphone comme on veut, l'image se recentre
 * doucement, et seuls les mouvements la font glisser.
 *
 * Repère du téléphone (celui d'Android) : x vers la droite, y vers le haut de l'écran,
 * z vers l'utilisateur.
 */
class TiltFilter(
    /** Rotation (en radians, environ 17°) qui donne le décalage maximal. */
    private val maxAngle: Float = 0.3f,
    /** Temps caractéristique (en secondes) du retour au centre. */
    private val settleSeconds: Float = 1.5f,
) {
    /** Décalage de l'image, de -1 à 1, vers la droite si positif. */
    var shiftX: Float = 0f
        private set

    /** Décalage de l'image, de -1 à 1, vers le haut de l'écran si positif. */
    var shiftY: Float = 0f
        private set

    /** Rotation depuis la position de repos, autour des axes x et y du téléphone (radians). */
    private var angleX = 0f
    private var angleY = 0f
    private var lastNanos = 0L

    /** Direction de la gravité au repos (vecteur unitaire), pour le calcul sans gyroscope. */
    private var rest: FloatArray? = null

    fun reset() {
        shiftX = 0f
        shiftY = 0f
        angleX = 0f
        angleY = 0f
        lastNanos = 0L
        rest = null
    }

    /** Vitesse de rotation mesurée par le gyroscope (rad/s), autour des axes x et y du téléphone. */
    fun onRotationSpeed(wx: Float, wy: Float, timestampNanos: Long) {
        val dt = elapsed(timestampNanos) ?: return
        val keep = exp(-dt / settleSeconds)
        angleX = (angleX + wx * dt) * keep
        angleY = (angleY + wy * dt) * keep
        publish()
    }

    /** Gravité mesurée dans le repère du téléphone (n'importe quelle unité). */
    fun onGravity(gx: Float, gy: Float, gz: Float, timestampNanos: Long) {
        val norm = sqrt(gx * gx + gy * gy + gz * gz)
        if (norm < 1e-3f) return
        val ux = gx / norm
        val uy = gy / norm
        val uz = gz / norm
        val r = rest
        if (r == null) {
            rest = floatArrayOf(ux, uy, uz)
            lastNanos = timestampNanos
            return
        }
        val dt = elapsed(timestampNanos) ?: return
        // Vue du téléphone, la gravité (fixe) tourne dans le sens inverse du téléphone :
        // la rotation du téléphone depuis le repos vaut environ u × repos.
        angleX = uy * r[2] - uz * r[1]
        angleY = uz * r[0] - ux * r[2]
        // La position de repos rattrape doucement la position actuelle.
        val k = 1f - exp(-dt / settleSeconds)
        r[0] += (ux - r[0]) * k
        r[1] += (uy - r[1]) * k
        r[2] += (uz - r[2]) * k
        val n = max(sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]), 1e-6f)
        r[0] /= n
        r[1] /= n
        r[2] /= n
        publish()
    }

    private fun elapsed(timestampNanos: Long): Float? {
        val last = lastNanos
        lastNanos = timestampNanos
        if (last == 0L) return null
        val dt = (timestampNanos - last) / 1e9f
        // Trou dans les mesures (capteur en pause) : on ignore cet intervalle.
        return if (dt <= 0f || dt > 0.25f) null else dt
    }

    private fun publish() {
        // L'image, comme un décor derrière la vitre, glisse du côté où l'œil se déplace par
        // rapport au téléphone : bord droit qui s'éloigne (rotation autour de y) → elle va
        // vers la gauche ; haut de l'écran qui s'approche (rotation autour de x) → elle monte.
        shiftX = (-angleY / maxAngle).coerceIn(-1f, 1f)
        shiftY = (angleX / maxAngle).coerceIn(-1f, 1f)
    }
}

/** Calculs d'affichage de l'effet parallaxe. */
object Parallax {

    /**
     * Agrandissement de l'image pour pouvoir la décaler de [maxShift] dans toutes les
     * directions sans montrer ses bords.
     */
    fun zoom(width: Float, height: Float, maxShift: Float): Float {
        if (width <= 0f || height <= 0f || maxShift <= 0f) return 1f
        return max(1f + 2f * maxShift / width, 1f + 2f * maxShift / height)
    }

    /**
     * Décalage à l'écran (x vers la droite, y vers le bas) pour un décalage exprimé dans le
     * repère du téléphone ([TiltFilter]), selon la rotation de l'affichage
     * (0, 1, 2, 3 pour 0°, 90°, 180°, 270°, comme `Surface.ROTATION_*`).
     */
    fun toScreen(shiftX: Float, shiftY: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        1 -> shiftY to shiftX
        2 -> -shiftX to shiftY
        3 -> -shiftY to -shiftX
        else -> shiftX to -shiftY
    }
}
