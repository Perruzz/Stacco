package io.github.perruzz.stacco.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MissingExitTest {

    private fun d(s: String) = LocalDate.parse(s)

    private val data = StaccoData(
        days = mapOf(
            "2026-11-10" to DayRecord(entry = 540), // exit forgotten
            "2026-11-12" to DayRecord(entry = 540, exit = 1050),
            "2026-11-13" to DayRecord(type = DayType.SMART),
        ),
    )

    @Test
    fun `a recent day without exit is pointed out`() {
        assertEquals(d("2026-11-10"), Engine.missingExit(data, d("2026-11-16")))
        assertEquals(d("2026-11-10"), Engine.missingExit(data, d("2026-11-11")))
    }

    @Test
    fun `the current day and old days are left alone`() {
        // The day being worked is not "missing" its exit yet.
        assertNull(Engine.missingExit(data, d("2026-11-10")))
        // More than two weeks ago: not worth nagging about anymore.
        assertNull(Engine.missingExit(data, d("2026-11-30")))
    }

    @Test
    fun `fixing the exit clears it`() {
        val fixed = data.copy(days = data.days + ("2026-11-10" to DayRecord(entry = 540, exit = 1030)))
        assertNull(Engine.missingExit(fixed, d("2026-11-16")))
    }

    @Test
    fun `every day left out of the bank is listed, however old`() {
        val days = mapOf(
            "2025-01-10" to DayRecord(entry = 540), // last year, exit forgotten
            "2026-10-01" to DayRecord(entry = 540, breakOut = 780, exit = 1050), // break never closed
            "2026-11-10" to DayRecord(entry = 540),
            "2026-11-11" to DayRecord(exit = 1050), // exit without entry
            "2026-11-12" to DayRecord(entry = 540, exit = 1050),
            "2026-11-13" to DayRecord(type = DayType.SMART, entry = 540), // smart working: not counted anyway
            "2026-11-14" to DayRecord(type = DayType.SICK, breakOut = 700),
            "2026-11-16" to DayRecord(entry = 540), // the day under way
        )
        val data = StaccoData(days = days)
        val expected = listOf(
            d("2026-11-11") to DayFix.INCONSISTENT,
            d("2026-11-10") to DayFix.MISSING_EXIT,
            d("2026-10-01") to DayFix.INCONSISTENT,
            d("2025-01-10") to DayFix.MISSING_EXIT,
        )
        assertEquals(expected, Engine.daysToFix(data, d("2026-11-16")))
        // Today's card points out the most recent one, within two weeks.
        assertEquals(expected.first(), Engine.dayToFix(data, d("2026-11-16")))
        assertNull(Engine.dayToFix(StaccoData(days = mapOf("2026-11-13" to DayRecord(type = DayType.SMART, entry = 540))), d("2026-11-16")))
        // Exactly the office days the bank leaves out.
        val calendar = WorkCalendar.from(data)
        val listed = expected.map { it.first }.toSet()
        for (date in data.parsedDays().keys.filter { it < d("2026-11-16") }) {
            val counted = calendar.delta(date) != null
            val office = calendar.effectiveType(date) == DayType.OFFICE
            assertEquals("$date", office && !counted, date in listed)
        }
        // Fixed, a day leaves the list.
        val fixed = data.copy(days = days + ("2025-01-10" to DayRecord(entry = 540, exit = 1050)))
        assertEquals(expected.dropLast(1), Engine.daysToFix(fixed, d("2026-11-16")))
    }

    @Test
    fun `days whose stamps do not add up are pointed out too`() {
        val broken = StaccoData(days = mapOf("2026-11-12" to DayRecord(entry = 540, breakOut = 780, exit = 1050)))
        assertEquals(d("2026-11-12") to DayFix.INCONSISTENT, Engine.dayToFix(broken, d("2026-11-16")))
        // A missing exit is reported as such; the most recent day comes first.
        assertEquals(d("2026-11-10") to DayFix.MISSING_EXIT, Engine.dayToFix(data, d("2026-11-16")))
        assertNull(Engine.dayToFix(StaccoData(), d("2026-11-16")))
    }
}
