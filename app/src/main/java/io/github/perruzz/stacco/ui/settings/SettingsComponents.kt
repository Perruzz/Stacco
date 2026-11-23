package io.github.perruzz.stacco.ui.settings

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.res.stringResource
import io.github.perruzz.stacco.R
import java.time.format.TextStyle
import java.time.DayOfWeek
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.ui.components.IconBubble
import io.github.perruzz.stacco.ui.components.RollingText
import io.github.perruzz.stacco.ui.components.bouncyClickable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val hasEnd = value != null || trailing != null || onClick != null
    Layout(
        content = {
            IconBubble(icon, tint = tint, container = tint.copy(alpha = 0.13f), size = 40.dp)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    AnimatedContent(
                        targetState = subtitle,
                        transitionSpec = {
                            (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(260)) { it / 3 }) togetherWith
                                fadeOut(tween(120)) using SizeTransform(clip = false)
                        },
                        label = "setting-subtitle",
                    ) { text ->
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) {
                    RollingText(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                if (trailing != null) {
                    if (value != null) Spacer(Modifier.width(8.dp))
                    trailing()
                } else if (onClick != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.bouncyClickable(pressed = 0.98f, onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val (iconPart, textPart, endPart) = measurables
        val iconP = iconPart.measure(Constraints())
        val endP = endPart.measure(Constraints(maxWidth = width))
        val textX = iconP.width + 14.dp.roundToPx()
        val gap = if (hasEnd) 8.dp.roundToPx() else 0
        val room = width - textX
        val besideRoom = room - endP.width - gap
        // The value (or the − + buttons) stays beside the text while the text keeps at least half
        // the room and its longest word whole; with large fonts on a narrow phone it goes under it
        // ("Scad/enza saldo posit/ivo" otherwise).
        val beside = besideRoom >= maxOf(textPart.minIntrinsicWidth(Constraints.Infinity), room / 2)
        if (beside) {
            val textP = textPart.measure(Constraints(maxWidth = besideRoom.coerceAtLeast(0)))
            val height = maxOf(iconP.height, textP.height, endP.height)
            layout(width, height) {
                iconP.place(0, (height - iconP.height) / 2)
                textP.place(textX, (height - textP.height) / 2)
                endP.place(width - endP.width, (height - endP.height) / 2)
            }
        } else {
            val textP = textPart.measure(Constraints(maxWidth = room.coerceAtLeast(0)))
            val below = 6.dp.roundToPx()
            val height = maxOf(iconP.height, textP.height + below + endP.height)
            layout(width, height) {
                iconP.place(0, (height - iconP.height) / 2)
                textP.place(textX, 0)
                endP.place(width - endP.width, textP.height + below)
            }
        }
    }
}

@Composable
fun SwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    SettingRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        tint = tint,
        // The whole row is the switch: TalkBack reads one element with its title, subtitle and state.
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = {
                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onCheckedChange(it)
            },
        ),
        trailing = { Switch(checked = checked, enabled = enabled, onCheckedChange = null) },
    )
}

@Composable
fun StepperRow(
    icon: ImageVector,
    title: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    subtitle: String? = null,
    format: (Int) -> String = { it.toString() },
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val haptics = LocalHapticFeedback.current
    SettingRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        tint = tint,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onValueChange((value - 1).coerceIn(range))
                    },
                    enabled = value > range.first,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp),
                ) { Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.a11y_decrease, title), modifier = Modifier.size(18.dp)) }
                RollingText(
                    format(value),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                FilledTonalIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onValueChange((value + 1).coerceIn(range))
                    },
                    enabled = value < range.last,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp),
                ) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.a11y_increase, title), modifier = Modifier.size(18.dp)) }
            }
        },
    )
}

/** Mon..Sun toggles. Values are ISO days of week (1 = Monday). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekdayToggles(selected: Set<Int>, order: List<Int>, labels: (Int) -> String, onToggle: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val locale = LocalConfiguration.current.locales[0]
        order.forEach { day ->
            val on = day in selected
            val name = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale)
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, label = "wd-bg")
            val fg by animateColorAsState(if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "wd-fg")
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .widthIn(max = 52.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(bg)
                        .bouncyClickable(pressed = 0.85f, role = Role.Checkbox) { onToggle(day) }
                        // The full name of the day (not "Lu"), and whether it is a working day.
                        .clearAndSetSemantics {
                            contentDescription = name
                            toggleableState = ToggleableState(on)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(labels(day), style = MaterialTheme.typography.labelLarge, color = fg)
                }
            }
        }
    }
}

/** Selectable option card (used for theme and simple enums). */
@Composable
fun OptionCard(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: (@Composable () -> Unit)? = null,
) {
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), label = "opt-border")
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer, label = "opt-bg")
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(20.dp))
            .bouncyClickable(pressed = 0.93f, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (preview != null) {
            preview()
        } else {
            Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** How long a press must last before it counts as holding the button. */
private const val HOLD_GRACE_MS = 120L

/**
 * A destructive button that must be held down: the fill sweeps across while you hold it. With
 * TalkBack it is a button whose action ([actionLabel]) needs a double tap and hold.
 */
@Composable
fun HoldToConfirmButton(
    text: String,
    holdingText: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = text,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val error = MaterialTheme.colorScheme.error
    Box(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .drawBehind {
                drawRect(error, size = Size(size.width * progress.value, size.height))
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onLongClick(label = actionLabel) {
                    onConfirmed()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // A scroll that happens to start on the button is not a hold: give it a
                        // moment to turn into one before the fill (and the buzz) begins.
                        if (withTimeoutOrNull(HOLD_GRACE_MS) { tryAwaitRelease() } != null) return@detectTapGestures
                        var job: Job? = null
                        job = scope.launch {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            var lastTick = 0
                            progress.animateTo(1f, tween(1800, easing = LinearEasing)) {
                                val tick = (value * 10).toInt()
                                if (tick != lastTick) {
                                    lastTick = tick
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                }
                            }
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onConfirmed()
                            progress.snapTo(0f)
                        }
                        tryAwaitRelease()
                        if (progress.value < 1f) {
                            job.cancel()
                            scope.launch { progress.animateTo(0f, spring(stiffness = 300f)) }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (progress.value > 0f) holdingText else text,
            style = MaterialTheme.typography.titleMedium,
            color = if (progress.value > 0.5f) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
