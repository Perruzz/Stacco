package io.github.perruzz.stacco.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.Rounding
import io.github.perruzz.stacco.domain.RoundingDirection
import io.github.perruzz.stacco.domain.RoundingSettings
import io.github.perruzz.stacco.domain.WorkSettings
import io.github.perruzz.stacco.ui.components.DurationPickerDialog
import io.github.perruzz.stacco.ui.components.IconBubble
import io.github.perruzz.stacco.ui.components.RollingText
import io.github.perruzz.stacco.ui.components.SectionLabel
import io.github.perruzz.stacco.ui.components.StaccoDialog
import io.github.perruzz.stacco.ui.components.bouncyClickable
import io.github.perruzz.stacco.ui.util.Fmt
import java.time.DayOfWeek
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { hours = days.associate { it.value to work.dailyMinutes } },
                enabled = hours.values.any { it != work.dailyMinutes },
            ) { Text(stringResource(R.string.action_restore)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSave(hours.filterValues { it != work.dailyMinutes }) }) { Text(stringResource(R.string.action_save)) }
        }
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
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            SegmentedButton(
                selected = rounding.stepMinutes == step || (step == 0 && !rounding.enabled),
                onClick = { onChange(rounding.copy(stepMinutes = step, toleranceMinutes = rounding.toleranceMinutes.coerceAtMost((step - 1).coerceAtLeast(0)))) },
                shape = SegmentedButtonDefaults.itemShape(index, steps.size),
                icon = {},
            ) {
                Text(
                    if (step == 0) stringResource(R.string.rounding_off) else stringResource(R.string.rounding_minutes, step),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
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
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (direction, label) ->
            SegmentedButton(
                selected = value == direction,
                onClick = { onChange(direction) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) {
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, color = Color.Unspecified)
            }
        }
    }
}
