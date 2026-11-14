package io.github.perruzz.stacco.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.WeekFields
import java.util.Locale

/** Everything about "today", computed from the persisted data at a given instant. */
data class TodaySnapshot(
    val date: LocalDate,
    val nowMinutes: Int,
    val nowSeconds: Int,
    val record: DayRecord,
    /** Effective type (null = rest day). */
    val type: DayType?,
    val scheduled: Boolean,
    val holiday: Holiday?,
    val projection: DayProjection,
    val ledger: BankLedger,
    val alerts: List<BankAlert>,
    val smartUsed: Int,
    val data: StaccoData,
) {
    val work: WorkSettings get() = data.work
    val isSmart: Boolean get() = type == DayType.SMART
    val isOffice: Boolean get() = type == DayType.OFFICE
    val isRestDay: Boolean get() = type == null
    val isAway: Boolean get() = type == DayType.VACATION || type == DayType.SICK || type == DayType.HOLIDAY

    /** Exit time chosen by the user as reference for countdown and reminders. */
    val targetExit: Int? get() = projection.targetExit(data.notifications.exitTarget)

    /** Seconds left to [targetExit] (negative once it has passed). */
    fun secondsTo(exit: Int): Int = exit * 60 - nowSeconds
}

object Engine {

    fun firstDayOfWeek(locale: Locale = Locale.getDefault()): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

    fun ledger(data: StaccoData, today: LocalDate): BankLedger = BankCalculator.compute(data, today)

    fun today(data: StaccoData, ledger: BankLedger, now: LocalDateTime): TodaySnapshot {
        val date = now.toLocalDate()
        val calendar = WorkCalendar.from(data)
        val record = data.days[date.toString()] ?: DayRecord.EMPTY
        val nowMinutes = now.hour * 60 + now.minute
        val required = calendar.required(date)
        val projection = DayCalculator.project(
            r = record,
            rules = BreakRules.from(data.work),
            required = required,
            minimum = calendar.minimum(date),
            bankBefore = ledger.balanceBeforeToday,
            now = nowMinutes,
        )
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
            nowMinutes = nowMinutes,
            nowSeconds = nowMinutes * 60 + now.second,
            record = record,
            type = calendar.effectiveType(date),
            scheduled = calendar.isScheduled(date),
            holiday = calendar.holiday(date),
            projection = projection,
            ledger = ledger,
            alerts = alerts,
            smartUsed = calendar.smartUsed(smartRange),
            data = data,
        )
    }

    fun snapshot(data: StaccoData, now: LocalDateTime = LocalDateTime.now()): TodaySnapshot =
        today(data, ledger(data, now.toLocalDate()), now)
}
