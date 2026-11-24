package io.github.perruzz.stacco.data

import androidx.datastore.core.DataStore
import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.ShiftTemplate
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.domain.WorkMode
import io.github.perruzz.stacco.domain.WorkSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Stamps from a notification button, the widget or the Quick Settings tile. */
class QuickStampTest {

    private class MemoryStore(initial: StaccoData) : DataStore<StaccoData> {
        private val state = MutableStateFlow(initial)
        private val lock = Mutex()
        override val data: Flow<StaccoData> = state
        override suspend fun updateData(transform: suspend (t: StaccoData) -> StaccoData): StaccoData =
            lock.withLock { transform(state.value).also { state.value = it } }
    }

    private val monday = LocalDate.of(2026, 11, 23)
    private val tuesday = monday.plusDays(1)
    private fun at(date: LocalDate, h: Int, m: Int) = date.atTime(h, m)
    private fun repo(vararg days: Pair<LocalDate, DayRecord>, work: WorkSettings = WorkSettings()) = StaccoRepository(
        MemoryStore(StaccoData(onboardingDone = true, work = work, days = days.associate { (d, r) -> d.toString() to r })),
    )

    @Test
    fun `a button left from before the exit was stamped does not overwrite it`() = runTest {
        val repo = repo(monday to DayRecord(entry = 480, breakOut = 750, breakIn = 780, exit = 1020))
        assertNull(repo.quickStamp(at(monday, 18, 30), StampSlot.EXIT, monday, elapsedMillis = 0))
        assertEquals(1020, repo.current().day(monday).exit)
    }

    @Test
    fun `yesterday's reminder does not stamp today`() = runTest {
        val repo = repo(monday to DayRecord(entry = 480, exit = 1020), tuesday to DayRecord(entry = 500))
        assertNull(repo.quickStamp(at(tuesday, 16, 0), StampSlot.EXIT, monday, elapsedMillis = 0))
        assertNull(repo.current().day(tuesday).exit)
        // The same button of today's notification does.
        assertEquals(StampSlot.EXIT, repo.quickStamp(at(tuesday, 16, 0), StampSlot.EXIT, tuesday, elapsedMillis = 0)?.slot)
    }

    @Test
    fun `no exit on a day not started, nor while on break`() = runTest {
        val repo = repo(tuesday to DayRecord(entry = 480, breakOut = 750))
        assertNull(repo.quickStamp(at(monday, 16, 0), StampSlot.EXIT, monday, elapsedMillis = 0))
        assertNull(repo.current().days[monday.toString()])
        assertNull(repo.quickStamp(at(tuesday, 13, 0), StampSlot.EXIT, tuesday, elapsedMillis = 0))
        assertEquals(StampSlot.BREAK_IN, repo.quickStamp(at(tuesday, 13, 0), StampSlot.BREAK_IN, tuesday, elapsedMillis = 0)?.slot)
        assertEquals(780, repo.current().day(tuesday).breakIn)
    }

    @Test
    fun `the exit without a break is still allowed`() = runTest {
        val repo = repo(tuesday to DayRecord(entry = 480))
        assertEquals(StampSlot.EXIT, repo.quickStamp(at(tuesday, 15, 0), StampSlot.EXIT, tuesday, elapsedMillis = 0)?.slot)
        assertEquals(900, repo.current().day(tuesday).exit)
    }

    @Test
    fun `a double tap stamps once`() = runTest {
        val repo = repo()
        assertEquals(StampSlot.ENTRY, repo.quickStamp(at(tuesday, 8, 0), elapsedMillis = 1_000)?.slot)
        assertNull(repo.quickStamp(at(tuesday, 8, 0), elapsedMillis = 1_600))
        assertNull(repo.quickStamp(at(tuesday, 8, 0), StampSlot.BREAK_OUT, tuesday, elapsedMillis = 4_000))
        assertEquals(DayRecord(entry = 480), repo.current().day(tuesday))
        // A real second stamp, later on.
        assertEquals(StampSlot.BREAK_OUT, repo.quickStamp(at(tuesday, 12, 30), elapsedMillis = 1_000 + StaccoRepository.QUICK_STAMP_GAP_MS)?.slot)
    }

    @Test
    fun `the widget and the tile do not stamp a day away from the office`() = runTest {
        val repo = repo(monday to DayRecord(type = DayType.SMART), tuesday to DayRecord(type = DayType.VACATION))
        assertNull(repo.quickStamp(at(monday, 9, 0), elapsedMillis = 0))
        assertNull(repo.quickStamp(at(tuesday, 9, 0), elapsedMillis = 10_000))
        assertEquals(DayRecord(type = DayType.SMART), repo.current().day(monday))
        assertEquals(DayRecord(type = DayType.VACATION), repo.current().day(tuesday))
        // A Saturday is a day off, and some overtime can be stamped on it.
        val saturday = LocalDate.of(2026, 11, 28)
        assertEquals(StampSlot.ENTRY, repo.quickStamp(at(saturday, 9, 0), elapsedMillis = 20_000)?.slot)
    }

    @Test
    fun `the exit of a night shift is stamped after midnight on the shift's day`() = runTest {
        val night = ShiftTemplate("n", "Notte", 21 * 60, 7 * 60)
        val work = WorkSettings(mode = WorkMode.SHIFTS, shifts = listOf(night))
        val repo = repo(monday to DayRecord(shiftId = "n", entry = 21 * 60), work = work)
        val stamp = repo.quickStamp(at(tuesday, 6, 55), StampSlot.EXIT, monday, elapsedMillis = 0)
        assertEquals(monday, stamp?.date)
        assertEquals(6 * 60 + 55 + 1440, repo.current().day(monday).exit)
    }
}
