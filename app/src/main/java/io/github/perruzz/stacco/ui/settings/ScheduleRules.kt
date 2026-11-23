package io.github.perruzz.stacco.ui.settings

import io.github.perruzz.stacco.ui.components.DialogButtons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.Rounding
import io.github.perruzz.stacco.domain.RoundingDirection
import io.github.perruzz.stacco.domain.RoundingSettings
import io.github.perruzz.stacco.domain.Schedule
import io.github.perruzz.stacco.domain.ScheduleHistory
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.WorkMode
import io.github.perruzz.stacco.domain.WorkSettings
import io.github.perruzz.stacco.ui.components.DurationPickerDialog
import io.github.perruzz.stacco.ui.components.IconBubble
import io.github.perruzz.stacco.ui.components.RollingText
import io.github.perruzz.stacco.ui.components.SectionLabel
import io.github.perruzz.stacco.ui.components.SegmentLabel
import io.github.perruzz.stacco.ui.components.SegmentPadding
import io.github.perruzz.stacco.ui.components.SegmentRowModifier
import io.github.perruzz.stacco.ui.components.StaccoDialog
import io.github.perruzz.stacco.ui.components.bouncyClickable
import io.github.perruzz.stacco.ui.util.Fmt
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle

/** Working days in the user's week order. */
private fun weekOrder(work: WorkSettings): List<DayOfWeek> {
    val first = Engine.firstDayOfWeek()
    return (0 until 7).map { first.plus(it.toLong()) }.filter { it.value in work.workDays }
}

@Composable
private fun dayName(day: DayOfWeek, style: TextStyle = TextStyle.FULL_STANDALONE): String {
    val locale = LocalConfiguration.current.locales[0]
    return day.getDisplayName(style, locale).replaceFirstChar { it.titlecase(locale) }
}

/** "Same every day" or the weekdays that differ, e.g. "Ven 4h". */
@Composable
fun weekHoursSummary(work: WorkSettings): String {
    val custom = weekOrder(work).filter { work.dailyByDay[it.value]?.let { minutes -> minutes != work.dailyMinutes } == true }
    if (custom.isEmpty()) return stringResource(R.string.settings_daily_by_day_same)
    return custom.map { "${dayName(it, TextStyle.SHORT_STANDALONE)} ${Fmt.duration(work.dailyFor(it))}" }.joinToString(" · ")
}

