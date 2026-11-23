package io.github.perruzz.stacco.ui.today

import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.BreakInfo
import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.ui.components.RollingText
import io.github.perruzz.stacco.ui.components.bouncyClickable
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import io.github.perruzz.stacco.ui.theme.container
import io.github.perruzz.stacco.ui.theme.tint
import io.github.perruzz.stacco.ui.util.Fmt

/**
 * Vertical timeline of the four stamps. Each row is tappable to edit the time;
 * the next expected stamp gently pulses.
 */
@Composable
fun StampTimeline(
    record: DayRecord,
    next: StampSlot?,
    breakInfo: BreakInfo?,
    worked: Int?,
    onEdit: (StampSlot) -> Unit,
    modifier: Modifier = Modifier,
    showBreak: Boolean = true,
    /** The day as counted (rounded entry/exit), to show when it differs from the stamps. */
    counted: DayRecord? = null,
) {
    // Without a planned break (e.g. many shifts) the timeline is just entry → exit.
    val slots = if (showBreak || record.breakOut != null || record.breakIn != null) {
        StampSlot.entries
    } else {
        listOf(StampSlot.ENTRY, StampSlot.EXIT)
    }
    Column(modifier) {
        slots.forEachIndexed { index, slot ->
            val value = record[slot]
            val countedAt = counted?.get(slot)?.takeIf { value != null && it != value }
                ?.let { stringResource(R.string.stamp_counted, Fmt.timeDay(it)) }
            val stampNote = when (slot) {
                StampSlot.BREAK_IN -> breakInfo?.let { b ->
                    when {
                        b.ongoing && b.actual != null -> stringResource(R.string.stamps_break_live, Fmt.duration(b.actual))
                        !b.assumed && b.actual != null -> stringResource(R.string.stamps_break_actual, Fmt.duration(b.actual))
                        else -> null
                    }
                }
                StampSlot.EXIT -> if (value != null && worked != null) stringResource(R.string.stamps_worked, Fmt.duration(worked)) else null
                else -> null
            }
            val supporting = listOfNotNull(countedAt, stampNote).joinToString(" · ").ifEmpty { null }
            TimelineRow(
                slot = slot,
                minutes = value,
                isNext = slot == next,
                lineAbove = if (index == 0) null else value != null,
                lineBelow = if (index == slots.lastIndex) null else record[slots[index + 1]] != null,
                supporting = supporting,
                onClick = { onEdit(slot) },
            )
        }
    }
}

