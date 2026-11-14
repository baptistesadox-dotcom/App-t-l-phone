package fr.epure.launcher.data.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Règles d'affichage des événements (indépendantes d'Android, donc testables). */
object AgendaFormat {

    /** Jour (local) de l'événement. Les événements « journée entière » sont stockés en UTC. */
    fun localDate(event: AgendaEvent, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        if (event.allDay) Instant.ofEpochMilli(event.beginMillis).atZone(ZoneOffset.UTC).toLocalDate()
        else Instant.ofEpochMilli(event.beginMillis).atZone(zone).toLocalDate()

    fun dayStartLocal(event: AgendaEvent, zone: ZoneId = ZoneId.systemDefault()): Long =
        localDate(event, zone).toEpochDay()

    /** Vrai si l'événement n'est pas terminé et commence avant la fin de la fenêtre affichée. */
    fun isStillRelevant(
        event: AgendaEvent,
        nowMillis: Long,
        windowEndMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (event.allDay) {
            val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            val lastDay = Instant.ofEpochMilli(event.endMillis - 1).atZone(ZoneOffset.UTC).toLocalDate()
            val windowEnd = Instant.ofEpochMilli(windowEndMillis).atZone(zone).toLocalDate()
            return !lastDay.isBefore(today) && localDate(event, zone).isBefore(windowEnd)
        }
        return event.endMillis > nowMillis && event.beginMillis < windowEndMillis
    }

    /** Ordre d'affichage : par jour, la journée entière d'abord, puis par heure. */
    fun sorted(events: List<AgendaEvent>, zone: ZoneId = ZoneId.systemDefault()): List<AgendaEvent> =
        events.sortedWith(
            compareBy<AgendaEvent>({ dayStartLocal(it, zone) }, { if (it.allDay) 0 else 1 }, { it.beginMillis }),
        )

    /**
     * Texte de l'horaire : « Maintenant · jusqu’à 15:00 », « 14:30 », « Demain · 09:00 »,
     * « Toute la journée », « mer. 18 nov. · 10:00 »…
     */
    fun whenLabel(
        event: AgendaEvent,
        nowMillis: Long,
        use24Hour: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.FRANCE,
    ): String {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val eventDay = localDate(event, zone)
        val time = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale)
        val dayLabel = when {
            event.allDay && !eventDay.isAfter(today) -> "Aujourd’hui"
            eventDay == today -> null
            eventDay == today.plusDays(1) -> "Demain"
            else -> DateTimeFormatter.ofPattern("EEE d MMM", locale).format(eventDay)
        }
        if (event.allDay) {
            return if (dayLabel == null || dayLabel == "Aujourd’hui") "Toute la journée" else "$dayLabel · journée"
        }
        val begin = Instant.ofEpochMilli(event.beginMillis).atZone(zone)
        val end = Instant.ofEpochMilli(event.endMillis).atZone(zone)
        if (event.beginMillis <= nowMillis && nowMillis < event.endMillis) {
            return "Maintenant · jusqu’à ${time.format(end)}"
        }
        return if (dayLabel == null) time.format(begin) else "$dayLabel · ${time.format(begin)}"
    }
}
