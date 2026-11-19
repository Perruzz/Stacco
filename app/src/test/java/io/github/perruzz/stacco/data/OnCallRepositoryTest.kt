package io.github.perruzz.stacco.data

import androidx.datastore.core.DataStore
import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.Intervention
import io.github.perruzz.stacco.domain.OnCallSettings
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.domain.SupportPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** How the intervention timer and the working-day stamps share a day, and what a backup restores. */
class OnCallRepositoryTest {

    private class MemoryStore(initial: StaccoData) : DataStore<StaccoData> {
        private val state = MutableStateFlow(initial)
        private val lock = Mutex()
        override val data: Flow<StaccoData> = state
        override suspend fun updateData(transform: suspend (t: StaccoData) -> StaccoData): StaccoData =
            lock.withLock { transform(state.value).also { state.value = it } }
    }

    private fun repo(data: StaccoData) = StaccoRepository(MemoryStore(data))
    private fun at(day: Int, h: Int, m: Int, s: Int = 0) = LocalDateTime.of(2026, 11, day, h, m, s)
    private val onCall = StaccoData(onboardingDone = true, onCall = OnCallSettings(enabled = true))

    @Test
    fun `undoing a stamp keeps an intervention started meanwhile`() = runTest {
        // Saturday on call: the badge stamped by mistake, then the intervention started, then the stamp undone.
        val saturday = LocalDate.of(2026, 11, 21)
        val repo = repo(onCall.copy(days = mapOf("2026-11-21" to DayRecord(onCall = true))))
        val stamp = repo.stampAt(at(21, 9, 0))!!
        assertEquals(StampSlot.ENTRY, stamp.slot)
        assertNotNull(repo.startIntervention(at(21, 9, 0, 20)))
        repo.undoStamp(stamp.date, stamp.previous)
        val day = repo.current().day(saturday)
        assertNull(day.entry)
        assertTrue(day.onCall)
        assertEquals(1, day.interventions.size)
        assertNull(day.interventions.single().end) // still running

        // On a smart working day the stamp made it an office day: undone, it is smart again.
        val smart = repo(onCall.copy(days = mapOf("2026-11-18" to DayRecord(type = DayType.SMART))))
        val s = smart.stampAt(at(18, 8, 30))!!
        assertEquals(DayType.OFFICE, smart.current().day(s.date).type)
        smart.undoStamp(s.date, s.previous)
        assertEquals(DayRecord(type = DayType.SMART), smart.current().day(s.date))
    }

    @Test
    fun `undoing a stop resumes the intervention and leaves the stamps alone`() = runTest {
        val wednesday = LocalDate.of(2026, 11, 18)
        val repo = repo(onCall.copy(days = mapOf("2026-11-18" to DayRecord(entry = 8 * 60, onCall = true))))
        repo.startIntervention(at(18, 10, 0))
        val stopped = repo.stopIntervention(at(18, 10, 25))!!
        assertEquals(10 * 60 + 25, stopped.intervention.end)
        // Meanwhile the exit is stamped from the notification.
        repo.stampAt(at(18, 10, 26), StampSlot.EXIT)
        repo.resumeIntervention(stopped.date, stopped.intervention.id, at(18, 10, 27))
        val day = repo.current().day(wednesday)
        assertEquals(8 * 60, day.entry)
        assertEquals(10 * 60 + 26, day.exit)
        assertNull(day.interventions.single().end)

        // Not if another timer was started in the meantime: never two at once.
        val other = repo(onCall.copy(days = mapOf("2026-11-18" to DayRecord(onCall = true))))
        other.startIntervention(at(18, 10, 0), id = 1)
        val first = other.stopIntervention(at(18, 10, 20))!!
        other.startIntervention(at(18, 10, 21), id = 2)
        other.resumeIntervention(first.date, first.intervention.id, at(18, 10, 22))
        assertEquals(listOf(10 * 60 + 20, null), other.current().day(wednesday).interventions.map { it.end })
    }

    @Test
    fun `turning on-call duty off stops a running timer instead of leaving it unseen`() = runTest {
        val repo = repo(onCall)
        repo.startIntervention(at(30, 23, 45))
        val change = repo.setOnCallEnabled(false, LocalDateTime.of(2026, 12, 1, 0, 10))
        assertNotNull(change)
        val data = repo.current()
        assertFalse(data.onCall.enabled)
        assertEquals(LocalDate.of(2026, 11, 30), change!!.date)
        assertEquals(1440 + 10, data.day(LocalDate.of(2026, 11, 30)).interventions.single().end)
        // Back on: everything is where it was, nothing else changes.
        assertNull(repo.setOnCallEnabled(true, LocalDateTime.of(2026, 12, 2, 9, 0)))
        assertTrue(repo.current().onCall.enabled)
        assertEquals(data.days, repo.current().days)
        // Off with nothing running: just off.
        assertNull(repo.setOnCallEnabled(false, LocalDateTime.of(2026, 12, 2, 9, 1)))
    }

    @Test
    fun `restoring a backup keeps don't show again for the coffee card`() = runTest {
        val backup = onCall.copy(
            days = mapOf("2026-11-17" to DayRecord(onCall = true, interventions = listOf(Intervention(3, 1440 + 130, 1440 + 168)))),
            support = SupportPrompt(nextAt = "2026-10-01"),
        )
        // "Don't show again" pressed on this phone, then an older backup restored.
        val here = repo(StaccoData(onboardingDone = true, support = SupportPrompt(nextAt = "2026-12-12", dismissed = true)))
        here.replaceAll(Backup.decode(Backup.encode(backup))!!)
        val restored = here.current()
        assertEquals(backup.days, restored.days)
        assertTrue(restored.onCall.enabled)
        assertEquals(SupportPrompt(nextAt = "2026-12-12", dismissed = true), restored.support)

        // A new phone (onboarding just done) takes the backup's choice as it is.
        val fresh = repo(StaccoData(onboardingDone = true))
        fresh.replaceAll(Backup.decode(Backup.encode(backup.copy(support = SupportPrompt(nextAt = "2026-12-20", dismissed = true))))!!)
        assertEquals(SupportPrompt(nextAt = "2026-12-20", dismissed = true), fresh.current().support)
        assertTrue(fresh.current().onboardingDone)
    }
}