@Composable
private fun TimelineRow(
    slot: StampSlot,
    minutes: Int?,
    isNext: Boolean,
    lineAbove: Boolean?,
    lineBelow: Boolean?,
    supporting: String?,
    onClick: () -> Unit,
) {
    val filled = minutes != null
    val primary = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outlineVariant
    val dotColor by animateColorAsState(if (filled) primary else Color.Transparent, label = "dot")
    val iconColor by animateColorAsState(
        if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "dot-icon",
    )
    val pop = remember { Animatable(1f) }
    LaunchedEffect(filled) {
        if (filled) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
        }
    }
    val transition = rememberInfiniteTransition(label = "next")
    val halo by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "halo",
    )

    // The halo of the next stamp grows past the row: it is drawn around the row, outside the clip
    // that rounds the row's press feedback, so the whole ring stays round at every step.
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (isNext) {
                    val scale = 1f + halo * NODE_HALO_GROWTH
                    val stroke = 2.dp.toPx() * scale
                    drawCircle(
                        color = primary.copy(alpha = 1f - halo),
                        radius = (NODE_SIZE / 2).toPx() * scale - stroke / 2f,
                        center = Offset((ROW_PADDING + NODE_COLUMN / 2).toPx(), size.height / 2f),
                        style = Stroke(width = stroke),
                    )
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A full touch target even when the text is short (47dp at normal size).
                .heightIn(min = 48.dp)
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(18.dp))
                .bouncyClickable(pressed = 0.98f, onClick = onClick)
                .padding(horizontal = ROW_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(NODE_COLUMN)
                    .fillMaxHeight()
                    .drawBehind {
                        // Each row draws its half of the connectors, from its node to its edge: green
                        // between stamps already taken, grey towards the ones still to come. Both are
                        // continuous, so the two halves always meet (a dash pattern started again in
                        // each half and left gaps next to the nodes).
                        val x = size.width / 2f
                        val dotRadius = (NODE_SIZE / 2).toPx()
                        val stroke = 3.dp.toPx()
                        lineAbove?.let { done ->
                            drawLine(
                                color = if (done) primary else idle,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height / 2f - dotRadius),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round,
                            )
                        }
                        lineBelow?.let { done ->
                            drawLine(
                                color = if (done) primary else idle,
                                start = Offset(x, size.height / 2f + dotRadius),
                                end = Offset(x, size.height),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round,
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(NODE_SIZE)
                        .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                        .background(dotColor, CircleShape)
                        .border(2.dp, if (filled) primary else if (isNext) primary else idle, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(slot.icon, contentDescription = null, tint = if (isNext && !filled) primary else iconColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp),
            ) {
                Text(stringResource(slot.label), style = MaterialTheme.typography.titleSmall)
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RollingText(
                text = Fmt.time(minutes),
                style = MaterialTheme.typography.headlineMedium,
                color = if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** The circle of a stamp, the column it sits in and the row's side padding. */
private val NODE_SIZE = 40.dp
private val NODE_COLUMN = 44.dp
private val ROW_PADDING = 4.dp

/** How much the halo of the next stamp grows (1.45× the circle). */
private const val NODE_HALO_GROWTH = 0.45f

/** Chips to choose what kind of day it is. `null` means "follow the calendar". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayTypeSelector(
    selected: DayType?,
    effective: DayType?,
    onSelect: (DayType?) -> Unit,
    modifier: Modifier = Modifier,
    smartBadge: String? = null,
    vacationBadge: String? = null,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DayTypeChip(
            label = stringResource(R.string.type_auto),
            icon = AutoTypeIcon,
            tint = MaterialTheme.colorScheme.primary,
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            selected = selected == null,
            badge = if (selected == null) stringResource(effective.label) else null,
            onClick = { onSelect(null) },
        )
        DayType.entries.forEach { type ->
            DayTypeChip(
                label = stringResource(type.label),
                icon = type.icon,
                tint = type.tint(),
                container = type.container(),
                selected = selected == type,
                badge = when (type) {
                    DayType.SMART -> smartBadge
                    DayType.VACATION -> vacationBadge
                    else -> null
                },
                onClick = { onSelect(type) },
            )
        }
    }
}

@Composable
private fun DayTypeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    container: Color,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(if (selected) container else MaterialTheme.colorScheme.surfaceContainer, label = "chip-bg")
    val borderColor by animateColorAsState(if (selected) tint else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), label = "chip-border")
    val scale by animateFloatAsState(if (selected) 1.04f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "chip-scale")
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        if (badge != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                modifier = Modifier
                    .background(tint.copy(alpha = 0.14f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * Horizontal picture of the day: work, lunch, the remaining part and the key exits.
 */
@Composable
fun DayTimelineBar(
    record: DayRecord,
    now: Int,
    standardExit: Int?,
    bankExit: Int?,
    modifier: Modifier = Modifier,
) {
    val entry = record.entry ?: return
    val end = listOfNotNull(record.exit, standardExit, bankExit, now).maxOrNull() ?: return
    if (end <= entry) return
    val primary = MaterialTheme.colorScheme.primary
    val lunch = StaccoTheme.colors.lunch
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val positive = StaccoTheme.colors.positive
    val negative = StaccoTheme.colors.negative
    val nowColor = MaterialTheme.colorScheme.onSurface
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp),
        ) {
            val w = size.width * reveal.value
            val h = size.height
            val barTop = h * 0.2f
            val barHeight = h * 0.6f
            val radius = CornerRadius(barHeight / 2f)
            fun x(t: Int) = ((t - entry).toFloat() / (end - entry)) * w

            drawRoundRect(track, topLeft = Offset(0f, barTop), size = Size(w, barHeight), cornerRadius = radius)
            val stop = record.exit ?: now
            val out = record.breakOut
            val back = record.breakIn
            // Worked segments
            if (out == null || stop <= out) {
                drawRoundRect(primary, Offset(0f, barTop), Size(x(stop.coerceAtLeast(entry)), barHeight), radius)
            } else {
                drawRoundRect(primary, Offset(0f, barTop), Size(x(out), barHeight), radius)
                val breakEnd = back?.coerceAtMost(stop) ?: stop
                drawRoundRect(lunch, Offset(x(out), barTop), Size(x(breakEnd) - x(out), barHeight), radius)
                if (back != null && stop > back) {
                    drawRoundRect(primary, Offset(x(back), barTop), Size(x(stop) - x(back), barHeight), radius)
                }
            }
            // Exits
            fun tick(t: Int?, color: Color) {
                if (t == null || t < entry || t > end) return
                val px = x(t)
                drawLine(color, Offset(px, 0f), Offset(px, h), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            if (bankExit != null && bankExit != standardExit) tick(bankExit, if (bankExit < (standardExit ?: bankExit)) positive else negative)
            tick(standardExit, nowColor.copy(alpha = 0.55f))
            if (record.exit == null) {
                val px = x(now.coerceIn(entry, end))
                drawCircle(nowColor, radius = 5.dp.toPx(), center = Offset(px, h / 2f))
                drawCircle(track, radius = 2.5.dp.toPx(), center = Offset(px, h / 2f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(Fmt.time(entry), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(Fmt.time(end), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/** Chips to plan a shift on a day (or a rest day). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftSelector(
    shifts: List<io.github.perruzz.stacco.domain.ShiftTemplate>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    restLabel: String = stringResource(R.string.type_rest),
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shifts.forEach { shift ->
            ShiftChip(
                code = shift.code,
                label = shift.name,
                sub = "${Fmt.time(shift.start)}–${Fmt.time(shift.end)}",
                color = io.github.perruzz.stacco.ui.theme.shiftColor(shift.color),
                selected = selected == shift.id,
                onClick = { onSelect(shift.id) },
            )
        }
        ShiftChip(
            code = "–",
            label = restLabel,
            sub = null,
            color = MaterialTheme.colorScheme.outline,
            selected = selected == null,
            onClick = { onSelect(null) },
        )
    }
}

@Composable
fun ShiftChip(code: String, label: String, sub: String?, color: Color, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainer, label = "shift-bg")
    val borderColor by animateColorAsState(if (selected) color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), label = "shift-border")
    val scale by animateFloatAsState(if (selected) 1.05f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "shift-scale")
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .bouncyClickable(onClick = onClick)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(code, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
