package io.github.perruzz.stacco.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

enum class Holiday {
    NEW_YEAR, EPIPHANY, EASTER, EASTER_MONDAY, LIBERATION, LABOUR, REPUBLIC,
    ASSUMPTION, ST_FRANCIS, ALL_SAINTS, IMMACULATE, CHRISTMAS, ST_STEPHEN,

    /** The local patron saint's day, when the user set one. */
    PATRON,
}

/** Italian national public holidays (including Easter Monday and, since 2026, 4 October). */
object ItalianHolidays {

    fun holiday(date: LocalDate): Holiday? {
        val m = date.monthValue
        val d = date.dayOfMonth
        val fixed = when {
            m == 1 && d == 1 -> Holiday.NEW_YEAR
            m == 1 && d == 6 -> Holiday.EPIPHANY
            m == 4 && d == 25 -> Holiday.LIBERATION
            m == 5 && d == 1 -> Holiday.LABOUR
            m == 6 && d == 2 -> Holiday.REPUBLIC
            m == 8 && d == 15 -> Holiday.ASSUMPTION
            m == 10 && d == 4 && date.year >= 2026 -> Holiday.ST_FRANCIS
            m == 11 && d == 1 -> Holiday.ALL_SAINTS
            m == 12 && d == 8 -> Holiday.IMMACULATE
            m == 12 && d == 25 -> Holiday.CHRISTMAS
            m == 12 && d == 26 -> Holiday.ST_STEPHEN
            else -> null
        }
        if (fixed != null) return fixed
        val easter = easterSunday(date.year)
        return when (date) {
            easter -> Holiday.EASTER
            easter.plusDays(1) -> Holiday.EASTER_MONDAY
            else -> null
        }
    }

    /** Anonymous Gregorian algorithm (Meeus/Jones/Butcher). */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }
}

/** Calendar periods used by the hour bank rules. */
object Periods {

    /** Last day of the expiry period that contains [date]. Periods are aligned to the calendar year. */
    fun expiryEnd(date: LocalDate, period: ExpiryPeriod): LocalDate? {
        if (period == ExpiryPeriod.NEVER) return null
        val n = period.months
        val endMonth = ((date.monthValue - 1) / n + 1) * n
        return YearMonth.of(date.year, endMonth).atEndOfMonth()
    }

    fun isExpiryEnd(date: LocalDate, period: ExpiryPeriod): Boolean {
        if (period == ExpiryPeriod.NEVER) return false
        return date == date.with(TemporalAdjusters.lastDayOfMonth()) && date.monthValue % period.months == 0
    }

    fun monthEnd(date: LocalDate): LocalDate = date.with(TemporalAdjusters.lastDayOfMonth())

    /** Stable key of the period containing [date], e.g. "2026-Q4". */
    fun expiryKey(date: LocalDate, period: ExpiryPeriod): String {
        if (period == ExpiryPeriod.NEVER) return "never"
        val index = (date.monthValue - 1) / period.months
        return "${date.year}-${period.name}-$index"
    }

    fun weekRange(date: LocalDate, firstDayOfWeek: DayOfWeek): ClosedRange<LocalDate> {
        val start = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return start..start.plusDays(6)
    }
}

/**
 * Knows which days are working days and how much must be worked on each of them,
 * both for a fixed weekly schedule and for planned shifts.
 */
