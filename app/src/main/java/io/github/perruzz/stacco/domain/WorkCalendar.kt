package io.github.perruzz.stacco.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

enum class Holiday {
    NEW_YEAR, EPIPHANY, EASTER, EASTER_MONDAY, LIBERATION, LABOUR, REPUBLIC,
    ASSUMPTION, ST_FRANCIS, ALL_SAINTS, IMMACULATE, CHRISTMAS, ST_STEPHEN,
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

    fun holiday(date: LocalDate): Holiday? =
        if (work.italianHolidays) ItalianHolidays.holiday(date) else null

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

    fun required(date: LocalDate): Int {
        if (!isScheduled(date) || effectiveType(date) != DayType.OFFICE) return 0
        val leave = days[date]?.leaveMinutes ?: 0
        val planned = if (isShiftMode) shift(date)?.workMinutes ?: 0 else work.dailyMinutes
        return max(0, planned - leave)
    }

    fun minimum(date: LocalDate): Int {
        if (isShiftMode) return required(date)
        if (!isScheduled(date) || effectiveType(date) != DayType.OFFICE) return 0
        val leave = days[date]?.leaveMinutes ?: 0
        return max(0, minOf(work.minDailyMinutes, work.dailyMinutes) - leave)
    }

    fun breakRules(date: LocalDate): BreakRules =
        BreakRules(if (isShiftMode) shift(date)?.breakMinutes ?: 0 else work.minBreakMinutes)

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

    /** Hour bank delta of a completed office day, or null when there is nothing to count. */
    fun delta(date: LocalDate): Int? {
        val record = days[date] ?: return null
        if (effectiveType(date) != DayType.OFFICE) return null
        return DayCalculator.result(record, breakRules(date), required(date))?.delta
    }

    fun smartUsed(range: ClosedRange<LocalDate>): Int =
        days.count { (date, record) -> record.type == DayType.SMART && date in range }

    fun smartRange(reference: LocalDate, period: SmartPeriod, firstDayOfWeek: DayOfWeek): ClosedRange<LocalDate> =
        when (period) {
            SmartPeriod.MONTH -> YearMonth.from(reference).let { it.atDay(1)..it.atEndOfMonth() }
            SmartPeriod.WEEK -> Periods.weekRange(reference, firstDayOfWeek)
        }

    companion object {
        fun from(data: StaccoData): WorkCalendar = WorkCalendar(data.work, data.parsedDays())
    }
}

fun StaccoData.parsedDays(): Map<LocalDate, DayRecord> =
    days.mapNotNull { (key, value) -> runCatching { LocalDate.parse(key) }.getOrNull()?.let { it to value } }.toMap()

fun ceilDiv(a: Int, b: Int): Int = if (b <= 0) a else (a + b - 1) / b
