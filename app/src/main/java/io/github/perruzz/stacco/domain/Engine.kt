package io.github.perruzz.stacco.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The "working day" we are living right now. Usually it is today, but during a night shift
 * that started yesterday it is still yesterday: [minutes] then goes beyond 1440.
 */
data class ActiveDay(val date: LocalDate, val minutes: Int, val seconds: Int)

/** Everything about the current working day, computed from the persisted data at a given instant. */
data class TodaySnapshot(
    /** The working day shown (yesterday during a night shift that crosses midnight). */
    val date: LocalDate,
    /** The real calendar date. */
    val calendarDate: LocalDate,
    val nowMinutes: Int,
    val nowSeconds: Int,
    val record: DayRecord,
    /** Effective type (null = rest day). */
    val type: DayType?,
    val scheduled: Boolean,
    val holiday: Holiday?,
    val shift: ShiftTemplate?,
    val expectsBreak: Boolean,
    val projection: DayProjection,
    val ledger: BankLedger,
    val alerts: List<BankAlert>,
    val smartUsed: Int,
    val data: StaccoData,
    /** On-call duty, when the module is on. */
    val onCall: OnCallState? = null,
) {
    /** The settings in force on [date] (an older contract for a day before a change). */
    val work: WorkSettings get() = WorkCalendar.from(data).workAt(date)
    val isShiftMode: Boolean get() = work.mode == WorkMode.SHIFTS
    val isSmart: Boolean get() = type == DayType.SMART
    val isTravel: Boolean get() = type == DayType.TRAVEL
    val isOffice: Boolean get() = type == DayType.OFFICE
    val isRestDay: Boolean get() = type == null
    val isAway: Boolean get() = type == DayType.VACATION || type == DayType.SICK || type == DayType.HOLIDAY
    val fromYesterday: Boolean get() = date != calendarDate

    val nextSlot: StampSlot? get() = record.nextSlot(expectsBreak)
    val alternativeSlot: StampSlot? get() = record.alternativeSlot(expectsBreak)

    /** The stamp the widget and the Quick Settings tile record: none on a day away from the office. */
    val quickStampSlot: StampSlot? get() = nextSlot.takeIf { Engine.quickStampsAllowed(type) }

    /** Exit time chosen by the user as reference for countdown and reminders. */
    val targetExit: Int? get() = projection.targetExit(data.notifications.exitTarget)

    /**
     * Seconds left to [minutes] of the working day (negative once it has passed), in real time:
     * an hour less up to a clock that goes forward in the night, an hour more up to one that goes back.
     */
    fun secondsTo(minutes: Int): Int = minutes * 60 - nowSeconds - clockShift(date, nowMinutes, minutes) * 60

    /** A recent day whose exit was never stamped (it stays out of the hour bank until fixed). */
    val missingExit: LocalDate? get() = Engine.missingExit(data, date)

    /** A recent day left out of the hour bank: exit missing or stamps that do not add up. */
    val dayToFix: Pair<LocalDate, DayFix>? get() = Engine.dayToFix(data, date)
}

enum class DayFix { MISSING_EXIT, INCONSISTENT }

/** On-call duty as Today shows it. */
data class OnCallState(
    /** The intervention whose timer is running, with its day. */
    val running: Pair<LocalDate, Intervention>?,
    /** Seconds the running timer has been going. */
    val runningSeconds: Int?,
    /** The day an intervention started now belongs to (yesterday in the night after an on-call day). */
    val dutyDate: LocalDate,
    /** On call today or last night, or a timer running: the on-call card belongs on Today. */
    val onDuty: Boolean,
    /** Finished interventions of [dutyDate], the latest first. */
    val interventions: List<Intervention>,
    val recovery: RecoveryLedger,
    val alert: RecoveryAlert?,
)

object Engine {

    /** How long after its expected end a working day still counts as "current" past midnight. */
    private const val NIGHT_GRACE_MINUTES = 4 * 60

    fun firstDayOfWeek(locale: Locale = Locale.getDefault()): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

    /**
     * Whether a stamp asked from outside the app (the widget, the tile, a notification button) may
     * go on a day of [type]: an office day or a rest day, not one of smart working, travel or leave,
     * which a stamp in the app would turn into an office day.
     */
    fun quickStampsAllowed(type: DayType?): Boolean = type == null || type == DayType.OFFICE