class WorkCalendar(
    private val work: WorkSettings,
    private val days: Map<LocalDate, DayRecord>,
) {
    private val shiftsById = work.shifts.associateBy { it.id }
    val isShiftMode: Boolean get() = work.mode == WorkMode.SHIFTS

    fun record(date: LocalDate): DayRecord? = days[date]

    fun holiday(date: LocalDate): Holiday? {
        if (work.italianHolidays) ItalianHolidays.holiday(date)?.let { return it }
        return if (work.patronDay != 0 && date.monthValue * 100 + date.dayOfMonth == work.patronDay) Holiday.PATRON else null
    }

    /** Planned shift of the day (shift mode only). */
    fun shift(date: LocalDate): ShiftTemplate? =
        if (isShiftMode) days[date]?.shiftId?.let(shiftsById::get) else null

    /** A day that is planned as working: weekly schedule (minus holidays) or a planned shift. */
    fun isScheduled(date: LocalDate): Boolean =
        if (isShiftMode) {
            shift(date) != null
        } else {
            date.dayOfWeek.value in work.workDays && holiday(date) == null
        }

    /** Explicit type, or the one implied by the schedule. Null means a rest day. */
    fun effectiveType(date: LocalDate): DayType? {
        val record = days[date]
        record?.type?.let { return it }
        if (record?.hasStamps == true) return DayType.OFFICE
        if (isScheduled(date)) return DayType.OFFICE
        if (!isShiftMode && holiday(date) != null && date.dayOfWeek.value in work.workDays) return DayType.HOLIDAY
        return null
    }

    /**
     * The days a type is written on when many are picked at once: with a fixed schedule the days
     * off in the selection are left alone (a week of vacation does not touch the weekend), unless
     * only days off were picked. Shifts have no fixed days off.
     */
    fun daysToMark(dates: Collection<LocalDate>, type: DayType?): List<LocalDate> {
        if (type == null || isShiftMode) return dates.sorted()
        val working = dates.filter(::isScheduled)
        return working.ifEmpty { dates.toList() }.sorted()
    }

    fun required(date: LocalDate): Int {
        if (!isScheduled(date) || effectiveType(date) != DayType.OFFICE) return 0
        val leave = days[date]?.leaveMinutes ?: 0
        val planned = if (isShiftMode) shift(date)?.workMinutes ?: 0 else work.dailyFor(date.dayOfWeek)
        return max(0, planned - leave)
    }

    fun minimum(date: LocalDate): Int {
        if (isShiftMode) return required(date)
        if (!isScheduled(date) || effectiveType(date) != DayType.OFFICE) return 0
        val leave = days[date]?.leaveMinutes ?: 0
        // The bank can shorten every weekday by the same amount (e.g. 1h, also on a short Friday).
        return max(0, work.dailyFor(date.dayOfWeek) - work.flexMinutes - leave)
    }

    /**
     * The lunch break rule of the day. With a fixed schedule it is only due on days longer than
     * six hours (as Italian law requires): a 4h part-time day, a short Friday or a day mostly
     * covered by leave neither expects a break nor gets one deducted. Shifts keep their own break.
     */
    fun breakRules(date: LocalDate): BreakRules = BreakRules(
        when {
            isShiftMode -> shift(date)?.breakMinutes ?: 0
            required(date) in 1..SHORT_DAY_MINUTES -> 0
            else -> work.minBreakMinutes
        },
    )

    fun expectsBreak(date: LocalDate): Boolean = breakRules(date).min > 0

    /** Planned start of the shift (shift mode, working day). */
    fun plannedStart(date: LocalDate): Int? =
        shift(date)?.takeIf { effectiveType(date) == DayType.OFFICE }?.start

    /** Planned end of the shift, possibly beyond midnight (shift mode, working day). */
    fun plannedEnd(date: LocalDate): Int? =
        shift(date)?.takeIf { effectiveType(date) == DayType.OFFICE }?.endAbsolute

    /** Office days (where the badge is stamped and the bank can move) in [from]..[to]. */
    fun officeDays(from: LocalDate, to: LocalDate): Int {
        if (to.isBefore(from)) return 0
        var count = 0
        var d = from
        while (!d.isAfter(to)) {
            if (isScheduled(d) && effectiveType(d) == DayType.OFFICE) count++
            d = d.plusDays(1)
        }
        return count
    }

    /**
     * Result of a completed day. A night shift on the night the clocks change lasts one hour
     * less (spring) or more (autumn) than its stamps say: the real elapsed time is counted.
     */
    fun result(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DayResult? {
        val record = counted(date) ?: return null
        val entry = record.entry ?: return null
        val exit = record.exit ?: return null
        return DayCalculator.result(record, breakRules(date), required(date), clockShift(date, entry, exit, zone))
    }

    /** The day as it is counted: entry and exit rounded by the company rules, if any. */
    fun counted(date: LocalDate): DayRecord? = days[date]?.let { Rounding.counted(it, work.rounding) }

    /** Hour bank delta of a completed office day, or null when there is nothing to count. */
    fun delta(date: LocalDate): Int? {
        if (effectiveType(date) != DayType.OFFICE) return null
        return result(date)?.delta
    }

    fun smartUsed(range: ClosedRange<LocalDate>): Int =
        days.count { (date, record) -> record.type == DayType.SMART && date in range }

    fun smartRange(reference: LocalDate, period: SmartPeriod, firstDayOfWeek: DayOfWeek): ClosedRange<LocalDate> =
        when (period) {
            SmartPeriod.MONTH -> YearMonth.from(reference).let { it.atDay(1)..it.atEndOfMonth() }
            SmartPeriod.WEEK -> Periods.weekRange(reference, firstDayOfWeek)
        }

    companion object {
        /** Up to this many hours of work no lunch break is due. */
        const val SHORT_DAY_MINUTES = 6 * 60

        /** The last calendar built: the same data comes back every second from the clock. */
        @Volatile
        private var cached: Pair<StaccoData, WorkCalendar>? = null

        fun from(data: StaccoData): WorkCalendar {
            cached?.let { (source, calendar) -> if (source === data) return calendar }
            return WorkCalendar(data.work, data.parsedDays()).also { cached = data to it }
        }
    }
}

/**
 * Minutes the clocks moved between two clock times of [date]'s working day (minutes may go past
 * midnight): +60 across the spring-forward night, −60 across the autumn one, 0 otherwise.
 */
fun clockShift(date: LocalDate, from: Int, to: Int, zone: ZoneId = ZoneId.systemDefault()): Int {
    val start = date.atStartOfDay()
    val a = zone.rules.getOffset(start.plusMinutes(from.toLong())).totalSeconds
    val b = zone.rules.getOffset(start.plusMinutes(to.toLong())).totalSeconds
    return (b - a) / 60
}

fun StaccoData.parsedDays(): Map<LocalDate, DayRecord> =
    days.mapNotNull { (key, value) -> runCatching { LocalDate.parse(key) }.getOrNull()?.let { it to value } }.toMap()

fun ceilDiv(a: Int, b: Int): Int = if (b <= 0) a else (a + b - 1) / b
