package io.github.perruzz.stacco.domain

import kotlin.math.max
import kotlin.math.min

/** Company rule for the lunch break: a shorter break still counts as [min] minutes. */
data class BreakRules(val min: Int)

/**
 * How the break is accounted for.
 *
 * @property actual measured break (live while it is still ongoing), null when not stamped.
 * @property deducted minutes subtracted from the presence time (never less than the minimum).
 * @property assumed true when no break was stamped and the default one is applied.
 * @property shortfall minutes lost because the break was shorter than the minimum.
 */
data class BreakInfo(
    val actual: Int?,
    val deducted: Int,
    val assumed: Boolean,
    val ongoing: Boolean,
    val shortfall: Int,
)

enum class DayPhase { NOT_STARTED, MORNING, ON_BREAK, AFTERNOON, DONE }

enum class DayIssue { MISSING_ENTRY, OUT_OF_ORDER, BREAK_IN_WITHOUT_OUT, BREAK_NOT_CLOSED }

/** Final outcome of a completed day. [delta] goes into the hour bank. */
data class DayResult(val worked: Int, val required: Int, val delta: Int)

/**
 * Everything the "Today" screen needs. All times are minutes since midnight of the day
 * (they may exceed 1440 when a day ends after midnight).
 */
data class DayProjection(
    val phase: DayPhase,
    val required: Int,
    val minimum: Int,
    val breakInfo: BreakInfo,
    /** Exit of a regular day (entry + required + break), or the end of the planned shift. */
    val standardExit: Int?,
    /** Earliest exit allowed by the minimum daily hours (fixed schedule only). */
    val minimumExit: Int?,
    /** Exit that uses the hour bank: earlier with a positive balance, later to recover a negative one. */
    val bankExit: Int?,
    /** Bank minutes consumed at [bankExit] (negative = minutes recovered into the bank). */
    val bankUsage: Int,
    /** Balance before today's work. */
    val bankBefore: Int,
    /** Minutes of presence worked so far (live). */
    val workedSoFar: Int,
    /** While on break: from when the minimum break is over. */
    val earliestReturn: Int?,
    /** Planned shift, if any. */
    val plannedStart: Int?,
    val plannedEnd: Int?,
    val result: DayResult?,
    val issues: Set<DayIssue>,
) {
    val isValid: Boolean get() = issues.isEmpty()
    val isShift: Boolean get() = plannedEnd != null

    fun targetExit(target: ExitTarget): Int? = when (target) {
        ExitTarget.STANDARD -> standardExit
        ExitTarget.BANK -> bankExit ?: standardExit
    }
}

object DayCalculator {

    fun issues(r: DayRecord): Set<DayIssue> {
        val issues = mutableSetOf<DayIssue>()
        if (r.entry == null && (r.breakOut != null || r.breakIn != null || r.exit != null)) {
            issues += DayIssue.MISSING_ENTRY
        }
        val sequence = listOfNotNull(r.entry, r.breakOut, r.breakIn, r.exit)
        if (sequence.zipWithNext().any { (a, b) -> b < a }) issues += DayIssue.OUT_OF_ORDER
        if (r.entry != null && r.exit != null && r.exit <= r.entry) issues += DayIssue.OUT_OF_ORDER
        if (r.breakIn != null && r.breakOut == null) issues += DayIssue.BREAK_IN_WITHOUT_OUT
        if (r.breakOut != null && r.breakIn == null && r.exit != null) issues += DayIssue.BREAK_NOT_CLOSED
        return issues
    }

    /**
     * @param required minutes required today (0 on a non-working day: everything is overtime).
     * @param now minutes since midnight, used for an ongoing break. Null for historical days.
     */
    fun breakInfo(r: DayRecord, rules: BreakRules, required: Int, now: Int?): BreakInfo {
        val out = r.breakOut
        val back = r.breakIn
        return when {
            out != null && back != null -> measured(back - out, rules, ongoing = false)
            out != null && r.exit == null -> measured((now ?: out) - out, rules, ongoing = true)
            else -> {
                // No break stamped: the company deducts the minimum break anyway.
                // On extra (non-working) days nothing is assumed.
                val assumed = if (required > 0) rules.min else 0
                BreakInfo(actual = null, deducted = assumed, assumed = true, ongoing = false, shortfall = 0)
            }
        }
    }

