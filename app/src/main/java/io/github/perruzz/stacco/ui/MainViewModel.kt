package io.github.perruzz.stacco.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.StaccoApp
import io.github.perruzz.stacco.data.Backup
import io.github.perruzz.stacco.data.StaccoRepository
import io.github.perruzz.stacco.data.day
import io.github.perruzz.stacco.domain.BankLedger
import io.github.perruzz.stacco.domain.DayPhase
import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.Intervention
import io.github.perruzz.stacco.domain.OnCall
import io.github.perruzz.stacco.domain.RecoveryLedger
import io.github.perruzz.stacco.domain.SupportRules
import io.github.perruzz.stacco.domain.Schedule
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.domain.TodaySnapshot
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.notify.AppSync
import io.github.perruzz.stacco.notify.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

sealed interface UiEvent {
    /** A stamp was recorded; [previous] holds the stamps as they were, for undo. */
    data class Stamped(val slot: StampSlot, val minutes: Int, val date: LocalDate, val previous: DayRecord) : UiEvent
    data class DayCompleted(val delta: Int, val worked: Int) : UiEvent
    data class Info(val message: Int) : UiEvent

    /** A type was written on several days at once; [previous] restores them. */
    data class DaysMarked(val type: DayType?, val count: Int, val previous: Map<LocalDate, DayRecord?>, val vacationLeft: Int?) : UiEvent

    /** Several days marked (or unmarked) as on call; [previous] restores them. */
    data class OnCallMarked(val on: Boolean, val count: Int, val previous: Map<LocalDate, DayRecord?>) : UiEvent

    /** An intervention timer was stopped; intervention [id] of [date] can run again (null: no undo). */
    data class InterventionStopped(val real: Int, val credited: Int, val date: LocalDate, val id: Long?) : UiEvent
}

