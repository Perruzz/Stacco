package io.github.perruzz.stacco.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TreeMap
import kotlin.math.max
import kotlin.math.min

/**
 * On-call duty (reperibilità). Every intervention is credited by the quarter hour, rounded up, to a
 * recovery account kept apart from the hour bank and from vacation: the hours earned in a month can
 * be taken until the end of the second month after it, in blocks of half an hour (or the rest of
 * the day), and they cover the required time of the day they are taken on.
 */
object OnCall {

    /** Interventions are credited in quarter hours, rounded up. */
    const val CREDIT_STEP = 15

    /** Recovery is taken in blocks of half an hour (or whatever is left of the day). */
    const val RECOVERY_STEP = 30

    /** Until 09:00 an intervention still belongs to the night of yesterday's on-call day. */
    const val NIGHT_END_MINUTES = 9 * 60

    /** How far back a timer left running is still found. */
    private const val RUNNING_LOOKBACK_DAYS = 7L

    /** 32 minutes → 45, 15 → 15, 1 → 15, 0 → 0. */
    fun credited(realMinutes: Int): Int = if (realMinutes <= 0) 0 else ceilDiv(realMinutes, CREDIT_STEP) * CREDIT_STEP

    /** Real length of a finished intervention of [date] (across a daylight saving change too); null while it runs. */
    fun realMinutes(date: LocalDate, intervention: Intervention, zone: ZoneId = ZoneId.systemDefault()): Int? {
        val end = intervention.end ?: return null
        return max(0, end - intervention.start - clockShift(date, intervention.start, end, zone))
    }

    /** What a finished intervention of [date] earns; nothing while its timer runs. */
    fun credited(date: LocalDate, intervention: Intervention, zone: ZoneId = ZoneId.systemDefault()): Int =
        realMinutes(date, intervention, zone)?.let { credited(it) } ?: 0

    /** Last day to take the hours earned on [date]: the end of the second month after it. */
    fun expiryOf(date: LocalDate): LocalDate = YearMonth.from(date).plusMonths(2).atEndOfMonth()

    /**
     * The on-call day an intervention starting at [now] belongs to, with its start in that day's
     * minutes: in the early hours after an on-call day (today not on call) it is still yesterday,
     * past 1440, as for a night shift; otherwise today.
     */
    fun dutyDay(data: StaccoData, now: LocalDateTime): Pair<LocalDate, Int> {
        val today = now.toLocalDate()
        val minutes = now.hour * 60 + now.minute
        val yesterday = today.minusDays(1)
        val night = minutes < NIGHT_END_MINUTES &&
            data.days[yesterday.toString()]?.onCall == true &&
            data.days[today.toString()]?.onCall != true
        return if (night) yesterday to minutes + 1440 else today to minutes
    }

    /** The intervention whose timer is running, with its day (one forgotten for up to a week too). */
    fun running(data: StaccoData, today: LocalDate): Pair<LocalDate, Intervention>? =
        (0L..RUNNING_LOOKBACK_DAYS).asSequence().map { today.minusDays(it) }.firstNotNullOfOrNull { date ->
            data.days[date.toString()]?.interventions?.firstOrNull { it.end == null }?.let { date to it }
        }

    /** [now] as minutes of [date]'s day: past 1440 on the days after it. */
    fun minutesOf(date: LocalDate, now: LocalDateTime): Int =
        ChronoUnit.DAYS.between(date, now.toLocalDate()).toInt() * 1440 + now.hour * 60 + now.minute

    /**
     * Recovery hours actually taken on [date]: only on an office day, and never more than what is
     * left of the day once a permit is counted.
     */
    fun recoveryCost(calendar: WorkCalendar, date: LocalDate): Int {
        val record = calendar.record(date) ?: return 0
        if (record.recoveryMinutes <= 0 || calendar.effectiveType(date) != DayType.OFFICE) return 0
        return min(record.recoveryMinutes, recoveryRoom(calendar, date))
    }

    /** How much recovery [date] can take: the planned day minus its permit (nothing on a day off). */
    fun recoveryRoom(calendar: WorkCalendar, date: LocalDate): Int {
        if (!calendar.isScheduled(date)) return 0
        val day = VacationCalculator.dayLength(calendar, date)
        val leave = min(calendar.record(date)?.leaveMinutes ?: 0, day)
        return max(0, day - leave)
    }

    /** The on-call figures of [month], as in the calendar summary and the CSV. */
    fun month(data: StaccoData, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): OnCallMonth {
        val calendar = WorkCalendar.from(data)
        var days = 0
        var count = 0
        var real = 0
        var credited = 0
        var used = 0
        for ((date, record) in data.parsedDays()) {
            if (YearMonth.from(date) != month) continue
            if (record.onCall) days++
            for (intervention in record.interventions) {
                val minutes = realMinutes(date, intervention, zone) ?: continue
                count++
                real += minutes
                credited += credited(minutes)
            }
            used += recoveryCost(calendar, date)
        }
        return OnCallMonth(days = days, interventions = count, real = real, credited = credited, used = used)
    }
}

