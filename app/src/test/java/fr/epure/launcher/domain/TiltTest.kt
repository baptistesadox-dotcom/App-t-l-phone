package fr.epure.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class TiltTest {

    private val step = 20_000_000L // 20 ms entre deux mesures

    /** Fait tourner le téléphone à vitesse constante (rad/s) pendant [seconds]. */
    private fun TiltFilter.rotate(wx: Float, wy: Float, seconds: Float, start: Long): Long {
        var t = start
        repeat((seconds * 50).toInt()) {
            t += step
            onRotationSpeed(wx, wy, t)
        }
        return t
    }

    @Test
    fun `incliner le bord droit vers l arriere fait glisser l image vers la gauche`() {
        val filter = TiltFilter()
        filter.rotate(0f, 0.5f, 0.2f, start = 1L)
        assertTrue(filter.shiftX < -0.2f)
        assertEquals(0f, filter.shiftY, 1e-6f)
    }

    @Test
    fun `approcher le haut de l ecran fait monter l image`() {
        val filter = TiltFilter()
        filter.rotate(0.5f, 0f, 0.2f, start = 1L)
        assertTrue(filter.shiftY > 0.2f)
        assertEquals(0f, filter.shiftX, 1e-6f)
    }

    @Test
    fun `le decalage est borne puis revient au centre quand on ne bouge plus`() {
        val filter = TiltFilter()
        var t = filter.rotate(0f, 3f, 0.5f, start = 1L)
        assertEquals(-1f, filter.shiftX, 1e-6f)
        t = filter.rotate(0f, 0f, 8f, start = t)
        assertTrue(abs(filter.shiftX) < 0.05f)
        // Une longue pause (écran éteint) n'est pas comptée comme un mouvement.
        filter.onRotationSpeed(0f, 5f, t + 5_000_000_000L)
        assertTrue(abs(filter.shiftX) < 0.05f)
    }

    @Test
    fun `sans gyroscope la gravite donne le meme sens`() {
        val filter = TiltFilter()
        // Téléphone tenu droit : la gravité mesurée pointe vers le haut de l'écran.
        filter.onGravity(0f, 9.81f, 0f, 1L)
        // Le haut de l'écran s'approche de 0,1 rad : vue du téléphone, la gravité penche vers l'arrière.
        val angle = 0.1f
        filter.onGravity(0f, 9.81f * cos(angle), -9.81f * sin(angle), 1L + step)
        assertTrue(filter.shiftY > 0.2f)
        assertEquals(0f, filter.shiftX, 1e-6f)
    }

    @Test
    fun `agrandissement juste suffisant pour ne jamais montrer les bords`() {
        val zoom = Parallax.zoom(1080f, 2400f, 60f)
        assertEquals(1f + 120f / 1080f, zoom, 1e-6f)
        assertTrue((zoom - 1f) * 1080f / 2f >= 60f - 1e-3f)
        assertTrue((zoom - 1f) * 2400f / 2f >= 60f - 1e-3f)
        assertEquals(1f, Parallax.zoom(1080f, 2400f, 0f), 0f)
    }

    @Test
    fun `decalage a l ecran selon la rotation de l affichage`() {
        // Portrait : y du téléphone vers le haut, y de l'écran vers le bas.
        assertEquals(0.5f to -0.25f, Parallax.toScreen(0.5f, 0.25f, 0))
        assertEquals(0.25f to 0.5f, Parallax.toScreen(0.5f, 0.25f, 1))
        assertEquals(-0.5f to 0.25f, Parallax.toScreen(0.5f, 0.25f, 2))
        assertEquals(-0.25f to -0.5f, Parallax.toScreen(0.5f, 0.25f, 3))
    }
}
