package io.github.perruzz.stacco.ui.oncall

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.Intervention
import io.github.perruzz.stacco.domain.OnCall
import io.github.perruzz.stacco.domain.OnCallState
import io.github.perruzz.stacco.domain.RecoveryAlert
import io.github.perruzz.stacco.domain.RecoveryLedger
import io.github.perruzz.stacco.domain.RecoveryLot
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.parsedDays
import io.github.perruzz.stacco.ui.components.BigPillButton
import io.github.perruzz.stacco.ui.components.ConfirmDialog
import io.github.perruzz.stacco.ui.components.DialogButtons
import io.github.perruzz.stacco.ui.components.DialogHeader
import io.github.perruzz.stacco.ui.components.IconBubble
import io.github.perruzz.stacco.ui.components.Pill
import io.github.perruzz.stacco.ui.components.RollingText
import io.github.perruzz.stacco.ui.components.SectionLabel
import io.github.perruzz.stacco.ui.components.StaccoCard
import io.github.perruzz.stacco.ui.components.StaccoDialog
import io.github.perruzz.stacco.ui.components.TimePickerDialog
import io.github.perruzz.stacco.ui.components.bouncyClickable
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import io.github.perruzz.stacco.ui.today.AlertRow
import io.github.perruzz.stacco.ui.util.Fmt
import java.time.LocalDate
import java.time.LocalTime

/** On-call duty everywhere in the app. */
val OnCallIcon: ImageVector get() = Icons.Rounded.PhoneInTalk

/** Recovery hours: time off earned by the interventions. */
val RecoveryIcon: ImageVector get() = Icons.Rounded.Weekend

/** Size of [OnCallBadge]: as big as the dots of the calendar legend. */
val OnCallBadgeSize = 10.dp

/**
 * The badge of an on-call day in the calendar: a small phone dot, over any kind of day, with a
 * plain handset that still reads at this size.
 */
@Composable
fun OnCallBadge(modifier: Modifier = Modifier) {
    val c = StaccoTheme.colors
    Box(
        modifier
            .size(OnCallBadgeSize)
            .background(c.onCall, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Call, contentDescription = null, tint = c.onCallContainer, modifier = Modifier.size(7.dp))
    }
}

