package io.github.perruzz.stacco.domain

import io.github.perruzz.stacco.data.Backup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleHistoryTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun t(h: Int, m: Int = 0) = h * 60 + m
    private val h = 60

    private val fullTime = Schedule(dailyMinutes = 8 * h, minDailyMinutes = 7 * h)
    private val partTime = Schedule(dailyMinutes = 6 * h, minDailyMinutes = 5 * h, workDays = setOf(1, 2, 3, 4))
    private val shortWeek = Schedule(dailyMinutes = 4 * h, minDailyMinutes = 4 * h)
    private val shifts = Schedule(mode = WorkMode.SHIFTS)

    // --- How the timeline changes ------------------------------------------------------------

    @Test
    fun `a change keeps the old schedule until the day before`() {
        val history = ScheduleHistory.change(emptyList(), current = fullTime, next = partTime, from = d("2026-11-16"))
        assertEquals(listOf(SchedulePeriod("2026-11-15", fullTime)), history)
        assertEquals(d("2026-11-16"), ScheduleHistory.currentSince(history))
    }

    @Test
    fun `successive contracts pile up`() {
        val first = ScheduleHistory.change(emptyList(), fullTime, partTime, d("2026-11-16"))
        val second = ScheduleHistory.change(first, partTime, shortWeek, d("2027-01-01"))
        assertEquals(listOf(SchedulePeriod("2026-11-15", fullTime), SchedulePeriod("2026-12-31", partTime)), second)
    }

    @Test
    fun `a change dated before a later one replaces it`() {
        // Part-time from 16 Nov… then it turns out the new contract started on 1 Nov.
        val first = ScheduleHistory.change(emptyList(), fullTime, partTime, d("2026-11-16"))
        val corrected = ScheduleHistory.change(first, partTime, partTime, d("2026-11-01"))
        assertEquals(listOf(SchedulePeriod("2026-10-31", fullTime)), corrected)
    }

    @Test
    fun `going back to the previous contract merges the periods`() {
        val first = ScheduleHistory.change(emptyList(), fullTime, partTime, d("2026-11-16"))
        // Undo on the same date: nothing is left in the history.
        assertEquals(emptyList<SchedulePeriod>(), ScheduleHistory.change(first, partTime, fullTime, d("2026-11-16")))
        // Back to full time from December: the part-time weeks stay where they are.
        assertEquals(
            listOf(SchedulePeriod("2026-11-15", fullTime), SchedulePeriod("2026-11-30", partTime)),
            ScheduleHistory.change(first, partTime, fullTime, d("2026-12-01")),
        )
    }

    @Test
    fun `no change and changes for every date leave no history`() {
        val first = ScheduleHistory.change(emptyList(), fullTime, partTime, d("2026-11-16"))
        assertEquals(first, ScheduleHistory.change(first, partTime, partTime, d("2026-12-01")))
        assertEquals(emptyList<SchedulePeriod>(), ScheduleHistory.change(first, partTime, shortWeek, from = null))
        assertNull(ScheduleHistory.currentSince(emptyList()))
    }

    // --- Each day is counted with the rules of its time --------------------------------------

    /** Full time (8h, Mon–Fri) until 15 Nov, part-time (6h, Mon–Thu) from 16 Nov. */
    private fun midMonthChange(days: Map<String, DayRecord>) = StaccoData(
        onboardingDone = true,
        work = WorkSettings().withSchedule(partTime),
        days = days,
        scheduleHistory = listOf(SchedulePeriod("2026-11-15", fullTime)),
    )

    @Test
    fun `a mid-month contract change leaves the first half untouched`() {
        val data = midMonthChange(
            mapOf(
                "2026-11-10" to DayRecord(entry = t(8), breakOut = t(12), breakIn = t(12, 30), exit = t(17)), // 8h30
                "2026-11-17" to DayRecord(entry = t(8), exit = t(14)), // 6h, no break due
            ),
        )
        val cal = WorkCalendar.from(data)
        assertEquals(8 * h, cal.required(d("2026-11-13"))) // Friday, still full time
        assertEquals(0, cal.required(d("2026-11-20"))) // Friday, no longer a working day
        assertEquals(6 * h, cal.required(d("2026-11-19"))) // Thursday, part-time
        assertTrue(cal.isScheduled(d("2026-11-13")))
        assertTrue(!cal.isScheduled(d("2026-11-20")))
        assertEquals(30, cal.delta(d("2026-11-10")))
        assertEquals(0, cal.delta(d("2026-11-17")))
        // The bank adds up without any "set balance": +30 from the first half, 0 after.
        assertEquals(30, BankCalculator.compute(data, d("2026-11-30")).balance)
        // Without the history the old day would be recounted against 6h: +2h30.
        val rewritten = data.copy(scheduleHistory = emptyList())
        assertEquals(2 * h + 30, WorkCalendar.from(rewritten).delta(d("2026-11-10")))
    }

    @Test
    fun `switching to shifts mid-month`() {
        val shiftTemplates = listOf(ShiftTemplate("m", "Mattina", t(7), t(14)))
        val data = StaccoData(
            work = WorkSettings(shifts = shiftTemplates).withSchedule(shifts),
            days = mapOf(
                "2026-11-10" to DayRecord(entry = t(8), breakOut = t(12), breakIn = t(12, 30), exit = t(16, 30), shiftId = "m"),
                "2026-11-17" to DayRecord(shiftId = "m", entry = t(7), exit = t(14)),
            ),
            scheduleHistory = listOf(SchedulePeriod("2026-11-15", fullTime)),
        )
        val cal = WorkCalendar.from(data)
        // Before the change the day follows the fixed hours, even with a shift id around.
        assertNull(cal.shift(d("2026-11-10")))
        assertEquals(8 * h, cal.required(d("2026-11-10")))
        assertEquals(0, cal.delta(d("2026-11-10")))
        // After it, the planned shift.
        assertEquals("m", cal.shift(d("2026-11-17"))?.id)
        assertEquals(7 * h, cal.required(d("2026-11-17")))
        assertEquals(0, cal.delta(d("2026-11-17")))
        assertTrue(!cal.isShiftDay(d("2026-11-10")) && cal.isShiftDay(d("2026-11-17")))
    }

    @Test
    fun `vacation costs the hours of its own time`() {
        val data = midMonthChange(
            mapOf(
                "2026-11-13" to DayRecord(type = DayType.VACATION), // full-time Friday: 8h
                "2026-11-19" to DayRecord(type = DayType.VACATION), // part-time Thursday: 6h
                "2026-11-20" to DayRecord(type = DayType.VACATION), // part-time Friday: day off
            ),
        ).copy(vacation = VacationSettings(annualMinutes = 100 * h))
        assertEquals(14 * h, VacationCalculator.balance(data, d("2026-11-30")).used)
    }

    @Test
    fun `today shows the rules in force that day`() {
        val data = midMonthChange(mapOf("2026-11-13" to DayRecord(entry = t(8))))
        val before = Engine.today(data, BankLedger.EMPTY, d("2026-11-13").atTime(10, 0))
        assertEquals(8 * h, before.work.dailyMinutes)
        assertEquals(8 * h, before.projection.required)
        val after = Engine.today(data, BankLedger.EMPTY, d("2026-11-19").atTime(10, 0))
        assertEquals(6 * h, after.work.dailyMinutes)
    }

    // --- Backward compatibility --------------------------------------------------------------

    @Test
    fun `without a history the current settings apply to every date`() {
        val cal = WorkCalendar(WorkSettings(), emptyMap())
        assertEquals(8 * h, cal.required(d("2020-01-08")))
        assertEquals(8 * h, cal.required(d("2030-01-09")))
    }

    @Test
    fun `old backups without a history still load`() {
        val old = """{"schemaVersion":2,"onboardingDone":true,"work":{"dailyMinutes":450},"days":{"2026-11-16":{"entry":480,"exit":960}}}"""
        val data = Backup.decode(old)!!
        assertEquals(emptyList<SchedulePeriod>(), data.scheduleHistory)
        assertEquals(450, WorkCalendar.from(data).required(d("2026-11-16")))
        // And a history survives a backup round trip.
        val withHistory = midMonthChange(emptyMap())
        assertEquals(withHistory.scheduleHistory, Backup.decode(Backup.encode(withHistory))!!.scheduleHistory)
    }
}
