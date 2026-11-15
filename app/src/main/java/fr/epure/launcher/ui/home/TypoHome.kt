package fr.epure.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.epure.launcher.R
import fr.epure.launcher.data.calendar.AgendaFormat
import fr.epure.launcher.data.config.HostedWidget
import fr.epure.launcher.data.config.ScrollerSide
import fr.epure.launcher.data.config.TodoItem
import fr.epure.launcher.domain.Greeting
import fr.epure.launcher.domain.WeatherCodes
import fr.epure.launcher.ui.AgendaState
import fr.epure.launcher.ui.AppMenuOrigin
import fr.epure.launcher.ui.HomeEntry
import fr.epure.launcher.ui.LauncherActions
import fr.epure.launcher.ui.LauncherState
import fr.epure.launcher.ui.LauncherUiController
import fr.epure.launcher.ui.WeatherState
import fr.epure.launcher.ui.theme.LocalLauncherLook
import fr.epure.launcher.ui.widgets.TodoWidget
import fr.epure.shared.AppUserKey
import fr.epure.shared.NotificationPreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private val French = Locale.FRANCE

/** Mot affiché en négatif (texte sombre dans un cadre de la couleur du texte), comme « [BON] ». */
@Composable
fun Highlight(text: String, style: TextStyle, modifier: Modifier = Modifier, horizontalPadding: Int = 6) {
    val look = LocalLauncherLook.current
    val ink = if (look.onWallpaper.luminance() > 0.5f) Color(0xFF0E0E10) else Color.White
    Text(
        text = text,
        style = style.copy(color = ink, shadow = null),
        modifier = modifier
            .background(look.onWallpaper, RoundedCornerShape(2.dp))
            .padding(horizontal = horizontalPadding.dp),
    )
}

/**
 * Accueil « typographique » (thème Rivage) : en haut une petite ligne heure/date,
 * une grande salutation, une phrase météo ; en bas « MES APPS » et les favoris
 * avec de petites icônes au trait.
 */
