package io.github.perruzz.stacco.data

import io.github.perruzz.stacco.domain.BankAdjustment
import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.Intervention
import io.github.perruzz.stacco.domain.Schedule
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.WorkSettings
import java.time.LocalDate

/**
 * Keeps the data within what Stacco itself can produce, so that a backup edited by hand or a
 * damaged file can never make the app count nonsense, freeze or crash: dates far outside any
 * calendar, negative hours, impossible stamps, duplicated ids. Values the app can produce are
 * left exactly as they are.
 */
object Sanitizer {

    private const val DAY = 24 * 60

    /** Far beyond the date pickers (1900–2100) and the calendar (±10 years), far from an overflow. */
    private val years = 1800..2200

    /** A stamp runs into the next day on a night shift (up to its end plus four hours), never further. */
    private val stamps = 0 until 3 * DAY

    /** A timer forgotten for a week is still stopped; nothing lasts longer than that. */
    private const val LONGEST_INTERVENTION = 10 * DAY

    /** Far beyond the bank dialogs (±300h), still far from an overflow. */
    private const val LARGEST_ADJUSTMENT = 1_000_000

    /** Every hour of a leap year. */
    private const val YEAR = 366 * DAY

    fun clean(data: StaccoData): StaccoData = data.copy(
        // Decoded by this version: the data now follows its schema.
        schemaVersion = StaccoData().schemaVersion,
        work = clean(data.work),
        bank = data.bank.copy(alertLeadDays = data.bank.alertLeadDays.coerceIn(0, 31)),
        smart = data.smart.copy(allowance = data.smart.allowance.coerceAtLeast(0)),
        notifications = data.notifications.copy(
            exitReminderLeadMinutes = data.notifications.exitReminderLeadMinutes.coerceIn(0, DAY),
            shiftStartLeadMinutes = data.notifications.shiftStartLeadMinutes.coerceIn(0, DAY),
        ),
        days = buildMap {
            for ((key, day) in data.days) {
                val date = date(key) ?: continue
                val cleaned = clean(day)
                if (!cleaned.isEmpty) put(date.toString(), cleaned)
            }
        },
        adjustments = data.adjustments
            .filter { date(it.date) != null }
            .map { it.copy(minutes = it.minutes.coerceIn(-LARGEST_ADJUSTMENT, LARGEST_ADJUSTMENT)) }
            .uniqueIds(BankAdjustment::id) { adjustment, id -> adjustment.copy(id = id) },
        vacation = data.vacation.copy(
            annualMinutes = data.vacation.annualMinutes.coerceIn(0, YEAR),
            adjustments = data.vacation.adjustments
                .filterKeys { key -> key.toIntOrNull()?.let { it in years } == true }
                .mapValues { it.value.coerceIn(-YEAR, YEAR) },
        ),
        scheduleHistory = data.scheduleHistory.filter { date(it.until) != null }.map { it.copy(schedule = clean(it.schedule)) },
        support = data.support.copy(nextAt = data.support.nextAt?.takeIf { date(it) != null }),
    )

    private fun clean(work: WorkSettings): WorkSettings = work.withSchedule(clean(work.schedule)).copy(
        minBreakMinutes = work.minBreakMinutes.coerceIn(0, DAY),
        shifts = work.shifts.map {
            it.copy(start = it.start.coerceIn(0, DAY - 1), end = it.end.coerceIn(0, DAY - 1), breakMinutes = it.breakMinutes.coerceIn(0, DAY))
        },
        rounding = work.rounding.let { r ->
            val step = r.stepMinutes.coerceIn(0, 60)
            r.copy(stepMinutes = step, toleranceMinutes = r.toleranceMinutes.coerceIn(0, (step - 1).coerceAtLeast(0)))
        },
    )

    private fun clean(schedule: Schedule): Schedule = schedule.copy(
        dailyMinutes = schedule.dailyMinutes.coerceIn(0, DAY),
        minDailyMinutes = schedule.minDailyMinutes.coerceIn(0, DAY),
        dailyByDay = schedule.dailyByDay.filterKeys { it in 1..7 }.mapValues { it.value.coerceIn(0, DAY) },
        workDays = schedule.workDays.filter { it in 1..7 }.toSet(),
    )

    private fun clean(day: DayRecord): DayRecord = day.copy(
        entry = day.entry?.takeIf { it in stamps },
        breakOut = day.breakOut?.takeIf { it in stamps },
        breakIn = day.breakIn?.takeIf { it in stamps },
        exit = day.exit?.takeIf { it in stamps },
        leaveMinutes = day.leaveMinutes.coerceIn(0, DAY),
        recoveryMinutes = day.recoveryMinutes.coerceIn(0, DAY),
        interventions = day.interventions
            .filter { it.start in stamps }
            .map { it.copy(end = it.end?.coerceAtMost(it.start + LONGEST_INTERVENTION), startSecond = it.startSecond.coerceIn(0, 59)) }
            .uniqueIds(Intervention::id) { intervention, id -> intervention.copy(id = id) },
    )

    private fun date(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()?.takeIf { it.year in years }

    /** Items whose id is already taken get a new one (lists, edits and deletes find them by id). */
    private fun <T> List<T>.uniqueIds(id: (T) -> Long, withId: (T, Long) -> T): List<T> {
        val seen = HashSet<Long>()
        var next = (maxOfOrNull(id) ?: 0L) + 1
        return map { item -> if (seen.add(id(item))) item else withId(item, next++) }
    }
}
