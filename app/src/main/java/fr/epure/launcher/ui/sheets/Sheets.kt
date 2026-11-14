package fr.epure.launcher.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import fr.epure.launcher.R
import fr.epure.launcher.data.apps.AppShortcut
import fr.epure.launcher.data.config.HostedWidget
import fr.epure.launcher.data.config.TodoItem
import fr.epure.launcher.data.config.addAppToFolder
import fr.epure.launcher.data.config.deleteFolder
import fr.epure.launcher.data.config.hideApp
import fr.epure.launcher.data.config.moveHostedWidget
import fr.epure.launcher.data.config.removeAppFromFolder
import fr.epure.launcher.data.config.removeFolderFromHome
import fr.epure.launcher.data.config.renameApp
import fr.epure.launcher.data.config.resizeHostedWidget
import fr.epure.launcher.data.config.toggleFavorite
import fr.epure.launcher.ui.AppEntry
import fr.epure.launcher.ui.AppMenuOrigin
import fr.epure.launcher.ui.AppMenuRequest
import fr.epure.launcher.ui.FolderEditorRequest
import fr.epure.launcher.ui.FolderEntry
import fr.epure.launcher.ui.LauncherActions
import fr.epure.launcher.ui.LauncherState
import fr.epure.launcher.ui.LauncherUiController
import fr.epure.launcher.ui.common.AppIcon
import fr.epure.launcher.ui.common.ShortcutIcon
import kotlinx.coroutines.launch

/** Ferme la feuille avec son animation, puis exécute [then]. */
@Composable
private fun rememberDismiss(sheetState: SheetState, onDismiss: () -> Unit): (then: () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return { then ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }
}

@Composable
fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    SheetActionRow(label = label, onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SheetActionRow(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String? = null, leading: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Menu d'une app (appui long) : raccourcis, favoris, dossiers, renommer, masquer… */
@Composable
fun AppActionsSheet(
    request: AppMenuRequest,
    state: LauncherState,
    actions: LauncherActions,
    ui: LauncherUiController,
) {
    val entry = request.entry
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismiss = rememberDismiss(sheetState) { ui.appMenu = null }
    var shortcuts by remember(entry.key) { mutableStateOf<List<AppShortcut>>(emptyList()) }
    LaunchedEffect(entry.key) { shortcuts = actions.shortcutsFor(entry) }
    val current = state.appByKey(entry.key) ?: entry
    val hiddenMessage = stringResource(R.string.app_hidden_message, current.label)

    ModalBottomSheet(onDismissRequest = { ui.appMenu = null }, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            SheetHeader(
                title = current.label,
                subtitle = if (current.isRenamed) current.originalLabel else null,
                leading = { AppIcon(app = current.app, label = current.label, size = 44.dp) },
            )
            if (shortcuts.isNotEmpty()) {
                shortcuts.forEach { shortcut ->
                    SheetActionRow(label = shortcut.label, onClick = { dismiss { actions.launchShortcut(shortcut) } }) {
                        ShortcutIcon(shortcut = shortcut, size = 24.dp)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
            SheetAction(
                icon = if (current.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                label = stringResource(if (current.isFavorite) R.string.app_remove_home else R.string.app_add_home),
                onClick = { dismiss { actions.updateConfig { it.toggleFavorite(current.key) } } },
            )
            val origin = request.origin
            if (origin is AppMenuOrigin.Folder) {
                val folderName = state.folderById(origin.folderId)?.name.orEmpty()
                SheetAction(
                    icon = Icons.Outlined.FolderOff,
                    label = stringResource(R.string.app_remove_from_folder, folderName),
                    onClick = { dismiss { actions.updateConfig { it.removeAppFromFolder(origin.folderId, current.key) } } },
                )
            }
            SheetAction(
                icon = Icons.Outlined.CreateNewFolder,
                label = stringResource(R.string.app_add_to_folder),
                onClick = { dismiss { ui.addToFolder = current } },
            )
            SheetAction(
                icon = Icons.Outlined.Edit,
                label = stringResource(R.string.app_rename),
                onClick = { dismiss { ui.renameApp = current } },
            )
            SheetAction(
                icon = Icons.Outlined.VisibilityOff,
                label = stringResource(R.string.app_hide),
                onClick = {
                    dismiss {
                        actions.updateConfig { it.hideApp(current.key) }
                        actions.showMessage(hiddenMessage)
                    }
                },
            )
            SheetAction(
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.app_info),
                onClick = { dismiss { actions.openAppInfo(current) } },
            )
            SheetAction(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.app_uninstall),
                onClick = { dismiss { actions.uninstall(current) } },
            )
        }
    }
}

/** Menu d'un dossier de l'accueil (appui long). */
@Composable
fun FolderMenuSheet(folder: FolderEntry, actions: LauncherActions, ui: LauncherUiController) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismiss = rememberDismiss(sheetState) { ui.folderMenu = null }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = { ui.folderMenu = null }, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            SheetHeader(
                title = folder.name,
                subtitle = pluralStringResource(R.plurals.folder_count, folder.apps.size, folder.apps.size),
                leading = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(32.dp)) },
            )
            SheetAction(
                icon = Icons.Outlined.Edit,
                label = stringResource(R.string.folder_edit),
                onClick = { dismiss { ui.folderEditor = FolderEditorRequest(folderId = folder.id) } },
            )
            SheetAction(
                icon = Icons.Outlined.RemoveCircleOutline,
                label = stringResource(R.string.folder_remove_home),
                onClick = { dismiss { actions.updateConfig { it.removeFolderFromHome(folder.id) } } },
            )
            SheetAction(
                icon = Icons.Outlined.DeleteOutline,
                label = stringResource(R.string.folder_delete),
                onClick = { confirmDelete = true },
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.folder_delete_title, folder.name)) },
            text = { Text(stringResource(R.string.folder_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    dismiss { actions.updateConfig { it.deleteFolder(folder.id) } }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Menu de l'accueil (appui long sur une zone vide). */
@Composable
fun HomeMenuSheet(actions: LauncherActions, ui: LauncherUiController) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismiss = rememberDismiss(sheetState) { ui.homeMenuOpen = false }
    ModalBottomSheet(onDismissRequest = { ui.homeMenuOpen = false }, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            SheetHeader(title = stringResource(R.string.home_menu_title))
            SheetAction(Icons.Outlined.Widgets, stringResource(R.string.home_menu_add_widget), { dismiss { ui.widgetPickerOpen = true } })
            SheetAction(Icons.Outlined.Add, stringResource(R.string.home_menu_new_folder), {
                dismiss { ui.folderEditor = FolderEditorRequest(folderId = null, addToHome = true) }
            })
            SheetAction(Icons.Outlined.Wallpaper, stringResource(R.string.home_menu_wallpaper), { dismiss { actions.openWallpaperPicker() } })
            SheetAction(Icons.Outlined.Settings, stringResource(R.string.home_menu_settings), { dismiss { actions.openSettings() } })
        }
    }
}

/** Menu d'un widget Android posé sur l'accueil. */
@Composable
fun WidgetMenuSheet(widget: HostedWidget, actions: LauncherActions, ui: LauncherUiController) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismiss = rememberDismiss(sheetState) { ui.widgetMenu = null }
    ModalBottomSheet(onDismissRequest = { ui.widgetMenu = null }, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            SheetHeader(title = stringResource(R.string.widget_menu_title))
            SheetAction(Icons.Outlined.UnfoldMore, stringResource(R.string.widget_taller), {
                actions.updateConfig { it.resizeHostedWidget(widget.appWidgetId, widget.heightDp + 40) }
                ui.widgetMenu = widget.copy(heightDp = widget.heightDp + 40)
            })
            SheetAction(Icons.Outlined.UnfoldLess, stringResource(R.string.widget_shorter), {
                actions.updateConfig { it.resizeHostedWidget(widget.appWidgetId, widget.heightDp - 40) }
                ui.widgetMenu = widget.copy(heightDp = (widget.heightDp - 40).coerceAtLeast(60))
            })
            SheetAction(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.widget_move_up), {
                actions.updateConfig { it.moveHostedWidget(widget.appWidgetId, -1) }
            })
            SheetAction(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.widget_move_down), {
                actions.updateConfig { it.moveHostedWidget(widget.appWidgetId, 1) }
            })
            SheetAction(Icons.Outlined.DeleteOutline, stringResource(R.string.widget_remove), {
                dismiss { actions.removeWidget(widget) }
            })
        }
    }
}

