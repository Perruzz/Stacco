package io.github.perruzz.stacco.domain

import kotlinx.serialization.Serializable

/**
 * The whole persisted state of Stacco. Everything the app shows is *derived* from this object,
 * so the hour bank can always be recomputed from scratch (no fragile incremental counters).
 */
@Serializable
data class StaccoData(
    val schemaVersion: Int = 2,
    val onboardingDone: Boolean = false,
    val profile: Profile = Profile(),
    val work: WorkSettings = WorkSettings(),
    val bank: BankSettings = BankSettings(),
    val smart: SmartSettings = SmartSettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    /** Day records keyed by ISO date (yyyy-MM-dd). */
    val days: Map<String, DayRecord> = emptyMap(),
    val adjustments: List<BankAdjustment> = emptyList(),
    val alerts: AlertsState = AlertsState(),
)

@Serializable
data class Profile(val name: String = "")

/** How the user works: a fixed daily schedule with a lunch break, or planned shifts. */
@Serializable
enum class WorkMode { FIXED, SHIFTS }

@Serializable
data class WorkSettings(
    val mode: WorkMode = WorkMode.FIXED,
    /** Minutes that must be worked on a regular day (e.g. 8h = 480). */
    val dailyMinutes: Int = 8 * 60,
    /** Minimum minutes to be worked in a day: caps how much hour bank can be used daily. */
    val minDailyMinutes: Int = 7 * 60,
    /** Lunch break shorter than this is still counted as this long. */
    val minBreakMinutes: Int = 30,
    /** ISO-8601 days of week (1 = Monday … 7 = Sunday). */
    val workDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    /** Treat Italian national holidays as non-working days (fixed schedule only). */
    val italianHolidays: Boolean = true,
    /** Shift templates (shift mode). */
    val shifts: List<ShiftTemplate> = emptyList(),
)

/**
 * A shift such as "Morning 07:00–14:00" or "Night 21:00–07:00" (ends the next day).
 * Times are minutes since midnight of the day the shift starts.
 */
@Serializable
data class ShiftTemplate(
    val id: String,
    val name: String,
    val start: Int,
    val end: Int,
    val breakMinutes: Int = 0,
    val color: Int = 0,
) {
    val crossesMidnight: Boolean get() = end <= start
    val endAbsolute: Int get() = if (crossesMidnight) end + 1440 else end
    val durationMinutes: Int get() = endAbsolute - start
    val workMinutes: Int get() = (durationMinutes - breakMinutes).coerceAtLeast(0)
    val code: String get() = name.trim().take(1).uppercase().ifEmpty { "?" }
}

@Serializable
enum class ExpiryPeriod(val months: Int) {
    NEVER(0), MONTHLY(1), BIMONTHLY(2), QUARTERLY(3), FOUR_MONTHLY(4), SEMIANNUAL(6), ANNUAL(12)
}

@Serializable
data class BankSettings(
    /** A positive balance is wiped at the end of each of these periods. */
    val positiveExpiry: ExpiryPeriod = ExpiryPeriod.QUARTERLY,
    /** The month cannot be closed with a negative balance: enables recovery alerts. */
    val negativeMustBeRecovered: Boolean = true,
    /** If a month still closes negative: true = reset to zero (handled by HR), false = carry over. */
    val resetNegativeAtMonthEnd: Boolean = true,
    /** Extra safety margin (in office days) added to the days strictly needed before alerting. */
    val alertLeadDays: Int = 3,
)

@Serializable
enum class SmartPeriod { WEEK, MONTH }

@Serializable
data class SmartSettings(
    val allowance: Int = 10,
    val period: SmartPeriod = SmartPeriod.MONTH,
)

@Serializable
enum class ExitTarget { STANDARD, BANK }