/** Hours per working weekday: each day can differ from the daily hours (short Friday, part-time). */
@Composable
fun WeekHoursDialog(work: WorkSettings, onDismiss: () -> Unit, onSave: (Map<Int, Int>) -> Unit) {
    val days = weekOrder(work)
    var hours by remember { mutableStateOf(days.associate { it.value to work.dailyFor(it) }) }
    var picking by remember { mutableStateOf<DayOfWeek?>(null) }
    val total = hours.values.sum()

    StaccoDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.ViewWeek, tint = MaterialTheme.colorScheme.primary, container = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.week_hours_title), style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.week_hours_sub, Fmt.duration(work.flexMinutes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { day ->
                val minutes = hours[day.value] ?: work.dailyMinutes
                val custom = minutes != work.dailyMinutes
                val background by animateColorAsState(
                    if (custom) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = "week-day",
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(background)
                        .bouncyClickable(pressed = 0.97f) { picking = day }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dayName(day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    RollingText(
                        Fmt.duration(minutes),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (custom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.week_hours_total, Fmt.duration(total)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        DialogButtons(
            onDismiss = onDismiss,
            onConfirm = { onSave(hours.filterValues { it != work.dailyMinutes }) },
            confirmLabel = stringResource(R.string.action_save),
            onExtra = { hours = days.associate { it.value to work.dailyMinutes } },
            extraLabel = stringResource(R.string.action_restore),
            extraDestructive = false,
            extraEnabled = hours.values.any { it != work.dailyMinutes },
        )
    }

    picking?.let { day ->
        DurationPickerDialog(
            title = dayName(day),
            initialMinutes = hours[day.value] ?: work.dailyMinutes,
            icon = Icons.Rounded.Schedule,
            maxHours = 16,
            onDismiss = { picking = null },
            onConfirm = { minutes ->
                hours = hours + (day.value to minutes)
                picking = null
            },
        )
    }
}

/** The company rounding rules, with a live example. */
@Composable
fun RoundingSection(rounding: RoundingSettings, onChange: (RoundingSettings) -> Unit) {
    SectionLabel(stringResource(R.string.settings_rounding))
    Text(
        stringResource(R.string.settings_rounding_sub),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 12.dp),
    )
    val steps = listOf(0, 5, 10, 15, 30)
    SingleChoiceSegmentedButtonRow(SegmentRowModifier) {
        steps.forEachIndexed { index, step ->
            SegmentedButton(
                selected = rounding.stepMinutes == step || (step == 0 && !rounding.enabled),
                onClick = { onChange(rounding.copy(stepMinutes = step, toleranceMinutes = rounding.toleranceMinutes.coerceAtMost((step - 1).coerceAtLeast(0)))) },
                shape = SegmentedButtonDefaults.itemShape(index, steps.size),
                modifier = Modifier.fillMaxHeight(),
                contentPadding = SegmentPadding,
                icon = {},
            ) {
                SegmentLabel(
                    if (step == 0) stringResource(R.string.rounding_off) else stringResource(R.string.rounding_minutes, step),
                    MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
    AnimatedVisibility(rounding.enabled, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column {
            Spacer(Modifier.height(14.dp))
            DirectionChoice(
                title = stringResource(R.string.rounding_entry),
                icon = Icons.AutoMirrored.Rounded.Login,
                value = rounding.entry,
                onChange = { onChange(rounding.copy(entry = it)) },
            )
            Spacer(Modifier.height(12.dp))
            DirectionChoice(
                title = stringResource(R.string.rounding_exit),
                icon = Icons.AutoMirrored.Rounded.Logout,
                value = rounding.exit,
                onChange = { onChange(rounding.copy(exit = it)) },
            )
            Spacer(Modifier.height(6.dp))
            StepperRow(
                icon = Icons.Rounded.Timer,
                title = stringResource(R.string.rounding_tolerance),
                subtitle = stringResource(R.string.rounding_tolerance_sub),
                value = rounding.toleranceMinutes,
                range = 0..(rounding.stepMinutes - 1).coerceIn(0, 15),
                format = { "$it min" },
                onValueChange = { onChange(rounding.copy(toleranceMinutes = it)) },
            )
            val inAt = 8 * 60 + 7
            val outAt = 16 * 60 + 52
            Text(
                stringResource(
                    R.string.rounding_example,
                    Fmt.time(inAt),
                    Fmt.time(Rounding.round(inAt, rounding.stepMinutes, rounding.entry, rounding.toleranceMinutes)),
                    Fmt.time(outAt),
                    Fmt.time(Rounding.round(outAt, rounding.stepMinutes, rounding.exit, rounding.toleranceMinutes)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun DirectionChoice(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, value: RoundingDirection, onChange: (RoundingDirection) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
    val options = listOf(
        RoundingDirection.DOWN to R.string.rounding_down,
        RoundingDirection.NEAREST to R.string.rounding_nearest,
        RoundingDirection.UP to R.string.rounding_up,
    )
    SingleChoiceSegmentedButtonRow(SegmentRowModifier) {
        options.forEachIndexed { index, (direction, label) ->
            SegmentedButton(
                selected = value == direction,
                onClick = { onChange(direction) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.fillMaxHeight(),
                contentPadding = SegmentPadding,
                icon = {},
            ) {
                SegmentLabel(stringResource(label), MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// --- Schedule history ---------------------------------------------------------------------------

/** "8h · Lun–Ven · Ven 4h", or "Turni". */
@Composable
fun scheduleSummary(schedule: Schedule): String {
    if (schedule.mode == WorkMode.SHIFTS) return stringResource(R.string.mode_shifts)
    val week = (0 until 7).map { Engine.firstDayOfWeek().plus(it.toLong()) }
    // Runs of consecutive working days in week order: "Lun–Ven", "Lun, Mer, Ven".
    val runs = mutableListOf<MutableList<DayOfWeek>>()
    week.forEach { day ->
        if (day.value in schedule.workDays) {
            if (runs.isEmpty() || week.indexOf(runs.last().last()) != week.indexOf(day) - 1) runs += mutableListOf(day) else runs.last() += day
        }
    }
    val short = week.associateWith { dayName(it, TextStyle.SHORT_STANDALONE) }
    val days = runs.joinToString(", ") { run ->
        if (run.size >= 3) "${short[run.first()]}–${short[run.last()]}" else run.joinToString(", ") { short.getValue(it) }
    }
    val custom = week.filter { it.value in schedule.workDays && (schedule.dailyByDay[it.value] ?: schedule.dailyMinutes) != schedule.dailyMinutes }
        .joinToString(" · ") { "${short[it]} ${Fmt.duration(schedule.dailyByDay[it.value] ?: schedule.dailyMinutes)}" }
    return listOf(Fmt.duration(schedule.dailyMinutes), days, custom).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** Floating bar shown while the schedule has unsaved edits: from when do they apply? */
@Composable
fun SchedulePendingBar(onToday: () -> Unit, onPickDate: () -> Unit, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.History, tint = MaterialTheme.colorScheme.primary, container = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.schedule_pending_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.schedule_pending_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.IconButton(onClick = onDiscard) {
                    androidx.compose.material3.Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel))
                }
            }
            Spacer(Modifier.height(10.dp))
            // Even buttons: with large fonts "Scegli il giorno" goes on two lines instead of being cut.
            Row(Modifier.padding(end = 10.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onToday, modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = PendingButtonPadding) {
                    SegmentLabel(stringResource(R.string.schedule_from_today))
                }
                androidx.compose.material3.FilledTonalButton(onClick = onPickDate, modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = PendingButtonPadding) {
                    SegmentLabel(stringResource(R.string.schedule_from_date))
                }
            }
        }
    }
}

private val PendingButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

/** Picks the first day of the new schedule; "Always" rewrites the past too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDatePicker(onDismiss: () -> Unit, onApply: (LocalDate?) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onApply(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.schedule_apply)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(null) }) { Text(stringResource(R.string.schedule_always)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    ) {
        DatePicker(
            state = state,
            showModeToggle = false,
            title = {
                Text(
                    stringResource(R.string.schedule_pick_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}

/** The contracts over time, oldest first, with the current one at the end. */
@Composable
fun ScheduleHistoryDialog(data: StaccoData, onDismiss: () -> Unit, onClear: () -> Unit) {
    val periods = ScheduleHistory.parse(data.scheduleHistory)
    StaccoDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.History, tint = MaterialTheme.colorScheme.primary, container = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.schedule_history), style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.schedule_history_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            periods.forEach { (until, schedule) ->
                HistoryRow(stringResource(R.string.schedule_period_until, Fmt.dayMonthYear(until)), scheduleSummary(schedule), current = false)
            }
            periods.lastOrNull()?.let { (until, _) ->
                HistoryRow(stringResource(R.string.schedule_period_current, Fmt.dayMonthYear(until.plusDays(1))), scheduleSummary(data.work.schedule), current = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.schedule_history_clear), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    }
}

@Composable
private fun HistoryRow(title: String, summary: String, current: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(summary, style = MaterialTheme.typography.titleSmall)
    }
}
