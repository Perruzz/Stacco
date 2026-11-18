package io.github.perruzz.stacco.export

import io.github.perruzz.stacco.domain.BankCalculator
import io.github.perruzz.stacco.domain.DayCalculator
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.Holiday
import io.github.perruzz.stacco.domain.OnCall
import io.github.perruzz.stacco.domain.RecoveryCalculator
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.WorkCalendar
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** The words of the export, taken from the app resources (this file stays free of Android code). */
class CsvLabels(
    /** Date, weekday, type, shift, entry, break out, break in, exit, leave, break, worked, planned, balance, note. */
    val headers: List<String>,
    /** Counted entry and exit, added after the real stamps when badge times are rounded. */
    val countedHeaders: List<String>,
    /** On call, interventions (real time), credited, recovery taken: before the note, with on-call duty on. */
    val onCallHeaders: List<String>,
    /** The on-call column of a day on call. */
    val yes: String,
    val onCallDays: String,
    val creditedHours: String,
    val recoveryHours: String,
    val recoveryOn: (LocalDate) -> String,
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
        // With rounding the hours are counted from the rounded times: show them next to the real
        // stamps, so that every row adds up on its own.
        val rounded = data.work.rounding.enabled
        // On-call duty adds its own columns just before the note.
        val onCall = data.onCall.enabled
        fun shape(row: List<String>, entry: String, exit: String, duty: List<String>): List<String> {
            val withDuty = if (onCall) row.dropLast(1) + duty + row.last() else row
            return if (rounded) withDuty.take(8) + listOf(entry, exit) + withDuty.drop(8) else withDuty
        }
        val rows = mutableListOf(shape(labels.headers, labels.countedHeaders[0], labels.countedHeaders[1], labels.onCallHeaders))
        var worked = 0
        var required = 0
        var balance = 0
        var office = 0
        var smart = 0
        var travel = 0
        var vacation = 0
        var sick = 0
        var leave = 0
        var onCallDays = 0
        var interventionTime = 0
        var credited = 0
        var recovery = 0

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
            val counted = calendar.counted(date)
            // The permit as it is taken from the vacation hours (never more than the day).
            val permit = if (isOffice && (record?.leaveMinutes ?: 0) > 0) VacationCalculator.cost(calendar, date) else 0
            val interventions = record?.interventions.orEmpty().mapNotNull { OnCall.realMinutes(date, it) }
            val dayCredit = interventions.sumOf { OnCall.credited(it) }
            val dayRecovery = OnCall.recoveryCost(calendar, date)
            rows += shape(
                listOf(
                    labels.date(date),
                    labels.weekday(date),
                    typeText,
                    calendar.shift(date)?.name.orEmpty(),
                    clock(record?.entry),
                    clock(record?.breakOut),
                    clock(record?.breakIn),
                    clock(record?.exit),
                    if (permit > 0) hours(permit) else "",
                    pause?.let { hours(it) }.orEmpty(),
                    result?.worked?.let { hours(it) }.orEmpty(),
                    if (planned > 0) hours(planned) else "",
                    result?.delta?.let { hours(it, signed = true) }.orEmpty(),
                    record?.note.orEmpty(),
                ),
                clock(counted?.entry),
                clock(counted?.exit),
                listOf(
                    if (record?.onCall == true) labels.yes else "",
                    if (interventions.isNotEmpty()) hours(interventions.sum()) else "",
                    if (dayCredit > 0) hours(dayCredit) else "",
                    if (dayRecovery > 0) hours(dayRecovery) else "",
                ),
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
            leave += permit
            if (record?.onCall == true) onCallDays++
            interventionTime += interventions.sum()
            credited += dayCredit
            recovery += dayRecovery
        }

        val blank = List(labels.headers.size) { "" }
        rows += shape(blank, "", "", List(labels.onCallHeaders.size) { "" })
        rows += shape(
            blank.toMutableList().also {
                it[0] = labels.total
                it[10] = hours(worked)
                it[11] = hours(required)
                it[12] = hours(balance, signed = true)
            },
            "",
            "",
            listOf(onCallDays.toString(), hours(interventionTime), hours(credited), hours(recovery)),
        )
        rows += listOf(labels.officeDays, office.toString())
        rows += listOf(labels.smartDays, smart.toString())
        rows += listOf(labels.travelDays, travel.toString())
        rows += listOf(labels.vacationDays, vacation.toString())
        rows += listOf(labels.sickDays, sick.toString())
        rows += listOf(labels.leaveHours, hours(leave))
        if (onCall) {
            rows += listOf(labels.onCallDays, onCallDays.toString())
            rows += listOf(labels.creditedHours, hours(credited))
            rows += listOf(labels.recoveryHours, hours(recovery))
        }
        val bankDay = minOf(month.atEndOfMonth(), today)
        if (!bankDay.isBefore(month.atDay(1))) {
            rows += listOf(labels.bankOn(bankDay), hours(BankCalculator.compute(data, bankDay).balance, signed = true))
            if (onCall) rows += listOf(labels.recoveryOn(bankDay), hours(RecoveryCalculator.compute(data, bankDay).balance))
        }

        return BOM + rows.joinToString("\r\n") { row -> row.joinToString(SEPARATOR) { cell(it) } } + "\r\n"
    }
}
