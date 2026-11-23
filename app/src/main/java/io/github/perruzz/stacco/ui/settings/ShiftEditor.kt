package io.github.perruzz.stacco.ui.settings

import io.github.perruzz.stacco.ui.components.DialogButtons
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.ShiftTemplate
import io.github.perruzz.stacco.ui.components.IconBubble
import io.github.perruzz.stacco.ui.components.StaccoDialog
import io.github.perruzz.stacco.ui.components.TimePickerDialog
import io.github.perruzz.stacco.ui.components.bouncyClickable
import io.github.perruzz.stacco.ui.theme.ShiftColorsLight
import io.github.perruzz.stacco.ui.theme.shiftColor
import io.github.perruzz.stacco.ui.util.Fmt

/** The three classic shifts, ready to be renamed or adjusted. */
@Composable
fun defaultShifts(): List<ShiftTemplate> = listOf(
    ShiftTemplate("morning", stringResource(R.string.shift_morning), 7 * 60, 14 * 60, 0, 0),
    ShiftTemplate("afternoon", stringResource(R.string.shift_afternoon), 14 * 60, 21 * 60, 0, 1),
    ShiftTemplate("night", stringResource(R.string.shift_night), 21 * 60, 7 * 60, 0, 2),
)

/** "07:00 – 14:00 · 7h" (+ break, + next day). */
@Composable
fun shiftSummary(shift: ShiftTemplate): String {
    val base = "${Fmt.time(shift.start)} – ${Fmt.timeDay(shift.endAbsolute)} · ${Fmt.duration(shift.workMinutes)}"
    return if (shift.breakMinutes > 0) base + " · " + stringResource(R.string.shift_break_short, Fmt.duration(shift.breakMinutes)) else base
}

@Composable
fun ShiftList(
    shifts: List<ShiftTemplate>,
    onEdit: (ShiftTemplate) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.animateContentSize(spring(dampingRatio = 0.85f, stiffness = 400f))) {
        shifts.forEach { shift ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .bouncyClickable(pressed = 0.98f) { onEdit(shift) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(shiftColor(shift.color), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (shift.crossesMidnight) {
                        Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text(shift.code, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(shift.name, style = MaterialTheme.typography.titleSmall)
                    Text(shiftSummary(shift), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        TextButton(onClick = onAdd, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.shift_add))
        }
    }
}

/** Create or edit a shift: name, start, end (possibly the next day), break and colour. */
@Composable
fun ShiftEditorDialog(
    initial: ShiftTemplate?,
    onDismiss: () -> Unit,
    onSave: (ShiftTemplate) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var start by remember { mutableIntStateOf(initial?.start ?: 8 * 60) }
    var end by remember { mutableIntStateOf(initial?.end ?: 16 * 60) }
    var breakMinutes by remember { mutableIntStateOf(initial?.breakMinutes ?: 0) }
    var color by remember { mutableIntStateOf(initial?.color ?: 3) }
    var picking by remember { mutableStateOf<Int?>(null) }
    val draft = ShiftTemplate(initial?.id ?: "s${System.currentTimeMillis()}", name.trim(), start, end, breakMinutes, color)

    StaccoDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Schedule, tint = shiftColor(color), container = shiftColor(color).copy(alpha = 0.16f), size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(if (initial == null) R.string.shift_new else R.string.shift_edit),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = { Text(stringResource(R.string.shift_name)) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeTile(stringResource(R.string.shift_start), Fmt.time(start), Modifier.weight(1f)) { picking = 0 }
            TimeTile(stringResource(R.string.shift_end), Fmt.time(end), Modifier.weight(1f)) { picking = 1 }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (draft.crossesMidnight) {
                stringResource(R.string.shift_duration_night, Fmt.duration(draft.durationMinutes))
            } else {
                stringResource(R.string.shift_duration, Fmt.duration(draft.durationMinutes))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        StepperRow(
            icon = Icons.Rounded.Coffee,
            title = stringResource(R.string.shift_break),
            subtitle = stringResource(R.string.shift_break_sub),
            value = breakMinutes / 5,
            range = 0..24,
            format = { Fmt.duration(it * 5) },
            onValueChange = { breakMinutes = it * 5 },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            ShiftColorsLight.indices.forEach { index ->
                val selected = index == color
                val scale by animateFloatAsState(if (selected) 1.15f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "swatch")
                val name = stringResource(R.string.a11y_shift_color, index + 1)
                // Each colour owns its share of the row (48dp and more on most phones).
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(CircleShape)
                        .bouncyClickable(pressed = 0.85f, role = Role.RadioButton) { color = index }
                        .semantics {
                            contentDescription = name
                            this.selected = selected
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(CircleShape)
                            .background(shiftColor(index))
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        DialogButtons(
            onDismiss = onDismiss,
            onConfirm = { onSave(draft) },
            confirmLabel = stringResource(R.string.action_save),
            onExtra = onDelete,
            extraLabel = stringResource(R.string.action_delete),
            confirmEnabled = name.isNotBlank() && draft.durationMinutes > 0,
        )
    }

    picking?.let { which ->
        TimePickerDialog(
            title = stringResource(if (which == 0) R.string.shift_start else R.string.shift_end),
            initialMinutes = if (which == 0) start else end,
            icon = Icons.Rounded.Schedule,
            tint = shiftColor(color),
            onDismiss = { picking = null },
            onConfirm = {
                if (which == 0) start = it else end = it
                picking = null
            },
        )
    }
}

@Composable
private fun TimeTile(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .bouncyClickable(pressed = 0.95f, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium)
    }
}
