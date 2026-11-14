package io.github.perruzz.stacco.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BankLedgerTest {

    private val rules = BankSettings(
        positiveExpiry = ExpiryPeriod.QUARTERLY,
        negativeMustBeRecovered = true,
        resetNegativeAtMonthEnd = true,
    )

    private fun d(s: String) = LocalDate.parse(s)
    private fun set(date: String, minutes: Int) = BankAdjustment(1, date, AdjustmentKind.SET_BALANCE, minutes)

    @Test
    fun `positive balance survives within the quarter and expires after it`() {
        // 5h on 14 November: still there on 31 December, wiped on 1 January.
        val adj = listOf(set("2026-11-14", 300))
        val dec31 = BankCalculator.compute(emptyList(), adj, rules, d("2026-12-31"))
        assertEquals(300, dec31.balance)
        val jan1 = BankCalculator.compute(emptyList(), adj, rules, d("2027-01-01"))
        assertEquals(0, jan1.balance)
        assertTrue(jan1.movements.any { it.kind == MovementKind.EXPIRY && it.amount == -300 })
    }

    @Test
    fun `month end does not wipe a positive balance in the middle of a quarter`() {
        val adj = listOf(set("2026-10-10", 120))
        val ledger = BankCalculator.compute(emptyList(), adj, rules, d("2026-11-03"))
        assertEquals(120, ledger.balance)
    }

    @Test
    fun `negative balance is reset at month end when the rule says so`() {
        val deltas = listOf(d("2026-11-27") to -45)
        val ledger = BankCalculator.compute(deltas, emptyList(), rules, d("2026-12-01"))
        assertEquals(0, ledger.balance)
        assertTrue(ledger.movements.any { it.kind == MovementKind.NEGATIVE_RESET })

        val carry = BankCalculator.compute(deltas, emptyList(), rules.copy(resetNegativeAtMonthEnd = false), d("2026-12-01"))
        assertEquals(-45, carry.balance)
    }

    @Test
    fun `day deltas accumulate and today's work is kept apart`() {
        val deltas = listOf(d("2026-11-12") to 15, d("2026-11-13") to -5, d("2026-11-14") to 20)
        val ledger = BankCalculator.compute(deltas, emptyList(), rules, d("2026-11-14"))
        assertEquals(30, ledger.balance)
        assertEquals(10, ledger.balanceBeforeToday)
        assertEquals(20, ledger.todayDelta)
    }

    @Test
    fun `balance anchor overrides older history`() {
        val deltas = listOf(d("2026-11-10") to 100, d("2026-11-14") to 10)
        val adj = listOf(set("2026-11-12", 60))
        val ledger = BankCalculator.compute(deltas, adj, rules, d("2026-11-14"))
        assertEquals(70, ledger.balance)
    }

    @Test
    fun `anchor keeps the balance equal to what the user typed`() {
        val data = StaccoData(
            days = mapOf("2026-11-14" to DayRecord(entry = 8 * 60, exit = 17 * 60 + 15)),
        )
        val today = d("2026-11-14")
        val anchor = BankCalculator.anchorFor(target = 200, data = data, today = today)
        val withAnchor = data.copy(adjustments = listOf(set("2026-11-14", anchor)))
        assertEquals(200, BankCalculator.compute(withAnchor, today).balance)
    }

    @Test
    fun `alerts adapt to the balance`() {
        val work = WorkSettings()
        val calendar = WorkCalendar(work, emptyMap())
        // 5h on 14 November, quarter ends on 31 December: plenty of time, no push yet.
        val early = AlertPlanner.plan(300, d("2026-11-14"), false, calendar, work, rules)
        val expiry = early.filterIsInstance<BankAlert.Expiry>().single()
        assertEquals(d("2026-12-31"), expiry.expiry)
        assertEquals(5, expiry.daysNeeded)
        assertFalse(expiry.inWindow)

        // A few office days before the end of the quarter the alert fires.
        val late = AlertPlanner.plan(300, d("2026-12-21"), false, calendar, work, rules)
        assertTrue(late.filterIsInstance<BankAlert.Expiry>().single().inWindow)

        // Negative balance a few days before month end.
        val negative = AlertPlanner.plan(-45, d("2026-11-26"), false, calendar, work, rules)
        val n = negative.filterIsInstance<BankAlert.Negative>().single()
        assertTrue(n.inWindow)
        assertEquals(3, n.officeDaysLeft) // 26, 27, 30 November
        assertEquals(15, n.perDay)
    }
}
