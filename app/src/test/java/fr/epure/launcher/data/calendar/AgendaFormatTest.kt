package fr.epure.launcher.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class AgendaFormatTest {

    private val paris = ZoneId.of("Europe/Paris")
    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(paris).toInstant().toEpochMilli()
    private fun utcDay(date: String): Long = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private val now = at("2026-11-14T10:00")

    private fun timed(begin: String, end: String) = AgendaEvent(1, "Réunion", at(begin), at(end), allDay = false, color = null)
    private fun allDay(from: String, toExclusive: String) =
        AgendaEvent(2, "Anniversaire", utcDay(from), utcDay(toExclusive), allDay = true, color = null)

    private fun label(event: AgendaEvent) = AgendaFormat.whenLabel(event, now, use24Hour = true, zone = paris, locale = Locale.FRANCE)

    @Test
    fun `horaires lisibles en francais`() {
        assertEquals("14:30", label(timed("2026-11-14T14:30", "2026-11-14T15:00")))
        assertEquals("Maintenant · jusqu’à 10:30", label(timed("2026-11-14T09:30", "2026-11-14T10:30")))
        assertEquals("Demain · 09:00", label(timed("2026-11-15T09:00", "2026-11-15T10:00")))
        assertEquals("Toute la journée", label(allDay("2026-11-14", "2026-11-15")))
        assertEquals("Demain · journée", label(allDay("2026-11-15", "2026-11-16")))
        assertTrue(label(timed("2026-11-17T08:15", "2026-11-17T09:00")).endsWith("17 nov. · 08:15"))
    }

    @Test
    fun `evenements termines ignores`() {
        val windowEnd = now + 2 * 24 * 3600 * 1000L
        assertFalse(AgendaFormat.isStillRelevant(timed("2026-11-14T08:00", "2026-11-14T09:00"), now, windowEnd, paris))
        assertTrue(AgendaFormat.isStillRelevant(timed("2026-11-14T09:00", "2026-11-14T11:00"), now, windowEnd, paris))
        assertFalse(AgendaFormat.isStillRelevant(allDay("2026-11-13", "2026-11-14"), now, windowEnd, paris))
        assertTrue(AgendaFormat.isStillRelevant(allDay("2026-11-13", "2026-11-15"), now, windowEnd, paris))
        assertFalse(AgendaFormat.isStillRelevant(timed("2026-11-20T09:00", "2026-11-20T10:00"), now, windowEnd, paris))
    }

    @Test
    fun `tri par jour, journee entiere en premier`() {
        val events = listOf(
            timed("2026-11-15T08:00", "2026-11-15T09:00"),
            timed("2026-11-14T16:00", "2026-11-14T17:00"),
            allDay("2026-11-14", "2026-11-15"),
            timed("2026-11-14T11:00", "2026-11-14T12:00"),
        )
        val sorted = AgendaFormat.sorted(events, paris)
        assertEquals(listOf(events[2], events[3], events[1], events[0]), sorted)
    }
}