    /**
     * The next moment the working day shown outside the app (widget, tile) changes by itself, with
     * no stamp: midnight, or the end of yesterday's day still under way past midnight.
     */
    fun nextDayChange(data: StaccoData, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        val midnight = today.plusDays(1).atStartOfDay()
        val until = yesterdayUntil(data, today) ?: return midnight
        val minutes = now.hour * 60 + now.minute
        return if (minutes < until) minOf(today.atStartOfDay().plusMinutes(until.toLong()), midnight) else midnight
    }

    fun ledger(data: StaccoData, today: LocalDate): BankLedger = BankCalculator.compute(data, today)

    /** The on-call recovery account (empty while the module is off). */
    fun recovery(data: StaccoData, today: LocalDate): RecoveryLedger =
        if (data.onCall.enabled) RecoveryCalculator.compute(data, today) else RecoveryLedger.EMPTY

    /** How far back a forgotten exit is still worth pointing out. */
    private const val MISSING_EXIT_DAYS = 14L

    /**
     * The most recent day before [date] (two weeks) that the bank skips: an entry without exit, or
     * stamps that do not add up (e.g. a break never closed).
     */
    fun dayToFix(data: StaccoData, date: LocalDate): Pair<LocalDate, DayFix>? {
        val calendar = WorkCalendar.from(data)
        return (1L..MISSING_EXIT_DAYS).asSequence().map { date.minusDays(it) }.firstNotNullOfOrNull { day ->
            fixOf(calendar, day)?.let { day to it }
        }
    }

    /**
     * Every day before [date] that the bank leaves out until it is fixed, the most recent first,
     * however old: the Today card only points out the last two weeks.
     */
    fun daysToFix(data: StaccoData, date: LocalDate): List<Pair<LocalDate, DayFix>> {
        val calendar = WorkCalendar.from(data)
        return data.parsedDays().keys.filter { it.isBefore(date) }.sortedDescending().mapNotNull { day ->
            fixOf(calendar, day)?.let { day to it }
        }
    }

    /**
     * Why [date] stays out of the bank, if it does: only a day that would count once fixed (an
     * office day), not one marked e.g. as smart working after a stamp.
     */
    private fun fixOf(calendar: WorkCalendar, date: LocalDate): DayFix? {
        val record = calendar.record(date) ?: return null
        if (!record.hasStamps || calendar.effectiveType(date) != DayType.OFFICE) return null
        return when {
            record.entry != null && record.exit == null -> DayFix.MISSING_EXIT
            DayCalculator.issues(record).isNotEmpty() -> DayFix.INCONSISTENT
            else -> null
        }
    }

    /** The most recent day before [date] with an entry but no exit, within two weeks. */
    fun missingExit(data: StaccoData, date: LocalDate): LocalDate? =
        (1L..MISSING_EXIT_DAYS).asSequence()
            .map { date.minusDays(it) }
            .firstOrNull { day -> data.days[day.toString()]?.let { it.entry != null && it.exit == null } == true }

    /**
     * Today, unless yesterday's working day is not over yet: without an exit it stays the current
     * one until four hours after its expected end (a night shift, an evening that runs past
     * midnight), so that the exit stamped after midnight still closes it.
     */
    fun activeDay(data: StaccoData, now: LocalDateTime): ActiveDay {
        val today = now.toLocalDate()
        val minutes = now.hour * 60 + now.minute
        val until = yesterdayUntil(data, today)
        if (until != null && minutes < until) return ActiveDay(today.minusDays(1), minutes + 1440, now.second)
        return ActiveDay(today, minutes, now.second)
    }

    /**
     * Until which minute of [today] yesterday's working day stays the current one: when it has no
     * exit, four hours after its expected end. Null when it does not reach into today.
     */
    private fun yesterdayUntil(data: StaccoData, today: LocalDate): Int? {
        val yesterday = today.minusDays(1)
        val record = data.days[yesterday.toString()]
        if (record?.exit != null) return null
        val calendar = WorkCalendar.from(data)
        val end = calendar.plannedEnd(yesterday) ?: record?.entry?.let { entry ->
            val b = DayCalculator.breakInfo(record, calendar.breakRules(yesterday), calendar.required(yesterday), null)
            entry + calendar.required(yesterday) + b.deducted
        } ?: return null
        return end + NIGHT_GRACE_MINUTES - 1440
    }

