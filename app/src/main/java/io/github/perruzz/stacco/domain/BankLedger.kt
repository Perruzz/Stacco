package io.github.perruzz.stacco.domain

import java.time.LocalDate

enum class MovementKind { DAY, MANUAL, SET_BALANCE, EXPIRY, NEGATIVE_RESET }

data class Movement(
    val date: LocalDate,
    val kind: MovementKind,
    /** Change applied to the balance. */
    val amount: Int,
    val balanceAfter: Int,
    val note: String = "",
    val adjustmentId: Long? = null,
)

data class BankLedger(
    /** Balance right now (including today's completed day). */
    val balance: Int,
    /** Balance available for today's calculations (today's own work excluded). */
    val balanceBeforeToday: Int,
    /** Today's day delta, if the day is complete. */
    val todayDelta: Int?,
    /** Chronological list of every movement. */
    val movements: List<Movement>,
    /** End-of-day balance for each day from the first movement to today. */
    val daily: List<Pair<LocalDate, Int>>,
) {
    companion object {
        val EMPTY = BankLedger(0, 0, null, emptyList(), emptyList())
    }
}

/**
 * Rebuilds the hour bank from scratch: day deltas + adjustments, applying the company rules
 * (positive balance expiring at the end of each period, negative balance reset at month end).
 */
object BankCalculator {

    private data class Event(
        val date: LocalDate,
        val order: Int,
        val kind: MovementKind,
        val amount: Int,
        val note: String = "",
        val id: Long? = null,
    )

    fun compute(data: StaccoData, today: LocalDate): BankLedger {
        val calendar = WorkCalendar.from(data)
        val deltas = data.parsedDays().keys.mapNotNull { date -> calendar.delta(date)?.let { date to it } }
        return compute(deltas, data.adjustments, data.bank, today)
    }

    fun compute(
        dayDeltas: List<Pair<LocalDate, Int>>,
        adjustments: List<BankAdjustment>,
        rules: BankSettings,
        today: LocalDate,
    ): BankLedger {
        val events = buildList {
            adjustments.forEach { adj ->
                val date = runCatching { LocalDate.parse(adj.date) }.getOrNull() ?: return@forEach
                when (adj.kind) {
                    // A balance anchor is applied at the very start of its day…
                    AdjustmentKind.SET_BALANCE -> add(Event(date, 0, MovementKind.SET_BALANCE, adj.minutes, adj.note, adj.id))
                    // …manual corrections after the day's work.
                    AdjustmentKind.MANUAL -> add(Event(date, 2, MovementKind.MANUAL, adj.minutes, adj.note, adj.id))
                }
            }
            dayDeltas.forEach { (date, delta) -> add(Event(date, 1, MovementKind.DAY, delta)) }
        }.filter { !it.date.isAfter(today) }
            .sortedWith(compareBy<Event>({ it.date }, { it.order }))

        if (events.isEmpty()) return BankLedger.EMPTY

        var balance = 0
        var todayDelta: Int? = null
        val movements = ArrayList<Movement>(events.size + 8)
        val daily = ArrayList<Pair<LocalDate, Int>>()
        var index = 0
        var day = events.first().date

        while (!day.isAfter(today)) {
            while (index < events.size && events[index].date == day) {
                val e = events[index++]
                val before = balance
                balance = if (e.kind == MovementKind.SET_BALANCE) e.amount else balance + e.amount
                if (e.kind == MovementKind.DAY && day == today) todayDelta = e.amount
                movements += Movement(
                    date = day,
                    kind = e.kind,
                    amount = balance - before,
                    balanceAfter = balance,
                    note = e.note,
                    adjustmentId = e.id,
                )
            }
            daily += day to balance

            // Period boundaries are applied once the day is over.
            if (day.isBefore(today) && day == Periods.monthEnd(day)) {
                if (balance > 0 && Periods.isExpiryEnd(day, rules.positiveExpiry)) {
                    movements += Movement(day, MovementKind.EXPIRY, -balance, 0)
                    balance = 0
                }
                if (balance < 0 && rules.negativeMustBeRecovered && rules.resetNegativeAtMonthEnd) {
                    movements += Movement(day, MovementKind.NEGATIVE_RESET, -balance, 0)
                    balance = 0
                }
            }
            day = day.plusDays(1)
        }

        return BankLedger(
            balance = balance,
            balanceBeforeToday = balance - (todayDelta ?: 0),
            todayDelta = todayDelta,
            movements = movements,
            daily = daily,
        )
    }

    /**
     * The anchor to store so that the balance *right now* becomes [target]:
     * today's work and today's manual corrections are applied on top of the anchor.
     */
    fun anchorFor(target: Int, data: StaccoData, today: LocalDate): Int {
        val todayKey = today.toString()
        val calendar = WorkCalendar.from(data)
        val todayWork = calendar.delta(today) ?: 0
        val todayManual = data.adjustments
            .filter { it.kind == AdjustmentKind.MANUAL && it.date == todayKey }
            .sumOf { it.minutes }
        return target - todayWork - todayManual
    }
}
