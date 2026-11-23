package io.github.perruzz.stacco.ui.calendar

import io.github.perruzz.stacco.ui.components.AdaptiveRow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.OnCall
import io.github.perruzz.stacco.domain.RecoveryCalculator
import io.github.perruzz.stacco.ui.oncall.OnCallDaySection
import io.github.perruzz.stacco.ui.oncall.RecoveryDialog
import io.github.perruzz.stacco.ui.oncall.RecoveryRow
import io.github.perruzz.stacco.data.day
import io.github.perruzz.stacco.domain.DayCalculator
import io.github.perruzz.stacco.domain.DayIssue
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.ui.MainViewModel
import io.github.perruzz.stacco.ui.bank.vacationHours
import io.github.perruzz.stacco.ui.components.ConfirmDialog
import io.github.perruzz.stacco.ui.components.DurationPickerDialog
import io.github.perruzz.stacco.ui.components.Pill
import io.github.perruzz.stacco.ui.components.RollingText
import io.github.perruzz.stacco.ui.components.SectionLabel
import io.github.perruzz.stacco.ui.components.SheetColumn
import io.github.perruzz.stacco.ui.components.SheetInsets
import io.github.perruzz.stacco.ui.components.TimePickerDialog
import io.github.perruzz.stacco.ui.components.bouncyClickable
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import io.github.perruzz.stacco.ui.theme.deltaColor
import io.github.perruzz.stacco.ui.theme.shiftContainer
import io.github.perruzz.stacco.ui.today.DayTypeSelector
import io.github.perruzz.stacco.ui.today.Hint
import io.github.perruzz.stacco.ui.today.ShiftSelector
import io.github.perruzz.stacco.ui.today.StampTimeline
import io.github.perruzz.stacco.ui.today.icon
import io.github.perruzz.stacco.ui.today.label
import io.github.perruzz.stacco.ui.util.Fmt
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun DaySheet(date: LocalDate, data: StaccoData, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val calendar = remember(data) { WorkCalendar.from(data) }
    val record = data.day(date)
    val type = calendar.effectiveType(date)
    val today = LocalDate.now()
    val rules = calendar.breakRules(date)
    val required = calendar.required(date)
    val shift = calendar.shift(date)
    val result = calendar.result(date)
    val breakInfo = DayCalculator.breakInfo(record, rules, required, null)
    val issues = DayCalculator.issues(record)
    val smartRange = calendar.smartRange(date, data.smart.period, Engine.firstDayOfWeek())
    val smartUsed = calendar.smartUsed(smartRange)
    val holiday = calendar.holiday(date)
    // As it counts: a permit never takes more than its day (after a contract change too).
    val permit = VacationCalculator.permitHeld(calendar, date)
    val vacationLeft = remember(data) {
        if (data.vacation.tracked) VacationCalculator.balance(data, LocalDate.now()).remaining else null
    }

    var editing by remember { mutableStateOf<StampSlot?>(null) }
    var editingLeave by remember { mutableStateOf(false) }
    var editingRecovery by remember { mutableStateOf(false) }
    val onCallOn = data.onCall.enabled
    var confirmClear by remember { mutableStateOf(false) }
    var note by remember(date) { mutableStateOf(record.note) }
    LaunchedEffect(note) {
        if (note != record.note) {
            delay(450)
            viewModel.setNote(date, note)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        // The body handles the insets: the sheet must keep its size wherever it is (see SheetInsets).
        contentWindowInsets = { SheetInsets },
    ) {
        SheetColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.longDate(date), style = MaterialTheme.typography.headlineSmall)
                    val relative = when (date) {
                        today -> stringResource(R.string.relative_today)
                        today.minusDays(1) -> stringResource(R.string.relative_yesterday)
                        today.plusDays(1) -> stringResource(R.string.relative_tomorrow)
                        else -> null
                    }
                    val subtitle = listOfNotNull(relative, holiday?.let { stringResource(it.label) }).joinToString(" · ")
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (shift != null && type == DayType.OFFICE) {
                    Pill(shift.name, icon = DayType.OFFICE.icon, color = shiftContainer(shift.color), contentColor = MaterialTheme.colorScheme.onSurface)
                } else {
                    Pill(stringResource(type.label), icon = type.icon)
                }
            }

            if (calendar.isShiftDay(date) && data.work.shifts.isNotEmpty()) {
                SectionLabel(stringResource(R.string.day_shift))
                ShiftSelector(
                    shifts = data.work.shifts,
                    selected = record.shiftId,
                    onSelect = { viewModel.setShift(date, it) },
                )
            }

            SectionLabel(stringResource(R.string.day_type))
            DayTypeSelector(
                selected = record.type,
                effective = if (record.type == null) type else null,
                smartBadge = "$smartUsed/${data.smart.allowance}",
                vacationBadge = vacationLeft?.let { vacationHours(it) },
                onSelect = { viewModel.setDayType(date, it) },
            )
            AnimatedVisibility(record.type == DayType.SMART && smartUsed > data.smart.allowance) {
                Hint(
                    icon = Icons.Rounded.Warning,
                    text = stringResource(R.string.day_smart_over, smartUsed, data.smart.allowance),
                    color = StaccoTheme.colors.negative,
                )
            }

            AnimatedVisibility(
                visible = type == DayType.OFFICE || record.hasStamps,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.section_stamps))
                    StampTimeline(
                        record = record,
                        next = if (date >= today.minusDays(1)) record.nextSlot(calendar.expectsBreak(date)) else null,
                        breakInfo = if (record.entry != null) breakInfo else null,
                        worked = result?.worked,
                        onEdit = { editing = it },
                        showBreak = calendar.expectsBreak(date),
                        counted = calendar.counted(date),
                    )
                    if (issues.isNotEmpty()) {
                        Hint(
                            icon = Icons.Rounded.Warning,
                            text = stringResource(if (DayIssue.BREAK_NOT_CLOSED in issues) R.string.calc_issue_break else R.string.calc_issue_order),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (result != null) {
                        // Three in a row; with large fonts on a narrow phone one goes below ("8h 10r" otherwise).
                        AdaptiveRow {
                            SheetStat(stringResource(R.string.day_worked), Fmt.duration(result.worked), MaterialTheme.colorScheme.primary)
                            SheetStat(stringResource(R.string.day_break), Fmt.duration(breakInfo.deducted), StaccoTheme.colors.lunch)
                            SheetStat(stringResource(R.string.day_bank), Fmt.duration(result.delta, signed = true), deltaColor(result.delta))
                        }
                    }
                    if (calendar.isScheduled(date)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .bouncyClickable { editingLeave = true }
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.day_leave), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.day_leave_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                if (permit > 0) Fmt.duration(permit) else "—",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        if (onCallOn) RecoveryRow(OnCall.recoveryHeld(calendar, date), onClick = { editingRecovery = true })
                    }
                }
            }
            if (type == null && !record.hasStamps) {
                Hint(icon = Icons.Rounded.Info, text = stringResource(R.string.day_rest_hint))
            }

            if (onCallOn) {
                OnCallDaySection(
                    date = date,
                    record = record,
                    onOnCall = { viewModel.setOnCall(date, it) },
                    onSave = { viewModel.saveIntervention(date, it) },
                    onDelete = { viewModel.deleteIntervention(date, it.id) },
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(280) },
                label = { Text(stringResource(R.string.day_note)) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            if (!record.isEmpty) {
                TextButton(onClick = { confirmClear = true }, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.day_clear), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    editing?.let { slot ->
        val current = record[slot]
        TimePickerDialog(
            title = stringResource(slot.label),
            subtitle = Fmt.longDate(date),
            initialMinutes = current ?: when (slot) {
                StampSlot.ENTRY -> shift?.start ?: (9 * 60)
                StampSlot.BREAK_IN -> record.breakOut?.let { it + rules.min.coerceAtLeast(1) }
                StampSlot.EXIT -> shift?.endAbsolute ?: record.entry?.let { it + required + rules.min }
                else -> record.entry?.plus(4 * 60) ?: (13 * 60)
            },
            icon = slot.icon,
            onDismiss = { editing = null },
            onConfirm = {
                viewModel.setStamp(date, slot, it)
                editing = null
            },
            onClear = if (current != null) {
                {
                    viewModel.setStamp(date, slot, null)
                    editing = null
                }
            } else {
                null
            },
        )
    }

    if (editingLeave) {
        DurationPickerDialog(
            title = stringResource(R.string.day_leave),
            subtitle = stringResource(R.string.day_leave_sub),
            initialMinutes = permit,
            icon = Icons.Rounded.EventBusy,
            maxHours = 12,
            onDismiss = { editingLeave = false },
            onConfirm = {
                viewModel.setLeave(date, it)
                editingLeave = false
            },
        )
    }

    if (editingRecovery) {
        val room = remember(data) { OnCall.recoveryRoom(calendar, date) }
        val available = remember(data) { RecoveryCalculator.availableOn(data, date) }
        RecoveryDialog(
            initial = record.recoveryMinutes,
            room = room,
            available = available,
            onDismiss = { editingRecovery = false },
            onConfirm = {
                viewModel.setRecovery(date, it)
                editingRecovery = false
            },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.day_clear_title),
            // Clearing a day also removes its on-call duty: say so when there is some.
            text = stringResource(
                if (record.onCall || record.interventions.isNotEmpty() || record.recoveryMinutes > 0) R.string.day_clear_text_on_call else R.string.day_clear_text,
            ),
            confirmLabel = stringResource(R.string.action_delete),
            icon = Icons.Rounded.DeleteOutline,
            destructive = true,
            onDismiss = { confirmClear = false },
            onConfirm = {
                viewModel.clearDay(date)
                confirmClear = false
                onDismiss()
            },
        )
    }
}

@Composable
private fun SheetStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(vertical = 12.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RollingText(value, style = MaterialTheme.typography.titleLarge, color = color)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
