package io.github.perruzz.stacco.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ShiftsTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun t(h: Int, m: Int = 0) = h * 60 + m

    private val morning = ShiftTemplate("m", "Mattina", t(7), t(14))
    private val afternoon = ShiftTemplate("p", "Pomeriggio", t(14), t(21), breakMinutes = 30)
    private val night = ShiftTemplate("n", "Notte", t(21), t(7))
    private val work = WorkSettings(mode = WorkMode.SHIFTS, shifts = listOf(morning, afternoon, night))

    @Test
    fun `shift templates`() {
        assertFalse(morning.crossesMidnight)
        assertEquals(420, morning.workMinutes)
        assertTrue(night.crossesMidnight)
        assertEquals(t(7) + 1440, night.endAbsolute)
        assertEquals(600, night.workMinutes)
        assertEquals(390, afternoon.workMinutes)
        assertEquals("N", night.code)
    }

    @Test
    fun `calendar follows planned shifts`() {
        val days = mapOf(
            d("2026-11-16") to DayRecord(shiftId = "m"),
            d("2026-11-17") to DayRecord(shiftId = "n"),
            d("2026-11-18") to DayRecord(shiftId = "p", type = DayType.VACATION),
        )
        val cal = WorkCalendar(work, days)
        assertTrue(cal.isScheduled(d("2026-11-16")))
        assertEquals(420, cal.required(d("2026-11-16")))
        assertEquals(420, cal.minimum(d("2026-11-16")))
        assertEquals(t(7) + 1440, cal.plannedEnd(d("2026-11-17")))
        assertEquals(0, cal.required(d("2026-11-18")))
        assertNull(cal.effectiveType(d("2026-11-19")))
        // Public holidays do not stop shift work.
        assertEquals(DayType.OFFICE, WorkCalendar(work, mapOf(d("2026-12-25") to DayRecord(shiftId = "m"))).effectiveType(d("2026-12-25")))
    }

    @Test
    fun `night shift stays the active day after midnight`() {
        val data = StaccoData(work = work, days = mapOf("2026-11-17" to DayRecord(shiftId = "n", entry = t(20, 58))))
        val at2 = Engine.activeDay(data, LocalDateTime.of(2026, 11, 18, 2, 0))
        assertEquals(d("2026-11-17"), at2.date)
        assertEquals(t(2) + 1440, at2.minutes)

        val snapshot = Engine.snapshot(data, LocalDateTime.of(2026, 11, 18, 2, 0))
        assertEquals(StampSlot.EXIT, snapshot.nextSlot)
        assertEquals(t(7) + 1440, snapshot.targetExit)

        // Once the exit is stamped the new day begins.
        val done = data.copy(days = mapOf("2026-11-17" to DayRecord(shiftId = "n", entry = t(20, 58), exit = t(7, 2) + 1440)))
        assertEquals(d("2026-11-18"), Engine.activeDay(done, LocalDateTime.of(2026, 11, 18, 7, 30)).date)
    }

    @Test
    fun `a forgotten exit does not drag yesterday into today`() {
        val data = StaccoData(days = mapOf("2026-11-17" to DayRecord(entry = t(8, 30))))
        assertEquals(d("2026-11-18"), Engine.activeDay(data, LocalDateTime.of(2026, 11, 18, 8, 15)).date)
    }

    @Test
    fun `stamps after midnight are normalized`() {
        val r = DayRecord(entry = t(21))
        assertEquals(t(7) + 1440, Engine.normalizeStamp(r, StampSlot.EXIT, t(7)))
        assertEquals(t(23), Engine.normalizeStamp(r, StampSlot.EXIT, t(23)))
        assertEquals(t(6), Engine.normalizeStamp(r, StampSlot.ENTRY, t(6)))
    }

    @Test
    fun `travel days are not office days`() {
        val cal = WorkCalendar(WorkSettings(), mapOf(d("2026-11-18") to DayRecord(type = DayType.TRAVEL)))
        assertEquals(0, cal.required(d("2026-11-18")))
        assertEquals(1, cal.officeDays(d("2026-11-18"), d("2026-11-19")))
    }

    @Test
    fun `shift delta uses the shift break`() {
        val days = mapOf(d("2026-11-16") to DayRecord(shiftId = "p", entry = t(14), exit = t(21, 10)))
        // 7h10m presence - 30m planned break = 6h40m vs 6h30m planned.
        assertEquals(10, WorkCalendar(work, days).delta(d("2026-11-16")))
    }
}
