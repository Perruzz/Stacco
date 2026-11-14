package io.github.perruzz.stacco.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.ui.util.Fmt
import java.time.LocalTime
import kotlin.math.abs

@Composable
fun StaccoDialog(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val visible = remember { MutableTransitionState(false) }.apply { targetState = true }
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.86f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f)),
            exit = fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .padding(20.dp)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun DialogHeader(title: String, icon: ImageVector?, tint: Color, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            IconBubble(icon, tint = tint, container = tint.copy(alpha = 0.16f), size = 44.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DialogButtons(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = stringResource(R.string.action_confirm),
    destructive: Boolean = false,
    onExtra: (() -> Unit)? = null,
    extraLabel: String? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onExtra != null && extraLabel != null) {
            TextButton(onClick = onExtra) {
                Text(extraLabel, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onConfirm,
            colors = if (destructive) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
            } else {
                ButtonDefaults.buttonColors()
            },
        ) { Text(confirmLabel) }
    }
}

/** Wheel time picker in a dialog, with quick "now" and ±5 minutes shortcuts. */
@Composable
fun TimePickerDialog(
    title: String,
    initialMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    onClear: (() -> Unit)? = null,
) {
    val now = LocalTime.now()
    val start = initialMinutes?.let { Math.floorMod(it, 1440) } ?: (now.hour * 60 + now.minute)
    var hour by remember { mutableIntStateOf(start / 60) }
    var minute by remember { mutableIntStateOf(start % 60) }

    fun shift(delta: Int) {
        val total = Math.floorMod(hour * 60 + minute + delta, 1440)
        hour = total / 60
        minute = total % 60
    }

    StaccoDialog(onDismiss) {
        DialogHeader(title, icon, tint, subtitle)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            WheelPicker(count = 24, value = hour, onValueChange = { hour = it })
            Text(":", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 6.dp))
            WheelPicker(count = 60, value = minute, onValueChange = { minute = it })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Pill("${MINUS_SIGN}5m", onClick = { shift(-5) })
            Pill(
                stringResource(R.string.action_now),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {
                    val n = LocalTime.now()
                    hour = n.hour
                    minute = n.minute
                },
            )
            Pill("+5m", onClick = { shift(5) })
        }
        Spacer(Modifier.height(20.dp))
        DialogButtons(
            onDismiss = onDismiss,
            onConfirm = { onConfirm(hour * 60 + minute) },
            onExtra = onClear,
            extraLabel = if (onClear != null) stringResource(R.string.action_remove) else null,
        )
    }
}

private const val MINUS_SIGN = "−"

/** Hours + minutes picker, optionally signed (for the hour bank). */
@Composable
fun DurationPickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    maxHours: Int = 23,
    allowNegative: Boolean = false,
    extraContent: (@Composable ColumnScope.(Int) -> Unit)? = null,
) {
    var negative by remember { mutableStateOf(initialMinutes < 0) }
    val start = abs(initialMinutes)
    var hours by remember { mutableIntStateOf((start / 60).coerceAtMost(maxHours)) }
    var minutes by remember { mutableIntStateOf(start % 60) }
    val value = (hours * 60 + minutes) * if (negative) -1 else 1

    StaccoDialog(onDismiss) {
        DialogHeader(title, icon, tint, subtitle)
        Spacer(Modifier.height(16.dp))
        if (allowNegative) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !negative,
                    onClick = { negative = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.bank_positive)) }
                SegmentedButton(
                    selected = negative,
                    onClick = { negative = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.bank_negative)) }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            WheelPicker(
                count = maxHours + 1,
                value = hours,
                onValueChange = { hours = it },
                loop = false,
                label = { "${it}h" },
                width = 100.dp,
            )
            Spacer(Modifier.width(10.dp))
            WheelPicker(
                count = 60,
                value = minutes,
                onValueChange = { minutes = it },
                label = { String.format(java.util.Locale.ROOT, "%02dm", it) },
                width = 100.dp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = Fmt.duration(value, signed = allowNegative),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        extraContent?.invoke(this, value)
        Spacer(Modifier.height(20.dp))
        DialogButtons(onDismiss = onDismiss, onConfirm = { onConfirm(value) })
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    StaccoDialog(onDismiss) {
        DialogHeader(
            title = title,
            icon = icon,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        DialogButtons(onDismiss = onDismiss, onConfirm = onConfirm, confirmLabel = confirmLabel, destructive = destructive)
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    icon: ImageVector? = null,
    singleLine: Boolean = true,
) {
    var text by remember { mutableStateOf(initial) }
    StaccoDialog(onDismiss) {
        DialogHeader(title, icon, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(if (singleLine) 40 else 280) },
            label = { Text(label) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        DialogButtons(onDismiss = onDismiss, onConfirm = { onConfirm(text.trim()) }, confirmLabel = stringResource(R.string.action_save))
    }
}

/** A tonal action button used inside cards. */
@Composable
fun SoftButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}
