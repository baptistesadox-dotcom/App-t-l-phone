package fr.epure.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import fr.epure.launcher.data.config.HostedWidget
import fr.epure.launcher.data.config.ScrollerSide
import fr.epure.launcher.data.config.TodoItem
import fr.epure.launcher.domain.ALL_SECTIONS
import fr.epure.launcher.ui.drawer.AlphabetScroller
import fr.epure.launcher.ui.drawer.AppDrawer
import fr.epure.launcher.ui.home.HomeContent
import fr.epure.launcher.ui.sheets.AddToFolderDialog
import fr.epure.launcher.ui.sheets.AppActionsSheet
import fr.epure.launcher.ui.sheets.EditTodoDialog
import fr.epure.launcher.ui.sheets.FolderEditorDialog
import fr.epure.launcher.ui.sheets.FolderMenuSheet
import fr.epure.launcher.ui.sheets.HomeMenuSheet
import fr.epure.launcher.ui.sheets.RenameAppDialog
import fr.epure.launcher.ui.sheets.WidgetMenuSheet
import fr.epure.launcher.ui.sheets.WidgetPickerDialog
import fr.epure.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.launch

/**
 * Écran complet du launcher : accueil, liste des apps par-dessus, alphabet latéral,
 * et menus. Ne dépend d'aucun service Android (tout passe par [actions]),
 * ce qui permet de le dessiner dans les tests.
 */
@Composable
fun LauncherScreen(
    state: LauncherState,
    drawer: DrawerList,
    query: String,
    selectedTab: String?,
    weather: WeatherState,
    agenda: AgendaState,
    now: Long,
    use24Hour: Boolean,
    actions: LauncherActions,
    ui: LauncherUiController,
    hostedWidget: @Composable (HostedWidget) -> Unit,
    onConfigureWeather: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    /** Lettre affichée comme touchée (captures d'écran de test uniquement). */
    previewScrollerLetter: String? = null,
) {
    val look = LocalLauncherLook.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var editingTodo by remember { mutableStateOf<TodoItem?>(null) }

    fun closeDrawer() {
        keyboard?.hide()
        focusManager.clearFocus()
        ui.drawerOpen = false
        actions.resetDrawer()
    }

    // Retour : ferme d'abord la liste des apps ; sur l'accueil, ne fait rien.
    BackHandler(enabled = true) {
        when {
            ui.drawerOpen -> closeDrawer()
            ui.expandedFolderId != null -> ui.expandedFolderId = null
        }
    }

    LaunchedEffect(ui.homeResetCount) {
        if (ui.homeResetCount > 0) {
            keyboard?.hide()
            focusManager.clearFocus()
            actions.resetDrawer()
            listState.scrollToItem(0)
        }
    }

    // Revient en haut de la liste quand on change d'onglet ou de recherche.
    LaunchedEffect(selectedTab, query.isBlank()) { listState.scrollToItem(0) }

    Box(Modifier.fillMaxSize()) {
        // Assombrissement du fond d'écran (réglable).
        if (look.wallpaperDim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = look.wallpaperDim)),
            )
        }

        // L'accueil s'efface quand la liste des apps s'ouvre (il reste composé : les widgets
        // Android ne sont pas recréés).
        val homeAlpha by animateFloatAsState(
            targetValue = if (ui.drawerOpen) 0f else 1f,
            animationSpec = tween(if (ui.drawerOpen) 160 else 240),
            label = "homeAlpha",
        )
        HomeContent(
            state = state,
            weather = weather,
            agenda = agenda,
            now = now,
            use24Hour = use24Hour,
            actions = actions,
            ui = ui,
            onOpenDrawer = { ui.drawerOpen = true },
            onTodoLongPress = { editingTodo = it },
            onConfigureWeather = onConfigureWeather,
            hostedWidget = hostedWidget,
            modifier = Modifier.graphicsLayer { alpha = homeAlpha },
        )

        AnimatedVisibility(
            visible = ui.drawerOpen,
            enter = slideInVertically(tween(260)) { it / 6 } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it / 6 } + fadeOut(tween(180)),
        ) {
            AppDrawer(
                drawer = drawer,
                tabs = state.tabs,
                selectedTab = selectedTab,
                query = query,
                listState = listState,
                actions = actions,
                ui = ui,
                onClose = ::closeDrawer,
            )
        }

        // Alphabet : visible sur l'accueil (si activé) et dans la liste hors recherche.
        val showScroller = (ui.drawerOpen && !drawer.isSearch) || (!ui.drawerOpen && look.showScrollerOnHome)
        if (showScroller) {
            val enabledLetters = remember(drawer.letterIndex, state.apps, ui.drawerOpen) {
                if (ui.drawerOpen) drawer.letterIndex.keys else state.apps.mapTo(HashSet()) { it.section }
            }
            AlphabetScroller(
                letters = ALL_SECTIONS,
                enabledLetters = enabledLetters,
                side = look.scrollerSide,
                restingAlpha = if (ui.drawerOpen) 0.75f else 0.45f,
                onTouchStart = {
                    if (!ui.drawerOpen) {
                        actions.resetDrawer()
                        ui.drawerOpen = true
                    }
                },
                onLetter = { letter ->
                    ui.scrollerLetter = letter
                    drawer.letterIndex[letter]?.let { index -> scope.launch { listState.scrollToItem(index) } }
                },
                onTouchEnd = { ui.scrollerLetter = null },
                previewLetter = previewScrollerLetter,
                modifier = Modifier
                    .align(if (look.scrollerSide == ScrollerSide.END) Alignment.CenterEnd else Alignment.CenterStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = if (ui.drawerOpen) 132.dp else 96.dp, bottom = 24.dp)
                    .fillMaxHeight(),
            )
        }
    }

    // ----- Menus et fenêtres -----
    ui.appMenu?.let { AppActionsSheet(request = it, state = state, actions = actions, ui = ui) }
    ui.folderMenu?.let { FolderMenuSheet(folder = it, actions = actions, ui = ui) }
    if (ui.homeMenuOpen) HomeMenuSheet(actions = actions, ui = ui)
    ui.widgetMenu?.let { WidgetMenuSheet(widget = it, actions = actions, ui = ui) }
    ui.renameApp?.let { RenameAppDialog(entry = it, actions = actions, onDismiss = { ui.renameApp = null }) }
    ui.addToFolder?.let {
        AddToFolderDialog(entry = it, state = state, actions = actions, ui = ui, onDismiss = { ui.addToFolder = null })
    }
    ui.folderEditor?.let {
        FolderEditorDialog(request = it, state = state, actions = actions, onDismiss = { ui.folderEditor = null })
    }
    if (ui.widgetPickerOpen) WidgetPickerDialog(actions = actions, onDismiss = { ui.widgetPickerOpen = false })
    editingTodo?.let { EditTodoDialog(item = it, actions = actions, onDismiss = { editingTodo = null }) }
}
