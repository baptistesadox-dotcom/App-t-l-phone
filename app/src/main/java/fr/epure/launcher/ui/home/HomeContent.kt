package fr.epure.launcher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.epure.launcher.R
import fr.epure.launcher.data.config.HostedWidget
import fr.epure.launcher.data.config.ScrollerSide
import fr.epure.launcher.data.config.TodoItem
import fr.epure.shared.AppUserKey
import fr.epure.shared.NotificationPreview
import fr.epure.launcher.ui.AgendaState
import fr.epure.launcher.ui.AppEntry
import fr.epure.launcher.ui.AppMenuOrigin
import fr.epure.launcher.ui.AppMenuRequest
import fr.epure.launcher.ui.FolderEntry
import fr.epure.launcher.ui.HomeEntry
import fr.epure.launcher.ui.LauncherActions
import fr.epure.launcher.ui.LauncherState
import fr.epure.launcher.ui.LauncherUiController
import fr.epure.launcher.ui.WeatherState
import fr.epure.launcher.ui.common.AppIcon
import fr.epure.launcher.ui.common.AppRow
import fr.epure.launcher.ui.common.LauncherRow
import fr.epure.launcher.ui.common.NotificationDot
import fr.epure.launcher.ui.theme.LauncherLook
import fr.epure.launcher.ui.theme.LocalLauncherLook
import fr.epure.launcher.ui.widgets.GlanceBlock
import fr.epure.launcher.ui.widgets.TodoWidget
import kotlin.math.max

/** Marge gauche commune à l'heure, aux icônes et à la liste des apps. */
val HomeMargin: Dp = 40.dp

/** Espace réservé à l'alphabet latéral. */
val ScrollerSpace: Dp = 44.dp

/** Styles de texte des lignes d'apps (nom, puis notification en dessous). */
@Composable
fun appLabelStyle(look: LauncherLook = LocalLauncherLook.current): TextStyle = with(look) {
    MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp * textScale, fontWeight = FontWeight.Medium).onWallpaper()
}

@Composable
fun appSecondaryStyle(look: LauncherLook = LocalLauncherLook.current): TextStyle = with(look) {
    MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp * textScale, lineHeight = 20.sp * textScale).onWallpaper(muted = true)
}

/** Espacements des lignes d'apps, selon le côté de l'alphabet. */
fun appRowPadding(look: LauncherLook, scrollerVisible: Boolean): PaddingValues {
    val space = if (scrollerVisible) ScrollerSpace else 0.dp
    return PaddingValues(
        start = if (look.scrollerSide == ScrollerSide.START) HomeMargin + space else HomeMargin,
        end = if (look.scrollerSide == ScrollerSide.END) 12.dp + space else 16.dp,
        top = 7.dp,
        bottom = 7.dp,
    )
}

/**
 * Écran d'accueil : tâches et widgets Android en haut, puis, un peu sous le centre,
 * l'heure suivie des apps favorites (avec leurs notifications).
 * Glisser vers le haut ouvre la liste des apps, vers le bas les notifications.
 */
