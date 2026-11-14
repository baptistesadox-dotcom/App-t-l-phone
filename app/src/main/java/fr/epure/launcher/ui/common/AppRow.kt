package fr.epure.launcher.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.epure.launcher.data.notifications.NotificationFormat
import fr.epure.launcher.data.notifications.NotificationPreview
import fr.epure.launcher.ui.AppEntry

/** Garde la position d'un élément sans provoquer de recomposition (pour l'animation d'ouverture). */
class BoundsHolder {
    var coordinates: LayoutCoordinates? = null
    fun bounds(): Rect? = coordinates?.takeIf { it.isAttached }?.boundsInWindow()
}

/**
 * Élément touchable du launcher : à l'appui il s'enfonce légèrement (sans halo gris),
 * l'appui long fait vibrer et ouvre le menu.
 */
@Composable
fun LauncherRow(
    onClick: (Rect?) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 9.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    val holder = remember { BoundsHolder() }
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onPlaced { holder.coordinates = it }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onClick(holder.bounds()) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .graphicsLayer {
                val scale = 1f - 0.035f * press
                scaleX = scale
                scaleY = scale
                alpha = 1f - 0.3f * press
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .padding(contentPadding),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.Start,
        content = content,
    )
}

/** Pastille discrète signalant des notifications. */
@Composable
fun NotificationDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(7.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
    )
}

/**
 * Ligne d'app. Sur l'accueil, la dernière notification s'affiche sous le nom
 * (« Discord · 19 min », titre, message) avec un chevron pour tout déplier.
 */
@Composable
fun AppRow(
    entry: AppEntry,
    textStyle: TextStyle,
    showIcon: Boolean,
    iconSize: Dp,
    onClick: (Rect?) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 9.dp),
    iconGap: Dp = 20.dp,
    notifications: List<NotificationPreview> = emptyList(),
    secondaryStyle: TextStyle = textStyle,
    now: Long = 0L,
    showDot: Boolean = false,
    expanded: Boolean = false,
    onToggleExpanded: (() -> Unit)? = null,
    onOpenNotification: (NotificationPreview) -> Unit = {},
    onDismissNotification: (NotificationPreview) -> Unit = {},
) {
    val latest = notifications.firstOrNull()
    Column(modifier) {
        LauncherRow(onClick = onClick, onLongClick = onLongClick, contentPadding = contentPadding) {
            if (showIcon) {
                AppIcon(app = entry.app, label = entry.label, size = iconSize)
                Spacer(Modifier.width(iconGap))
            }
            Column(Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.label,
                        style = textStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (latest != null) {
                        Text(
                            text = " · " + NotificationFormat.ago(latest.postTime, now),
                            style = textStyle,
                            maxLines = 1,
                        )
                    }
                    if (showDot && latest == null) {
                        Spacer(Modifier.width(10.dp))
                        NotificationDot()
                    }
                    if (entry.app.isWorkProfile) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Outlined.BusinessCenter,
                            contentDescription = null,
                            tint = textStyle.color.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (latest != null && !expanded) {
                    NotificationText(
                        preview = latest,
                        style = secondaryStyle,
                        onOpen = { onOpenNotification(latest) },
                        onLongClick = onLongClick,
                    )
                }
            }
            if (latest != null && onToggleExpanded != null) {
                val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
                Spacer(Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = secondaryStyle.color,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(interactionSource = null, indication = null, onClick = onToggleExpanded)
                        .padding(4.dp)
                        .rotate(rotation),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded && notifications.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(
                    start = contentPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
                        if (showIcon) iconSize + iconGap else 0.dp,
                    end = contentPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    bottom = 8.dp,
                ),
            ) {
                notifications.forEach { preview ->
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            NotificationText(
                                preview = preview,
                                style = secondaryStyle,
                                onOpen = { onOpenNotification(preview) },
                                onLongClick = onLongClick,
                                ago = NotificationFormat.ago(preview.postTime, now),
                                maxTextLines = 4,
                            )
                        }
                        if (preview.clearable) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
                                tint = secondaryStyle.color,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clickable(interactionSource = null, indication = null) { onDismissNotification(preview) }
                                    .padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationText(
    preview: NotificationPreview,
    style: TextStyle,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    ago: String? = null,
    maxTextLines: Int = 2,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = onOpen,
                onLongClick = onLongClick,
            )
            .padding(top = 2.dp),
    ) {
        if (preview.title.isNotEmpty()) {
            Text(
                text = if (ago != null) "${preview.title} · $ago" else preview.title,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (preview.text.isNotEmpty()) {
            Text(
                text = preview.text,
                style = style,
                maxLines = maxTextLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
