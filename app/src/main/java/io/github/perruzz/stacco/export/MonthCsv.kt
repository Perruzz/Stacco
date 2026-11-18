package io.github.perruzz.stacco.export

import io.github.perruzz.stacco.domain.BankCalculator
import io.github.perruzz.stacco.domain.DayCalculator
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.Holiday
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.WorkCalendar
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** The words of the export, taken from the app resources (this file stays free of Android code). */
class CsvLabels(
    /** Date, weekday, type, shift, entry, break out, break in, exit, leave, break, worked, planned, balance, note. */
    val headers: List<String>,
    val type: (DayType?) -> String,
    val holiday: (Holiday) -> String,
    val date: (LocalDate) -> String,
    val weekday: (LocalDate) -> String,
    val total: String,
    val officeDays: String,
    val smartDays: String,
    val travelDays: String,
    val vacationDays: String,
    val sickDays: String,
    val leaveHours: String,
    val bankOn: (LocalDate) -> String,
)

/**
 * One month as a spreadsheet: a row per day with the real stamps and the counted hours, then the
 * totals (the same as the calendar summary) and the hour bank at the end of the month.
 * Semicolon separated with a BOM, so spreadsheets in Italian open it straight away.
 */
object MonthCsv {

    private const val SEPARATOR = ";"
    private const val BOM = "\uFEFF"

    /** "8:16", "-1:05", "+0:30". */
    fun hours(minutes: Int, signed: Boolean = false): String {
        val sign = when {
            minutes < 0 -> "-"
            signed && minutes > 0 -> "+"
            else -> ""
        }
        val m = abs(minutes)
        return "$sign${m / 60}:${(m % 60).toString().padStart(2, '0')}"
    }

    /** "08:30", or "07:00 (+1)" for a stamp after midnight of a night shift. */
    fun clock(minutes: Int?): String {
        if (minutes == null) return ""
        val inDay = Math.floorMod(minutes, 1440)
        val text = "${(inDay / 60).toString().padStart(2, '0')}:${(inDay % 60).toString().padStart(2, '0')}"
        return if (minutes >= 1440) "$text (+1)" else text
    }

    private fun cell(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    fun build(data: StaccoData, month: YearMonth, labels: CsvLabels, today: LocalDate = LocalDate.now()): String {
        val calendar = WorkCalendar.from(data)
        val rows = mutableListOf(labels.headers)
        var worked = 0
        var required = 0
        var balance = 0
        var office = 0
        var smart = 0
        var travel = 0
        var vacation = 0
        var sick = 0
        var leave = 0

        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val record = calendar.record(date)
            val type = calendar.effectiveType(date)
            val holiday = calendar.holiday(date)
            val isOffice = type == DayType.OFFICE
            val result = if (isOffice) calendar.result(date) else null
            val planned = if (isOffice) calendar.required(date) else 0
            val pause = if (result != null) {
                calendar.counted(date)?.let { DayCalculator.breakInfo(it, calendar.breakRules(date), planned, null).deducted }
            } else {
                null
            }
            val typeText = when {
                holiday != null && (type == null || type == DayType.HOLIDAY) -> labels.holiday(holiday)
                else -> labels.type(type)
            }
            rows += listOf(
                labels.date(date),
                labels.weekday(date),
                typeText,
                calendar.shift(date)?.name.orEmpty(),
                clock(record?.entry),
                clock(record?.breakOut),
                clock(record?.breakIn),
                clock(record?.exit),
                record?.leaveMinutes?.takeIf { it > 0 && isOffice }?.let { hours(it) }.orEmpty(),
                pause?.let { hours(it) }.orEmpty(),
                result?.worked?.let { hours(it) }.orEmpty(),
                if (planned > 0) hours(planned) else "",
                result?.delta?.let { hours(it, signed = true) }.orEmpty(),
                record?.note.orEmpty(),
            )

            if (result != null) {
                worked += result.worked
                balance += result.delta
            }
            required += planned
            if (isOffice && record?.hasStamps == true) office++
            when (record?.type) {
                DayType.SMART -> smart++
                DayType.TRAVEL -> travel++
                DayType.VACATION -> vacation++
                DayType.SICK -> sick++
                else -> Unit
            }
            if (isOffice) leave += record?.leaveMinutes ?: 0
        }

        val blank = List(labels.headers.size) { "" }
        rows += blank
        rows += blank.toMutableList().also {
            it[0] = labels.total
            it[10] = hours(worked)
            it[11] = hours(required)
            it[12] = hours(balance, signed = true)
        }
        rows += listOf(labels.officeDays, office.toString())
        rows += listOf(labels.smartDays, smart.toString())
        rows += listOf(labels.travelDays, travel.toString())
        rows += listOf(labels.vacationDays, vacation.toString())
        rows += listOf(labels.sickDays, sick.toString())
        rows += listOf(labels.leaveHours, hours(leave))
        val bankDay = minOf(month.atEndOfMonth(), today)
        if (!bankDay.isBefore(month.atDay(1))) {
            rows += listOf(labels.bankOn(bankDay), hours(BankCalculator.compute(data, bankDay).balance, signed = true))
        }

        return BOM + rows.joinToString("\r\n") { row -> row.joinToString(SEPARATOR) { cell(it) } } + "\r\n"
    }
}