@Composable
fun HomeContent(
    state: LauncherState,
    weather: WeatherState,
    agenda: AgendaState,
    notifications: Map<AppUserKey, List<NotificationPreview>>,
    now: Long,
    use24Hour: Boolean,
    actions: LauncherActions,
    ui: LauncherUiController,
    onOpenDrawer: () -> Unit,
    onTodoLongPress: (TodoItem) -> Unit,
    onConfigureWeather: () -> Unit,
    hostedWidget: @Composable (HostedWidget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalLauncherLook.current
    val density = LocalDensity.current
    val config = state.config
    val scrollState = rememberScrollState()
    val swipeThresholdPx = with(density) { 72.dp.toPx() }
    val flingThreshold = with(density) { 900.dp.toPx() }
    val latestOpenDrawer by rememberUpdatedState(onOpenDrawer)
    val swipeDownEnabled by rememberUpdatedState(config.gestures.swipeDownNotifications)

    LaunchedEffect(ui.homeResetCount) { scrollState.animateScrollTo(0) }

    val gestures = remember(actions) {
        object : NestedScrollConnection {
            private var pulledDown = 0f
            private var pulledUp = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                when {
                    available.y > 0f -> {
                        pulledUp = 0f
                        pulledDown += available.y
                        if (pulledDown > swipeThresholdPx) {
                            pulledDown = Float.NEGATIVE_INFINITY // une seule fois par geste
                            if (swipeDownEnabled) actions.expandNotifications()
                        }
                    }
                    available.y < 0f -> {
                        pulledDown = 0f
                        pulledUp -= available.y
                        if (pulledUp > swipeThresholdPx) {
                            pulledUp = Float.NEGATIVE_INFINITY
                            latestOpenDrawer()
                        }
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val alreadyHandled = pulledDown == Float.NEGATIVE_INFINITY || pulledUp == Float.NEGATIVE_INFINITY
                if (!alreadyHandled) {
                    if (available.y < -flingThreshold) latestOpenDrawer()
                    else if (available.y > flingThreshold && swipeDownEnabled) actions.expandNotifications()
                }
                pulledDown = 0f
                pulledUp = 0f
                return Velocity.Zero
            }
        }
    }

    val rowPadding = appRowPadding(look, look.showScrollerOnHome)
    val blockPadding = Modifier.padding(
        start = rowPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        end = rowPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    )
    val labelStyle = appLabelStyle(look)
    val secondaryStyle = appSecondaryStyle(look)
    val showNotifications = config.notifications.showOnHome

    BoxWithConstraints(modifier.fillMaxSize()) {
        val insets = WindowInsets.safeDrawing
        val usableHeight = (constraints.maxHeight - insets.getTop(density) - insets.getBottom(density)).coerceAtLeast(0)
        val gapPx = with(density) { 20.dp.roundToPx() }

        Layout(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onLongPress = { ui.homeMenuOpen = true }) }
                .nestedScroll(gestures)
                .verticalScroll(scrollState)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            content = {
                // ----- En haut : tâches et widgets Android -----
                Column(Modifier.padding(top = 20.dp)) {
                    if (config.widgets.showTodo) {
                        Box(blockPadding) {
                            TodoWidget(
                                todos = config.todos,
                                onAdd = actions::addTodo,
                                onToggle = actions::toggleTodo,
                                onLongPress = onTodoLongPress,
                                onClearDone = actions::clearDoneTodos,
                            )
                        }
                    }
                    if (config.widgets.hosted.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp),
                        ) {
                            config.widgets.hosted.forEach { widget ->
                                key(widget.appWidgetId) { hostedWidget(widget) }
                            }
                        }
                    }
                }

                // ----- Bloc principal : heure, puis favoris -----
                Column(Modifier.padding(bottom = 16.dp)) {
                    val widgets = config.widgets
                    if (widgets.showClock || widgets.showDate || widgets.showWeather || widgets.showAgenda) {
                        GlanceBlock(
                            now = now,
                            use24Hour = use24Hour,
                            widgets = widgets,
                            weather = weather,
                            agenda = agenda,
                            onClockClick = actions::openClock,
                            onDateClick = actions::openCalendar,
                            onWeatherRefresh = actions::refreshWeather,
                            onConfigureWeather = onConfigureWeather,
                            onRequestCalendarPermission = actions::requestCalendarPermission,
                            onEventClick = actions::openEvent,
                            modifier = blockPadding,
                        )
                        Spacer(Modifier.height(18.dp))
                    }
                    if (state.home.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_empty_hint),
                            style = secondaryStyle,
                            modifier = blockPadding.padding(vertical = 12.dp),
                        )
                    }
                    state.home.forEach { entry ->
                        key(entry.key) {
                            when (entry) {
                                is HomeEntry.App -> FavoriteApp(
                                    entry = entry.entry,
                                    notifications = if (showNotifications) notifications.forApp(entry.entry) else emptyList(),
                                    now = now,
                                    labelStyle = labelStyle,
                                    secondaryStyle = secondaryStyle,
                                    rowPadding = rowPadding,
                                    origin = AppMenuOrigin.Home,
                                    actions = actions,
                                    ui = ui,
                                )
                                is HomeEntry.FolderItem -> HomeFolder(
                                    folder = entry.folder,
                                    expanded = ui.expandedFolderId == entry.folder.id,
                                    hasNotifications = showNotifications &&
                                        entry.folder.apps.any { notifications.forApp(it).isNotEmpty() },
                                    notifications = if (showNotifications) notifications else emptyMap(),
                                    now = now,
                                    labelStyle = labelStyle,
                                    secondaryStyle = secondaryStyle,
                                    rowPadding = rowPadding,
                                    actions = actions,
                                    ui = ui,
                                )
                            }
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val loose = Constraints(maxWidth = width)
            val top = measurables[0].measure(loose)
            val main = measurables[1].measure(loose)
            val height = max(usableHeight, top.height + gapPx + main.height)
            layout(width, height) {
                top.place(0, 0)
                // Le bloc principal se place un peu sous le centre de l'espace libre.
                val free = (height - top.height - main.height).coerceAtLeast(0)
                val y = max(top.height + (free * 0.6f).toInt(), top.height + gapPx)
                main.place(0, y.coerceAtMost(height - main.height))
            }
        }
    }
}

