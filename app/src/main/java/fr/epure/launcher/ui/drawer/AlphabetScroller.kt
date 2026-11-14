package fr.epure.launcher.ui.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.epure.launcher.data.config.ScrollerSide
import fr.epure.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/** Élément « étoile » de l'alphabet : revenir aux favoris. */
const val FAVORITES_ITEM = "★"

/** Nombre de lettres voisines soulevées de chaque côté par la vague. */
private const val WAVE_RADIUS = 4.2f

/** Hauteur idéale d'une lettre de l'alphabet. */
private val PreferredSlot: Dp = 23.dp

/**
 * Alphabet vertical façon « vague » : en glissant le doigt, les lettres proches
 * s'écartent du bord selon une courbe douce et la lettre touchée grossit.
 * Seules les lettres qui ont des apps sont affichées, précédées d'une étoile.
 *
 * @param onItem appelé à chaque changement de lettre, avec la hauteur du doigt dans la fenêtre
 */
@Composable
fun AlphabetScroller(
    items: List<String>,
    side: ScrollerSide,
    restingAlpha: Float,
    onTouchStart: () -> Unit,
    onItem: (item: String, yInWindow: Float) -> Unit,
    onTouchEnd: () -> Unit,
    modifier: Modifier = Modifier,
    /** Lettre forcée (utilisée par les captures d'écran de test). */
    previewItem: String? = null,
) {
    val look = LocalLauncherLook.current
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val strength = remember { Animatable(if (previewItem != null) 1f else 0f) }
    var touchY by remember { mutableFloatStateOf(-1f) }
    var current by remember { mutableStateOf(previewItem) }
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }

    val latestOnTouchStart by rememberUpdatedState(onTouchStart)
    val latestOnItem by rememberUpdatedState(onItem)
    val latestOnTouchEnd by rememberUpdatedState(onTouchEnd)
    // Le geste continue pendant que la liste s'ouvre : il doit toujours lire les valeurs à jour.
    val latestItems by rememberUpdatedState(items)

    val direction = if (side == ScrollerSide.END) -1f else 1f
    val maxShiftPx = with(density) { 52.dp.toPx() }

    BoxWithConstraints(modifier) {
        val slot = if (items.isEmpty()) PreferredSlot else minOf(PreferredSlot, maxHeight / items.size)
        val slotPx = with(density) { slot.toPx() }
        val latestSlotPx by rememberUpdatedState(slotPx)
        val letterSize = (slot.value * 0.56f).coerceIn(9f, 13.5f).sp

        if (previewItem != null && touchY < 0f) {
            touchY = (items.indexOf(previewItem).coerceAtLeast(0) + 0.5f) * slotPx
        }

        fun itemAt(y: Float): String? {
            val all = latestItems
            if (all.isEmpty() || latestSlotPx <= 0f) return null
            return all[(y / latestSlotPx).toInt().coerceIn(0, all.lastIndex)]
        }

        fun update(y: Float) {
            val all = latestItems
            touchY = y.coerceIn(0f, latestSlotPx * all.size)
            val item = itemAt(touchY) ?: return
            if (item != current) {
                current = item
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                val originY = coordinates[0]?.takeIf { it.isAttached }?.positionInWindow()?.y ?: 0f
                latestOnItem(item, originY + touchY)
            }
        }

        /** Soulèvement d'une lettre (0 à 1) selon sa distance au doigt : courbe en cloche douce. */
        fun GraphicsLayerScope.wave(index: Int): Float {
            if (touchY < 0f || slotPx <= 0f) return 0f
            val d = abs(((index + 0.5f) * slotPx - touchY) / slotPx)
            if (d >= WAVE_RADIUS) return 0f
            return (0.5f * (1f + cos(PI.toFloat() * d / WAVE_RADIUS))) * strength.value
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(36.dp)
                .height(slot * items.size)
                .semantics { contentDescription = "Alphabet" }
                .onPlaced { coordinates[0] = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        current = null
                        latestOnTouchStart()
                        scope.launch { strength.animateTo(1f, tween(140)) }
                        update(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            update(change.position.y)
                            change.consume()
                        }
                        latestOnTouchEnd()
                        scope.launch {
                            strength.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
                        }
                    }
                },
        ) {
            items.forEachIndexed { index, item ->
                val selected = item == current && strength.value > 0f
                Box(
                    modifier = Modifier
                        .height(slot)
                        .fillMaxWidth()
                        .graphicsLayer {
                            // Lecture des états dans le calque : pas de recomposition pendant le geste.
                            val influence = wave(index)
                            translationX = direction * influence * maxShiftPx
                            val scale = 1f + 0.7f * influence
                            scaleX = scale
                            scaleY = scale
                            alpha = (restingAlpha + (1f - restingAlpha) * maxOf(influence, strength.value * 0.75f)).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val color = if (selected) colors.primary else look.onWallpaper
                    if (item == FAVORITES_ITEM) {
                        Icon(
                            imageVector = if (selected) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size((letterSize.value + 3f).dp),
                        )
                    } else {
                        Text(
                            text = item,
                            fontSize = letterSize,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = color,
                            style = MaterialTheme.typography.labelLarge.copy(shadow = look.textShadow),
                        )
                    }
                }
            }
        }
    }
}
