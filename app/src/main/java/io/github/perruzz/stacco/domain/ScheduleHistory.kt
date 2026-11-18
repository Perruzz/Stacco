package io.github.perruzz.stacco.domain

import java.time.LocalDate

/**
 * The timeline of contracts. Each past period ends on a day ([SchedulePeriod.until]) and the next
 * one starts the day after; after the last period the current settings apply.
 */
object ScheduleHistory {

    /** Periods with their end date, oldest first (unreadable dates are ignored). */
    fun parse(history: List<SchedulePeriod>): List<Pair<LocalDate, Schedule>> =
        history.mapNotNull { period -> runCatching { LocalDate.parse(period.until) }.getOrNull()?.let { it to period.schedule } }
            .sortedBy { it.first }

    /**
     * The history once [next] takes over from [from] (null: on every date, the past included).
     * Days before [from] keep the schedule they had; whatever started on or after [from] is replaced.
     */
    fun change(history: List<SchedulePeriod>, current: Schedule, next: Schedule, from: LocalDate?): List<SchedulePeriod> {
        if (from == null) return emptyList()
        val dayBefore = from.minusDays(1)
        val kept = mutableListOf<Pair<LocalDate, Schedule>>()
        var covered = false
        for ((until, schedule) in parse(history)) {
            if (until.isBefore(dayBefore)) {
                kept += until to schedule
            } else {
                // The schedule in force the day before the change now ends there.
                kept += dayBefore to schedule
                covered = true
                break
            }
        }
        if (!covered) kept += dayBefore to current
        return compact(kept, next)
    }

    /** Merges neighbours with the same schedule; a last period equal to [next] is not needed. */
    private fun compact(periods: List<Pair<LocalDate, Schedule>>, next: Schedule): List<SchedulePeriod> {
        val merged = mutableListOf<Pair<LocalDate, Schedule>>()
        for (period in periods) {
            if (merged.isNotEmpty() && merged.last().second == period.second) {
                merged[merged.lastIndex] = period
            } else {
                merged += period
            }
        }
        if (merged.isNotEmpty() && merged.last().second == next) merged.removeAt(merged.lastIndex)
        return merged.map { (until, schedule) -> SchedulePeriod(until.toString(), schedule) }
    }

    /** The first day of the current settings, if they did not always apply. */
    fun currentSince(history: List<SchedulePeriod>): LocalDate? = parse(history).lastOrNull()?.first?.plusDays(1)
}
