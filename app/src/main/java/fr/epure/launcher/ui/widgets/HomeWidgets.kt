package fr.epure.launcher.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.epure.launcher.R
import fr.epure.launcher.data.calendar.AgendaEvent
import fr.epure.launcher.data.calendar.AgendaFormat
import fr.epure.launcher.data.config.TodoItem
import fr.epure.launcher.data.config.WidgetsConfig
import fr.epure.launcher.domain.WeatherCodes
import fr.epure.launcher.domain.WeatherKind
import fr.epure.launcher.ui.AgendaState
import fr.epure.launcher.ui.WeatherState
import fr.epure.launcher.ui.theme.LocalLauncherLook
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Heure courante, mise à jour à chaque nouvelle minute et au retour sur l'accueil. */
@Composable
fun rememberNow(fixedNow: Long? = null): Long {
    if (fixedNow != null) return fixedNow
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val current = System.currentTimeMillis()
            now = current
            delay(60_000 - current % 60_000 + 20)
        }
    }
    return now
}

private val French = Locale.FRANCE

private fun WeatherKind.icon(isDay: Boolean): ImageVector = when (this) {
    WeatherKind.CLEAR -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
    WeatherKind.PARTLY_CLOUDY -> Icons.Outlined.FilterDrama
    WeatherKind.CLOUDY -> Icons.Outlined.Cloud
    WeatherKind.FOG -> Icons.Outlined.Dehaze
    WeatherKind.DRIZZLE -> Icons.Outlined.Grain
    WeatherKind.RAIN -> Icons.Outlined.Umbrella
    WeatherKind.SNOW -> Icons.Outlined.AcUnit
    WeatherKind.STORM -> Icons.Outlined.FlashOn
    WeatherKind.UNKNOWN -> Icons.Outlined.Cloud
}

private fun formatTemp(value: Double): String = "${value.roundToInt()}°"

/** Clic sans halo gris (le texte posé sur le fond d'écran reste sobre). */
private fun Modifier.quietClick(onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, onClick = onClick)

/**
 * Bloc « coup d'œil » posé juste au-dessus des favoris : grande heure, date courte
 * avec la météo sur la même ligne, puis les prochains rendez-vous.
 */
@Composable
fun GlanceBlock(
    now: Long,
    use24Hour: Boolean,
    widgets: WidgetsConfig,
    weather: WeatherState,
    agenda: AgendaState,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    onWeatherRefresh: () -> Unit,
    onConfigureWeather: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onEventClick: (AgendaEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalLauncherLook.current
    val type = MaterialTheme.typography
    val zoned = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    var forecastOpen by rememberSaveable { mutableStateOf(false) }
    val secondary = with(look) {
        type.titleMedium.copy(fontSize = 16.sp * textScale, fontWeight = FontWeight.Medium).onWallpaper(muted = true)
    }

    Column(modifier) {
        if (widgets.showClock) {
            Text(
                text = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm", French).format(zoned),
                style = with(look) {
                    type.displayMedium.copy(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.5).sp,
                    ).onWallpaper()
                },
                modifier = Modifier.quietClick(onClockClick),
            )
        }
        val showWeather = widgets.showWeather
        if (widgets.showDate || showWeather) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp)) {
                if (widgets.showDate) {
                    Text(
                        text = DateTimeFormatter.ofPattern("EEE d MMM", French).format(zoned),
                        style = secondary,
                        modifier = Modifier.quietClick(onDateClick),
                    )
                }
                if (showWeather) {
                    if (widgets.showDate) Text("  ·  ", style = secondary)
                    WeatherInline(
                        state = weather,
                        hasLocation = widgets.weatherLocation != null,
                        style = secondary,
                        onClick = {
                            if (widgets.weatherLocation == null) onConfigureWeather()
                            else {
                                forecastOpen = !forecastOpen
                                onWeatherRefresh()
                            }
                        },
                    )
                }
            }
        }
        val report = weather.report
        AnimatedVisibility(
            visible = forecastOpen && report != null && report.days.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.padding(start = 2.dp, top = 8.dp)) {
                widgets.weatherLocation?.let { Text(it.name, style = secondary) }
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(top = 6.dp)) {
                    report?.days?.take(4)?.forEachIndexed { index, day ->
                        val dayName = if (index == 0) stringResource(R.string.weather_today) else runCatching {
                            DateTimeFormatter.ofPattern("EEE", French).format(LocalDate.parse(day.date))
                        }.getOrDefault(day.date)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(dayName, style = secondary.copy(fontSize = 13.sp))
                            Icon(
                                imageVector = WeatherCodes.kindOf(day.code).icon(true),
                                contentDescription = WeatherCodes.labelOf(day.code),
                                tint = look.onWallpaper,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .size(18.dp),
                            )
                            Text("${formatTemp(day.max)} ${formatTemp(day.min)}", style = secondary.copy(fontSize = 13.sp))
                        }
                    }
                }
            }
        }
        if (widgets.showAgenda) {
            Spacer(Modifier.height(8.dp))
            AgendaLines(
                state = agenda,
                now = now,
                use24Hour = use24Hour,
                style = secondary.copy(fontSize = 15.sp * look.textScale),
                onRequestPermission = onRequestCalendarPermission,
                onEventClick = onEventClick,
            )
        }
    }
}

