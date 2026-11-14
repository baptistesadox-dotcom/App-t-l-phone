package fr.epure.launcher.ui.widgets

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.Event
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.epure.launcher.R
import fr.epure.launcher.data.calendar.AgendaEvent
import fr.epure.launcher.data.calendar.AgendaFormat
import fr.epure.launcher.data.config.TodoItem
import fr.epure.launcher.data.config.WeatherLocation
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

@Composable
fun ClockWidget(
    now: Long,
    use24Hour: Boolean,
    showClock: Boolean,
    showDate: Boolean,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
) {
    val look = LocalLauncherLook.current
    val zoned = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    Column {
        if (showClock) {
            val time = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm", French).format(zoned)
            Text(
                text = time,
                style = with(look) {
                    MaterialTheme.typography.displayLarge.copy(
                        fontSize = 68.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-1).sp,
                    ).onWallpaper()
                },
                modifier = Modifier.clickable(onClick = onClockClick),
            )
        }
        if (showDate) {
            val date = DateTimeFormatter.ofPattern("EEEE d MMMM", French).format(zoned)
                .replaceFirstChar { it.titlecase(French) }
            Text(
                text = date,
                style = with(look) { MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp).onWallpaper(muted = true) },
                modifier = Modifier
                    .clickable(onClick = onDateClick)
                    .padding(vertical = 2.dp),
            )
        }
    }
}

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

@Composable
fun WeatherWidget(
    state: WeatherState,
    location: WeatherLocation?,
    onRefresh: () -> Unit,
    onConfigure: () -> Unit,
) {
    val look = LocalLauncherLook.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val report = state.report
    val text = MaterialTheme.typography
    if (location == null) {
        Text(
            text = stringResource(R.string.weather_choose_city),
            style = with(look) { text.titleMedium.onWallpaper(muted = true) },
            modifier = Modifier
                .clickable(onClick = onConfigure)
                .padding(vertical = 6.dp),
        )
        return
    }
    Column(
        modifier = Modifier.clickable {
            expanded = !expanded
            onRefresh()
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            if (report != null) {
                Icon(
                    imageVector = WeatherCodes.kindOf(report.code).icon(report.isDay),
                    contentDescription = null,
                    tint = look.onWallpaper,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = formatTemp(report.temperature),
                    style = with(look) { text.headlineSmall.copy(fontWeight = FontWeight.Medium).onWallpaper() },
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${location.name} · ${WeatherCodes.labelOf(report.code, report.isDay)}",
                    style = with(look) { text.titleMedium.onWallpaper(muted = true) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = if (state.failed) stringResource(R.string.weather_unavailable, location.name)
                    else stringResource(R.string.weather_loading, location.name),
                    style = with(look) { text.titleMedium.onWallpaper(muted = true) },
                )
            }
        }
        AnimatedVisibility(visible = expanded && report != null && report.days.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                report?.days?.take(4)?.forEachIndexed { index, day ->
                    val dayName = when (index) {
                        0 -> stringResource(R.string.weather_today)
                        else -> runCatching {
                            DateTimeFormatter.ofPattern("EEE", French).format(LocalDate.parse(day.date))
                                .replaceFirstChar { it.titlecase(French) }
                        }.getOrDefault(day.date)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dayName, style = with(look) { text.labelLarge.onWallpaper(muted = true) })
                        Icon(
                            imageVector = WeatherCodes.kindOf(day.code).icon(true),
                            contentDescription = WeatherCodes.labelOf(day.code),
                            tint = look.onWallpaper,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .size(20.dp),
                        )
                        Text(
                            text = "${formatTemp(day.max)} ${formatTemp(day.min)}",
                            style = with(look) { text.labelLarge.onWallpaper() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AgendaWidget(
    state: AgendaState,
    now: Long,
    use24Hour: Boolean,
    onRequestPermission: () -> Unit,
    onEventClick: (AgendaEvent) -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val look = LocalLauncherLook.current
    val text = MaterialTheme.typography
    if (!state.hasPermission) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onRequestPermission)
                .padding(vertical = 6.dp),
        ) {
            Icon(Icons.Outlined.Event, contentDescription = null, tint = look.onWallpaperMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.agenda_allow), style = with(look) { text.titleMedium.onWallpaper(muted = true) })
        }
        return
    }
    if (state.events.isEmpty()) {
        Text(
            text = stringResource(R.string.agenda_empty),
            style = with(look) { text.titleMedium.onWallpaper(muted = true) },
            modifier = Modifier
                .clickable(onClick = onOpenCalendar)
                .padding(vertical = 6.dp),
        )
        return
    }
    Column {
        state.events.forEach { event ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEventClick(event) }
                    .padding(vertical = 5.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary, CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = event.title.ifEmpty { stringResource(R.string.agenda_untitled) },
                        style = with(look) { text.titleMedium.onWallpaper() },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = AgendaFormat.whenLabel(event, now, use24Hour),
                        style = with(look) { text.bodyMedium.onWallpaper(muted = true) },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun TodoWidget(
    todos: List<TodoItem>,
    onAdd: (String) -> Unit,
    onToggle: (String) -> Unit,
    onLongPress: (TodoItem) -> Unit,
    onClearDone: () -> Unit,
    /** Ouvre directement le champ de saisie (captures d'écran de test). */
    startAdding: Boolean = false,
) {
    val look = LocalLauncherLook.current
    val text = MaterialTheme.typography
    val haptics = LocalHapticFeedback.current
    var adding by rememberSaveable { mutableStateOf(startAdding) }
    var draft by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
            Text(
                text = stringResource(R.string.todo_title),
                style = with(look) { text.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp).onWallpaper(muted = true) },
                modifier = Modifier.weight(1f),
            )
            if (todos.any { it.done }) {
                Text(
                    text = stringResource(R.string.todo_clear_done),
                    style = with(look) { text.labelLarge.onWallpaper(muted = true) },
                    modifier = Modifier
                        .clickable(onClick = onClearDone)
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
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = item.text,
                    style = with(look) {
                        text.titleMedium.copy(
                            textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                        ).onWallpaper(muted = item.done)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (adding) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = null, tint = look.onWallpaperMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        Text(stringResource(R.string.todo_new_hint), style = with(look) { text.titleMedium.onWallpaper(muted = true) })
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = with(look) { text.titleMedium.onWallpaper() },
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
                    .clickable { adding = true }
                    .padding(vertical = 5.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = look.onWallpaperMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.todo_add), style = with(look) { text.titleMedium.onWallpaper(muted = true) })
            }
        }
    }
}

/** Espace vertical régulier entre les widgets de l'accueil. */
@Composable
fun WidgetSpacer() = Spacer(Modifier.height(18.dp))
