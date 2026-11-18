package io.github.perruzz.stacco.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

class ScheduleRulesTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun t(h: Int, m: Int = 0) = h * 60 + m
    private val h = 60

    // --- Hours per weekday -------------------------------------------------------------------

    /** 9h Monday to Thursday, 4h on Friday (a "short week" of 40h). */
    private val shortFriday = WorkSettings(dailyMinutes = 9 * h, minDailyMinutes = 8 * h, dailyByDay = mapOf(5 to 4 * h))

    @Test
    fun `each weekday can have its own hours`() {
        val cal = WorkCalendar(shortFriday, emptyMap())
        assertEquals(9 * h, cal.required(d("2026-11-16"))) // Monday
        assertEquals(4 * h, cal.required(d("2026-11-20"))) // Friday
        // The bank shortens every day by the same hour.
        assertEquals(8 * h, cal.minimum(d("2026-11-16")))
        assertEquals(3 * h, cal.minimum(d("2026-11-20")))
    }

    @Test
    fun `a short day counts against its own hours`() {
        val friday = DayRecord(entry = t(8), exit = t(12))
        val cal = WorkCalendar(shortFriday, mapOf(d("2026-11-20") to friday))
        assertEquals(0, cal.delta(d("2026-11-20")))
    }

    @Test
    fun `a vacation day costs that weekday's hours`() {
        val data = StaccoData(
            work = shortFriday,
            vacation = VacationSettings(annualMinutes = 100 * h),
            days = mapOf("2026-11-19" to DayRecord(type = DayType.VACATION), "2026-11-20" to DayRecord(type = DayType.VACATION)),
        )
        assertEquals(9 * h + 4 * h, VacationCalculator.balance(data, d("2026-11-30")).used)
    }

    @Test
    fun `without overrides every day has the daily hours`() {
        val cal = WorkCalendar(WorkSettings(), emptyMap())
        assertEquals(8 * h, cal.required(d("2026-11-20")))
        assertEquals(7 * h, cal.minimum(d("2026-11-20")))
    }

    // --- Rounding ----------------------------------------------------------------------------

    @Test
    fun `rounding up, down and to the nearest step`() {
        assertEquals(t(8, 15), Rounding.round(t(8, 7), 15, RoundingDirection.UP))
        assertEquals(t(8, 0), Rounding.round(t(8, 7), 15, RoundingDirection.DOWN))
        assertEquals(t(8, 0), Rounding.round(t(8, 7), 15, RoundingDirection.NEAREST))
        assertEquals(t(8, 15), Rounding.round(t(8, 8), 15, RoundingDirection.NEAREST))
        assertEquals(t(8, 30), Rounding.round(t(8, 30), 15, RoundingDirection.UP)) // already on a step
        assertEquals(t(8, 7), Rounding.round(t(8, 7), 0, RoundingDirection.UP)) // off
        // Past midnight (night shifts): 00:52 of the next day rounded up to 01:00.
        assertEquals(1440 + t(1), Rounding.round(1440 + t(0, 52), 15, RoundingDirection.UP))
    }

    @Test
    fun `tolerance keeps a few minutes on the near step`() {
        // Arriving at 8:04 with 5 minutes of tolerance is still 8:00; at 8:06 it is 8:15.
        assertEquals(t(8), Rounding.round(t(8, 4), 15, RoundingDirection.UP, tolerance = 5))
        assertEquals(t(8, 15), Rounding.round(t(8, 6), 15, RoundingDirection.UP, tolerance = 5))
        // Leaving at 16:56 is already 17:00; at 16:54 it is 16:45.
        assertEquals(t(17), Rounding.round(t(16, 56), 15, RoundingDirection.DOWN, tolerance = 5))
        assertEquals(t(16, 45), Rounding.round(t(16, 54), 15, RoundingDirection.DOWN, tolerance = 5))
    }

    @Test
    fun `the day is counted with rounded entry and exit`() {
        val rules = RoundingSettings(stepMinutes = 15, entry = RoundingDirection.UP, exit = RoundingDirection.DOWN)
        val work = WorkSettings(rounding = rules)
        // In at 8:07 (counts 8:15), lunch 12:30-13:00, out at 16:50 (counts 16:45): exactly 8h.
        val day = DayRecord(entry = t(8, 7), breakOut = t(12, 30), breakIn = t(13), exit = t(16, 50))
        val cal = WorkCalendar(work, mapOf(d("2026-11-16") to day))
        assertEquals(t(8, 15), cal.counted(d("2026-11-16"))!!.entry)
        assertEquals(8 * h, cal.result(d("2026-11-16"))!!.worked)
        assertEquals(0, cal.delta(d("2026-11-16")))
        // Without rounding the same stamps give 8h13m.
        assertEquals(13, WorkCalendar(WorkSettings(), mapOf(d("2026-11-16") to day)).delta(d("2026-11-16")))
    }

    @Test
    fun `rounding never turns a valid day into an invalid one`() {
        val rules = RoundingSettings(stepMinutes = 30, entry = RoundingDirection.UP, exit = RoundingDirection.DOWN)
        val tiny = DayRecord(entry = t(8, 5), exit = t(8, 20)) // would become 8:30 -> 8:00
        assertSame(tiny, Rounding.counted(tiny, rules))
    }

    @Test
    fun `the timer counts the rounded entry too`() {
        val work = WorkSettings(rounding = RoundingSettings(stepMinutes = 15))
        val data = StaccoData(work = work, days = mapOf("2026-11-16" to DayRecord(entry = t(8, 7))))
        val snapshot = Engine.today(data, BankLedger.EMPTY, d("2026-11-16").atTime(10, 0))
        // Standard exit from 8:15: + 8h + 30m of assumed break.
        assertEquals(t(16, 45), snapshot.projection.standardExit)
        // The snapshot still shows the real stamp.
        assertEquals(t(8, 7), snapshot.record.entry)
    }
}
