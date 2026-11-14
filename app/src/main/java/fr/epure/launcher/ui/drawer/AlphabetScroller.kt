package fr.epure.launcher.ui.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.epure.launcher.data.config.ScrollerSide
import fr.epure.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.max

/** Nombre de lettres voisines entraînées par la vague. */
private const val WAVE_SIGMA = 2.1f

/**
 * Alphabet vertical : en glissant le doigt dessus, les lettres proches se soulèvent
 * comme une vague et la liste saute à la lettre touchée.
 *
 * @param letters lettres affichées, de haut en bas
 * @param enabledLetters lettres qui ont au moins une app (les autres sont grisées et ignorées)
 * @param restingAlpha opacité des lettres au repos (discret sur l'accueil)
 */
@Composable
fun AlphabetScroller(
    letters: List<String>,
    enabledLetters: Set<String>,
    side: ScrollerSide,
    restingAlpha: Float,
    onTouchStart: () -> Unit,
    onLetter: (String) -> Unit,
    onTouchEnd: () -> Unit,
    modifier: Modifier = Modifier,
    /** Lettre forcée (utilisée par les captures d'écran de test). */
    previewLetter: String? = null,
) {
    val look = LocalLauncherLook.current
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val strength = remember { Animatable(if (previewLetter != null) 1f else 0f) }
    var touchY by remember { mutableFloatStateOf(-1f) }
    var heightPx by remember { mutableIntStateOf(0) }
    var currentLetter by remember { mutableStateOf(previewLetter) }

    val latestOnTouchStart by rememberUpdatedState(onTouchStart)
    val latestOnLetter by rememberUpdatedState(onLetter)
    val latestOnTouchEnd by rememberUpdatedState(onTouchEnd)
    // Le geste continue pendant que la liste s'ouvre : il doit toujours lire les valeurs à jour.
    val latestLetters by rememberUpdatedState(letters)
    val latestEnabled by rememberUpdatedState(enabledLetters)

    val direction = if (side == ScrollerSide.END) -1f else 1f
    val maxShiftPx = with(density) { 42.dp.toPx() }
    val slotPx = if (letters.isEmpty() || heightPx == 0) 0f else heightPx.toFloat() / letters.size
    val letterSize = with(density) { (slotPx * 0.62f).toSp() }.value.coerceIn(7f, 13f).sp

    if (previewLetter != null && touchY < 0 && slotPx > 0f) {
        touchY = (letters.indexOf(previewLetter).coerceAtLeast(0) + 0.5f) * slotPx
    }

    fun letterAt(y: Float): String? {
        val all = latestLetters
        val enabled = latestEnabled
        if (all.isEmpty() || heightPx == 0) return null
        val slot = heightPx.toFloat() / all.size
        val index = (y / slot).toInt().coerceIn(0, all.lastIndex)
        if (all[index] in enabled) return all[index]
        // Lettre vide : on prend la lettre disponible la plus proche.
        for (offset in 1..all.size) {
            all.getOrNull(index - offset)?.takeIf { it in enabled }?.let { return it }
            all.getOrNull(index + offset)?.takeIf { it in enabled }?.let { return it }
        }
        return null
    }

    fun update(y: Float) {
        touchY = y.coerceIn(0f, heightPx.toFloat())
        val letter = letterAt(touchY)
        if (letter != null && letter != currentLetter) {
            currentLetter = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            latestOnLetter(letter)
        }
    }

    Box(
        modifier = modifier
            .width(34.dp)
            .fillMaxHeight()
            .semantics { contentDescription = "Alphabet" }
            .onSizeChanged { heightPx = it.height }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    currentLetter = null
                    latestOnTouchStart()
                    scope.launch { strength.animateTo(1f, tween(120)) }
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
        Column(Modifier.fillMaxSize()) {
            letters.forEachIndexed { index, letter ->
                val enabled = letter in enabledLetters
                val selected = letter == currentLetter
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letter,
                        fontSize = letterSize,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            selected -> colors.primary
                            enabled -> look.onWallpaper
                            else -> look.onWallpaperMuted.copy(alpha = 0.3f)
                        },
                        modifier = Modifier.graphicsLayer {
                            // Lecture des états dans le calque : pas de recomposition pendant le geste.
                            val s = strength.value
                            val influence = if (touchY < 0f || slotPx == 0f) 0f else {
                                val d = ((index + 0.5f) * slotPx - touchY) / slotPx
                                exp(-(d * d) / (2f * WAVE_SIGMA * WAVE_SIGMA)) * s
                            }
                            translationX = direction * influence * maxShiftPx
                            val scale = 1f + 0.85f * influence
                            scaleX = scale
                            scaleY = scale
                            alpha = (restingAlpha + (1f - restingAlpha) * max(influence, s * 0.7f)).coerceIn(0f, 1f)
                        },
                    )
                }
            }
        }
        // Grande bulle avec la lettre en cours, à côté du doigt.
        val bubbleLetter = currentLetter
        if (bubbleLetter != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .requiredSize(60.dp)
                    .graphicsLayer {
                        val s = strength.value
                        alpha = s
                        scaleX = 0.6f + 0.4f * s
                        scaleY = 0.6f + 0.4f * s
                        translationX = direction * (maxShiftPx + 64.dp.toPx())
                        translationY = (touchY - 30.dp.toPx())
                            .coerceIn(0f, (heightPx - 60.dp.toPx()).coerceAtLeast(0f))
                    }
                    .background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bubbleLetter,
                    color = colors.onPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
