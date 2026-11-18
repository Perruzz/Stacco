package io.github.perruzz.stacco.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VacationTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun t(h: Int, m: Int = 0) = h * 60 + m
    private val h = 60

    // --- Permits: the day can cost hours, never add them -------------------------------------

    @Test
    fun `working more than needed with a permit does not go to the bank`() {
        // 8h day, 4h permit, 5h worked: the extra hour is a wasted permit, not a credit.
        val day = DayRecord(entry = t(8), breakOut = t(12), breakIn = t(12, 30), exit = t(13, 30), leaveMinutes = 4 * h)
        val cal = WorkCalendar(WorkSettings(), mapOf(d("2026-11-16") to day))
        assertEquals(4 * h, cal.required(d("2026-11-16")))
        assertEquals(0, cal.delta(d("2026-11-16")))
        val result = DayCalculator.result(day, BreakRules(30), 4 * h)!!
        assertEquals(5 * h, result.worked)
    }

    @Test
    fun `working less than needed with a permit costs the bank`() {
        // 8h day, 4h permit, 3h worked: one hour less in the bank.
        val day = DayRecord(entry = t(8), breakOut = t(10), breakIn = t(10, 30), exit = t(11, 30), leaveMinutes = 4 * h)
        val cal = WorkCalendar(WorkSettings(), mapOf(d("2026-11-16") to day))
        assertEquals(-1 * h, cal.delta(d("2026-11-16")))
    }

    @Test
    fun `permit days without a stamped break match the examples`() {
        // 8h day with 4h of permit: no lunch break is due on what is left (4h).
        val five = DayRecord(entry = t(8), exit = t(13), leaveMinutes = 4 * h)
        val three = DayRecord(entry = t(8), exit = t(11), leaveMinutes = 4 * h)
        val cal = WorkCalendar(WorkSettings(), mapOf(d("2026-11-16") to five, d("2026-11-17") to three))
        assertEquals(0, cal.delta(d("2026-11-16")))
        assertEquals(-1 * h, cal.delta(d("2026-11-17")))
    }

    @Test
    fun `with a permit the bank is never recovered by staying longer`() {
        val day = DayRecord(entry = t(8), leaveMinutes = 4 * h)
        val p = DayCalculator.project(day, BreakRules(30), required = 4 * h, minimum = 3 * h, bankBefore = -2 * h, now = t(9))
        // Standard exit: 8:00 + 4h + 30m assumed break; no later "recovery" exit.
        assertEquals(t(12, 30), p.standardExit)
        assertEquals(t(12, 30), p.bankExit)
        // Leaving early with a positive bank is still possible.
        val early = DayCalculator.project(day, BreakRules(30), required = 4 * h, minimum = 3 * h, bankBefore = 2 * h, now = t(9))
        assertEquals(t(11, 30), early.bankExit)
    }

    @Test
    fun `without a permit overtime still goes to the bank`() {
        val day = DayRecord(entry = t(8), breakOut = t(12), breakIn = t(12, 30), exit = t(17))
        val cal = WorkCalendar(WorkSettings(), mapOf(d("2026-11-16") to day))
        assertEquals(30, cal.delta(d("2026-11-16")))
    }

    // --- Vacation hours ----------------------------------------------------------------------

    private val today = d("2026-11-18")

    private fun data(vacation: VacationSettings, days: Map<String, DayRecord>, work: WorkSettings = WorkSettings()) =
        StaccoData(work = work, vacation = vacation, days = days)

    @Test
    fun `vacation days and permits are taken from the yearly hours`() {
        val data = data(
            // No carry-over here: the 2025 day must not touch 2026 at all.
            VacationSettings(annualMinutes = 160 * h, carryOver = false),
            mapOf(
                "2026-03-09" to DayRecord(type = DayType.VACATION), // Monday
                "2026-03-10" to DayRecord(type = DayType.VACATION), // Tuesday
                "2026-03-14" to DayRecord(type = DayType.VACATION), // Saturday: free
                "2026-12-08" to DayRecord(type = DayType.VACATION), // Immacolata: free
                "2026-04-15" to DayRecord(entry = t(8), exit = t(12), leaveMinutes = 4 * h),
                "2026-12-28" to DayRecord(type = DayType.VACATION), // planned
                "2025-07-01" to DayRecord(type = DayType.VACATION), // another year
            ),
        )
        val b = VacationCalculator.balance(data, today)
        assertEquals(2026, b.year)
        assertEquals(20 * h, b.used)
        assertEquals(8 * h, b.planned)
        assertEquals(2, b.daysTaken)
        assertEquals(1, b.daysPlanned)
        assertEquals(4 * h, b.leave)
        assertEquals(140 * h, b.available)
        assertEquals(132 * h, b.remaining)
    }

    @Test
    fun `leftover hours move to the next year only when carried over`() {
        val days = mapOf("2025-07-01" to DayRecord(type = DayType.VACATION), "2025-07-02" to DayRecord(type = DayType.VACATION))
        val carried = VacationCalculator.balance(data(VacationSettings(annualMinutes = 20 * h), days), today)
        assertEquals(4 * h, carried.carriedOver)
        assertEquals(24 * h, carried.available)
        val reset = VacationCalculator.balance(data(VacationSettings(annualMinutes = 20 * h, carryOver = false), days), today)
        assertEquals(0, reset.carriedOver)
        assertEquals(20 * h, reset.available)
    }

    @Test
    fun `available hours can be aligned to the payslip`() {
        val start = data(VacationSettings(annualMinutes = 160 * h), mapOf("2026-03-09" to DayRecord(type = DayType.VACATION)))
        val adjustment = VacationCalculator.adjustmentFor(100 * h, start, today)
        val aligned = start.copy(vacation = start.vacation.copy(adjustments = mapOf("2026" to adjustment)))
        assertEquals(100 * h, VacationCalculator.balance(aligned, today).available)
        assertTrue(aligned.vacation.tracked)
        assertFalse(VacationSettings().tracked)
    }

    @Test
    fun `with shifts a vacation day is worth the planned shift`() {
        val shifts = listOf(
            ShiftTemplate("m", "Mattina", t(7), t(14)),
            ShiftTemplate("p", "Pomeriggio", t(14), t(21)),
            ShiftTemplate("n", "Notte", t(21), t(7)),
        )
        val work = WorkSettings(mode = WorkMode.SHIFTS, shifts = shifts)
        val days = mapOf(
            "2026-11-14" to DayRecord(type = DayType.VACATION, shiftId = "n"), // a Saturday night shift
            "2026-11-15" to DayRecord(type = DayType.VACATION), // no shift planned: median shift (7h)
        )
        val b = VacationCalculator.balance(data(VacationSettings(annualMinutes = 100 * h), days, work), today)
        assertEquals(10 * h + 7 * h, b.used)
    }

    @Test
    fun `marking many days skips days off unless only days off were picked`() {
        val cal = WorkCalendar(WorkSettings(), emptyMap())
        val week = (9..15).map { d("2026-11-%02d".format(it)) } // Mon 9 .. Sun 15
        assertEquals((9..13).map { d("2026-11-%02d".format(it)) }, cal.daysToMark(week, DayType.VACATION))
        val weekend = listOf(d("2026-11-14"), d("2026-11-15"))
        assertEquals(weekend, cal.daysToMark(weekend, DayType.OFFICE))
        assertEquals(week, cal.daysToMark(week, null))
        val preview = VacationCalculator.costAsVacation(StaccoData(), week)
        assertEquals(5 * 8 * h, preview)
    }
}
