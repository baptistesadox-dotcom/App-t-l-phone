package fr.epure.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import fr.epure.launcher.data.config.HomeStyle
import fr.epure.launcher.data.config.HostedWidget
import fr.epure.launcher.data.config.ScrollerSide
import fr.epure.launcher.data.config.ScrollerSpeed
import fr.epure.launcher.data.config.TodoItem
import fr.epure.shared.AppUserKey
import fr.epure.shared.NotificationPreview
import fr.epure.launcher.ui.common.BoundsHolder
import fr.epure.launcher.ui.drawer.AlphabetScroller
import fr.epure.launcher.ui.drawer.AppDrawer
import fr.epure.launcher.ui.drawer.FAVORITES_ITEM
import fr.epure.launcher.ui.home.HomeContent
import fr.epure.launcher.ui.home.TypoHome
import fr.epure.launcher.ui.home.ScrollerSpace
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
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
    notifications: Map<AppUserKey, List<NotificationPreview>>,
    now: Long,
    use24Hour: Boolean,
    actions: LauncherActions,
    ui: LauncherUiController,
    hostedWidget: @Composable (HostedWidget) -> Unit,
    onConfigureWeather: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    /** Lettre affichée comme touchée (captures d'écran de test uniquement). */
    previewScrollerItem: String? = null,
    /** Image de l'accueil dessinée par Épure (effet parallaxe), sous tout le reste. */
    wallpaper: @Composable () -> Unit = {},
    /** Éléments de l'accueil au-dessus des favoris (mise à jour, musique). */
    homeExtras: @Composable (Modifier) -> Unit = {},
    /** Calcul, contacts et réglages d'Android trouvés par la recherche. */
    searchExtras: SearchExtras = SearchExtras(),
) {
    val look = LocalLauncherLook.current
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var editingTodo by remember { mutableStateOf<TodoItem?>(null) }
    val listPosition = remember { BoundsHolder() }
    val scope = rememberCoroutineScope()
    var touching by remember { mutableStateOf(false) }
    // Vrai quand le saut en cours vient d'ouvrir la liste : il se fait d'un coup.
    var jumpInstantly by remember { mutableStateOf(true) }
    val latestDrawer by rememberUpdatedState(drawer)

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
            ui.expandedNotifications != null -> ui.expandedNotifications = null
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

    // Saut à la lettre touchée : son en-tête vient se placer à hauteur du doigt. La liste y
    // glisse en douceur (sauf en vitesse « rapide », et au saut qui ouvre la liste), et
    // l'animation se poursuit si la lettre change entre-temps ou si le doigt se lève.
    // (Relancé aussi quand la liste change, par exemple juste après son ouverture.)
    val latestSpeed by rememberUpdatedState(look.scrollerSpeed)
    LaunchedEffect(listState) {
        snapshotFlow {
            val letter = ui.scrollerLetter ?: return@snapshotFlow null
            val index = latestDrawer.letterIndex[letter] ?: return@snapshotFlow null
            LetterJump(index, ui.scrollerY, latestDrawer)
        }.filterNotNull().collectLatest { jump ->
            val listTop = listPosition.bounds()?.top ?: 0f
            val headerHalf = with(density) { 22.dp.toPx() }
            val offset = (jump.fingerY - listTop - headerHalf).toInt().coerceAtLeast(0)
            listState.scrollToLetter(jump.index, offset, glide = latestSpeed != ScrollerSpeed.FAST && !jumpInstantly)
        }
    }

    // Progression de l'ouverture de la liste (0 = accueil, 1 = liste) : une seule courbe
    // pilote le recul de l'accueil et l'assombrissement du fond d'écran.
    val progress by animateFloatAsState(
        targetValue = if (ui.drawerOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "drawer",
    )

    val scrimColor = if (look.onWallpaper.luminance() > 0.5f) Color.Black else Color.White

    // Les icônes au trait prennent la couleur du texte posé sur le fond d'écran.
    CompositionLocalProvider(LocalContentColor provides look.onWallpaper) {
    Box(Modifier.fillMaxSize()) {
        wallpaper()

        // Voile sur le fond d'écran : léger sur l'accueil, plus marqué derrière la liste.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val alpha = look.wallpaperDim + (0.62f - look.wallpaperDim).coerceAtLeast(0f) * progress
                    drawRect(scrimColor.copy(alpha = alpha))
                },
        )

        // L'accueil recule et s'efface quand la liste s'ouvre (il reste composé : les widgets
        // Android ne sont pas recréés).
        val homeModifier = Modifier.graphicsLayer {
            alpha = 1f - progress
            val scale = 1f - 0.06f * progress
            scaleX = scale
            scaleY = scale
        }
        if (look.homeStyle == HomeStyle.TYPOGRAPHIC) {
            TypoHome(
                state = state,
                weather = weather,
                agenda = agenda,
                notifications = notifications,
                now = now,
                use24Hour = use24Hour,
                actions = actions,
                ui = ui,
                onOpenDrawer = { ui.drawerOpen = true },
                onTodoLongPress = { editingTodo = it },
                onConfigureWeather = onConfigureWeather,
                hostedWidget = hostedWidget,
                modifier = homeModifier,
                homeExtras = homeExtras,
            )
        } else {
            HomeContent(
                state = state,
                weather = weather,
                agenda = agenda,
                notifications = notifications,
                now = now,
                use24Hour = use24Hour,
                actions = actions,
                ui = ui,
                onOpenDrawer = { ui.drawerOpen = true },
                onTodoLongPress = { editingTodo = it },
                onConfigureWeather = onConfigureWeather,
                hostedWidget = hostedWidget,
                modifier = homeModifier,
                homeExtras = homeExtras,
            )
        }

        AnimatedVisibility(
            visible = ui.drawerOpen,
            enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 10 } + fadeIn(tween(260)),
            exit = slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it / 10 } + fadeOut(tween(200)),
        ) {
            AppDrawer(
                drawer = drawer,
                tabs = state.tabs,
                selectedTab = selectedTab,
                query = query,
                listState = listState,
                notifications = notifications,
                showDots = state.config.notifications.showDots,
                actions = actions,
                ui = ui,
                onClose = ::closeDrawer,
                listPosition = listPosition,
                extras = searchExtras,
            )
        }

        // Alphabet : sur l'accueil (si activé) et dans la liste hors recherche.
        val showScroller = (ui.drawerOpen && !drawer.isSearch) || (!ui.drawerOpen && look.showScrollerOnHome)
        if (showScroller) {
            val letters = remember(drawer.letterIndex, state.apps, ui.drawerOpen) {
                val sections = if (ui.drawerOpen) drawer.letters else state.apps.map { it.section }.distinct()
                listOf(FAVORITES_ITEM) + sections
            }
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = if (ui.drawerOpen) 128.dp else 72.dp, bottom = 24.dp),
            ) {
                AlphabetScroller(
                    items = letters,
                    side = look.scrollerSide,
                    restingAlpha = if (ui.drawerOpen) 0.75f else 0.5f,
                    onTouchStart = { touching = true },
                    onItem = { item, y ->
                        if (item == FAVORITES_ITEM) {
                            // L'étoile ramène aux favoris.
                            if (ui.drawerOpen) closeDrawer()
                            ui.scrollerLetter = null
                        } else {
                            jumpInstantly = !ui.drawerOpen
                            if (!ui.drawerOpen) {
                                actions.resetDrawer()
                                ui.drawerOpen = true
                            }
                            ui.scrollerY = y
                            ui.scrollerLetter = item
                        }
                    },
                    onTouchEnd = {
                        touching = false
                        // On oublie la lettre peu après : la liste ne doit plus y sauter toute seule.
                        scope.launch {
                            delay(350)
                            if (!touching) ui.scrollerLetter = null
                        }
                    },
                    previewItem = previewScrollerItem,
                    modifier = Modifier
                        .align(
                            BiasAlignment(
                                horizontalBias = if (look.scrollerSide == ScrollerSide.END) 1f else -1f,
                                verticalBias = 0.35f,
                            ),
                        )
                        .width(ScrollerSpace),
                )
            }
        }
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

/** Lettre à rejoindre : position de son en-tête dans la liste et hauteur du doigt. */
private class LetterJump(val index: Int, val fingerY: Float, val drawer: DrawerList) {
    override fun equals(other: Any?): Boolean =
        other is LetterJump && other.index == index && other.fingerY == fingerY && other.drawer === drawer

    override fun hashCode(): Int = 31 * index + fingerY.hashCode()
}

/**
 * Amène l'élément [index] à [offsetPx] du haut de la liste : en glissant (animation courte,
 * reprise en douceur si la cible change), ou d'un coup si [glide] est faux ou si la liste
 * n'est pas encore affichée.
 */
internal suspend fun LazyListState.scrollToLetter(index: Int, offsetPx: Int, glide: Boolean) {
    if (glide && layoutInfo.visibleItemsInfo.isNotEmpty()) {
        animateScrollToItem(index, -offsetPx)
    } else {
        scrollToItem(index, -offsetPx)
    }
}
