package io.github.perruzz.stacco.domain

import java.time.LocalDate
import kotlin.math.max

sealed interface BankAlert {
    /** Whether the deadline is close enough to deserve a push notification. */
    val inWindow: Boolean

    /** Negative balance that must be recovered before [monthEnd]. */
    data class Negative(
        val balance: Int,
        val monthEnd: LocalDate,
        val officeDaysLeft: Int,
        val perDay: Int,
        override val inWindow: Boolean,
    ) : BankAlert

    /** Positive balance that will be wiped on [expiry]. */
    data class Expiry(
        val balance: Int,
        val expiry: LocalDate,
        val officeDaysLeft: Int,
        val daysNeeded: Int,
        val maxPerDay: Int,
        val atRisk: Boolean,
        override val inWindow: Boolean,
    ) : BankAlert
}

/** On-call recovery hours that will be lost on [expiry] unless they are taken (planned ones excluded). */
data class RecoveryAlert(
    val minutes: Int,
    val expiry: LocalDate,
    val officeDaysLeft: Int,
    /** Office days it takes to use them, a whole day of recovery each. */
    val daysNeeded: Int,
    val inWindow: Boolean,
)

/**
 * Decides which hour-bank warnings apply. The warning window adapts to the balance:
 * the more hours you have to recover (or to use), the earlier Stacco warns you.
 */
object AlertPlanner {

    /**
     * The next on-call recovery hours that would be lost, with the same adaptive window as the hour
     * bank: the more days it takes to use them, the earlier the warning.
     */
    fun planRecovery(
        ledger: RecoveryLedger,
        today: LocalDate,
        todayDone: Boolean,
        calendar: WorkCalendar,
        work: WorkSettings,
        bank: BankSettings,
    ): RecoveryAlert? {
        val lot = ledger.lots.firstOrNull { it.atRisk > 0 } ?: return null
        val start = if (todayDone) today.plusDays(1) else today
        val left = calendar.officeDays(start, lot.expiry)
        val needed = ceilDiv(lot.atRisk, max(1, VacationCalculator.typicalDay(work)))
        return RecoveryAlert(
            minutes = lot.atRisk,
            expiry = lot.expiry,
            officeDaysLeft = left,
            daysNeeded = needed,
            inWindow = left <= needed + bank.alertLeadDays,
        )
    }

    /** Assumed sustainable recovery per day when the company has no flexibility margin. */
    private const val DEFAULT_DAILY_FLEX = 30

    fun plan(
        balance: Int,
        today: LocalDate,
        todayDone: Boolean,
        calendar: WorkCalendar,
        work: WorkSettings,
        bank: BankSettings,
    ): List<BankAlert> {
        val start = if (todayDone) today.plusDays(1) else today
        // How much the bank can move in a single day: with shifts the day cannot be shortened.
        val flex = if (work.mode == WorkMode.SHIFTS) 0 else work.flexMinutes
        val alerts = mutableListOf<BankAlert>()

        if (balance < 0 && bank.negativeMustBeRecovered) {
            val monthEnd = Periods.monthEnd(today)
            val left = calendar.officeDays(start, monthEnd)
            val deficit = -balance
            val needed = ceilDiv(deficit, max(flex, DEFAULT_DAILY_FLEX))
            alerts += BankAlert.Negative(
                balance = balance,
                monthEnd = monthEnd,
                officeDaysLeft = left,
                perDay = if (left > 0) ceilDiv(deficit, left) else deficit,
                inWindow = left <= needed + bank.alertLeadDays,
            )
        }

        val expiry = Periods.expiryEnd(today, bank.positiveExpiry)
        if (balance > 0 && expiry != null) {
            val left = calendar.officeDays(start, expiry)
            val needed = if (flex > 0) ceilDiv(balance, flex) else 0
            alerts += BankAlert.Expiry(
                balance = balance,
                expiry = expiry,
                officeDaysLeft = left,
                daysNeeded = needed,
                maxPerDay = flex,
                atRisk = flex == 0 || needed > left,
                inWindow = left <= needed + bank.alertLeadDays,
            )
        }
        return alerts
    }
}
