package fr.epure.launcher.ui.common

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.epure.launcher.ui.AppEntry

/** Garde la position d'un élément sans provoquer de recomposition (pour l'animation d'ouverture). */
class BoundsHolder {
    var coordinates: LayoutCoordinates? = null
    fun bounds(): Rect? = coordinates?.takeIf { it.isAttached }?.boundsInWindow()
}

/** Ligne cliquable avec appui long, utilisée pour les apps et les dossiers. */
@Composable
fun LauncherRow(
    onClick: (Rect?) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 9.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val holder = remember { BoundsHolder() }
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onPlaced { holder.coordinates = it }
            .combinedClickable(
                onClick = { onClick(holder.bounds()) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        content = content,
    )
}

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
) {
    LauncherRow(onClick = onClick, onLongClick = onLongClick, modifier = modifier, contentPadding = contentPadding) {
        if (showIcon) {
            AppIcon(app = entry.app, label = entry.label, size = iconSize)
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = entry.label,
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
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
}
