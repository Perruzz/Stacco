package io.github.perruzz.stacco.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayCalculatorTest {

    private val rules = BreakRules(min = 30)
    private fun t(h: Int, m: Int = 0) = h * 60 + m

    @Test
    fun `only entry - minimum break is assumed`() {
        val p = DayCalculator.project(
            DayRecord(entry = t(8, 30)), rules, required = 480, minimum = 420, bankBefore = 0, now = t(9),
        )
        assertEquals(DayPhase.MORNING, p.phase)
        assertTrue(p.breakInfo.assumed)
        assertEquals(30, p.breakInfo.deducted)
        assertEquals(t(17), p.standardExit)
    }

    @Test
    fun `short break still counts as the minimum`() {
        // Out 12:00, back 12:20 -> 30 minutes are counted anyway.
        val p = DayCalculator.project(
            DayRecord(entry = t(8), breakOut = t(12), breakIn = t(12, 20)),
            rules, 480, 420, 0, now = t(13),
        )
        assertEquals(20, p.breakInfo.actual)
        assertEquals(30, p.breakInfo.deducted)
        assertEquals(10, p.breakInfo.shortfall)
        assertEquals(t(16, 30), p.standardExit)
    }

    @Test
    fun `long break is simply the actual break`() {
        // Out 12:00, back 14:00 -> 2h of break, exit at 18:00.
        val p = DayCalculator.project(
            DayRecord(entry = t(8), breakOut = t(12), breakIn = t(14)),
            rules, 480, 420, bankBefore = 0, now = t(15),
        )
        assertEquals(120, p.breakInfo.deducted)
        assertEquals(t(18), p.standardExit)
    }

    @Test
    fun `positive bank - early exit capped by the minimum daily hours`() {
        // 8h day, 7h minimum, 5h in the bank: at most 1h can be used today.
        val p = DayCalculator.project(
            DayRecord(entry = t(8, 30)), rules, 480, 420, bankBefore = 300, now = t(9),
        )
        assertEquals(t(17), p.standardExit)
        assertEquals(t(16), p.bankExit)
        assertEquals(60, p.bankUsage)
    }

    @Test
    fun `small positive bank - uses all of it`() {
        val p = DayCalculator.project(
            DayRecord(entry = t(8, 30)), rules, 480, 420, bankBefore = 20, now = t(9),
        )
        assertEquals(t(16, 40), p.bankExit)
        assertEquals(20, p.bankUsage)
    }

    @Test
    fun `negative bank - later exit to recover`() {
        val p = DayCalculator.project(
            DayRecord(entry = t(8, 30)), rules, 480, 420, bankBefore = -45, now = t(9),
        )
        assertEquals(t(17, 45), p.bankExit)
        assertEquals(-45, p.bankUsage)
    }

    @Test
    fun `ongoing break pushes the exit while it lasts`() {
        val r = DayRecord(entry = t(8), breakOut = t(12))
        val early = DayCalculator.project(r, rules, 480, 420, 0, now = t(12, 10))
        assertEquals(DayPhase.ON_BREAK, early.phase)
        assertEquals(t(16, 30), early.standardExit) // still the minimum 30'
        assertEquals(t(12, 30), early.earliestReturn)

        val late = DayCalculator.project(r, rules, 480, 420, 0, now = t(13))
        assertEquals(t(17), late.standardExit) // 1h of break so far
    }

    @Test
    fun `completed day produces the bank delta`() {
        val r = DayRecord(entry = t(8), breakOut = t(12), breakIn = t(12, 45), exit = t(17, 0))
        val result = DayCalculator.result(r, rules, 480)!!
        assertEquals(t(17) - t(8) - 45, result.worked)
        assertEquals(15, result.delta)
    }

    @Test
    fun `completed day without break stamps deducts the minimum break`() {
        val r = DayRecord(entry = t(8), exit = t(17))
        assertEquals(30, DayCalculator.result(r, rules, 480)!!.delta)
    }

    @Test
    fun `extra day - no break assumed, everything is overtime`() {
        val r = DayRecord(entry = t(9), exit = t(12))
        assertEquals(180, DayCalculator.result(r, rules, 0)!!.delta)
    }

    @Test
    fun `night shift across midnight`() {
        // Night 21:00 -> 07:00 (+1), no break: 10h planned.
        val r = DayRecord(entry = t(20, 55), exit = t(7, 5) + 1440)
        val result = DayCalculator.result(r, BreakRules(0), 600)!!
        assertEquals(610, result.worked)
        assertEquals(10, result.delta)

        val p = DayCalculator.project(
            DayRecord(entry = t(21)), BreakRules(0), 600, 600, 0, now = t(2) + 1440,
            plannedStart = t(21), plannedEnd = t(7) + 1440,
        )
        assertEquals(t(7) + 1440, p.standardExit)
        assertNull(p.bankExit)
        assertEquals(300, p.workedSoFar)
    }

    @Test
    fun `inconsistent stamps are rejected`() {
        val r = DayRecord(entry = t(9), breakOut = t(8), exit = t(17))
        assertTrue(DayIssue.OUT_OF_ORDER in DayCalculator.issues(r))
        assertNull(DayCalculator.result(r, rules, 480))

        val open = DayRecord(entry = t(9), breakOut = t(12), exit = t(17))
        assertTrue(DayIssue.BREAK_NOT_CLOSED in DayCalculator.issues(open))
    }

    @Test
    fun `next slot follows the day`() {
        assertEquals(StampSlot.ENTRY, DayRecord().nextSlot(true))
        assertEquals(StampSlot.BREAK_OUT, DayRecord(entry = 1).nextSlot(true))
        assertEquals(StampSlot.EXIT, DayRecord(entry = 1).nextSlot(false))
        assertEquals(StampSlot.BREAK_OUT, DayRecord(entry = 1).alternativeSlot(false))
        assertEquals(StampSlot.EXIT, DayRecord(entry = 1).alternativeSlot(true))
        assertEquals(StampSlot.BREAK_IN, DayRecord(entry = 1, breakOut = 2).nextSlot(false))
        assertEquals(StampSlot.EXIT, DayRecord(entry = 1, breakOut = 2, breakIn = 3).nextSlot(true))
        assertNull(DayRecord(entry = 1, exit = 5).nextSlot(true))
    }

    @Test
    fun `worked so far excludes the break`() {
        val r = DayRecord(entry = t(8), breakOut = t(12), breakIn = t(13))
        assertEquals(240, DayCalculator.workedAt(r, t(12, 30)))
        assertEquals(240 + 90, DayCalculator.workedAt(r, t(14, 30)))
    }
}
