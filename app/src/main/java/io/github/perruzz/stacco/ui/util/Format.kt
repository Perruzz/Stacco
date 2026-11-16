package io.github.perruzz.stacco.ui.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

const val MINUS = "−"

object Fmt {

    /** "08:30" — minutes may exceed a day, the clock simply wraps. */
    fun time(minutes: Int?): String {
        if (minutes == null) return "--:--"
        val m = Math.floorMod(minutes, 1440)
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)
    }

    /** Like [time] but marks projections that fall on the next day: "00:14 +1". */
    fun timeDay(minutes: Int?): String = if (nextDay(minutes)) "${time(minutes)} +1" else time(minutes)

    /** True when a projected time falls on the next day. */
    fun nextDay(minutes: Int?): Boolean = minutes != null && minutes >= 1440

    /** "7h 30m", "45m", "8h"; with [signed] "+1h 05m" / "−45m". */
    fun duration(minutes: Int, signed: Boolean = false): String {
        val sign = when {
            minutes < 0 -> MINUS
            signed && minutes > 0 -> "+"
            else -> ""
        }
        val a = abs(minutes)
        val h = a / 60
        val m = a % 60
        val body = when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> String.format(Locale.ROOT, "%dh %02dm", h, m)
        }
        return sign + body
    }

    /** Very short signed delta for calendar cells: "+12m", "−1h05", "+2h". */
    fun compact(minutes: Int): String {
        if (minutes == 0) return "\u00B10"
        val sign = if (minutes < 0) MINUS else "+"
        val a = abs(minutes)
        val h = a / 60
        val m = a % 60
        return sign + when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> String.format(Locale.ROOT, "%dh%02d", h, m)
        }
    }

    /** Shift applied by the stamp dial: "−3 min", "+1 min", "−1h 05m". */
    fun offset(minutes: Int): String = when {
        minutes == 0 -> "0 min"
        abs(minutes) >= 60 -> duration(minutes, signed = true)
        minutes > 0 -> "+$minutes min"
        else -> "$MINUS${-minutes} min"
    }

    /** "2:14:05" or "14:05" for a countdown. */
    fun countdown(totalSeconds: Int): String {
        val s = abs(totalSeconds)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", m, sec)
        }
    }

    fun longDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(date).capitalized(locale)

    /** "martedì 10 novembre": the long date as it reads inside a sentence. */
    fun longDateInline(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(date)

    fun shortDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEE d MMM", locale).format(date).capitalized(locale)

    fun dayMonth(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("d MMMM", locale).format(date)

    fun monthYear(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month).capitalized(locale)

    fun monthName(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)

    fun weekdayNarrow(dow: java.time.DayOfWeek, locale: Locale = Locale.getDefault()): String =
        dow.getDisplayName(TextStyle.SHORT_STANDALONE, locale).take(3).capitalized(locale)

    private fun String.capitalized(locale: Locale) = replaceFirstChar { it.titlecase(locale) }
}
