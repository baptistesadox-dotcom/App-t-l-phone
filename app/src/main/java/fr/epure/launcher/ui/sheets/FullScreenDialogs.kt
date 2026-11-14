package fr.epure.launcher.ui.sheets

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import fr.epure.launcher.R
import fr.epure.launcher.data.config.addFolderToHome
import fr.epure.launcher.data.config.createFolder
import fr.epure.launcher.data.config.isFolderOnHome
import fr.epure.launcher.data.config.removeFolderFromHome
import fr.epure.launcher.data.config.renameFolder
import fr.epure.launcher.data.config.setFolderApps
import fr.epure.launcher.data.config.setFolderShowAsTab
import fr.epure.launcher.domain.AppSearch
import fr.epure.launcher.ui.AppEntry
import fr.epure.launcher.ui.FolderEditorRequest
import fr.epure.launcher.ui.LauncherActions
import fr.epure.launcher.ui.LauncherState
import fr.epure.launcher.ui.common.AppIcon
import fr.epure.launcher.widgets.WidgetProviderEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max

/** Fenêtre plein écran avec une barre de titre (fermer à gauche, action à droite). */
@Composable
fun FullScreenDialog(
    title: String,
    onDismiss: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    actions = { action?.invoke() },
                )
                content()
            }
        }
    }
}

/** Liste d'apps à cocher, avec recherche. */
@Composable
fun AppChecklist(
    apps: List<AppEntry>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf("") }
    val shown = remember(apps, filter) {
        if (filter.isBlank()) apps else AppSearch.search(apps, filter, { it.label }, { it.originalLabel })
    }
    Column(modifier) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.drawer_search_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(shown, key = { it.key }) { app ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(app.key) }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    AppIcon(app = app.app, label = app.label, size = 34.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Checkbox(checked = app.key in selected, onCheckedChange = { onToggle(app.key) })
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Création ou modification d'un dossier : nom, emplacement et apps. */
@Composable
fun FolderEditorDialog(
    request: FolderEditorRequest,
    state: LauncherState,
    actions: LauncherActions,
    onDismiss: () -> Unit,
) {
    val existing = request.folderId?.let { state.folderById(it) }
    val defaultName = stringResource(R.string.folder_default_name)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var selected by remember { mutableStateOf(existing?.folder?.apps?.toSet() ?: request.initialApps.toSet()) }
    var onHome by remember { mutableStateOf(existing?.let { state.config.isFolderOnHome(it.id) } ?: request.addToHome) }
    var asTab by remember { mutableStateOf(existing?.folder?.showAsTab ?: true) }

    fun save() {
        // Ordre alphabétique des apps, en gardant les apps masquées déjà présentes.
        val ordered = state.allApps.map { it.key }.filter { it in selected } + selected.filter { key -> state.allApps.none { it.key == key } }
        val finalName = name.trim().ifEmpty { defaultName }
        actions.updateConfig { config ->
            if (existing == null) {
                val id = UUID.randomUUID().toString()
                config.createFolder(id, finalName, ordered, onHome).setFolderShowAsTab(id, asTab)
            } else {
                val updated = config.renameFolder(existing.id, finalName)
                    .setFolderApps(existing.id, ordered)
                    .setFolderShowAsTab(existing.id, asTab)
                if (onHome) updated.addFolderToHome(existing.id) else updated.removeFolderFromHome(existing.id)
            }
        }
        onDismiss()
    }

    FullScreenDialog(
        title = stringResource(if (existing == null) R.string.folder_new_title else R.string.folder_edit_title),
        onDismiss = onDismiss,
        action = { TextButton(onClick = ::save) { Text(stringResource(R.string.action_save)) } },
    ) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name_label)) },
                placeholder = { Text(stringResource(R.string.folder_name_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SwitchRow(stringResource(R.string.folder_on_home), onHome) { onHome = it }
            SwitchRow(stringResource(R.string.folder_as_tab), asTab) { asTab = it }
            Text(
                text = pluralStringResource(R.plurals.folder_selected, selected.size, selected.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp),
            )
            AppChecklist(
                apps = state.apps,
                selected = selected,
                onToggle = { key -> selected = if (key in selected) selected - key else selected + key },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Sélecteur des widgets Android installés, regroupés par app. */
@Composable
fun WidgetPickerDialog(actions: LauncherActions, onDismiss: () -> Unit) {
    val providers by produceState<List<WidgetProviderEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { actions.listWidgetProviders() }
    }
    FullScreenDialog(title = stringResource(R.string.widget_picker_title), onDismiss = onDismiss) {
        val list = providers
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Text(
                text = stringResource(R.string.widget_picker_empty),
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                val groups = list.groupBy { it.appLabel }
                groups.forEach { (appLabel, entries) ->
                    item(key = "header:$appLabel") {
                        Text(
                            text = appLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
                        )
                    }
                    items(entries, key = { it.info.provider.flattenToString() + it.info.profile.hashCode() }) { entry ->
                        WidgetProviderRow(entry) {
                            onDismiss()
                            actions.addWidget(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetProviderRow(entry: WidgetProviderEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val maxPx = with(density) { 96.dp.roundToPx() }
    val preview by produceState<ImageBitmap?>(initialValue = null, entry) {
        value = withContext(Dispatchers.IO) {
            val dpi = context.resources.displayMetrics.densityDpi
            val drawable = runCatching { entry.info.loadPreviewImage(context, dpi) }.getOrNull()
                ?: runCatching { entry.info.loadIcon(context, dpi) }.getOrNull()
            drawable?.let { scaledBitmap(it, maxPx) }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 112.dp, height = 96.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            preview?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .widthIn(max = 104.dp)
                        .heightIn(max = 88.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.widget_size, entry.minWidthDp, entry.minHeightDp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun scaledBitmap(drawable: Drawable, maxPx: Int): ImageBitmap? = runCatching {
    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxPx
    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxPx
    val scale = maxPx.toFloat() / max(w, h)
    val width = (w * scale).toInt().coerceAtLeast(1)
    val height = (h * scale).toInt().coerceAtLeast(1)
    drawable.toBitmap(width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()
