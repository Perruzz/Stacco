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
) {
    val work: WorkSettings get() = data.work
    val isShiftMode: Boolean get() = data.work.mode == WorkMode.SHIFTS
    val isSmart: Boolean get() = type == DayType.SMART
    val isTravel: Boolean get() = type == DayType.TRAVEL
    val isOffice: Boolean get() = type == DayType.OFFICE
    val isRestDay: Boolean get() = type == null
    val isAway: Boolean get() = type == DayType.VACATION || type == DayType.SICK || type == DayType.HOLIDAY
    val fromYesterday: Boolean get() = date != calendarDate

    val nextSlot: StampSlot? get() = record.nextSlot(expectsBreak)
    val alternativeSlot: StampSlot? get() = record.alternativeSlot(expectsBreak)

    /** Exit time chosen by the user as reference for countdown and reminders. */
    val targetExit: Int? get() = projection.targetExit(data.notifications.exitTarget)

    /** Seconds left to [minutes] of the working day (negative once it has passed). */
    fun secondsTo(minutes: Int): Int = minutes * 60 - nowSeconds

    /** A recent day whose exit was never stamped (it stays out of the hour bank until fixed). */
    val missingExit: LocalDate? get() = Engine.missingExit(data, date)

    /** A recent day left out of the hour bank: exit missing or stamps that do not add up. */
    val dayToFix: Pair<LocalDate, DayFix>? get() = Engine.dayToFix(data, date)
}

enum class DayFix { MISSING_EXIT, INCONSISTENT }

object Engine {

    /** How long after the end of a night shift it still counts as "current". */
    private const val NIGHT_GRACE_MINUTES = 4 * 60

    fun firstDayOfWeek(locale: Locale = Locale.getDefault()): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

    fun ledger(data: StaccoData, today: LocalDate): BankLedger = BankCalculator.compute(data, today)

    /** How far back a forgotten exit is still worth pointing out. */
    private const val MISSING_EXIT_DAYS = 14L

    /**
     * The most recent day before [date] (two weeks) that the bank skips: an entry without exit, or
     * stamps that do not add up (e.g. a break never closed).
     */
    fun dayToFix(data: StaccoData, date: LocalDate): Pair<LocalDate, DayFix>? =
        (1L..MISSING_EXIT_DAYS).asSequence().map { date.minusDays(it) }.firstNotNullOfOrNull { day ->
            val record = data.days[day.toString()] ?: return@firstNotNullOfOrNull null
            when {
                record.entry != null && record.exit == null -> day to DayFix.MISSING_EXIT
                record.hasStamps && DayCalculator.issues(record).isNotEmpty() -> day to DayFix.INCONSISTENT
                else -> null
            }
        }

    /** The most recent day before [date] with an entry but no exit, within two weeks. */
    fun missingExit(data: StaccoData, date: LocalDate): LocalDate? =
        (1L..MISSING_EXIT_DAYS).asSequence()
            .map { date.minusDays(it) }
            .firstOrNull { day -> data.days[day.toString()]?.let { it.entry != null && it.exit == null } == true }

    /**
     * Today, unless yesterday's working day crosses midnight and is not over yet
     * (a night shift, or a day whose exit is projected after midnight).
     */
    fun activeDay(data: StaccoData, now: LocalDateTime): ActiveDay {
        val today = now.toLocalDate()
        val minutes = now.hour * 60 + now.minute
        val yesterday = today.minusDays(1)
        val calendar = WorkCalendar.from(data)
        val record = data.days[yesterday.toString()]
        if (record?.exit == null) {
            val end = calendar.plannedEnd(yesterday) ?: record?.entry?.let { entry ->
                val b = DayCalculator.breakInfo(record, calendar.breakRules(yesterday), calendar.required(yesterday), null)
                entry + calendar.required(yesterday) + b.deducted
            }
            if (end != null && end > 1440 && minutes + 1440 < end + NIGHT_GRACE_MINUTES) {
                return ActiveDay(yesterday, minutes + 1440, now.second)
            }
        }
        return ActiveDay(today, minutes, now.second)
    }

    fun today(data: StaccoData, ledger: BankLedger, now: LocalDateTime): TodaySnapshot {
        val active = activeDay(data, now)
        val date = active.date
        val calendar = WorkCalendar.from(data)
        val record = data.days[date.toString()] ?: DayRecord.EMPTY
        val required = calendar.required(date)
        val projected = DayCalculator.project(
            r = record,
            rules = calendar.breakRules(date),
            required = required,
            minimum = calendar.minimum(date),
            bankBefore = ledger.balanceBeforeToday,
            now = active.minutes,
            plannedStart = calendar.plannedStart(date),
            plannedEnd = calendar.plannedEnd(date),
        )
        // A finished day reads the same as in the calendar and the bank (daylight saving included).
        val projection = if (projected.result != null) projected.copy(result = calendar.result(date)) else projected
        val alerts = AlertPlanner.plan(
            balance = ledger.balance,
            today = date,
            todayDone = projection.phase == DayPhase.DONE || !calendar.isScheduled(date),
            calendar = calendar,
            work = data.work,
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
        )
    }

    fun snapshot(data: StaccoData, now: LocalDateTime = LocalDateTime.now()): TodaySnapshot =
        today(data, ledger(data, now.toLocalDate()), now)

    /**
     * Normalizes a stamp typed or picked as a clock time: a stamp earlier than the entry
     * must belong to the following day (night shifts).
     */
    fun normalizeStamp(record: DayRecord, slot: StampSlot, minutes: Int): Int {
        val entry = record.entry
        return if (slot != StampSlot.ENTRY && entry != null && minutes < entry) minutes + 1440 else minutes
    }
}
