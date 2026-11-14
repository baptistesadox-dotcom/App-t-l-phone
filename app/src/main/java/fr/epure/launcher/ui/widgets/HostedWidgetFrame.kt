package fr.epure.launcher.ui.widgets

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import fr.epure.launcher.R
import fr.epure.launcher.data.config.HostedWidget
import fr.epure.launcher.ui.theme.LocalLauncherLook
import fr.epure.launcher.widgets.LauncherWidgetHost
import fr.epure.launcher.widgets.LauncherWidgetHostView

/** Affiche un widget Android (d'une autre app) sur l'accueil. */
@Composable
fun HostedWidgetFrame(
    widget: HostedWidget,
    host: LauncherWidgetHost,
    onLongPress: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val info = remember(widget.appWidgetId) {
        AppWidgetManager.getInstance(context).getAppWidgetInfo(widget.appWidgetId)
    }
    if (info == null) {
        // L'app qui fournissait ce widget a été désinstallée.
        val look = LocalLauncherLook.current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.widget_missing),
                style = with(look) { MaterialTheme.typography.bodyLarge.onWallpaper() },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRemove) { Text(stringResource(R.string.widget_remove)) }
        }
        return
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(widget.heightDp.dp),
    ) {
        val widthDp = maxWidth.value.toInt()
        AndroidView(
            factory = { ctx -> host.createView(ctx, widget.appWidgetId, info) },
            update = { view ->
                (view as? LauncherWidgetHostView)?.let {
                    it.onLongPress = onLongPress
                    it.applySize(widthDp, widget.heightDp)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