@Serializable
data class NotificationSettings(
    val exitReminder: Boolean = true,
    val exitReminderLeadMinutes: Int = 5,
    val exitTarget: ExitTarget = ExitTarget.STANDARD,
    val liveNotification: Boolean = true,
    val bankAlerts: Boolean = true,
    /** Shift mode: remind before the shift starts. */
    val shiftStartReminder: Boolean = true,
    val shiftStartLeadMinutes: Int = 30,
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppearanceSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
)

@Serializable
enum class DayType { OFFICE, SMART, TRAVEL, VACATION, SICK, HOLIDAY }

/** The four badge stamps of a day, in chronological order. */
enum class StampSlot { ENTRY, BREAK_OUT, BREAK_IN, EXIT }

@Serializable
data class DayRecord(
    /** Explicit type chosen by the user; null = derived from the schedule. */
    val type: DayType? = null,
    /** Planned shift (shift mode). */
    val shiftId: String? = null,
    /**
     * Stamps as minutes since midnight of this day. Stamps after midnight of a shift that
     * crosses it are stored as 1440 + minutes.
     */
    val entry: Int? = null,
    val breakOut: Int? = null,
    val breakIn: Int? = null,
    val exit: Int? = null,
    /** Hourly leave (permesso/ROL) that reduces the required time of the day. */
    val leaveMinutes: Int = 0,
    val note: String = "",
) {
    operator fun get(slot: StampSlot): Int? = when (slot) {
        StampSlot.ENTRY -> entry
        StampSlot.BREAK_OUT -> breakOut
        StampSlot.BREAK_IN -> breakIn
        StampSlot.EXIT -> exit
    }

    fun with(slot: StampSlot, minutes: Int?): DayRecord = when (slot) {
        StampSlot.ENTRY -> copy(entry = minutes)
        StampSlot.BREAK_OUT -> copy(breakOut = minutes)
        StampSlot.BREAK_IN -> copy(breakIn = minutes)
        StampSlot.EXIT -> copy(exit = minutes)
    }

    val hasStamps: Boolean get() = entry != null || breakOut != null || breakIn != null || exit != null

    val isEmpty: Boolean get() = type == null && shiftId == null && !hasStamps && leaveMinutes == 0 && note.isBlank()

    fun clearStamps(): DayRecord = copy(entry = null, breakOut = null, breakIn = null, exit = null)

    /** Latest stamp recorded, if any. */
    val lastStamp: Int? get() = listOfNotNull(entry, breakOut, breakIn, exit).maxOrNull()

    /**
     * The next stamp to record, following the natural order of a working day.
     * When no lunch break is expected the day goes straight from entry to exit.
     */
    fun nextSlot(expectsBreak: Boolean): StampSlot? = when {
        entry == null -> StampSlot.ENTRY
        exit != null -> null
        breakOut == null -> if (expectsBreak) StampSlot.BREAK_OUT else StampSlot.EXIT
        breakIn == null -> StampSlot.BREAK_IN
        else -> StampSlot.EXIT
    }

    /** A stamp of the "other" path: exit without break, or a break when none is expected. */
    fun alternativeSlot(expectsBreak: Boolean): StampSlot? = when {
        entry == null || exit != null || breakOut != null -> null
        expectsBreak -> StampSlot.EXIT
        else -> StampSlot.BREAK_OUT
    }

    companion object {
        val EMPTY = DayRecord()
    }
}

@Serializable
enum class AdjustmentKind {
    /** Anchors the balance to an absolute value at the start of [BankAdjustment.date]. */
    SET_BALANCE,

    /** A manual correction (+/-) applied on [BankAdjustment.date]. */
    MANUAL,
}

@Serializable
data class BankAdjustment(
    val id: Long,
    val date: String,
    val kind: AdjustmentKind,
    val minutes: Int,
    val note: String = "",
)

@Serializable
data class AlertsState(
    val negativeLastDate: String? = null,
    val negativePeriod: String? = null,
    val expiryLastDate: String? = null,
    val expiryPeriod: String? = null,
)
