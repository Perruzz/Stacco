package io.github.perruzz.stacco.domain

/**
 * Rounding of badge times. Only the *counted* times are rounded: the stamps shown and stored are
 * the real ones, and the timer, the day, the calendar, the bank and the exports all count the same
 * rounded values, so they never disagree.
 */
object Rounding {

    /**
     * [minutes] rounded to a multiple of [step]. With [tolerance], a time within that many minutes
     * past a step (rounding up) or before it (rounding down) counts as that step: arriving at 8:04
     * with 5 minutes of tolerance is still 8:00, leaving at 16:56 is already 17:00.
     */
    fun round(minutes: Int, step: Int, direction: RoundingDirection, tolerance: Int = 0): Int {
        if (step <= 1) return minutes
        val down = Math.floorDiv(minutes, step) * step
        if (down == minutes) return minutes
        val up = down + step
        return when (direction) {
            RoundingDirection.UP -> if (minutes - down <= tolerance) down else up
            RoundingDirection.DOWN -> if (up - minutes <= tolerance) up else down
            RoundingDirection.NEAREST -> if (minutes - down < up - minutes) down else up
        }
    }

    /** The day as it is counted. Falls back to the real stamps if rounding would reorder them. */
    fun counted(record: DayRecord, settings: RoundingSettings): DayRecord {
        if (!settings.enabled) return record
        val entry = record.entry?.let { round(it, settings.stepMinutes, settings.entry, settings.toleranceMinutes) }
        val exit = record.exit?.let { round(it, settings.stepMinutes, settings.exit, settings.toleranceMinutes) }
        val rounded = record.copy(entry = entry, exit = exit)
        val sequence = listOfNotNull(rounded.entry, rounded.breakOut, rounded.breakIn, rounded.exit)
        val ordered = sequence.zipWithNext().all { (a, b) -> a <= b } && (entry == null || exit == null || exit > entry)
        return if (ordered) rounded else record
    }
}