/** Renommer une app (un nom vide rétablit le nom d'origine). */
@Composable
fun RenameAppDialog(entry: AppEntry, actions: LauncherActions, onDismiss: () -> Unit) {
    var value by remember {
        mutableStateOf(TextFieldValue(entry.label, selection = TextRange(0, entry.label.length)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.rename_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (entry.isRenamed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.rename_original, entry.originalLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                actions.updateConfig { it.renameApp(entry.key, value.text) }
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (entry.isRenamed) {
                    TextButton(onClick = {
                        actions.updateConfig { it.renameApp(entry.key, null) }
                        onDismiss()
                    }) { Text(stringResource(R.string.rename_reset)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** Choisir le(s) dossier(s) d'une app. */
@Composable
fun AddToFolderDialog(
    entry: AppEntry,
    state: LauncherState,
    actions: LauncherActions,
    ui: LauncherUiController,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_folder_title, entry.label)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state.folders.isEmpty()) {
                    Text(stringResource(R.string.add_to_folder_none), style = MaterialTheme.typography.bodyMedium)
                }
                state.folders.forEach { folder ->
                    val inFolder = entry.key in folder.folder.apps
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                actions.updateConfig {
                                    if (inFolder) it.removeAppFromFolder(folder.id, entry.key)
                                    else it.addAppToFolder(folder.id, entry.key)
                                }
                            }
                            .padding(vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = if (inFolder) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (inFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(folder.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(folder.apps.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                ui.folderEditor = FolderEditorRequest(folderId = null, initialApps = listOf(entry.key))
            }) { Text(stringResource(R.string.add_to_folder_new)) }
        },
    )
}

/** Modifier ou supprimer une tâche. */
@Composable
fun EditTodoDialog(item: TodoItem, actions: LauncherActions, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(item.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.todo_edit_title)) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = {
                actions.editTodo(item.id, text)
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = {
                actions.removeTodo(item.id)
                onDismiss()
            }) { Text(stringResource(R.string.action_delete)) }
        },
    )
}
