package io.github.perruzz.stacco.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.perruzz.stacco.StaccoApp
import io.github.perruzz.stacco.data.Backup
import io.github.perruzz.stacco.data.day
import io.github.perruzz.stacco.domain.BankLedger
import io.github.perruzz.stacco.domain.DayCalculator
import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.domain.TodaySnapshot
import io.github.perruzz.stacco.domain.WorkCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface UiEvent {
    data class Stamped(val slot: StampSlot, val minutes: Int, val date: LocalDate, val previous: DayRecord) : UiEvent
    data class DayCompleted(val delta: Int, val worked: Int) : UiEvent
    data class Info(val message: Int) : UiEvent
}

private data class Core(val data: StaccoData, val ledger: BankLedger)

/** One activity-scoped ViewModel: the app is small and every screen shares the same state. */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as StaccoApp).repository

    val data: StateFlow<StaccoData?> = repo.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Ticks on every second boundary. */
    private val clock: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }

    private val date: Flow<LocalDate> = clock.map { it.toLocalDate() }.distinctUntilChanged()

    private val core: Flow<Core> = combine(repo.data, date) { d, day -> Core(d, Engine.ledger(d, day)) }
        .flowOn(Dispatchers.Default)

    val ledger: StateFlow<BankLedger> = core.map { it.ledger }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BankLedger.EMPTY)

    val today: StateFlow<TodaySnapshot?> = combine(core, clock) { c, now -> Engine.today(c.data, c.ledger, now) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    fun notify(message: Int) {
        _events.tryEmit(UiEvent.Info(message))
    }

    /** Stamps now, or at [minutes] of the working day (the time picked on the dial). */
    fun stampAt(slot: StampSlot? = null, minutes: Int? = null) {
        viewModelScope.launch {
            val stamp = repo.stampAt(LocalDateTime.now(), slot, minutes) ?: return@launch
            _events.emit(UiEvent.Stamped(stamp.slot, stamp.minutes, stamp.date, stamp.previous))
            if (stamp.slot == StampSlot.EXIT) emitCompletion(stamp.date)
        }
    }

    fun stampNow(slot: StampSlot? = null) = stampAt(slot, null)

    private suspend fun emitCompletion(date: LocalDate) {
        val data = repo.current()
        val calendar = WorkCalendar.from(data)
        val record = data.day(date)
        val result = DayCalculator.result(record, calendar.breakRules(date), calendar.required(date))
        if (result != null) _events.emit(UiEvent.DayCompleted(result.delta, result.worked))
    }

    fun setStamp(date: LocalDate, slot: StampSlot, minutes: Int?) {
        viewModelScope.launch {
            val wasComplete = repo.current().day(date).exit != null
            repo.setStamp(date, slot, minutes)
            if (slot == StampSlot.EXIT && minutes != null && !wasComplete && date >= LocalDate.now().minusDays(1)) emitCompletion(date)
        }
    }

    fun setShift(date: LocalDate, shiftId: String?) {
        viewModelScope.launch { repo.setShift(date, shiftId) }
    }

    fun saveShiftTemplate(template: io.github.perruzz.stacco.domain.ShiftTemplate) {
        viewModelScope.launch { repo.saveShiftTemplate(template) }
    }

    fun deleteShiftTemplate(id: String) {
        viewModelScope.launch { repo.deleteShiftTemplate(id) }
    }

    fun restoreDay(date: LocalDate, record: DayRecord) {
        viewModelScope.launch { repo.updateDay(date) { record } }
    }

    fun setDayType(date: LocalDate, type: DayType?) {
        viewModelScope.launch { repo.setDayType(date, type) }
    }

    fun clearDay(date: LocalDate) {
        viewModelScope.launch { repo.clearDay(date) }
    }

    fun setLeave(date: LocalDate, minutes: Int) {
        viewModelScope.launch { repo.updateDay(date) { it.copy(leaveMinutes = minutes.coerceAtLeast(0)) } }
    }

    fun setNote(date: LocalDate, note: String) {
        viewModelScope.launch { repo.updateDay(date) { it.copy(note = note) } }
    }

    fun setBalance(target: Int) {
        viewModelScope.launch { repo.setBalance(target, LocalDate.now()) }
    }

    fun addAdjustment(date: LocalDate, minutes: Int, note: String) {
        viewModelScope.launch { repo.addAdjustment(date, minutes, note) }
    }

    fun deleteAdjustment(id: Long) {
        viewModelScope.launch { repo.deleteAdjustment(id) }
    }

    fun updateData(transform: (StaccoData) -> StaccoData) {
        viewModelScope.launch { repo.update(transform) }
    }

    /** Persists the onboarding answers and the starting balance. */
    fun completeOnboarding(draft: StaccoData, initialBalance: Int) {
        viewModelScope.launch {
            repo.update { draft.copy(onboardingDone = true, days = it.days, adjustments = it.adjustments) }
            repo.setBalance(initialBalance, LocalDate.now())
        }
    }

    fun reset() {
        viewModelScope.launch { repo.reset() }
    }

    suspend fun exportJson(): String = Backup.encode(repo.current())

    suspend fun importJson(text: String): Boolean {
        val parsed = Backup.decode(text) ?: return false
        repo.replaceAll(parsed)
        return true
    }
}
