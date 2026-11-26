package io.github.perruzz.stacco.data

import androidx.datastore.core.DataStore
import io.github.perruzz.stacco.domain.BankCalculator
import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.OnCallSettings
import io.github.perruzz.stacco.domain.Profile
import io.github.perruzz.stacco.domain.RoundingDirection
import io.github.perruzz.stacco.domain.RoundingSettings
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.VacationSettings
import io.github.perruzz.stacco.domain.WorkSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** What the first setup leaves in the data: its settings, and the balances "as of today". */
class OnboardingRepositoryTest {

    private class MemoryStore(initial: StaccoData) : DataStore<StaccoData> {
        private val state = MutableStateFlow(initial)
        private val lock = Mutex()
        override val data: Flow<StaccoData> = state
        override suspend fun updateData(transform: suspend (t: StaccoData) -> StaccoData): StaccoData =
            lock.withLock { transform(state.value).also { state.value = it } }
    }

    private val today = LocalDate.of(2026, 11, 25)

    private val answers = StaccoData(
        profile = Profile(name = "Giulia"),
        work = WorkSettings(
            dailyMinutes = 7 * 60 + 12,
            minDailyMinutes = 6 * 60,
            workDays = setOf(1, 2, 3, 4),
            dailyByDay = mapOf(4 to 5 * 60),
            patronDay = 1207,
            rounding = RoundingSettings(stepMinutes = 15, entry = RoundingDirection.UP, exit = RoundingDirection.DOWN, toleranceMinutes = 3),
        ),
        vacation = VacationSettings(annualMinutes = 26 * 8 * 60, carryOver = false),
        onCall = OnCallSettings(enabled = true),
    )

    @Test
    fun `the setup keeps every answer, the balance and the vacation left today`() = runTest {
        val repo = StaccoRepository(MemoryStore(StaccoData()))
        repo.completeOnboarding(answers, balance = 150, vacationLeft = 40 * 60, today = today)
        val saved = repo.current()
        assertTrue(saved.onboardingDone)
        assertEquals(answers.work, saved.work)
        assertEquals("Giulia", saved.profile.name)
        assertTrue(saved.onCall.enabled)
        assertFalse(saved.vacation.carryOver)
        assertEquals(26 * 8 * 60, saved.vacation.annualMinutes)
        // Both balances read today what was typed in.
        assertEquals(150, BankCalculator.compute(saved, today).balance)
        assertEquals(40 * 60, VacationCalculator.balance(saved, today).available)
        // And the year after, the whole yearly allowance comes (no carry-over asked).
        assertEquals(26 * 8 * 60, VacationCalculator.balance(saved, today.plusYears(1)).available)
    }

    @Test
    fun `skipping the numbers starts the bank from zero and counts no vacation`() = runTest {
        val repo = StaccoRepository(MemoryStore(StaccoData()))
        repo.completeOnboarding(StaccoData(), balance = 0, vacationLeft = null, today = today)
        val saved = repo.current()
        assertTrue(saved.onboardingDone)
        assertEquals(0, BankCalculator.compute(saved, today).balance)
        assertFalse(saved.vacation.tracked)
        assertFalse(saved.onCall.enabled)
    }

    @Test
    fun `vacation left equal to the yearly hours needs no correction`() = runTest {
        val repo = StaccoRepository(MemoryStore(StaccoData()))
        repo.completeOnboarding(answers, balance = 0, vacationLeft = answers.vacation.annualMinutes, today = today)
        assertTrue(repo.current().vacation.adjustments.isEmpty())
        assertEquals(answers.vacation.annualMinutes, VacationCalculator.balance(repo.current(), today).available)
    }

    @Test
    fun `days already there stay`() = runTest {
        // A backup restored halfway, or days stamped from the widget before the setup was over.
        val day = DayRecord(entry = 480, exit = 1000)
        val repo = StaccoRepository(MemoryStore(StaccoData(days = mapOf(today.toString() to day))))
        repo.completeOnboarding(answers, balance = 0, vacationLeft = null, today = today)
        assertEquals(day, repo.current().days[today.toString()])
    }
}
