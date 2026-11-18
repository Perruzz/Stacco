package io.github.perruzz.stacco.domain

import java.time.LocalDate

/** Vacation hours of one calendar year. All values in minutes. */
data class VacationBalance(
    val year: Int,
    val annual: Int,
    /** Left over from the previous year (when carried over). */
    val carriedOver: Int,
    /** Manual correction for this year. */
    val adjustment: Int,
    /** Vacation days and leave hours up to today. */
    val used: Int,
    /** Vacation days already in the calendar after today. */
    val planned: Int,
    val daysTaken: Int,
    val daysPlanned: Int,
    /** Leave (permit) hours of the year, included in [used] and [planned]. */
    val leave: Int,
) {
    val granted: Int get() = annual + carriedOver + adjustment

    /** What is left today, as on the payslip. */
    val available: Int get() = granted - used

    /** What will be left once the planned days are taken. */
    val remaining: Int get() = granted - used - planned
}

/**
 * Vacation bookkeeping: every vacation day costs a working day ("daily hours", or the planned shift),
 * every permit costs its hours. Days off and public holidays cost nothing.
 */
object VacationCalculator {

    /** What a day of vacation on [date] is worth (with the rules of that day): nothing on a day off. */
    fun dayLength(calendar: WorkCalendar, date: LocalDate): Int {
        val work = calendar.workAt(date)
        return if (work.mode == WorkMode.SHIFTS) {
            calendar.shift(date)?.workMinutes ?: typicalDay(work)
        } else if (date.dayOfWeek.value in work.workDays && calendar.holiday(date) == null) {
            work.dailyFor(date.dayOfWeek)
        } else {
            0
        }
    }

    /** A normal working day: the daily hours, or the median shift (also used without a planned shift). */
    fun typicalDay(work: WorkSettings): Int {
        if (work.mode != WorkMode.SHIFTS) return work.dailyMinutes
        val lengths = work.shifts.map { it.workMinutes }.filter { it > 0 }.sorted()
        return if (lengths.isEmpty()) work.dailyMinutes else lengths[lengths.size / 2]
    }

    /** Minutes [date] takes from the vacation hours. */
    fun cost(calendar: WorkCalendar, date: LocalDate): Int {
        val record = calendar.record(date) ?: return 0
        return when {
            record.type == DayType.VACATION -> dayLength(calendar, date)
            record.leaveMinutes > 0 && calendar.effectiveType(date) == DayType.OFFICE -> record.leaveMinutes
            else -> 0
        }
    }

    fun balance(data: StaccoData, today: LocalDate, year: Int = today.year): VacationBalance {
        val settings = data.vacation
        val days = data.parsedDays()
        val calendar = WorkCalendar(data.work, days, data.scheduleHistory)
        val costs = days.keys.mapNotNull { date -> cost(calendar, date).takeIf { it > 0 }?.let { date to it } }
        val first = (costs.map { it.first.year } + settings.adjustments.keys.mapNotNull { it.toIntOrNull() } + year).min()

        var left = 0
        for (y in first..year) {
            val inYear = costs.filter { it.first.year == y }
            val past = inYear.filter { it.first <= today }
            val future = inYear.filter { it.first > today }
            val vacationDay = { entry: Pair<LocalDate, Int> -> days[entry.first]?.type == DayType.VACATION }
            val balance = VacationBalance(
                year = y,
                annual = settings.annualMinutes,
                carriedOver = if (settings.carryOver && y > first) left else 0,
                adjustment = settings.adjustments[y.toString()] ?: 0,
                used = past.sumOf { it.second },
                planned = future.sumOf { it.second },
                daysTaken = past.count(vacationDay),
                daysPlanned = future.count(vacationDay),
                leave = inYear.filterNot(vacationDay).sumOf { it.second },
            )
            if (y == year) return balance
            left = balance.remaining
        }
        error("unreachable")
    }

    /** The correction for this year that makes today's available hours equal to [target]. */
    fun adjustmentFor(target: Int, data: StaccoData, today: LocalDate): Int {
        val key = today.year.toString()
        val without = data.copy(vacation = data.vacation.copy(adjustments = data.vacation.adjustments - key))
        val balance = balance(without, today)
        return target - balance.available
    }

    /** Extra minutes that marking [dates] as vacation would take (days off and days already counted excluded). */
    fun costAsVacation(data: StaccoData, dates: Collection<LocalDate>): Int {
        val calendar = WorkCalendar.from(data)
        return dates.sumOf { date -> dayLength(calendar, date) - cost(calendar, date) }
    }
}