    fun today(
        data: StaccoData,
        ledger: BankLedger,
        now: LocalDateTime,
        recovery: RecoveryLedger = RecoveryLedger.EMPTY,
    ): TodaySnapshot {
        val active = activeDay(data, now)
        val date = active.date
        val calendar = WorkCalendar.from(data)
        val record = data.days[date.toString()] ?: DayRecord.EMPTY
        val required = calendar.required(date)
        val projected = DayCalculator.project(
            r = Rounding.counted(record, data.work.rounding),
            rules = calendar.breakRules(date),
            required = required,
            minimum = calendar.minimum(date),
            bankBefore = ledger.balanceBeforeToday,
            now = active.minutes,
            plannedStart = calendar.plannedStart(date),
            plannedEnd = calendar.plannedEnd(date),
            covered = calendar.covered(date) > 0,
            clockShift = { from, to -> clockShift(date, from, to) },
        )
        // A finished day reads the same as in the calendar and the bank (daylight saving included).
        val projection = if (projected.result != null) projected.copy(result = calendar.result(date)) else projected
        val todayDone = projection.phase == DayPhase.DONE || !calendar.isScheduled(date)
        val alerts = AlertPlanner.plan(
            balance = ledger.balance,
            today = date,
            todayDone = todayDone,
            calendar = calendar,
            work = calendar.workAt(date),
            bank = data.bank,
        )
        val smartRange = calendar.smartRange(date, data.smart.period, firstDayOfWeek())
        return TodaySnapshot(
            date = date,
            calendarDate = now.toLocalDate(),
            nowMinutes = active.minutes,
            nowSeconds = active.minutes * 60 + active.seconds,
            record = record,
            type = calendar.effectiveType(date),
            scheduled = calendar.isScheduled(date),
            holiday = calendar.holiday(date),
            shift = calendar.shift(date),
            expectsBreak = calendar.expectsBreak(date),
            projection = projection,
            ledger = ledger,
            alerts = alerts,
            smartUsed = calendar.smartUsed(smartRange),
            data = data,
            onCall = if (data.onCall.enabled) onCall(data, now, recovery, calendar, date, todayDone) else null,
        )
    }

    private fun onCall(
        data: StaccoData,
        now: LocalDateTime,
        recovery: RecoveryLedger,
        calendar: WorkCalendar,
        workDate: LocalDate,
        todayDone: Boolean,
    ): OnCallState {
        val (dutyDate, _) = OnCall.dutyDay(data, now)
        val running = OnCall.running(data, now.toLocalDate())
        // In real time, as the intervention is credited once stopped (daylight saving nights too).
        val seconds = running?.let { (date, intervention) ->
            val minutes = OnCall.minutesOf(date, now)
            val clock = minutes * 60 + now.second - intervention.start * 60 - intervention.startSecond
            (clock - clockShift(date, intervention.start, minutes) * 60).coerceAtLeast(0)
        }
        val record = data.days[dutyDate.toString()]
        return OnCallState(
            running = running,
            runningSeconds = seconds,
            dutyDate = dutyDate,
            onDuty = running != null || record?.onCall == true,
            interventions = record?.interventions.orEmpty().filter { it.end != null }.sortedByDescending { it.start },
            recovery = recovery,
            alert = AlertPlanner.planRecovery(recovery, workDate, todayDone, calendar, calendar.workAt(workDate), data.bank),
        )
    }

    fun snapshot(data: StaccoData, now: LocalDateTime = LocalDateTime.now()): TodaySnapshot =
        today(data, ledger(data, now.toLocalDate()), now, recovery(data, now.toLocalDate()))

    /**
     * Normalizes a stamp typed or picked as a clock time: a stamp earlier than the entry
     * must belong to the following day (night shifts).
     */
    fun normalizeStamp(record: DayRecord, slot: StampSlot, minutes: Int): Int {
        val entry = record.entry
        return if (slot != StampSlot.ENTRY && entry != null && minutes < entry) minutes + 1440 else minutes
    }
}
