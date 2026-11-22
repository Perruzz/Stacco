package io.github.perruzz.stacco.data

import io.github.perruzz.stacco.domain.BankCalculator
import io.github.perruzz.stacco.domain.DayFix
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.OnCall
import io.github.perruzz.stacco.domain.OnCallMonth
import io.github.perruzz.stacco.domain.RecoveryCalculator
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.domain.WorkMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.TimeZone

/**
 * Backups written by every release since 1.0 (src/test/resources/backups, generated through each
 * release's own model classes) import into this version without losing anything, and count right.
 */
class BackupCompatibilityTest {

    private val rome = ZoneId.of("Europe/Rome")
    private var previousZone: TimeZone? = null

    @Before
    fun romeTime() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(rome))
    }

    @After
    fun restoreTime() {
        TimeZone.setDefault(previousZone)
    }

    private val versions = listOf("1.0.0", "1.2.1", "1.3.1", "1.4.0", "1.5.1", "1.6.4")

    private fun text(version: String) = javaClass.getResource("/backups/stacco-$version.json")!!.readText()
    private fun backup(version: String): StaccoData = Backup.decode(text(version)).also { assertNotNull("backup $version", it) }!!
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `every old backup imports without losing a single value`() {
        for (version in versions) {
            val original = StaccoJson.parseToJsonElement(text(version))
            val restored = StaccoJson.parseToJsonElement(Backup.encode(backup(version)))
            // 1.0 still had a maximum break (never used since 1.1) and schema 1.
            assertContained(version, "", original, restored, ignored = setOf("/work/maxBreakMinutes", "/schemaVersion"))
        }
    }

    @Test
    fun `restored backups follow the current schema`() {
        for (version in versions) assertEquals(version, StaccoData().schemaVersion, backup(version).schemaVersion)
    }

    @Test
    fun `restored backups survive a second backup unchanged`() {
        for (version in versions) {
            val once = backup(version)
            assertEquals(version, once, Backup.decode(Backup.encode(once)))
        }
    }

    @Test
    fun `1_0 - fixed hours, an anchor, a correction and two days to fix`() {
        val data = backup("1.0.0")
        assertEquals("Giulia", data.profile.name)
        assertEquals(11, data.days.size)
        // 2h anchor, +5 +10 -30 +180 (Saturday) 0 (permit) +45, -15 corrected; 11 and 13 November left out.
        assertEquals(315, BankCalculator.compute(data, d("2026-11-14")).balance)
        assertEquals(d("2026-11-13") to DayFix.MISSING_EXIT, Engine.dayToFix(data, d("2026-11-14")))
        assertEquals(d("2026-11-11") to DayFix.INCONSISTENT, Engine.dayToFix(data, d("2026-11-12")))
    }

    @Test
    fun `1_2 - shifts, the autumn night and a travel day`() {
        val data = backup("1.2.1")
        assertEquals(WorkMode.SHIFTS, data.work.mode)
        assertEquals(3, data.work.shifts.size)
        assertEquals(1207, data.work.patronDay)
        val calendar = WorkCalendar.from(data)
        // The night the clocks go back lasts nine hours: one more than the shift.
        assertEquals(60, calendar.delta(d("2026-10-24")))
        assertEquals(null, calendar.delta(d("2026-10-28")))
        // +60 +10 0 +90 (correction) -30.
        assertEquals(130, BankCalculator.compute(data, d("2026-11-17")).balance)
    }

    @Test
    fun `1_3 - vacation hours with a payslip correction, permits and planned days`() {
        val data = backup("1.3.1")
        val balance = VacationCalculator.balance(data, d("2026-11-18"))
        assertEquals(11232 + 1200, balance.granted)
        // Two vacation days (the Sunday costs nothing), a 2h permit and a whole-day permit.
        assertEquals(432 + 432 + 120 + 432, balance.used)
        assertEquals(2, balance.daysTaken)
        assertEquals(432 + 432, balance.planned)
        assertEquals(2, balance.daysPlanned)
        assertEquals(11016, balance.available)
        assertEquals(3, BankCalculator.compute(data, d("2026-11-18")).balance)
    }

    @Test
    fun `1_4 - a short Friday and badge rounding`() {
        val data = backup("1.4.0")
        val calendar = WorkCalendar.from(data)
        assertEquals(30, calendar.delta(d("2026-11-09"))) // 08:04-16:56 counted as 08:00-17:00
        assertEquals(-15, calendar.delta(d("2026-11-10"))) // 08:06-16:38 counted as 08:15-16:30
        assertEquals(0, calendar.delta(d("2026-11-13"))) // 6h Friday, no break due
        assertEquals(15, BankCalculator.compute(data, d("2026-11-18")).balance)
        assertEquals(360 + 60, VacationCalculator.balance(data, d("2026-11-18")).used)
    }

    @Test
    fun `1_5 - shifts until October, then a part-time with a 4h Friday`() {
        val data = backup("1.5.1")
        val calendar = WorkCalendar.from(data)
        assertTrue(calendar.isShiftDay(d("2026-10-30")))
        assertEquals(WorkMode.FIXED, calendar.workAt(d("2026-11-02")).mode)
        // +10 (shift), then the 6h days need no break: 09:00-15:30 is +30.
        assertEquals(40, BankCalculator.compute(data, d("2026-11-19")).balance)
        // A shift of vacation (8h), a 4h Friday and a 2h permit.
        assertEquals(480 + 240 + 120, VacationCalculator.balance(data, d("2026-11-19")).used)
    }

    @Test
    fun `1_6 - on-call duty, recovery and a timer still running`() {
        val data = backup("1.6.4")
        assertTrue(data.onCall.enabled)
        assertEquals("2026-12-10", data.support.nextAt)
        assertEquals(-45, BankCalculator.compute(data, d("2026-11-21")).balance)
        val recovery = RecoveryCalculator.compute(data, d("2026-11-21"), rome)
        // October: 1h30 + 3h (the night the clocks went back); November: 1h15. 1h + 4h taken.
        assertEquals(45, recovery.balance)
        assertEquals(YearMonth.of(2026, 11), recovery.nextExpiry?.month)
        assertEquals(d("2026-11-20"), OnCall.running(data, d("2026-11-21"))?.first)
        assertEquals(OnCallMonth(days = 2, interventions = 2, real = 260, credited = 270, used = 0), OnCall.month(data, YearMonth.of(2026, 10), rome))
        assertEquals(OnCallMonth(days = 3, interventions = 1, real = 75, credited = 75, used = 300), OnCall.month(data, YearMonth.of(2026, 11), rome))
    }

    /** Every value of [old] is in [new], at the same place. */
    private fun assertContained(version: String, path: String, old: JsonElement, new: JsonElement?, ignored: Set<String>) {
        if (path in ignored) return
        when (old) {
            is JsonObject -> {
                assertTrue("$version: $path is no longer an object", new is JsonObject)
                for ((key, value) in old) assertContained(version, "$path/$key", value, (new as JsonObject)[key], ignored)
            }
            is JsonArray -> {
                assertTrue("$version: $path is no longer a list", new is JsonArray)
                assertEquals("$version: $path size", old.size, (new as JsonArray).size)
                old.forEachIndexed { i, value -> assertContained(version, "$path/$i", value, new[i], ignored) }
            }
            is JsonPrimitive -> assertEquals("$version: $path", old, new)
        }
    }
}
