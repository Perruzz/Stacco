package io.github.perruzz.stacco.domain

import io.github.perruzz.stacco.notify.Notifications
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

class DaylightSavingTest {

    private val rome = ZoneId.of("Europe/Rome")
    private fun t(h: Int, m: Int = 0) = h * 60 + m
    private val h = 60

    private fun night(date: String): WorkCalendar {
        val shifts = listOf(ShiftTemplate("n", "Notte", t(21), t(7)))
        val record = DayRecord(shiftId = "n", entry = t(21), exit = t(7) + 1440)
        return WorkCalendar(WorkSettings(mode = WorkMode.SHIFTS, shifts = shifts), mapOf(LocalDate.parse(date) to record))
    }

    @Test
    fun `night shifts count the real time when the clocks change`() {
        // 29 March 2026: 02:00 -> 03:00, the night lasts 9 hours.
        assertEquals(9 * h, night("2026-03-28").result(LocalDate.parse("2026-03-28"), rome)!!.worked)
        // 25 October 2026: 03:00 -> 02:00, the night lasts 11 hours.
        assertEquals(11 * h, night("2026-10-24").result(LocalDate.parse("2026-10-24"), rome)!!.worked)
        // Any other night: 10 hours.
        assertEquals(10 * h, night("2026-11-14").result(LocalDate.parse("2026-11-14"), rome)!!.worked)
    }

    @Test
    fun `clock shift is zero within a normal day`() {
        assertEquals(0, clockShift(LocalDate.parse("2026-11-16"), t(8), t(17), rome))
        assertEquals(60, clockShift(LocalDate.parse("2026-03-28"), t(21), t(7) + 1440, rome))
        assertEquals(-60, clockShift(LocalDate.parse("2026-10-24"), t(21), t(7) + 1440, rome))
    }

    @Test
    fun `notification times are clock times on daylight saving days`() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(rome))
        try {
            val expected = ZonedDateTime.of(2026, 3, 29, 8, 0, 0, 0, rome).toInstant().toEpochMilli()
            assertEquals(expected, Notifications.epochMillis(LocalDate.parse("2026-03-29"), t(8)))
            // Past midnight of a night shift that starts the day before the change.
            val nextMorning = ZonedDateTime.of(2026, 10, 25, 7, 0, 0, 0, rome).toInstant().toEpochMilli()
            assertEquals(nextMorning, Notifications.epochMillis(LocalDate.parse("2026-10-24"), t(7) + 1440))
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