@Composable
private fun WeatherInline(state: WeatherState, hasLocation: Boolean, style: TextStyle, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    val report = state.report
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.quietClick(onClick)) {
        when {
            !hasLocation -> Text(stringResource(R.string.weather_choose_city_short), style = style)
            report == null -> Text(if (state.failed) "—" else "…", style = style)
            else -> {
                Icon(
                    imageVector = WeatherCodes.kindOf(report.code).icon(report.isDay),
                    contentDescription = WeatherCodes.labelOf(report.code, report.isDay),
                    tint = look.onWallpaperMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(formatTemp(report.temperature), style = style)
            }
        }
    }
}

@Composable
private fun AgendaLines(
    state: AgendaState,
    now: Long,
    use24Hour: Boolean,
    style: TextStyle,
    onRequestPermission: () -> Unit,
    onEventClick: (AgendaEvent) -> Unit,
) {
    if (!state.hasPermission) {
        Text(
            text = stringResource(R.string.agenda_allow),
            style = style,
            modifier = Modifier
                .quietClick(onRequestPermission)
                .padding(start = 2.dp, top = 2.dp),
        )
        return
    }
    state.events.take(3).forEach { event ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .quietClick { onEventClick(event) }
                .padding(start = 2.dp, top = 3.dp, bottom = 3.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = AgendaFormat.whenLabel(event, now, use24Hour) + "  " +
                    event.title.ifEmpty { stringResource(R.string.agenda_untitled) },
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Liste de tâches, en haut de l'accueil. */
@Composable
fun TodoWidget(
    todos: List<TodoItem>,
    onAdd: (String) -> Unit,
    onToggle: (String) -> Unit,
    onLongPress: (TodoItem) -> Unit,
    onClearDone: () -> Unit,
    /** Ouvre directement le champ de saisie (captures d'écran de test). */
    startAdding: Boolean = false,
    textSize: TextUnit = 16.sp,
) {
    val look = LocalLauncherLook.current
    val type = MaterialTheme.typography
    val haptics = LocalHapticFeedback.current
    var adding by rememberSaveable { mutableStateOf(startAdding) }
    var draft by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val itemStyle = with(look) { type.titleMedium.copy(fontSize = textSize * textScale, fontWeight = FontWeight.Medium).onWallpaper() }
    val mutedStyle = with(look) { type.titleMedium.copy(fontSize = textSize * textScale, fontWeight = FontWeight.Medium).onWallpaper(muted = true) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
            Text(
                text = stringResource(R.string.todo_title),
                style = with(look) { type.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp).onWallpaper(muted = true) },
                modifier = Modifier.weight(1f),
            )
            if (todos.any { it.done }) {
                Text(
                    text = stringResource(R.string.todo_clear_done),
                    style = with(look) { type.labelLarge.onWallpaper(muted = true) },
                    modifier = Modifier
                        .quietClick(onClearDone)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        todos.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClick = { onToggle(item.id) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress(item)
                        },
                    )
                    .padding(vertical = 5.dp),
            ) {
                Icon(
                    imageVector = if (item.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (item.done) look.onWallpaperMuted else look.onWallpaper,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = item.text,
                    style = (if (item.done) mutedStyle else itemStyle).copy(
                        textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (adding) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = null, tint = look.onWallpaperMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (draft.isEmpty()) Text(stringResource(R.string.todo_new_hint), style = mutedStyle)
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = itemStyle,
                        cursorBrush = SolidColor(look.onWallpaper),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (draft.isNotBlank()) onAdd(draft)
                            draft = ""
                            adding = false
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
            }
            LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .quietClick { adding = true }
                    .padding(vertical = 5.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = look.onWallpaperMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.todo_add), style = mutedStyle)
            }
        }
    }
}