@Composable
fun TypoHome(
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
    /** Éléments ajoutés au-dessus des favoris (mise à jour, musique), avec les marges reçues. */
    homeExtras: @Composable (Modifier) -> Unit = {},
) {
    val look = LocalLauncherLook.current
    val density = LocalDensity.current
    val config = state.config
    val widgets = config.widgets
    val scrollState = rememberScrollState()
    val gestures = rememberHomeGestures(actions, onOpenDrawer, config.gestures.swipeDownNotifications)
    LaunchedEffect(ui.homeResetCount) { scrollState.animateScrollTo(0) }

    val type = MaterialTheme.typography
    val scale = look.textScale
    val small = with(look) {
        type.labelMedium.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, lineHeight = 15.sp).onWallpaper()
    }
    val greeting = with(look) {
        type.displaySmall.copy(
            fontSize = 31.sp * scale,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            lineHeight = 37.sp * scale,
        ).onWallpaper()
    }
    val sentence = with(look) {
        type.labelSmall.copy(fontSize = 9.5.sp * scale, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, lineHeight = 15.sp * scale).onWallpaper()
    }
    val labelStyle = appLabelStyle(look).copy(fontSize = 13.sp * scale)
    val secondaryStyle = appSecondaryStyle(look).copy(fontSize = 11.sp * scale, lineHeight = 15.sp * scale)
    val margin = HomeMargin
    val scrollerSpace = if (look.showScrollerOnHome) ScrollerSpace else 0.dp
    val rowPadding = PaddingValues(
        start = if (look.scrollerSide == ScrollerSide.START) margin + scrollerSpace else margin,
        end = if (look.scrollerSide == ScrollerSide.END) 16.dp + scrollerSpace else 16.dp,
        top = 8.dp,
        bottom = 8.dp,
    )
    val textPadding = Modifier.padding(
        start = rowPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        end = 24.dp,
    )
    val zoned = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val showNotifications = config.notifications.showOnHome

    BoxWithConstraints(modifier.fillMaxSize()) {
        val insets = WindowInsets.safeDrawing
        val usableHeight = (constraints.maxHeight - insets.getTop(density) - insets.getBottom(density)).coerceAtLeast(0)
        val gapPx = with(density) { 28.dp.roundToPx() }

        Layout(
            modifier = Modifier
                .fillMaxSize()
                .homeTaps(config.gestures.doubleTapToLock, ui, actions)
                .nestedScroll(gestures)
                .verticalScroll(scrollState)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            content = {
                // ----- Haut : heure et date, salutation, météo, tâches, widgets -----
                Column {
                    Column(textPadding) {
                        if (widgets.showClock || widgets.showDate) {
                            if (widgets.showClock) {
                                val time = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "hh:mma", French).format(zoned)
                                Text(time.uppercase(French), style = small, modifier = Modifier.clickable(interactionSource = null, indication = null, onClick = actions::openClock))
                            }
                            if (widgets.showDate) {
                                val date = DateTimeFormatter.ofPattern("d MMMM", French).format(zoned).uppercase(French)
                                Text(date, style = small, modifier = Modifier.clickable(interactionSource = null, indication = null, onClick = actions::openCalendar))
                            }
                            Spacer(Modifier.height(18.dp))
                        }
                        if (widgets.showGreeting) {
                            val lines = Greeting.forDay(zoned.dayOfWeek)
                            Text(lines.first, style = greeting)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(lines.second + " ", style = greeting)
                                Highlight(lines.highlight, greeting)
                            }
                            Text(lines.last, style = greeting)
                            Spacer(Modifier.height(14.dp))
                            Box(
                                Modifier
                                    .width(34.dp)
                                    .height(3.dp)
                                    .background(look.onWallpaper),
                            )
                            Spacer(Modifier.height(22.dp))
                        }
                        val weatherText = weatherSentence(widgets.showWeather, widgets.weatherLocation?.name, weather)
                        if (weatherText != null) {
                            Text(
                                text = weatherText,
                                style = sentence,
                                modifier = Modifier
                                    .widthIn(max = 230.dp)
                                    .clickable(interactionSource = null, indication = null) {
                                        if (widgets.weatherLocation == null) onConfigureWeather() else actions.refreshWeather()
                                    },
                            )
                        }
                        if (widgets.showAgenda) {
                            val next = agenda.events.firstOrNull()
                            val agendaText = when {
                                !agenda.hasPermission -> stringResource(R.string.typo_agenda_allow)
                                next == null -> stringResource(R.string.typo_agenda_empty)
                                else -> stringResource(R.string.typo_agenda_next) + " " +
                                    AgendaFormat.whenLabel(next, now, use24Hour) + " · " + next.title
                            }.uppercase(French)
                            Text(
                                text = agendaText,
                                style = sentence,
                                maxLines = 2,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .widthIn(max = 230.dp)
                                    .clickable(interactionSource = null, indication = null) {
                                        when {
                                            !agenda.hasPermission -> actions.requestCalendarPermission()
                                            next != null -> actions.openEvent(next)
                                            else -> actions.openCalendar()
                                        }
                                    },
                            )
                        }
                        if (widgets.showTodo) {
                            Spacer(Modifier.height(22.dp))
                            TodoWidget(
                                todos = config.todos,
                                onAdd = actions::addTodo,
                                onToggle = actions::toggleTodo,
                                onLongPress = onTodoLongPress,
                                onClearDone = actions::clearDoneTodos,
                                textSize = 13.sp,
                            )
                        }
                    }
                    if (widgets.hosted.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp),
                        ) {
                            widgets.hosted.forEach { widget -> key(widget.appWidgetId) { hostedWidget(widget) } }
                        }
                    }
                }

                // ----- Bas : « MES APPS » puis les favoris -----
                Column {
                    homeExtras(textPadding)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = textPadding
                            .padding(bottom = 6.dp)
                            .clickable(interactionSource = null, indication = null, onClick = onOpenDrawer),
                    ) {
                        Text(stringResource(R.string.typo_my) + " ", style = small.copy(fontSize = 10.sp))
                        Highlight(stringResource(R.string.typo_apps), small.copy(fontSize = 10.sp), horizontalPadding = 4)
                    }
                    if (state.home.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_empty_hint),
                            style = secondaryStyle,
                            modifier = textPadding.padding(vertical = 10.dp),
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
            val bottom = measurables[1].measure(loose)
            // Comme sur la vidéo d'inspiration : le texte commence un peu sous le haut de l'écran,
            // les apps finissent un peu au-dessus du bas.
            val topOffset = (usableHeight * 0.07f).toInt()
            val bottomMargin = (usableHeight * 0.05f).toInt()
            val height = max(usableHeight, topOffset + top.height + gapPx + bottom.height + bottomMargin)
            layout(width, height) {
                top.place(0, topOffset)
                val bottomY = height - bottomMargin - bottom.height
                bottom.place(0, max(bottomY, topOffset + top.height + gapPx))
            }
        }
    }
}

/** Phrase météo en majuscules, ou null si la météo n'est pas affichée. */
@Composable
private fun weatherSentence(enabled: Boolean, city: String?, weather: WeatherState): String? {
    if (!enabled) return null
    if (city == null) return stringResource(R.string.typo_weather_choose).uppercase(French)
    val report = weather.report ?: return stringResource(
        if (weather.failed) R.string.weather_unavailable else R.string.weather_loading,
        city,
    ).uppercase(French)
    return WeatherCodes.sentence(city, report)
}
