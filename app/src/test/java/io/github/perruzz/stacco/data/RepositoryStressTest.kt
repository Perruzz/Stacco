package io.github.perruzz.stacco.data

import androidx.datastore.core.DataStore
import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.Schedule
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.domain.WorkMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Contract changes in the past and in the future, and stamps after midnight, through the repository. */
class RepositoryStressTest {

    private class MemoryStore(initial: StaccoData) : DataStore<StaccoData> {
        val state = MutableStateFlow(initial)
        override val data: Flow<StaccoData> = state
        override suspend fun updateData(transform: suspend (t: StaccoData) -> StaccoData): StaccoData =
            transform(state.value).also { state.value = it }
    }

    private fun d(s: String) = LocalDate.parse(s)
    private fun t(h: Int, m: Int = 0) = h * 60 + m
    private val h = 60
    private val today = d("2026-11-22")
    private val dates = generateSequence(d("2026-01-01")) { it.plusDays(1) }.takeWhile { it.year < 2028 }.toList()

    private fun schedules(data: StaccoData): Map<LocalDate, Schedule> {
        val calendar = WorkCalendar.from(data)
        return dates.associateWith { calendar.workAt(it).schedule }
    }

    private fun costs(data: StaccoData): Map<LocalDate, Int> {
        val calendar = WorkCalendar.from(data)
        return data.parsedDates().associateWith { VacationCalculator.cost(calendar, it) }
    }

    private fun StaccoData.parsedDates() = days.keys.map(LocalDate::parse)

    @Test
    fun `contract changes never rewrite the days before them`() = runTest {
        val changes = listOf(
            Schedule(dailyMinutes = 6 * h, minDailyMinutes = 5 * h) to d("2027-01-01"), // planned for next year
            Schedule(dailyMinutes = t(7, 12), minDailyMinutes = t(6, 12)) to d("2026-11-16"), // back to the middle of this month
            Schedule(mode = WorkMode.SHIFTS) to d("2026-12-01"), // planned before the first one
            Schedule(dailyByDay = mapOf(5 to 4 * h)) to d("2026-06-15"), // well into the past
            Schedule(dailyMinutes = 6 * h, minDailyMinutes = 5 * h) to d("2027-03-01"),
            Schedule() to null, // the same contract for every date
        )
        for (start in listOf(
            StaccoData(onboardingDone = true), // a fresh install: nothing recorded yet
            StaccoData(onboardingDone = true, days = mapOf("2026-12-28" to DayRecord(type = DayType.VACATION))), // only plans
            StaccoData(
                onboardingDone = true,
                days = mapOf(
                    "2026-03-02" to DayRecord(type = DayType.VACATION),
                    "2026-06-19" to DayRecord(leaveMinutes = 2 * h),
                    "2026-11-17" to DayRecord(entry = t(8), exit = t(17)),
                    "2026-12-04" to DayRecord(type = DayType.VACATION),
                ),
            ),
        )) {
            val store = MemoryStore(start)
            val repo = StaccoRepository(store)
            val recorded = start.parsedDates().toSet()
            for ((next, from) in changes) {
                val before = schedules(store.state.value)
                val costsBefore = costs(store.state.value)
                repo.changeSchedule(next, from, today)
                val after = schedules(store.state.value)
                for (date in dates) {
                    // Before the change, the days recorded and the days still to come keep their rules
                    // (an empty past day may follow the new contract: there is nothing on it to count).
                    val expected = when {
                        from == null || !date.isBefore(from) -> next
                        date in recorded || !date.isBefore(today) -> before.getValue(date)
                        else -> continue
                    }
                    assertEquals("$from: $date", expected, after.getValue(date))
                }
                for ((date, cost) in costs(store.state.value)) if (from != null && date.isBefore(from)) assertEquals("$from: $date", costsBefore[date], cost)
            }
            assertTrue(store.state.value.scheduleHistory.isEmpty())
        }
    }

    @Test
    fun `a change planned on a fresh install starts on its day, not today`() = runTest {
        val store = MemoryStore(StaccoData(onboardingDone = true))
        StaccoRepository(store).changeSchedule(Schedule(dailyMinutes = 4 * h, minDailyMinutes = 4 * h), d("2026-12-01"), today)
        val calendar = WorkCalendar.from(store.state.value)
        assertEquals(8 * h, calendar.workAt(today).dailyMinutes)
        assertEquals(8 * h, calendar.workAt(d("2026-11-30")).dailyMinutes)
        assertEquals(4 * h, calendar.workAt(d("2026-12-01")).dailyMinutes)
        // From today on with nothing recorded before: no history needed.
        val fresh = MemoryStore(StaccoData(onboardingDone = true))
        StaccoRepository(fresh).changeSchedule(Schedule(dailyMinutes = 4 * h), today, today)
        assertTrue(fresh.state.value.scheduleHistory.isEmpty())
    }

    @Test
    fun `the exit of an evening that runs past midnight closes that evening`() = runTest {
        val evening = DayRecord(entry = t(14), breakOut = t(18), breakIn = t(18, 30))
        val store = MemoryStore(StaccoData(onboardingDone = true, days = mapOf("2026-11-16" to evening)))
        val stamp = StaccoRepository(store).stampAt(LocalDateTime.of(2026, 11, 17, 0, 40))!!
        assertEquals(d("2026-11-16"), stamp.date)
        assertEquals(StampSlot.EXIT, stamp.slot)
        assertEquals(evening.copy(exit = t(0, 40) + 1440), store.state.value.days["2026-11-16"])
        assertEquals(null, store.state.value.days["2026-11-17"])
        // 10h40 of presence, 30 minutes of break: two hours and ten minutes of overtime.
        assertEquals(2 * h + 10, WorkCalendar.from(store.state.value).delta(d("2026-11-16")))
    }
}
