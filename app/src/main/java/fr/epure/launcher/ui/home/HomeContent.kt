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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.epure.launcher.R
import fr.epure.launcher.data.config.HostedWidget
import fr.epure.launcher.data.config.ScrollerSide
import fr.epure.launcher.data.config.TodoItem
import fr.epure.launcher.ui.AgendaState
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
import fr.epure.launcher.ui.theme.LocalLauncherLook
import fr.epure.launcher.ui.widgets.AgendaWidget
import fr.epure.launcher.ui.widgets.ClockWidget
import fr.epure.launcher.ui.widgets.TodoWidget
import fr.epure.launcher.ui.widgets.WeatherWidget
import fr.epure.launcher.ui.widgets.WidgetSpacer

/**
 * Écran d'accueil : widgets en haut, apps favorites et dossiers en bas (à portée de pouce).
 * Glisser vers le haut ouvre la liste des apps, vers le bas les notifications.
 */
@Composable
fun HomeContent(
    state: LauncherState,
    weather: WeatherState,
    agenda: AgendaState,
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

    val scrollerSpace = if (look.showScrollerOnHome) 40.dp else 0.dp
    val rowPadding = PaddingValues(
        start = if (look.scrollerSide == ScrollerSide.START) 24.dp + scrollerSpace else 24.dp,
        end = if (look.scrollerSide == ScrollerSide.END) 16.dp + scrollerSpace else 16.dp,
        top = 9.dp,
        bottom = 9.dp,
    )
    val widgetPadding = Modifier.padding(
        start = if (look.scrollerSide == ScrollerSide.START) 24.dp + scrollerSpace else 24.dp,
        end = if (look.scrollerSide == ScrollerSide.END) 16.dp + scrollerSpace else 24.dp,
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onLongPress = { ui.homeMenuOpen = true }) }
                .nestedScroll(gestures)
                .verticalScroll(scrollState)
                .heightIn(min = viewportHeight)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 28.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ----- Widgets -----
            Column(widgetPadding) {
                val widgets = config.widgets
                if (widgets.showClock || widgets.showDate) {
                    ClockWidget(
                        now = now,
                        use24Hour = use24Hour,
                        showClock = widgets.showClock,
                        showDate = widgets.showDate,
                        onClockClick = actions::openClock,
                        onDateClick = actions::openCalendar,
                    )
                    WidgetSpacer()
                }
                if (widgets.showWeather) {
                    WeatherWidget(
                        state = weather,
                        location = widgets.weatherLocation,
                        onRefresh = actions::refreshWeather,
                        onConfigure = onConfigureWeather,
                    )
                    WidgetSpacer()
                }
                if (widgets.showAgenda) {
                    AgendaWidget(
                        state = agenda,
                        now = now,
                        use24Hour = use24Hour,
                        onRequestPermission = actions::requestCalendarPermission,
                        onEventClick = actions::openEvent,
                        onOpenCalendar = actions::openCalendar,
                    )
                    WidgetSpacer()
                }
                if (widgets.showTodo) {
                    TodoWidget(
                        todos = config.todos,
                        onAdd = actions::addTodo,
                        onToggle = actions::toggleTodo,
                        onLongPress = onTodoLongPress,
                        onClearDone = actions::clearDoneTodos,
                    )
                    WidgetSpacer()
                }
            }
            if (config.widgets.hosted.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    config.widgets.hosted.forEach { widget ->
                        key(widget.appWidgetId) { hostedWidget(widget) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ----- Favoris -----
            Column(Modifier.fillMaxWidth()) {
                if (state.home.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_empty_hint),
                        style = with(look) { MaterialTheme.typography.bodyLarge.onWallpaper(muted = true) },
                        modifier = widgetPadding.padding(vertical = 12.dp),
                    )
                }
                state.home.forEach { entry ->
                    key(entry.key) {
                        when (entry) {
                            is HomeEntry.App -> AppRow(
                                entry = entry.entry,
                                textStyle = homeTextStyle(),
                                showIcon = look.showIcons,
                                iconSize = look.iconSize,
                                onClick = { bounds -> actions.launch(entry.entry, bounds) },
                                onLongClick = { ui.appMenu = AppMenuRequest(entry.entry, AppMenuOrigin.Home) },
                                contentPadding = rowPadding,
                            )
                            is HomeEntry.FolderItem -> HomeFolder(
                                folder = entry.folder,
                                expanded = ui.expandedFolderId == entry.folder.id,
                                rowPadding = rowPadding,
                                actions = actions,
                                ui = ui,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun homeTextStyle() = with(LocalLauncherLook.current) {
    MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp * textScale, fontWeight = FontWeight.Normal).onWallpaper()
}

@Composable
private fun HomeFolder(
    folder: FolderEntry,
    expanded: Boolean,
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
                Spacer(Modifier.width(16.dp))
            }
            Text(
                text = folder.name,
                style = homeTextStyle(),
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = folder.apps.size.toString(),
                style = with(look) { MaterialTheme.typography.titleMedium.onWallpaper(muted = true) },
            )
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
            Column(Modifier.padding(start = 20.dp)) {
                if (folder.apps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.folder_empty),
                        style = with(look) { MaterialTheme.typography.bodyLarge.onWallpaper(muted = true) },
                        modifier = Modifier.padding(rowPadding),
                    )
                }
                folder.apps.forEach { app ->
                    key(app.key) {
                        AppRow(
                            entry = app,
                            textStyle = with(look) {
                                MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp * textScale).onWallpaper()
                            },
                            showIcon = look.showIcons,
                            iconSize = look.iconSize * 0.85f,
                            onClick = { bounds -> actions.launch(app, bounds) },
                            onLongClick = { ui.appMenu = AppMenuRequest(app, AppMenuOrigin.Folder(folder.id)) },
                            contentPadding = rowPadding,
                        )
                    }
                }
            }
        }
    }
}

/** Petite grille des quatre premières icônes du dossier. */
@Composable
fun FolderIcon(folder: FolderEntry, size: Dp, modifier: Modifier = Modifier) {
    val cell = size * 0.42f
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        val apps = folder.apps.take(4)
        Column(verticalArrangement = Arrangement.spacedBy(size * 0.04f)) {
            apps.chunked(2).forEach { rowApps ->
                Row(horizontalArrangement = Arrangement.spacedBy(size * 0.04f)) {
                    rowApps.forEach { app -> AppIcon(app = app.app, label = app.label, size = cell) }
                    if (rowApps.size == 1) Spacer(Modifier.size(cell))
                }
            }
        }
    }
}