    private fun measured(rawActual: Int, rules: BreakRules, ongoing: Boolean): BreakInfo {
        val actual = max(0, rawActual)
        return BreakInfo(
            actual = actual,
            deducted = max(actual, rules.min),
            assumed = false,
            ongoing = ongoing,
            shortfall = if (ongoing) 0 else max(0, rules.min - actual),
        )
    }

    /** Result of a completed day, or null if the day is incomplete or inconsistent. */
    fun result(r: DayRecord, rules: BreakRules, required: Int): DayResult? {
        val entry = r.entry ?: return null
        val exit = r.exit ?: return null
        if (issues(r).isNotEmpty()) return null
        val b = breakInfo(r, rules, required, now = null)
        val worked = max(0, exit - entry - b.deducted)
        return DayResult(worked = worked, required = required, delta = worked - required)
    }

    /** Presence minutes worked up to [t] (break excluded). */
    fun workedAt(r: DayRecord, t: Int): Int {
        val entry = r.entry ?: return 0
        val end = r.exit?.let { min(t, it) } ?: t
        if (end <= entry) return 0
        val out = r.breakOut
        val back = r.breakIn
        return when {
            out == null || end <= out -> end - entry
            back == null || end <= back -> max(0, out - entry)
            else -> max(0, out - entry) + (end - back)
        }
    }

    fun phase(r: DayRecord, now: Int): DayPhase {
        val out = r.breakOut
        val back = r.breakIn
        return when {
            r.entry == null -> DayPhase.NOT_STARTED
            r.exit != null -> DayPhase.DONE
            out != null && back == null -> if (now >= out) DayPhase.ON_BREAK else DayPhase.MORNING
            back != null -> if (now >= back) DayPhase.AFTERNOON else DayPhase.ON_BREAK
            else -> DayPhase.MORNING
        }
    }

    /**
     * @param plannedStart / [plannedEnd] the planned shift (shift mode). With a shift the target exit
     * is simply the end of the shift and the hour bank cannot shorten the day.
     */
    fun project(
        r: DayRecord,
        rules: BreakRules,
        required: Int,
        minimum: Int,
        bankBefore: Int,
        now: Int,
        plannedStart: Int? = null,
        plannedEnd: Int? = null,
    ): DayProjection {
        val issues = issues(r)
        val b = breakInfo(r, rules, required, now)
        val phase = phase(r, now)
        val entry = r.entry
        val out = r.breakOut
        val earliestReturn = if (phase == DayPhase.ON_BREAK && out != null && r.breakIn == null && rules.min > 0) {
            out + rules.min
        } else {
            null
        }

        if (entry == null) {
            return DayProjection(
                phase = DayPhase.NOT_STARTED, required = required, minimum = minimum, breakInfo = b,
                standardExit = plannedEnd, minimumExit = null, bankExit = null, bankUsage = 0,
                bankBefore = bankBefore, workedSoFar = 0, earliestReturn = null,
                plannedStart = plannedStart, plannedEnd = plannedEnd, result = null, issues = issues,
            )
        }

        val standardExit: Int
        val minimumExit: Int?
        val bankExit: Int?
        val bankUsage: Int
        if (plannedEnd != null) {
            standardExit = plannedEnd
            minimumExit = null
            bankExit = null
            bankUsage = 0
        } else {
            standardExit = entry + required + b.deducted
            minimumExit = entry + minimum + b.deducted
            // Worked minutes that bring the bank back to zero, never below the daily minimum.
            val bankTargetWorked = max(minimum, required - bankBefore)
            bankExit = entry + b.deducted + bankTargetWorked
            bankUsage = required - bankTargetWorked
        }

        return DayProjection(
            phase = phase,
            required = required,
            minimum = minimum,
            breakInfo = b,
            standardExit = standardExit,
            minimumExit = minimumExit,
            bankExit = bankExit,
            bankUsage = bankUsage,
            bankBefore = bankBefore,
            workedSoFar = workedAt(r, now),
            earliestReturn = earliestReturn,
            plannedStart = plannedStart,
            plannedEnd = plannedEnd,
            result = if (phase == DayPhase.DONE) result(r, rules, required) else null,
            issues = issues,
        )
    }

    /** Exit time if the user clocked in right now (for the empty state preview). */
    fun previewExit(now: Int, required: Int, rules: BreakRules): Int =
        now + required + if (required > 0) rules.min else 0
}
