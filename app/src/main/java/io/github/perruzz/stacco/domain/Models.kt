package io.github.perruzz.stacco.domain

import kotlinx.serialization.Serializable

/**
 * The whole persisted state of Stacco. Everything the app shows is *derived* from this object,
 * so the hour bank can always be recomputed from scratch (no fragile incremental counters).
 */
@Serializable
data class StaccoData(
    val schemaVersion: Int = 1,
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

@Serializable
data class WorkSettings(
    /** Minutes that must be worked on a regular day (e.g. 8h = 480). */
    val dailyMinutes: Int = 8 * 60,
    /** Minimum minutes to be worked in a day: caps how much hour bank can be used daily. */
    val minDailyMinutes: Int = 7 * 60,
    /** Lunch break shorter than this is still counted as this long. */
    val minBreakMinutes: Int = 30,
    /** Lunch break beyond this is not "break" anymore: the excess is drawn from the hour bank. */
    val maxBreakMinutes: Int = 90,
    /** ISO-8601 days of week (1 = Monday … 7 = Sunday). */
    val workDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    /** Treat Italian national holidays as non-working days. */
    val italianHolidays: Boolean = true,
)

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
    /** Extra safety margin (in office days) added when deciding when to alert. */
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
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppearanceSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
)

@Serializable
enum class DayType { OFFICE, SMART, VACATION, SICK, HOLIDAY }

/** The four badge stamps of a day, in chronological order. */
enum class StampSlot { ENTRY, BREAK_OUT, BREAK_IN, EXIT }

@Serializable
data class DayRecord(
    /** Explicit type chosen by the user; null = derived from the work calendar. */
    val type: DayType? = null,
    /** Stamps as minutes since midnight. */
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

    val isEmpty: Boolean get() = type == null && !hasStamps && leaveMinutes == 0 && note.isBlank()

    fun clearStamps(): DayRecord = copy(entry = null, breakOut = null, breakIn = null, exit = null)

    /** The next stamp to record, following the natural order of a working day. */
    val nextSlot: StampSlot?
        get() = when {
            entry == null -> StampSlot.ENTRY
            exit != null -> null
            breakOut == null -> StampSlot.BREAK_OUT
            breakIn == null -> StampSlot.BREAK_IN
            else -> StampSlot.EXIT
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
