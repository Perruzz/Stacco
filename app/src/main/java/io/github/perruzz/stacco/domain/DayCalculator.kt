package io.github.perruzz.stacco.domain

import kotlin.math.max
import kotlin.math.min

/** Company rules for the lunch break. */
data class BreakRules(val min: Int, val max: Int) {
    val effectiveMax: Int get() = max(max, min)

    companion object {
        fun from(work: WorkSettings) = BreakRules(work.minBreakMinutes, work.maxBreakMinutes)
    }
}

/**
 * How the lunch break is accounted for.
 *
 * @property actual measured break (live while it is still ongoing), null when not stamped.
 * @property deducted minutes subtracted from the presence time (never less than the minimum).
 * @property counted break used for the "standard" exit (never more than the maximum).
 * @property assumed true when no break was stamped and the default one is applied.
 * @property shortfall minutes lost because the break was shorter than the minimum.
 * @property excess minutes beyond the maximum: they are drawn from the hour bank.
 */
data class BreakInfo(
    val actual: Int?,
    val deducted: Int,
    val counted: Int,
    val assumed: Boolean,
    val ongoing: Boolean,
    val shortfall: Int,
    val excess: Int,
)

enum class DayPhase { NOT_STARTED, MORNING, ON_BREAK, AFTERNOON, DONE }

enum class DayIssue { MISSING_ENTRY, OUT_OF_ORDER, BREAK_IN_WITHOUT_OUT, BREAK_NOT_CLOSED }

/** Final outcome of a completed day. [delta] goes into the hour bank. */
data class DayResult(val worked: Int, val required: Int, val delta: Int)

/**
 * Everything the "Today" screen needs. All times are minutes since midnight
 * (they may exceed 1440 when a day would end after midnight).
 */
data class DayProjection(
    val phase: DayPhase,
    val required: Int,
    val minimum: Int,
    val breakInfo: BreakInfo,
    /** Exit with a regular day: entry + required + counted break. */
    val standardExit: Int?,
    /** True when a huge break forced the standard exit later to respect the minimum hours. */
    val standardLimitedByMinimum: Boolean,
    /** Earliest exit allowed by the minimum daily hours. */
    val minimumExit: Int?,
    /** Exit that uses the hour bank: earlier with a positive balance, later to recover a negative one. */
    val bankExit: Int?,
    /** Bank minutes consumed at [bankExit] (negative = minutes recovered into the bank). */
    val bankUsage: Int,
    /** Balance before today's work. */
    val bankBefore: Int,
    /** Minutes of presence worked so far (live). */
    val workedSoFar: Int,
    /** While on break: earliest return (minimum break) .. latest return (maximum break). */
    val returnWindow: IntRange?,
    val result: DayResult?,
    val issues: Set<DayIssue>,
) {
    val isValid: Boolean get() = issues.isEmpty()

    /** Bank delta if leaving exactly at the standard exit (negative when the break was too long). */
    val standardDelta: Int
        get() = when {
            standardExit == null -> 0
            standardLimitedByMinimum -> minimum - required
            else -> -breakInfo.excess
        }

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
                BreakInfo(
                    actual = null, deducted = assumed, counted = assumed, assumed = true,
                    ongoing = false, shortfall = 0, excess = 0,
                )
            }
        }
    }

    private fun measured(rawActual: Int, rules: BreakRules, ongoing: Boolean): BreakInfo {
        val actual = max(0, rawActual)
        val deducted = max(actual, rules.min)
        return BreakInfo(
            actual = actual,
            deducted = deducted,
            counted = min(deducted, rules.effectiveMax),
            assumed = false,
            ongoing = ongoing,
            shortfall = if (ongoing) 0 else max(0, rules.min - actual),
            excess = max(0, actual - rules.effectiveMax),
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

    fun project(
        r: DayRecord,
        rules: BreakRules,
        required: Int,
        minimum: Int,
        bankBefore: Int,
        now: Int,
    ): DayProjection {
        val issues = issues(r)
        val b = breakInfo(r, rules, required, now)
        val phase = phase(r, now)
        val entry = r.entry
        if (entry == null) {
            return DayProjection(
                phase = DayPhase.NOT_STARTED, required = required, minimum = minimum, breakInfo = b,
                standardExit = null, standardLimitedByMinimum = false, minimumExit = null,
                bankExit = null, bankUsage = 0, bankBefore = bankBefore, workedSoFar = 0,
                returnWindow = null, result = null, issues = issues,
            )
        }

        val minimumExit = entry + minimum + b.deducted
        val workedAtStandard = required - b.excess
        val limited = required > 0 && workedAtStandard < minimum
        val standardExit = if (limited) minimumExit else entry + required + b.counted

        // Target worked minutes to bring the bank to zero, never below the daily minimum.
        val bankTargetWorked = max(minimum, required - bankBefore)
        val bankExit = entry + b.deducted + bankTargetWorked

        val out = r.breakOut
        val returnWindow = if (phase == DayPhase.ON_BREAK && out != null && r.breakIn == null) {
            (out + rules.min)..(out + rules.effectiveMax)
        } else {
            null
        }

        return DayProjection(
            phase = phase,
            required = required,
            minimum = minimum,
            breakInfo = b,
            standardExit = standardExit,
            standardLimitedByMinimum = limited,
            minimumExit = minimumExit,
            bankExit = bankExit,
            bankUsage = required - bankTargetWorked,
            bankBefore = bankBefore,
            workedSoFar = workedAt(r, now),
            returnWindow = returnWindow,
            result = if (phase == DayPhase.DONE) result(r, rules, required) else null,
            issues = issues,
        )
    }

    /** Exit time if the user clocked in right now (for the empty state preview). */
    fun previewExit(now: Int, required: Int, rules: BreakRules): Int =
        now + required + if (required > 0) rules.min else 0
}