private data class Core(val data: StaccoData, val ledger: BankLedger, val recovery: RecoveryLedger)

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

    /** Today's date, kept current across midnight. */
    val currentDate: StateFlow<LocalDate> = date.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now())

    private val core: Flow<Core> = combine(repo.data, date) { d, day -> Core(d, Engine.ledger(d, day), Engine.recovery(d, day)) }
        .flowOn(Dispatchers.Default)

    val ledger: StateFlow<BankLedger> = core.map { it.ledger }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BankLedger.EMPTY)

    /** The on-call recovery account (empty while on-call duty is off). */
    val recovery: StateFlow<RecoveryLedger> = core.map { it.recovery }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecoveryLedger.EMPTY)

    val today: StateFlow<TodaySnapshot?> = combine(core, clock) { c, now -> Engine.today(c.data, c.ledger, now, c.recovery) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether today's working day is over: changes a few times a day, unlike [today]. */
    val todayDone: StateFlow<Boolean> = today.map { it?.projection?.phase == DayPhase.DONE }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
        val result = WorkCalendar.from(data).result(date)
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

    /** Undo of a stamp: only the stamps go back, an intervention started meanwhile stays. */
    fun undoStamp(date: LocalDate, previous: DayRecord) {
        viewModelScope.launch { repo.undoStamp(date, previous) }
    }

    fun setDayType(date: LocalDate, type: DayType?) {
        viewModelScope.launch { repo.setDayType(date, type) }
    }

    fun clearDay(date: LocalDate) {
        viewModelScope.launch { repo.clearDay(date) }
    }

    /** Marks many days at once (days off are skipped, see [WorkCalendar.daysToMark]). */
    fun setDayTypes(dates: Collection<LocalDate>, type: DayType?) {
        viewModelScope.launch {
            val targets = WorkCalendar.from(repo.current()).daysToMark(dates, type)
            if (targets.isEmpty()) return@launch
            val previous = repo.setDayTypes(targets, type)
            val after = repo.current()
            val vacationLeft = if (after.vacation.tracked) VacationCalculator.balance(after, LocalDate.now()).remaining else null
            _events.emit(UiEvent.DaysMarked(type, targets.size, previous, vacationLeft))
        }
    }

    fun restoreDays(previous: Map<LocalDate, DayRecord?>) {
        viewModelScope.launch { repo.restoreDays(previous) }
    }

    private val _pendingSchedule = MutableStateFlow<Schedule?>(null)

    /** Schedule edits waiting for the user to say from when they apply. */
    val pendingSchedule: StateFlow<Schedule?> = _pendingSchedule

    /** Edits the draft of the schedule (hours, weekdays, mode); nothing is saved yet. */
    fun editSchedule(transform: (Schedule) -> Schedule) {
        val current = data.value?.work?.schedule ?: return
        val next = transform(_pendingSchedule.value ?: current)
        _pendingSchedule.value = next.takeIf { it != current }
    }

    fun discardSchedule() {
        _pendingSchedule.value = null
    }

    /** Saves the draft, valid from [from] (null: every date, the past included). */
    fun applySchedule(from: LocalDate?) {
        val next = _pendingSchedule.value ?: return
        _pendingSchedule.value = null
        viewModelScope.launch {
            repo.changeSchedule(next, from)
            _events.emit(UiEvent.Info(R.string.schedule_applied))
        }
    }

    /** The current schedule for every date: past days are recounted with it. */
    fun clearScheduleHistory() {
        viewModelScope.launch { repo.update { it.copy(scheduleHistory = emptyList()) } }
    }

    fun setVacationAvailable(target: Int) {
        viewModelScope.launch { repo.setVacationAvailable(target, LocalDate.now()) }
    }

    fun setLeave(date: LocalDate, minutes: Int) {
        viewModelScope.launch { repo.updateDay(date) { it.copy(leaveMinutes = minutes.coerceAtLeast(0)) } }
    }

    // --- On-call duty ---------------------------------------------------------------------------

    /** Off, a timer still running is stopped (it would go on unseen): the toast says so, without undo. */
    fun setOnCallEnabled(on: Boolean) {
        viewModelScope.launch {
            val change = repo.setOnCallEnabled(on, LocalDateTime.now()) ?: return@launch
            emitStopped(change, undo = false)
        }
    }

    fun setOnCall(date: LocalDate, on: Boolean) {
        viewModelScope.launch { repo.updateDay(date) { it.copy(onCall = on) } }
    }

    /** On call on many days at once: unlike day types, weekends and holidays are included. */
    fun setOnCallDays(dates: Collection<LocalDate>, on: Boolean) {
        if (dates.isEmpty()) return
        viewModelScope.launch {
            val previous = repo.setOnCallDays(dates, on)
            _events.emit(UiEvent.OnCallMarked(on, dates.size, previous))
        }
    }

    fun startIntervention() {
        viewModelScope.launch { repo.startIntervention(LocalDateTime.now()) }
    }

    fun stopIntervention() {
        viewModelScope.launch {
            val change = repo.stopIntervention(LocalDateTime.now()) ?: return@launch
            emitStopped(change, undo = true)
        }
    }

    private suspend fun emitStopped(change: StaccoRepository.InterventionChange, undo: Boolean) {
        val real = OnCall.realMinutes(change.date, change.intervention) ?: 0
        _events.emit(UiEvent.InterventionStopped(real, OnCall.credited(real), change.date, change.intervention.id.takeIf { undo }))
    }

    /** Undo of a stop: the timer runs again from its start (the stamps of the day are not touched). */
    fun resumeIntervention(date: LocalDate, id: Long) {
        viewModelScope.launch { repo.resumeIntervention(date, id, LocalDateTime.now()) }
    }

    fun saveIntervention(date: LocalDate, intervention: Intervention) {
        viewModelScope.launch { repo.saveIntervention(date, intervention) }
    }

    /** Deletes an intervention (the sheet asks first: a toast would be hidden under it). */
    fun deleteIntervention(date: LocalDate, id: Long) {
        viewModelScope.launch { repo.deleteIntervention(date, id) }
    }

    fun setRecovery(date: LocalDate, minutes: Int) {
        viewModelScope.launch { repo.updateDay(date) { it.copy(recoveryMinutes = minutes.coerceAtLeast(0)) } }
    }

    // --- Buy me a coffee -----------------------------------------------------------------------

    private val _support = MutableStateFlow(false)

    /** The "buy me a coffee" card is due on this opening of the app. */
    val support: StateFlow<Boolean> = _support

    init {
        viewModelScope.launch {
            // Only when the app opens on an already set up Stacco: never during or right after onboarding.
            val current = repo.data.first()
            val today = LocalDate.now()
            if (current.onboardingDone && SupportRules.isDue(current.support, installDate(), today)) {
                _support.value = true
                repo.update { it.copy(support = SupportRules.postponed(it.support, today)) }
            }
        }
    }

    private fun installDate(): LocalDate = runCatching {
        val app = getApplication<Application>()
        val installed = app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime
        Instant.ofEpochMilli(installed).atZone(ZoneId.systemDefault()).toLocalDate()
    }.getOrDefault(LocalDate.now())

    /** "Later": the card comes back in 21–30 days. */
    fun supportLater() {
        _support.value = false
        viewModelScope.launch { repo.update { it.copy(support = SupportRules.postponed(it.support, LocalDate.now())) } }
    }

    fun supportNever() {
        _support.value = false
        viewModelScope.launch { repo.update { it.copy(support = it.support.copy(dismissed = true)) } }
    }

    /** The link was opened: the card goes away until its next turn. */
    fun supportOpened() {
        _support.value = false
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

    /**
     * Brings notifications, alarms, widget and tile in line right away, when something they
     * depend on changed outside the data: notifications just allowed, a new language (which
     * also renames the notification categories in the system settings).
     */
    fun refreshOutside(languageChanged: Boolean = false) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            if (languageChanged) Notifications.createChannels(app)
            AppSync.sync(app, early = false)
        }
    }

    suspend fun exportJson(): String = Backup.encode(repo.current())

    suspend fun importJson(text: String): Boolean {
        val parsed = Backup.decode(text) ?: return false
        repo.replaceAll(parsed)
        // A backup saying "don't show again" hides the coffee card at once.
        if (repo.current().support.dismissed) _support.value = false
        return true
    }
}