fun Map<AppUserKey, List<NotificationPreview>>.forApp(entry: AppEntry): List<NotificationPreview> =
    this[AppUserKey(entry.app.packageName, entry.app.user)].orEmpty()

@Composable
private fun FavoriteApp(
    entry: AppEntry,
    notifications: List<NotificationPreview>,
    now: Long,
    labelStyle: TextStyle,
    secondaryStyle: TextStyle,
    rowPadding: PaddingValues,
    origin: AppMenuOrigin,
    actions: LauncherActions,
    ui: LauncherUiController,
    iconScale: Float = 1f,
) {
    val look = LocalLauncherLook.current
    val expanded = ui.expandedNotifications == entry.key
    AppRow(
        entry = entry,
        textStyle = labelStyle,
        showIcon = look.showIcons,
        iconSize = look.iconSize * iconScale,
        onClick = { bounds -> actions.launch(entry, bounds) },
        onLongClick = { ui.appMenu = AppMenuRequest(entry, origin) },
        contentPadding = rowPadding,
        notifications = notifications,
        secondaryStyle = secondaryStyle,
        now = now,
        expanded = expanded,
        onToggleExpanded = { ui.expandedNotifications = if (expanded) null else entry.key },
        onOpenNotification = actions::openNotification,
        onDismissNotification = actions::dismissNotification,
    )
}

@Composable
private fun HomeFolder(
    folder: FolderEntry,
    expanded: Boolean,
    hasNotifications: Boolean,
    notifications: Map<AppUserKey, List<NotificationPreview>>,
    now: Long,
    labelStyle: TextStyle,
    secondaryStyle: TextStyle,
    rowPadding: PaddingValues,
    actions: LauncherActions,
    ui: LauncherUiController,
) {
    val look = LocalLauncherLook.current
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column {
        LauncherRow(
            onClick = { ui.expandedFolderId = if (expanded) null else folder.id },
            onLongClick = { ui.folderMenu = folder },
            contentPadding = rowPadding,
        ) {
            if (look.showIcons) {
                FolderIcon(folder = folder, size = look.iconSize)
                Spacer(Modifier.width(20.dp))
            }
            Text(
                text = folder.name,
                style = labelStyle,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(10.dp))
            Text(text = folder.apps.size.toString(), style = secondaryStyle)
            if (hasNotifications) {
                Spacer(Modifier.width(10.dp))
                NotificationDot()
            }
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = look.onWallpaperMuted,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.padding(start = 24.dp)) {
                if (folder.apps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.folder_empty),
                        style = secondaryStyle,
                        modifier = Modifier.padding(rowPadding),
                    )
                }
                folder.apps.forEach { app ->
                    key(app.key) {
                        FavoriteApp(
                            entry = app,
                            notifications = notifications.forApp(app),
                            now = now,
                            labelStyle = labelStyle,
                            secondaryStyle = secondaryStyle,
                            rowPadding = rowPadding,
                            origin = AppMenuOrigin.Folder(folder.id),
                            actions = actions,
                            ui = ui,
                            iconScale = 0.86f,
                        )
                    }
                }
            }
        }
    }
}

/** Petite grille des quatre premières icônes du dossier, sur un fond translucide. */
@Composable
fun FolderIcon(folder: FolderEntry, size: Dp, modifier: Modifier = Modifier) {
    val cell = size * 0.38f
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        val apps = folder.apps.take(4)
        Column(verticalArrangement = Arrangement.spacedBy(size * 0.05f)) {
            apps.chunked(2).forEach { rowApps ->
                Row(horizontalArrangement = Arrangement.spacedBy(size * 0.05f)) {
                    rowApps.forEach { app -> AppIcon(app = app.app, label = app.label, size = cell) }
                    if (rowApps.size == 1) Spacer(Modifier.size(cell))
                }
            }
        }
    }
}