/** "On call" in the calendar legend, with its badge. */
@Composable
fun OnCallLegendEntry() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OnCallBadge()
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.day_on_call), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** The month's on-call days and credited hours, in the calendar summary. */
@Composable
fun OnCallMonthTile(days: Int, credited: Int, modifier: Modifier = Modifier) {
    val c = StaccoTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.onCall.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(OnCallIcon, contentDescription = null, tint = c.onCall, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            RollingText(pluralStringResource(R.plurals.summary_on_call_days, days, days), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.summary_on_call) + " · " + stringResource(R.string.summary_on_call_credited, Fmt.duration(credited)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What turning on-call duty on brings, shown under its switch in the settings. */
@Composable
fun OnCallRules(modifier: Modifier = Modifier) {
    val c = StaccoTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Icons.Rounded.Timer to R.string.on_call_rule_credit,
            RecoveryIcon to R.string.on_call_rule_recovery,
            Icons.Rounded.HourglassBottom to R.string.on_call_rule_expiry,
            Icons.Rounded.Savings to R.string.on_call_rule_off,
        ).forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = c.onCall, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A tappable row of the day sheet, in the style of its permit row. */
@Composable
fun SheetRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .bouncyClickable(onClick = onClick)
            .background(container)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/**
 * The on-call part of the day sheet: whether the day is on call and its interventions, which can
 * be typed in or corrected by hand.
 */
@Composable
fun OnCallDaySection(
    date: LocalDate,
    record: DayRecord,
    onOnCall: (Boolean) -> Unit,
    onSave: (Intervention) -> Unit,
    onDelete: (Intervention) -> Unit,
) {
    val c = StaccoTheme.colors
    var editing by remember { mutableStateOf<Intervention?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Intervention?>(null) }

    Column(Modifier.animateContentSize(spring(dampingRatio = 0.85f, stiffness = 400f)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(stringResource(R.string.settings_on_call_section))
        SheetRow(
            icon = OnCallIcon,
            tint = c.onCall,
            title = stringResource(R.string.day_on_call),
            subtitle = stringResource(R.string.day_on_call_sub),
            onClick = { onOnCall(!record.onCall) },
            container = if (record.onCall) c.onCall.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Switch(checked = record.onCall, onCheckedChange = onOnCall)
        }
        record.interventions.sortedBy { it.start }.forEach { intervention ->
            InterventionRow(date, intervention, onClick = { editing = intervention })
        }
        Pill(
            text = stringResource(R.string.intervention_add),
            icon = Icons.Rounded.Add,
            color = c.onCall.copy(alpha = 0.14f),
            contentColor = c.onCall,
            onClick = { adding = true },
        )
    }

    if (adding || editing != null) {
        val current = editing
        InterventionDialog(
            date = date,
            initial = current,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = {
                onSave(it)
                adding = false
                editing = null
            },
            onDelete = current?.let {
                {
                    deleting = it
                    editing = null
                }
            },
        )
    }

    deleting?.let { intervention ->
        val credited = OnCall.credited(date, intervention)
        ConfirmDialog(
            title = stringResource(R.string.intervention_delete_title),
            text = if (credited > 0) stringResource(R.string.intervention_delete_text, timesOf(intervention), Fmt.duration(credited)) else timesOf(intervention),
            confirmLabel = stringResource(R.string.intervention_delete),
            icon = OnCallIcon,
            destructive = true,
            onDismiss = { deleting = null },
            onConfirm = {
                onDelete(intervention)
                deleting = null
            },
        )
    }
}

/** "21:40 → 22:12" with "32m → 45m credited", or "running". */
@Composable
private fun InterventionRow(date: LocalDate, intervention: Intervention, onClick: () -> Unit) {
    val c = StaccoTheme.colors
    val real = OnCall.realMinutes(date, intervention)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .bouncyClickable(onClick = onClick)
            .background(c.onCall.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Timer, contentDescription = null, tint = c.onCall, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(timesOf(intervention), style = MaterialTheme.typography.titleSmall)
            Text(
                if (real != null) {
                    stringResource(R.string.intervention_credit, Fmt.duration(real), Fmt.duration(OnCall.credited(real)))
                } else {
                    stringResource(R.string.intervention_running_short)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (real == null) c.onCall else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "21:40 → 22:12", "23:30 → 00:40 +1", or "21:40 → …" while it runs. */
fun timesOf(intervention: Intervention): String =
    "${Fmt.timeDay(intervention.start)} → ${intervention.end?.let { Fmt.timeDay(it) } ?: "…"}"

/**
 * Types in or corrects an intervention of [date]: start and end, the end on the next day when it is
 * earlier than the start. A night intervention of the day after an on-call day stays in that night.
 */
@Composable
fun InterventionDialog(
    date: LocalDate,
    initial: Intervention?,
    onDismiss: () -> Unit,
    onSave: (Intervention) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = StaccoTheme.colors
    val defaultStart = remember {
        val now = LocalTime.now()
        if (date == LocalDate.now()) (now.hour * 60 + now.minute - 30).coerceAtLeast(0) else 20 * 60
    }
    // Night interventions (past midnight of an on-call day) keep their place in that night.
    val nightOffset = (initial?.start ?: 0) / 1440 * 1440
    var start by remember { mutableIntStateOf(initial?.start ?: defaultStart) }
    var end by remember { mutableStateOf(if (initial == null) defaultStart + 30 else initial.end) }
    var picking by remember { mutableStateOf<Boolean?>(null) }

    /** The end at [clock], on the start's day or the next one. */
    fun endAt(clock: Int, from: Int): Int {
        val sameDay = clock + from / 1440 * 1440
        return if (sameDay < from) sameDay + 1440 else sameDay
    }

    StaccoDialog(onDismiss) {
        DialogHeader(stringResource(R.string.intervention_title), OnCallIcon, c.onCall, Fmt.longDate(date))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeBox(stringResource(R.string.intervention_start), Fmt.timeDay(start), Modifier.weight(1f)) { picking = true }
            TimeBox(
                stringResource(R.string.intervention_end),
                end?.let { Fmt.timeDay(it) } ?: stringResource(R.string.intervention_running_short),
                Modifier.weight(1f),
            ) { picking = false }
        }
        val current = end
        val real = current?.let { OnCall.realMinutes(date, Intervention(0, start, it)) }
        AnimatedVisibility(real != null) {
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.intervention_done, Fmt.duration(real ?: 0), Fmt.duration(OnCall.credited(real ?: 0))),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.onCall,
                )
                if (current != null && current / 1440 > start / 1440) {
                    Text(stringResource(R.string.intervention_end_next_day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        DialogButtons(
            onDismiss = onDismiss,
            onConfirm = {
                val base = initial ?: Intervention(id = System.currentTimeMillis(), start = start)
                onSave(base.copy(start = start, end = end, startSecond = if (start == initial?.start) base.startSecond else 0))
            },
            onExtra = onDelete,
            extraLabel = if (onDelete != null) stringResource(R.string.intervention_delete) else null,
        )
    }

    when (picking) {
        true -> TimePickerDialog(
            title = stringResource(R.string.intervention_start),
            initialMinutes = start,
            icon = Icons.Rounded.PlayArrow,
            tint = c.onCall,
            onDismiss = { picking = null },
            onConfirm = { clock ->
                start = clock + nightOffset
                end = end?.let { endAt(Math.floorMod(it, 1440), start) }
                picking = null
            },
        )
        false -> TimePickerDialog(
            title = stringResource(R.string.intervention_end),
            initialMinutes = end ?: LocalTime.now().let { it.hour * 60 + it.minute },
            icon = Icons.Rounded.Stop,
            tint = c.onCall,
            onDismiss = { picking = null },
            onConfirm = { clock ->
                end = endAt(clock, start)
                picking = null
            },
        )
        null -> Unit
    }
}

@Composable
private fun TimeBox(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = StaccoTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .bouncyClickable(onClick = onClick)
            .background(c.onCall.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** The recovery row of an office day, next to the permit one. */
@Composable
fun RecoveryRow(minutes: Int, onClick: () -> Unit) {
    SheetRow(
        icon = RecoveryIcon,
        tint = StaccoTheme.colors.onCall,
        title = stringResource(R.string.day_recovery),
        subtitle = stringResource(R.string.day_recovery_sub),
        onClick = onClick,
    ) {
        Text(if (minutes > 0) Fmt.duration(minutes) else "—", style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Recovery hours for a day, in blocks of half an hour up to the whole day ([room], what the
 * planned day leaves after a permit). Going beyond what is [available] is allowed but flagged.
 */
@Composable
fun RecoveryDialog(initial: Int, room: Int, available: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val c = StaccoTheme.colors
    val step = OnCall.RECOVERY_STEP
    var value by remember { mutableIntStateOf(initial.coerceIn(0, room)) }

    StaccoDialog(onDismiss) {
        DialogHeader(
            stringResource(R.string.day_recovery),
            RecoveryIcon,
            c.onCall,
            stringResource(R.string.recovery_available_on, Fmt.duration(available)),
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            FilledTonalIconButton(onClick = { value = if (value > 0) (value - 1) / step * step else 0 }, enabled = value > 0) {
                Icon(Icons.Rounded.Remove, contentDescription = "−${step}m")
            }
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    val up = targetState > initialState
                    (fadeIn(tween(220)) + scaleIn(initialScale = if (up) 0.8f else 1.2f)) togetherWith (fadeOut(tween(160)) + scaleOut(targetScale = if (up) 1.2f else 0.8f))
                },
                label = "recovery-value",
                modifier = Modifier.weight(1f),
            ) { minutes ->
                Text(
                    if (minutes > 0) Fmt.duration(minutes) else "0m",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (minutes > available) c.negative else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FilledTonalIconButton(onClick = { value = minOf(room, (value / step + 1) * step) }, enabled = value < room) {
                Icon(Icons.Rounded.Add, contentDescription = "+${step}m")
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Pill(stringResource(R.string.recovery_none), onClick = { value = 0 })
            Pill(
                stringResource(R.string.recovery_whole_day, Fmt.duration(room)),
                color = c.onCall.copy(alpha = 0.16f),
                contentColor = c.onCall,
                onClick = { value = room },
            )
        }
        AnimatedVisibility(value > available, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = c.negative, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.recovery_over), style = MaterialTheme.typography.bodySmall, color = c.negative)
            }
        }
        Spacer(Modifier.height(20.dp))
        DialogButtons(onDismiss = onDismiss, onConfirm = { onConfirm(value) })
    }
}

/**
 * On-call duty on Today: start and stop the intervention timer, the interventions of the duty day
 * and the recovery hours at a glance.
 */
@Composable
fun OnCallCard(state: OnCallState, today: LocalDate, onStart: () -> Unit, onStop: () -> Unit, onOpen: () -> Unit) {
    val c = StaccoTheme.colors
    StaccoCard(color = c.onCallContainer.copy(alpha = 0.5f)) {
        SectionLabel(stringResource(R.string.settings_on_call_section)) {
            Text(
                stringResource(if (state.dutyDate == today) R.string.on_call_today else R.string.on_call_last_night),
                style = MaterialTheme.typography.labelMedium,
                color = c.onCall,
            )
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = state.running,
            contentKey = { it != null },
            transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = 0.94f)) togetherWith fadeOut(tween(200)) },
            label = "intervention",
        ) { shown ->
            if (shown != null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulsingDot(c.onCall)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.intervention_running), style = MaterialTheme.typography.titleSmall, color = c.onCall)
                    }
                    Text(
                        Fmt.countdown(state.runningSeconds ?: 0),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.intervention_since, Fmt.time(shown.second.start)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    BigPillButton(
                        text = stringResource(R.string.intervention_stop),
                        icon = Icons.Rounded.Stop,
                        onClick = onStop,
                        container = c.onCall,
                        content = c.onCallContainer,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                BigPillButton(
                    text = stringResource(R.string.intervention_start_now),
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onStart,
                    container = c.onCall,
                    content = c.onCallContainer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (state.interventions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            state.interventions.forEach { intervention ->
                val real = OnCall.realMinutes(state.dutyDate, intervention) ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = c.onCall, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(timesOf(intervention), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.intervention_credit, Fmt.duration(real), Fmt.duration(OnCall.credited(real))),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        RecoveryLine(state.recovery, onOpen, Modifier.padding(top = 12.dp))
        state.alert?.takeIf { it.inWindow }?.let { RecoveryAlertBanner(it) }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse-alpha")
    val scale by pulse.animateFloat(0.8f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse-scale")
    Box(
        Modifier
            .size(10.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .background(color, CircleShape),
    )
}

/** Balance of the recovery hours and the next deadline; opens the Bank tab. */
@Composable
fun RecoveryLine(ledger: RecoveryLedger, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = StaccoTheme.colors
    val next = ledger.nextExpiry
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .bouncyClickable(pressed = 0.97f, onClick = onOpen)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(RecoveryIcon, contentDescription = null, tint = c.onCall)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    ledger.balance < 0 -> stringResource(R.string.recovery_negative)
                    next != null -> stringResource(R.string.recovery_next_expiry, Fmt.duration(next.left), Fmt.dayMonth(next.expiry))
                    else -> stringResource(R.string.recovery_nothing_expiring)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (ledger.balance < 0) c.negative else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        RollingText(Fmt.duration(ledger.balance), style = MaterialTheme.typography.titleLarge, color = if (ledger.balance < 0) c.negative else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
    }
}

/** Recovery hours about to be lost, as in the hour bank warnings. */
@Composable
fun RecoveryAlertBanner(alert: RecoveryAlert, modifier: Modifier = Modifier) {
    AlertRow(
        icon = Icons.Rounded.HourglassBottom,
        color = StaccoTheme.colors.vacation,
        title = stringResource(R.string.recovery_alert_title, Fmt.duration(alert.minutes), Fmt.dayMonth(alert.expiry)),
        body = if (alert.officeDaysLeft > 0) {
            pluralStringResource(R.plurals.recovery_alert_days, alert.officeDaysLeft, alert.officeDaysLeft)
        } else {
            stringResource(R.string.recovery_alert_last)
        },
        modifier = modifier,
    )
}

/** On Today, on a day without on-call duty: just the warning that recovery hours are expiring. */
@Composable
fun RecoveryReminderCard(state: OnCallState, onOpen: () -> Unit) {
    StaccoCard {
        RecoveryLine(state.recovery, onOpen)
        state.alert?.let { RecoveryAlertBanner(it) }
    }
}

/**
 * The recovery account in the Bank tab: balance, the months still to use with their deadline and
 * the latest interventions. Kept apart from the hour bank, with its own colour.
 */
@Composable
fun RecoveryCard(data: StaccoData, ledger: RecoveryLedger, alert: RecoveryAlert?) {
    val c = StaccoTheme.colors
    val recent = remember(data) {
        data.parsedDays()
            .flatMap { (date, record) -> record.interventions.filter { it.end != null }.map { date to it } }
            .sortedWith(compareByDescending<Pair<LocalDate, Intervention>> { it.first }.thenByDescending { it.second.start })
            .take(5)
    }
    StaccoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(RecoveryIcon, tint = c.onCall, container = c.onCall.copy(alpha = 0.14f), size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RollingText(
                    Fmt.duration(ledger.balance),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (ledger.balance < 0) c.negative else c.onCall,
                )
            }
        }
        Text(
            stringResource(R.string.recovery_bank_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        alert?.takeIf { it.inWindow }?.let { RecoveryAlertBanner(it) }
        if (ledger.lots.isEmpty() && recent.isEmpty()) {
            Text(
                stringResource(R.string.recovery_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        ledger.lots.forEach { LotRow(it) }
        if (ledger.planned > 0) {
            Text(
                stringResource(R.string.recovery_planned, Fmt.duration(ledger.planned)),
                style = MaterialTheme.typography.bodySmall,
                color = c.onCall,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (ledger.expired > 0) {
            Text(
                stringResource(R.string.recovery_expired, Fmt.duration(ledger.expired)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.recovery_recent), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            recent.forEach { (date, intervention) ->
                val real = OnCall.realMinutes(date, intervention) ?: 0
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.shortDate(date), style = MaterialTheme.typography.titleSmall)
                        Text(timesOf(intervention), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "+" + Fmt.duration(OnCall.credited(real)),
                        style = MaterialTheme.typography.titleSmall,
                        color = c.onCall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LotRow(lot: RecoveryLot) {
    val c = StaccoTheme.colors
    val share by animateFloatAsState(
        if (lot.earned > 0) lot.left.toFloat() / lot.earned else 0f,
        spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "lot-left",
    )
    Column(Modifier.padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.recovery_lot, Fmt.monthName(lot.month)), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.recovery_lot_expiry, Fmt.dayMonth(lot.expiry)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lot.atRisk > 0 && lot.expiry.minusDays(14) <= LocalDate.now()) c.vacation else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Fmt.duration(lot.left), style = MaterialTheme.typography.titleMedium)
        }
        LinearProgressIndicator(
            progress = { share },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(6.dp),
            color = c.onCall,
            trackColor = c.onCall.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
    }
}
