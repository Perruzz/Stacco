package io.github.perruzz.stacco.ui.calendar

import android.content.Context
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.export.CsvLabels
import io.github.perruzz.stacco.ui.today.label
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** The export's words in the app language (the activity context already speaks it). */
fun csvLabels(context: Context): CsvLabels {
    val res = context.resources
    val locale = res.configuration.locales[0]
    val dates = DateTimeFormatter.ofPattern(res.getString(R.string.csv_date_pattern), locale)
    return CsvLabels(
        headers = res.getStringArray(R.array.csv_headers).toList(),
        type = { res.getString(it.label) },
        holiday = { res.getString(it.label) },
        date = { dates.format(it) },
        weekday = { it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { c -> c.titlecase(locale) } },
        total = res.getString(R.string.csv_total),
        officeDays = res.getString(R.string.csv_office_days),
        smartDays = res.getString(R.string.csv_smart_days),
        travelDays = res.getString(R.string.csv_travel_days),
        vacationDays = res.getString(R.string.csv_vacation_days),
        sickDays = res.getString(R.string.csv_sick_days),
        leaveHours = res.getString(R.string.csv_leave_hours),
        bankOn = { res.getString(R.string.csv_bank_on, dates.format(it)) },
    )
}
