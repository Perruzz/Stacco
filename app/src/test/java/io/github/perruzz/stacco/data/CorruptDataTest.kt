package io.github.perruzz.stacco.data

import androidx.datastore.core.CorruptionException
import io.github.perruzz.stacco.domain.AdjustmentKind
import io.github.perruzz.stacco.domain.BankAdjustment
import io.github.perruzz.stacco.domain.BankCalculator
import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.Intervention
import io.github.perruzz.stacco.domain.OnCallSettings
import io.github.perruzz.stacco.domain.RecoveryCalculator
import io.github.perruzz.stacco.domain.RoundingSettings
import io.github.perruzz.stacco.domain.Schedule
import io.github.perruzz.stacco.domain.SchedulePeriod
import io.github.perruzz.stacco.domain.ScheduleHistory
import io.github.perruzz.stacco.domain.ShiftTemplate
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.SupportPrompt
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.VacationSettings
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.domain.WorkMode
import io.github.perruzz.stacco.domain.WorkSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime

/** Backups and data files that are cut, edited by hand or damaged never wipe, freeze or crash the app. */
class CorruptDataTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun t(h: Int, m: Int = 0) = h * 60 + m
    private fun fixture(version: String) = javaClass.getResource("/backups/stacco-$version.json")!!.readText()

    private val sample = StaccoData(
        onboardingDone = true,
        onCall = OnCallSettings(enabled = true),
        days = mapOf(
            "2026-11-16" to DayRecord(entry = t(8), breakOut = t(12), breakIn = t(12, 30), exit = t(17)),
            "2026-11-17" to DayRecord(type = DayType.VACATION, note = "Ferie"),
            "2026-11-18" to DayRecord(onCall = true, interventions = listOf(Intervention(1, t(21), t(22)))),
        ),
        adjustments = listOf(BankAdjustment(1, "2026-11-16", AdjustmentKind.SET_BALANCE, 90)),
    )

    private fun store(text: String, damaged: (ByteArray) -> Unit = {}): StaccoData =
        runBlocking { StaccoDataSerializer(damaged).readFrom(text.byteInputStream()) }

    // --- Backups ---------------------------------------------------------------------------------

    @Test
    fun `a backup cut anywhere is refused, never half imported`() {
        for (version in listOf("1.0.0", "1.3.1", "1.6.4")) {
            val text = fixture(version).trimEnd()
            for (cut in 0 until text.length) {
                val decoded = try {
                    Backup.decode(text.substring(0, cut))
                } catch (e: Throwable) {
                    fail("$version cut at $cut threw $e")
                }
                assertNull("$version cut at $cut", decoded)
            }
            assertNotNull(Backup.decode(text))
        }
    }

    @Test
    fun `a backup saved again by an editor still imports`() {
        val text = Backup.encode(sample)
        assertEquals(sample, Backup.decode("\uFEFF" + text))
        assertEquals(sample, Backup.decode(text.replace("\n", "\r\n")))
        // Read from the file as it is: the mark is three bytes in front of the text.
        val withMark = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.encodeToByteArray()
        assertEquals(sample, Backup.decode(Backup.read(ByteArrayInputStream(withMark))!!))
    }

    @Test
    fun `a backup with values this version does not know is refused, the data stays`() {
        val text = Backup.encode(sample)
        assertNull(Backup.decode(text.replace("\"VACATION\"", "\"TRAINING\"")))
        assertNull(Backup.decode(text.replace("\"dailyMinutes\": 480", "\"dailyMinutes\": null")))
        assertNull(Backup.decode(text.replace("\"dailyMinutes\": 480", "\"dailyMinutes\": 99999999999")))
        assertNull(Backup.decode(text.replace("\"entry\": 480", "\"entry\": \"otto\"")))
    }

    @Test
    fun `a file far too large to be a backup is not even read`() {
        val huge = object : InputStream() {
            var left = Backup.MAX_BYTES + 1L
            override fun read(): Int = if (left-- > 0) ' '.code else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = minOf(len.toLong(), left).toInt()
                b.fill(' '.code.toByte(), off, off + n)
                left -= n
                return n
            }
        }
        assertNull(Backup.read(huge))
        assertEquals(Backup.encode(sample), Backup.read(Backup.encode(sample).byteInputStream()))
    }

    // --- Impossible values -----------------------------------------------------------------------

    @Test
    fun `negative hours and impossible stamps are brought back in range`() {
        val messy = StaccoData(
            work = WorkSettings(
                dailyMinutes = -60,
                minDailyMinutes = -30,
                minBreakMinutes = -30,
                workDays = setOf(0, 1, 2, 3, 4, 5, 8),
                dailyByDay = mapOf(5 to -120, 9 to 300),
                shifts = listOf(ShiftTemplate("n", "Notte", -10, 2000, breakMinutes = -30)),
                rounding = RoundingSettings(stepMinutes = -15, toleranceMinutes = -5),
            ),
            vacation = VacationSettings(annualMinutes = -600, adjustments = mapOf("2026" to 99_999_999, "-2000000000" to 60, "dopo" to 1)),
            days = mapOf(
                "2026-11-16" to DayRecord(entry = -30, breakOut = t(12), breakIn = t(12, 30), exit = 99_999, leaveMinutes = -60, recoveryMinutes = 5000),
                "2026-11-17" to DayRecord(entry = t(8), exit = t(17), onCall = true, interventions = listOf(Intervention(3, t(20), Int.MAX_VALUE, startSecond = 99), Intervention(4, -50, t(1)))),
            ),
        )
        val clean = Sanitizer.clean(messy)
        with(clean.work) {
            assertEquals(0, dailyMinutes)
            assertEquals(0, minDailyMinutes)
            assertEquals(0, minBreakMinutes)
            assertEquals(setOf(1, 2, 3, 4, 5), workDays)
            assertEquals(mapOf(5 to 0), dailyByDay)
            assertEquals(ShiftTemplate("n", "Notte", 0, t(23, 59), breakMinutes = 0), shifts.single())
            assertEquals(RoundingSettings(stepMinutes = 0, toleranceMinutes = 0), rounding)
        }
        assertEquals(0, clean.vacation.annualMinutes)
        assertEquals(setOf("2026"), clean.vacation.adjustments.keys)
        assertEquals(DayRecord(breakOut = t(12), breakIn = t(12, 30), recoveryMinutes = t(24)), clean.days["2026-11-16"])
        val night = clean.days.getValue("2026-11-17").interventions.single()
        assertEquals(t(20) + 10 * 1440, night.end)
        assertEquals(59, night.startSecond)
    }

    @Test(timeout = 10_000)
    fun `dates far outside any calendar are dropped instead of freezing or crashing the app`() {
        val far = listOf("+999999999-12-31", "-999999999-01-01", "0001-01-01", "9999-12-31", "2026-02-30", "2026-11-16T08:00")
        val text = Backup.encode(
            StaccoData(
                onCall = OnCallSettings(enabled = true),
                days = far.associateWith { DayRecord(entry = t(8), exit = t(17) + 1440, interventions = listOf(Intervention(1, t(1), t(2)))) } +
                    ("2026-11-16" to DayRecord(entry = t(8), exit = t(17))),
                adjustments = far.mapIndexed { i, date -> BankAdjustment(i.toLong(), date, AdjustmentKind.MANUAL, 60) },
                vacation = VacationSettings(annualMinutes = 600, adjustments = mapOf("-2000000000" to 10, "999999999" to 10)),
                scheduleHistory = listOf(SchedulePeriod("+999999999-12-31", Schedule()), SchedulePeriod("2026-06-30", Schedule(dailyMinutes = t(6)))),
                support = SupportPrompt(nextAt = "+999999999-12-31"),
            ),
        )
        val data = Backup.decode(text)!!
        assertEquals(setOf("2026-11-16"), data.days.keys)
        assertTrue(data.adjustments.isEmpty())
        assertTrue(data.vacation.adjustments.isEmpty())
        assertEquals(listOf("2026-06-30"), data.scheduleHistory.map { it.until })
        assertNull(data.support.nextAt)
        // Everything the app computes from it still works, at once.
        assertEquals(30, BankCalculator.compute(data, d("2026-11-20")).balance)
        RecoveryCalculator.compute(data, d("2026-11-20"))
        VacationCalculator.balance(data, d("2026-11-20"))
        assertEquals(d("2026-07-01"), ScheduleHistory.currentSince(data.scheduleHistory))
        Engine.snapshot(data, LocalDateTime.of(2026, 11, 20, 12, 0))
    }

    @Test
    fun `duplicated ids get new ones, so lists, edits and deletes find one item each`() {
        val data = Sanitizer.clean(
            StaccoData(
                adjustments = listOf(
                    BankAdjustment(7, "2026-11-16", AdjustmentKind.MANUAL, 0),
                    BankAdjustment(7, "2026-11-16", AdjustmentKind.MANUAL, 0),
                    BankAdjustment(9, "2026-11-17", AdjustmentKind.MANUAL, 30),
                ),
                days = mapOf("2026-11-18" to DayRecord(onCall = true, interventions = listOf(Intervention(5, t(20), t(21)), Intervention(5, t(22), t(23))))),
            ),
        )
        assertEquals(listOf(7L, 10L, 9L), data.adjustments.map { it.id })
        assertEquals(listOf(5L, 6L), data.days.getValue("2026-11-18").interventions.map { it.id })
        // The bank list keys every movement apart.
        val keys = BankCalculator.compute(data, d("2026-11-20")).movements.map { "${it.date}-${it.kind}-${it.adjustmentId}-${it.balanceAfter}-${it.amount}" }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `real data is never touched by the clean-up`() {
        val versions = listOf("1.0.0", "1.2.1", "1.3.1", "1.4.0", "1.5.1", "1.6.4")
        for (version in versions) {
            val decoded = StaccoJson.decodeFromString(StaccoData.serializer(), fixture(version)).copy(schemaVersion = StaccoData().schemaVersion)
            assertEquals(version, decoded, Sanitizer.clean(decoded))
        }
        // Night stamps, a forgotten timer stopped a week later, a 12h permit, the widest rounding.
        val edges = StaccoData(
            work = WorkSettings(mode = WorkMode.SHIFTS, rounding = RoundingSettings(stepMinutes = 30, toleranceMinutes = 15)),
            days = mapOf(
                "2016-11-22" to DayRecord(entry = t(23, 50), exit = t(7, 59) + 1440),
                "2036-11-22" to DayRecord(type = DayType.VACATION),
                "2026-11-16" to DayRecord(entry = t(1) + 1440, exit = t(9) + 1440, leaveMinutes = t(12)),
                "2026-11-17" to DayRecord(onCall = true, interventions = listOf(Intervention(1, t(8) + 1440, t(8) + 1440 + 7 * 1440 + 30))),
            ),
            scheduleHistory = listOf(SchedulePeriod("1899-12-31", Schedule())),
            adjustments = listOf(BankAdjustment(1, "2026-11-16", AdjustmentKind.SET_BALANCE, -300 * 60)),
        )
        assertEquals(edges, Sanitizer.clean(edges))
    }

    // --- The data file ---------------------------------------------------------------------------

    @Test
    fun `the data file reads as it is when nothing is wrong`() {
        var damaged: ByteArray? = null
        assertEquals(sample, store(StaccoJson.encodeToString(StaccoData.serializer(), sample)) { damaged = it })
        assertNull(damaged)
    }

    @Test
    fun `a data file with a value this version does not know keeps everything else`() {
        var damaged: ByteArray? = null
        val text = StaccoJson.encodeToString(StaccoData.serializer(), sample)
        val broken = text.replace("\"VACATION\"", "\"TRAINING\"").replace("\"SET_BALANCE\"", "\"ANCHOR\"")
        val read = store(broken) { damaged = it }
        // The unknown day type falls back to the schedule, the unknown adjustment is dropped.
        assertEquals(sample.days.keys, read.days.keys)
        assertEquals(null, read.days.getValue("2026-11-17").type)
        assertEquals("Ferie", read.days.getValue("2026-11-17").note)
        assertTrue(read.adjustments.isEmpty())
        assertEquals(sample.days["2026-11-16"], read.days["2026-11-16"])
        assertEquals(sample.days["2026-11-18"], read.days["2026-11-18"])
        // The file as it was is kept aside.
        assertEquals(broken, damaged?.decodeToString())
    }

    @Test
    fun `a damaged day or setting is dropped alone`() {
        val text = StaccoJson.encodeToString(StaccoData.serializer(), sample)
        val read = store(text.replace("\"entry\":480", "\"entry\":\"otto\"").replace("\"allowance\":10", "\"allowance\":[]"))
        assertEquals(setOf("2026-11-17", "2026-11-18"), read.days.keys)
        assertEquals(sample.adjustments, read.adjustments)
        assertEquals(StaccoData().smart, read.smart)
        assertTrue(read.onboardingDone)
    }

    @Test
    fun `a data file with nothing left to read is corrupt, and kept aside`() {
        val text = StaccoJson.encodeToString(StaccoData.serializer(), sample)
        for (bad in listOf(text.substring(0, text.length / 2), "\u0000\u0000\u0000", "[1,2,3]")) {
            var damaged: ByteArray? = null
            try {
                store(bad) { damaged = it }
                fail("read $bad")
            } catch (e: CorruptionException) {
                assertEquals(bad, damaged?.decodeToString())
            }
        }
    }

    @Test
    fun `out of range values in the data file are cleaned on reading`() {
        val text = StaccoJson.encodeToString(StaccoData.serializer(), StaccoData(work = WorkSettings(minBreakMinutes = -30), days = mapOf("2026-11-16" to DayRecord(entry = t(8), exit = t(16)))))
        val read = store(text)
        assertEquals(0, read.work.minBreakMinutes)
        // A negative minimum break would have added half an hour to the day (8h30 worked in 8h).
        assertEquals(t(8), WorkCalendar.from(read).result(d("2026-11-16"))?.worked)
        assertEquals(0, WorkCalendar.from(read).delta(d("2026-11-16")))
    }
}
