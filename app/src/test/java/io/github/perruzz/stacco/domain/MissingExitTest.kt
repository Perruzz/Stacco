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
    fun `days whose stamps do not add up are pointed out too`() {
        val broken = StaccoData(days = mapOf("2026-11-12" to DayRecord(entry = 540, breakOut = 780, exit = 1050)))
        assertEquals(d("2026-11-12") to DayFix.INCONSISTENT, Engine.dayToFix(broken, d("2026-11-16")))
        // A missing exit is reported as such; the most recent day comes first.
        assertEquals(d("2026-11-10") to DayFix.MISSING_EXIT, Engine.dayToFix(data, d("2026-11-16")))
        assertNull(Engine.dayToFix(StaccoData(), d("2026-11-16")))
    }
}
