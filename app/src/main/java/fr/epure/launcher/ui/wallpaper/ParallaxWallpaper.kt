package fr.epure.launcher.ui.wallpaper

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.epure.launcher.domain.Parallax
import fr.epure.launcher.domain.TiltFilter
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** Décalage maximal de l'image, à 100 % d'intensité. */
val PARALLAX_MAX_SHIFT: Dp = 40.dp

/**
 * Image de l'accueil, légèrement agrandie, qui glisse quand on incline le téléphone
 * (effet parallaxe). Les capteurs ne sont écoutés que lorsque l'accueil est affiché.
 *
 * @param strength intensité de l'effet, de 0 à 1 (0 : image fixe, sans agrandissement).
 * @param previewShift décalage imposé, de -1 à 1 (captures d'écran de test).
 */
@Composable
fun ParallaxWallpaper(
    image: ImageBitmap,
    strength: Float,
    modifier: Modifier = Modifier,
    previewShift: Offset? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val active = strength > 0f && previewShift == null
    // Décalage affiché, de -1 à 1 sur chaque axe (x vers la droite, y vers le bas).
    val shift = remember { Animatable(previewShift ?: Offset.Zero, Offset.VectorConverter) }
    val target = remember { mutableStateOf(Offset.Zero) }

    if (active) {
        LifecycleResumeEffect(context) {
            val sensor = TiltSensor(context) { x, y ->
                val (sx, sy) = Parallax.toScreen(x, y, view.display?.rotation ?: Surface.ROTATION_0)
                // Arrondi au 1/128 : les infimes tremblements ne redessinent pas l'écran.
                target.value = Offset((sx * 128f).roundToInt() / 128f, (sy * 128f).roundToInt() / 128f)
            }
            sensor.start()
            onPauseOrDispose {
                sensor.stop()
                target.value = Offset.Zero
            }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { target.value }.collectLatest { shift.animateTo(it, spring(stiffness = 300f)) }
        }
    } else {
        LaunchedEffect(previewShift) { shift.snapTo(previewShift ?: Offset.Zero) }
    }

    val density = LocalDensity.current
    val maxShift = with(density) { (PARALLAX_MAX_SHIFT * strength.coerceIn(0f, 1f)).toPx() }
    val latestShift = rememberUpdatedState(maxShift)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val zoom = Parallax.zoom(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), maxShift)
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Lu ici seulement : le mouvement ne recompose rien, il déplace un calque.
                    scaleX = zoom
                    scaleY = zoom
                    translationX = shift.value.x * latestShift.value
                    translationY = shift.value.y * latestShift.value
                },
        )
    }
}

/**
 * Écoute le gyroscope (ou, à défaut, la gravité) et donne le décalage de l'image dans le
 * repère du téléphone (voir [TiltFilter]).
 */
class TiltSensor(context: Context, private val onTilt: (x: Float, y: Float) -> Unit) : SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager?.let(::bestSensor)
    private val filter = TiltFilter()

    /** Accéléromètre brut lissé (il mesure aussi les secousses de la main). */
    private val smoothed = FloatArray(3)
    private var hasSmoothed = false

    fun start() {
        val sensor = sensor ?: return
        filter.reset()
        hasSmoothed = false
        manager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        manager?.unregisterListener(this)
        filter.reset()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> filter.onRotationSpeed(v[0], v[1], event.timestamp)
            Sensor.TYPE_ACCELEROMETER -> {
                if (!hasSmoothed) {
                    v.copyInto(smoothed, endIndex = 3)
                    hasSmoothed = true
                } else {
                    for (i in 0..2) smoothed[i] += (v[i] - smoothed[i]) * 0.2f
                }
                filter.onGravity(smoothed[0], smoothed[1], smoothed[2], event.timestamp)
            }
            else -> filter.onGravity(v[0], v[1], v[2], event.timestamp)
        }
        onTilt(filter.shiftX, filter.shiftY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private fun bestSensor(manager: SensorManager): Sensor? =
            manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
                ?: manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
                ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        /** Vrai si le téléphone a un capteur de mouvement utilisable pour l'effet. */
        fun isAvailable(context: Context): Boolean =
            context.getSystemService(SensorManager::class.java)?.let(::bestSensor) != null
    }
}