/** On-call duty in a month: days on call, finished interventions, hours earned and recovery taken. */
data class OnCallMonth(val days: Int, val interventions: Int, val real: Int, val credited: Int, val used: Int)

/** Recovery hours earned in one month, and what is left of them. */
data class RecoveryLot(
    val month: YearMonth,
    val earned: Int,
    /** Still to take today. */
    val left: Int,
    /** Last day to take them. */
    val expiry: LocalDate,
    /** What would still be lost at [expiry], once the recovery already planned is taken. */
    val atRisk: Int,
)

data class RecoveryLedger(
    /** Hours that can be taken today; negative when more was taken than earned. */
    val balance: Int,
    /** Months with hours still to take, the soonest to expire first. */
    val lots: List<RecoveryLot>,
    /** Recovery already planned on days after today. */
    val planned: Int,
    /** Hours lost so far because they were not taken in time. */
    val expired: Int,
) {
    val nextExpiry: RecoveryLot? get() = lots.firstOrNull()

    companion object {
        val EMPTY = RecoveryLedger(0, emptyList(), 0, 0)
    }
}

/**
 * Rebuilds the on-call recovery account from scratch, like the hour bank: hours earned by the
 * interventions, taken on the days that use them (the months that expire first go first) and lost
 * at the end of the second month after they were earned.
 */
object RecoveryCalculator {

    fun compute(data: StaccoData, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): RecoveryLedger {
        val (earned, used) = movements(data, zone)
        if (earned.isEmpty() && used.isEmpty()) return RecoveryLedger.EMPTY
        val past = earned.filterKeys { !it.isAfter(today) }
        val now = Account().apply { replay(past, used.filterKeys { !it.isAfter(today) }, expireBefore = today) }
        // The same hours carried forward through the recovery planned after today: what would still be lost.
        val ahead = Account().apply { replay(past, used, expireBefore = LocalDate.MAX) }
        val lots = now.lots.values.filter { it.left > 0 }.sortedBy { it.expiry }.map { lot ->
            RecoveryLot(lot.month, lot.earned, lot.left, lot.expiry, atRisk = ahead.lots[lot.month]?.lost ?: 0)
        }
        return RecoveryLedger(
            balance = now.balance,
            lots = lots,
            planned = used.filterKeys { it.isAfter(today) }.values.sum(),
            expired = now.expired,
        )
    }

    /** Hours that can be taken on [date]: earned by then, minus what was taken before and what expired. */
    fun availableOn(data: StaccoData, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int {
        val (earned, used) = movements(data, zone)
        return Account().apply {
            replay(earned.filterKeys { !it.isAfter(date) }, used.filterKeys { it.isBefore(date) }, expireBefore = date)
        }.balance
    }

    /** Minutes earned and taken, day by day. */
    private fun movements(data: StaccoData, zone: ZoneId): Pair<Map<LocalDate, Int>, Map<LocalDate, Int>> {
        val calendar = WorkCalendar.from(data)
        val earned = HashMap<LocalDate, Int>()
        val used = HashMap<LocalDate, Int>()
        for ((date, record) in data.parsedDays()) {
            val credit = record.interventions.sumOf { OnCall.credited(date, it, zone) }
            if (credit > 0) earned[date] = credit
            val cost = OnCall.recoveryCost(calendar, date)
            if (cost > 0) used[date] = cost
        }
        return earned to used
    }

    private class Lot(val month: YearMonth) {
        val expiry: LocalDate = OnCall.expiryOf(month.atDay(1))
        var earned = 0
        var left = 0
        var lost = 0
    }

    private class Account {
        /** By month, so also by expiry. */
        val lots = TreeMap<YearMonth, Lot>()

        /** Taken beyond what was there (e.g. an intervention deleted afterwards). */
        var deficit = 0
        var expired = 0
        val balance: Int get() = lots.values.sumOf { it.left } - deficit

        /** Day by day: first what expired the day before, then what is earned, then what is taken. */
        fun replay(earned: Map<LocalDate, Int>, used: Map<LocalDate, Int>, expireBefore: LocalDate) {
            for (date in (earned.keys + used.keys).toSortedSet()) {
                expire(before = date)
                earned[date]?.let { earn(YearMonth.from(date), it) }
                used[date]?.let { take(it) }
            }
            expire(before = expireBefore)
        }

        /** New hours first pay back what was taken in advance. */
        private fun earn(month: YearMonth, minutes: Int) {
            val lot = lots.getOrPut(month) { Lot(month) }
            lot.earned += minutes
            val repaid = min(deficit, minutes)
            deficit -= repaid
            lot.left += minutes - repaid
        }

        /** Hours taken come from the months that expire first. */
        private fun take(minutes: Int) {
            var rest = minutes
            for (lot in lots.values) {
                if (rest == 0) break
                val part = min(rest, lot.left)
                lot.left -= part
                rest -= part
            }
            deficit += rest
        }

        /** Months whose last day is before [before] lose what is left of them. */
        private fun expire(before: LocalDate) {
            for (lot in lots.values) {
                if (lot.left > 0 && lot.expiry.isBefore(before)) {
                    expired += lot.left
                    lot.lost += lot.left
                    lot.left = 0
                }
            }
        }
    }
}
