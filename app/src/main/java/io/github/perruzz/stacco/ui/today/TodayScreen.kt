package io.github.perruzz.stacco.ui.today

import io.github.perruzz.stacco.ui.components.rememberAmbientPhase
import io.github.perruzz.stacco.ui.components.readableWidth
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import io.github.perruzz.stacco.Links
import io.github.perruzz.stacco.ui.oncall.OnCallCard
import io.github.perruzz.stacco.ui.oncall.RecoveryReminderCard
import io.github.perruzz.stacco.ui.settings.openUrl
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.BankAlert
import io.github.perruzz.stacco.domain.DayIssue
import io.github.perruzz.stacco.domain.DayFix
import io.github.perruzz.stacco.domain.DayPhase
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.ExitTarget
import io.github.perruzz.stacco.domain.Rounding
import io.github.perruzz.stacco.domain.SmartPeriod
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.domain.TodaySnapshot
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.ui.MainTab
import io.github.perruzz.stacco.ui.MainViewModel
import io.github.perruzz.stacco.ui.components.DayRing
import io.github.perruzz.stacco.ui.bank.vacationHours
import io.github.perruzz.stacco.ui.calendar.DaySheet
import io.github.perruzz.stacco.ui.components.IconBubble
import io.github.perruzz.stacco.ui.components.InfoRow
import io.github.perruzz.stacco.ui.components.MorphBadge
import io.github.perruzz.stacco.ui.components.Pill
import io.github.perruzz.stacco.ui.components.RingMarker
import io.github.perruzz.stacco.ui.components.RollingText
import io.github.perruzz.stacco.ui.components.SectionLabel
import io.github.perruzz.stacco.ui.components.StaccoCard
import io.github.perruzz.stacco.ui.components.StaccoDialog
import io.github.perruzz.stacco.ui.components.StampDial
import io.github.perruzz.stacco.ui.components.TimePickerDialog
import io.github.perruzz.stacco.ui.components.bouncyClickable
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import io.github.perruzz.stacco.ui.theme.container
import io.github.perruzz.stacco.ui.theme.deltaColor
import io.github.perruzz.stacco.ui.theme.shiftColor
import io.github.perruzz.stacco.ui.theme.shiftContainer
import io.github.perruzz.stacco.ui.theme.tint
import io.github.perruzz.stacco.ui.util.Fmt
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun TodayScreen(viewModel: MainViewModel, onOpenTab: (MainTab) -> Unit) {
    val snapshot by viewModel.today.collectAsStateWithLifecycle()
    val supportDue by viewModel.support.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val s = snapshot ?: return
    var editing by remember { mutableStateOf<StampSlot?>(null) }
    var choosingType by remember { mutableStateOf(false) }
    var fixingExit by remember { mutableStateOf<LocalDate?>(null) }
    var fixingDay by remember { mutableStateOf<LocalDate?>(null) }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scroll = rememberScrollState()

    val working = s.isOffice || s.record.hasStamps
    // On call: the timer goes first, unless the working day is under way and nothing is running.
    val onCall = s.onCall
    val dayUnderWay = working && s.projection.phase in setOf(DayPhase.MORNING, DayPhase.ON_BREAK, DayPhase.AFTERNOON)
    val onCallFirst = onCall != null && onCall.onDuty && (onCall.running != null || !dayUnderWay)
    // Starting an intervention from the card below the stamps moves it to the top: follow it there
    // instead of leaving the screen on what slid up into its place.
    var wasFirst by remember { mutableStateOf(onCallFirst) }
    LaunchedEffect(onCallFirst) {
        if (onCallFirst && !wasFirst) scroll.animateScrollTo(0, spring(dampingRatio = 0.9f, stiffness = 220f))
        wasFirst = onCallFirst
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .readableWidth()
            .padding(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = bottom + 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        var index = 0
        Appear(index++) { Header(s, onTypeClick = { choosingType = true }) }

        val toFix = s.dayToFix
        // Keeps the last day while the card animates away after the fix.
        var lastToFix by remember { mutableStateOf(toFix) }
        if (toFix != null) lastToFix = toFix
        Appear(index++) {
            AnimatedVisibility(
                visible = toFix != null,
                enter = fadeIn(tween(400)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = 300f)),
                exit = fadeOut(tween(300)) + shrinkVertically(tween(450, delayMillis = 150)),
            ) {
                lastToFix?.let { (date, kind) ->
                    FixDayCard(
                        date = date,
                        kind = kind,
                        onFix = { if (kind == DayFix.MISSING_EXIT) fixingExit = date else fixingDay = date },
                    )
                }
            }
        }

        val onCallCard: @Composable () -> Unit = {
            if (onCall != null) {
                OnCallCard(
                    state = onCall,
                    today = s.calendarDate,
                    onStart = viewModel::startIntervention,
                    onStop = viewModel::stopIntervention,
                    onOpen = { onOpenTab(MainTab.BANK) },
                )
            }
        }
        if (onCallFirst) Appear(index++) { onCallCard() }
        when {
            s.isSmart -> Appear(index++) { SmartDayCard(s, onOffice = { viewModel.setDayType(s.date, if (s.scheduled) null else DayType.OFFICE) }) }
            s.isTravel -> Appear(index++) { TravelCard(s, onChange = { choosingType = true }) }
            s.isAway -> Appear(index++) { AwayCard(s, onChange = { choosingType = true }) }
            !working -> Appear(index++) { RestDayCard(s, onWork = { viewModel.stampAt(StampSlot.ENTRY, null) }) }
            else -> {
                if (s.projection.phase == DayPhase.NOT_STARTED) {
                    Appear(index++) { ReadyCard(s) }
                } else {
                    Appear(index++) {
                        HeroCard(
                            s = s,
                            onTarget = { target ->
                                viewModel.updateData { it.copy(notifications = it.notifications.copy(exitTarget = target)) }
                            },
                        )
                    }
                }
                Appear(index++) {
                    AnimatedVisibility(
                        visible = s.nextSlot != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut(tween(450)) + shrinkVertically(tween(500, delayMillis = 250)),
                    ) {
                        StampDialCard(s, onStamp = { slot, minutes -> viewModel.stampAt(slot, minutes) })
                    }
                }
                Appear(index++) {
                    StaccoCard {
                        SectionLabel(stringResource(R.string.section_stamps))
                        Spacer(Modifier.height(8.dp))
                        StampTimeline(
                            record = s.record,
                            next = s.nextSlot,
                            breakInfo = if (s.record.entry != null) s.projection.breakInfo else null,
                            worked = s.projection.result?.worked,
                            onEdit = { editing = it },
                            showBreak = s.expectsBreak,
                            counted = Rounding.counted(s.record, s.work.rounding),
                        )
                        AnimatedVisibility(s.record.entry != null && s.record.breakOut == null && s.projection.breakInfo.deducted > 0) {
                            Hint(
                                icon = Icons.Rounded.LocalCafe,
                                text = stringResource(R.string.stamps_break_assumed, Fmt.duration(s.projection.breakInfo.deducted)),
                            )
                        }
                    }
                }
                if (s.record.entry != null) {
                    Appear(index++) { CalculationCard(s) }
                }
            }
        }
        if (onCall != null && onCall.onDuty && !onCallFirst) Appear(index++) { onCallCard() }
        if (onCall != null && !onCall.onDuty && onCall.alert?.inWindow == true) {
            Appear(index++) { RecoveryReminderCard(onCall, onOpen = { onOpenTab(MainTab.BANK) }) }
        }
        Appear(index++) { BankSummaryCard(s, onOpen = { onOpenTab(MainTab.BANK) }) }
        Appear(index++) {
            AnimatedVisibility(
                visible = supportDue,
                enter = fadeIn(tween(500)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = 300f)),
                exit = fadeOut(tween(300)) + shrinkVertically(tween(450, delayMillis = 150)),
            ) {
                SupportCard(
                    onOpen = {
                        openUrl(context, Links.DONATE)
                        viewModel.supportOpened()
                    },
                    onLater = viewModel::supportLater,
                    onNever = viewModel::supportNever,
                )
            }
        }
    }

    editing?.let { slot ->
        val current = s.record[slot]
        TimePickerDialog(
            title = stringResource(slot.label),
            initialMinutes = current ?: defaultFor(slot, s),
            icon = slot.icon,
            onDismiss = { editing = null },
            onConfirm = {
                viewModel.setStamp(s.date, slot, it)
                editing = null
            },
            onClear = if (current != null) {
                {
                    viewModel.setStamp(s.date, slot, null)
                    editing = null
                }
            } else {
                null
            },
        )
    }

    fixingDay?.let { date ->
        DaySheet(date = date, data = s.data, viewModel = viewModel, onDismiss = { fixingDay = null })
    }

    fixingExit?.let { date ->
        TimePickerDialog(
            title = stringResource(R.string.missing_exit_pick, Fmt.longDateInline(date)),
            initialMinutes = suggestedExit(s.data, date),
            icon = StampSlot.EXIT.icon,
            onDismiss = { fixingExit = null },
            onConfirm = {
                viewModel.setStamp(date, StampSlot.EXIT, it)
                fixingExit = null
            },
        )
    }

    if (choosingType) {
        StaccoDialog(onDismiss = { choosingType = false }) {
            Text(stringResource(R.string.daytype_dialog_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(Fmt.longDate(s.date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            DayTypeSelector(
                selected = s.record.type,
                effective = if (s.record.type == null) s.type else null,
                smartBadge = "${s.smartUsed}/${s.data.smart.allowance}",
                onSelect = {
                    viewModel.setDayType(s.date, it)
                    choosingType = false
                },
            )
        }
    }
}

/** Where a forgotten exit most likely was: the planned end, or entry + hours + break. */
private fun suggestedExit(data: StaccoData, date: LocalDate): Int? {
    val record = data.days[date.toString()] ?: return null
    val entry = record.entry ?: return null
    val calendar = WorkCalendar.from(data)
    calendar.plannedEnd(date)?.let { return it }
    val out = record.breakOut
    val back = record.breakIn
    val min = calendar.breakRules(date).min
    val pause = when {
        out != null && back != null -> maxOf(back - out, min)
        calendar.expectsBreak(date) -> min
        else -> 0
    }
    return entry + calendar.required(date) + pause
}

/** A gentle nudge about a day the bank skips: exit never stamped, or stamps that do not add up. */
@Composable
private fun FixDayCard(date: LocalDate, kind: DayFix, onFix: () -> Unit) {
    val missingExit = kind == DayFix.MISSING_EXIT
    val c = StaccoTheme.colors
    val pulse = rememberInfiniteTransition(label = "missing-exit")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "missing-exit-scale",
    )
    StaccoCard(color = c.vacationContainer.copy(alpha = 0.55f)) {
        Row(verticalAlignment = Alignment.Top) {
            IconBubble(
                StampSlot.EXIT.icon,
                tint = c.vacation,
                container = c.vacation.copy(alpha = 0.16f),
                size = 44.dp,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (missingExit) R.string.missing_exit_title else R.string.fix_day_title, Fmt.longDateInline(date)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (missingExit) R.string.missing_exit_body else R.string.fix_day_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onFix) {
                    Icon(if (missingExit) StampSlot.EXIT.icon else Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (missingExit) R.string.missing_exit_action else R.string.fix_day_action))
                }
            }
        }
    }
}

/** A sensible starting point for the time picker of an empty slot. */
private fun defaultFor(slot: StampSlot, s: TodaySnapshot): Int? = when (slot) {
    StampSlot.ENTRY -> s.projection.plannedStart
    StampSlot.BREAK_IN -> s.record.breakOut?.let { it + s.projection.breakInfo.deducted.coerceAtLeast(1) }
    StampSlot.EXIT -> s.targetExit
    else -> null
}

/** Staggered entrance for the cards of a screen. */
@Composable
fun Appear(index: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(60L * index)
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 180f))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * 48.dp.toPx()
            val scale = 0.96f + 0.04f * progress.value
            scaleX = scale
            scaleY = scale
        },
    ) { content() }
}

@Composable
private fun Header(s: TodaySnapshot, onTypeClick: () -> Unit) {
    val hour = s.nowMinutes / 60
    val greeting = stringResource(
        when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_afternoon
            in 18..22 -> R.string.greeting_evening
            else -> R.string.greeting_night
        },
    )
    val name = s.data.profile.name.trim()
    val icon: ImageVector = when (hour) {
        in 5..7 -> Icons.Rounded.WbTwilight
        in 8..17 -> Icons.Rounded.WbSunny
        in 18..20 -> Icons.Rounded.WbTwilight
        else -> Icons.Rounded.Bedtime
    }
    // A turn every 30 seconds, 20 steps a second: Today does not redraw at 120 fps for it.
    val sun = rememberAmbientPhase(30_000)

    HeaderLayout {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = StaccoTheme.colors.gold,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { rotationZ = if (icon == Icons.Rounded.WbSunny) sun.value * 360f else 0f },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.today_date, Fmt.longDate(s.calendarDate)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (name.isEmpty()) greeting else stringResource(R.string.greeting_with_name, greeting, name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        val type = s.type
        val shift = s.shift
        if (shift != null && type == DayType.OFFICE) {
            Pill(
                text = shift.name,
                icon = Icons.Rounded.Schedule,
                color = shiftContainer(shift.color),
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClick = onTypeClick,
            )
        } else {
            Pill(
                text = stringResource(type.label),
                icon = type.icon,
                color = type.container(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClick = onTypeClick,
            )
        }
    }
}

/**
 * The greeting and the day type side by side, the type pill under the greeting when they do not
 * fit whole: with large fonts on a narrow phone "afternoon," stays one word (no ", Giulia" alone).
 */
@Composable
private fun HeaderLayout(content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = 12.dp.roundToPx()
        val (greeting, pill) = measurables
        val pillP = pill.measure(Constraints(maxWidth = width))
        val room = width - pillP.width - gap
        if (room >= greeting.minIntrinsicWidth(Constraints.Infinity)) {
            val greetingP = greeting.measure(Constraints(maxWidth = room))
            val height = maxOf(greetingP.height, pillP.height)
            layout(width, height) {
                greetingP.place(0, (height - greetingP.height) / 2)
                pillP.place(width - pillP.width, (height - pillP.height) / 2)
            }
        } else {
            val greetingP = greeting.measure(Constraints(maxWidth = width))
            val below = 8.dp.roundToPx()
            layout(width, greetingP.height + below + pillP.height) {
                greetingP.place(0, 0)
                pillP.place(0, greetingP.height + below)
            }
        }
    }
}

private enum class HeroState { READY, WORKING, BREAK, OVERTIME, DONE }

@Composable
private fun HeroCard(s: TodaySnapshot, onTarget: (ExitTarget) -> Unit) {
    val p = s.projection
    val target = s.data.notifications.exitTarget
    val exit = s.targetExit
    val entry = s.record.entry
    val c = StaccoTheme.colors
    val scheme = MaterialTheme.colorScheme

    val state = when {
        p.phase == DayPhase.NOT_STARTED -> HeroState.READY
        p.phase == DayPhase.DONE -> HeroState.DONE
        p.phase == DayPhase.ON_BREAK -> HeroState.BREAK
        exit != null && s.nowMinutes >= exit -> HeroState.OVERTIME
        else -> HeroState.WORKING
    }

    val progress = when (state) {
        HeroState.READY -> 0f
        HeroState.DONE, HeroState.OVERTIME -> 1f
        else -> if (entry != null && exit != null && exit > entry) {
            ((s.nowSeconds - entry * 60).toFloat() / ((exit - entry) * 60)).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    val ringColors = when (state) {
        HeroState.BREAK -> listOf(c.lunch.copy(alpha = 0.7f), c.lunch)
        HeroState.OVERTIME -> listOf(scheme.primary, c.gold)
        HeroState.DONE -> {
            val d = p.result?.delta ?: 0
            if (d < 0) listOf(c.negative.copy(alpha = 0.7f), c.negative) else listOf(scheme.primary, c.positive)
        }
        else -> listOf(scheme.primary, scheme.tertiary)
    }
    val markers = buildList {
        if (entry != null && exit != null && exit > entry && state == HeroState.WORKING) {
            val other = if (target == ExitTarget.STANDARD) p.bankExit else p.standardExit
            if (other != null && other != exit) add(RingMarker((other - entry).toFloat() / (exit - entry), c.gold))
        }
    }

    StaccoCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp, horizontal = 16.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DayRing(
                progress = progress,
                colors = ringColors,
                trackColor = scheme.surfaceContainerHighest,
                flowing = state == HeroState.WORKING || state == HeroState.OVERTIME,
                markers = markers,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth()
                    .height(250.dp),
            ) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (fadeIn(tween(400)) + scaleIn(tween(500), initialScale = 0.8f)) togetherWith
                            (fadeOut(tween(200)) + scaleOut(tween(300), targetScale = 1.1f))
                    },
                    label = "hero",
                ) { hero ->
                    HeroCenter(hero, s)
                }
            }
        }

        // Exit reference: standard or using the hour bank.
        val bankExit = p.bankExit
        val standardExit = p.standardExit
        AnimatedVisibility(
            visible = state != HeroState.DONE && state != HeroState.READY && bankExit != null && standardExit != null && bankExit != standardExit,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                TargetChip(
                    title = stringResource(R.string.target_standard),
                    time = Fmt.timeDay(standardExit),
                    selected = target == ExitTarget.STANDARD,
                    color = scheme.primary,
                    onClick = { onTarget(ExitTarget.STANDARD) },
                )
                TargetChip(
                    title = stringResource(if ((bankExit ?: 0) < (standardExit ?: 0)) R.string.target_bank else R.string.target_recover),
                    time = Fmt.timeDay(bankExit),
                    selected = target == ExitTarget.BANK,
                    color = if ((bankExit ?: 0) < (standardExit ?: 0)) c.positive else c.negative,
                    onClick = { onTarget(ExitTarget.BANK) },
                )
            }
        }
    }
}

@Composable
private fun HeroCenter(state: HeroState, s: TodaySnapshot) {
    val p = s.projection
    val exit = s.targetExit
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
        when (state) {
            HeroState.READY -> {
                Text(stringResource(R.string.hero_ready), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                RollingText(Fmt.time(s.nowMinutes), style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(6.dp))
                val preview = io.github.perruzz.stacco.domain.DayCalculator.previewExit(
                    s.nowMinutes, s.projection.required, io.github.perruzz.stacco.domain.BreakRules(s.projection.breakInfo.deducted),
                )
                Text(
                    stringResource(R.string.hero_preview, Fmt.timeDay(preview)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            HeroState.WORKING -> {
                Text(stringResource(if (s.projection.isShift) R.string.hero_shift_left else R.string.hero_left), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                RollingText(
                    Fmt.countdown(s.secondsTo(exit ?: s.nowMinutes)),
                    style = MaterialTheme.typography.displayMedium,
                    countingDown = true,
                )
                Spacer(Modifier.height(8.dp))
                Pill(
                    text = stringResource(R.string.hero_at, Fmt.timeDay(exit)),
                    icon = Icons.Rounded.Flag,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            HeroState.BREAK -> {
                Icon(Icons.Rounded.LocalCafe, contentDescription = null, tint = StaccoTheme.colors.lunch, modifier = Modifier.size(30.dp))
                Text(stringResource(R.string.hero_break), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val out = s.record.breakOut ?: s.nowMinutes
                RollingText(Fmt.countdown(s.nowSeconds - out * 60), style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(6.dp))
                p.earliestReturn?.let { from ->
                    Text(
                        stringResource(R.string.hero_return_from, Fmt.time(from)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            HeroState.OVERTIME -> {
                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = StaccoTheme.colors.gold, modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.hero_overtime), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                RollingText("+" + Fmt.countdown(-s.secondsTo(exit ?: s.nowMinutes)), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.hero_overtime_since, Fmt.time(exit)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HeroState.DONE -> {
                val result = p.result
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.hero_done), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RollingText(Fmt.duration(result?.worked ?: 0), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                if (result != null) {
                    Pill(
                        text = stringResource(R.string.hero_delta, Fmt.duration(result.delta, signed = true)),
                        color = deltaColor(result.delta).copy(alpha = 0.16f),
                        contentColor = deltaColor(result.delta),
                    )
                } else {
                    Pill(
                        text = stringResource(R.string.calc_issue_order_short),
                        icon = Icons.Rounded.Warning,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetChip(title: String, time: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) color.copy(alpha = 0.16f) else Color.Transparent, label = "target-bg")
    val border by animateColorAsState(if (selected) color else MaterialTheme.colorScheme.outlineVariant, label = "target-border")
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(1.5.dp, border, RoundedCornerShape(18.dp))
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(time, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The stamp dial: the clock in the middle, the bezel to fix the minutes. */
@Composable
private fun StampDialCard(s: TodaySnapshot, onStamp: (StampSlot, Int) -> Unit) {
    val next = s.nextSlot ?: return
    val c = StaccoTheme.colors
    StaccoCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 20.dp, horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = next,
                transitionSpec = {
                    (fadeIn(tween(350, delayMillis = 200)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 300f), initialScale = 0.7f)) togetherWith
                        (fadeOut(tween(450, delayMillis = 300)) + scaleOut(tween(500, delayMillis = 300), targetScale = 1.08f))
                },
                label = "dial-slot",
            ) { slot ->
                val lunch = slot == StampSlot.BREAK_OUT || slot == StampSlot.BREAK_IN
                StampDial(
                    actionLabel = stringResource(slot.action),
                    icon = slot.icon,
                    nowSeconds = s.nowSeconds,
                    nowLabel = stringResource(R.string.dial_now),
                    accent = if (lunch) c.lunch else MaterialTheme.colorScheme.primary,
                    onAccent = if (lunch) Color(0xFF2B1D07) else MaterialTheme.colorScheme.onPrimary,
                    onStamp = { minutes -> onStamp(slot, minutes) },
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.dial_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            val alternative = s.alternativeSlot
            AnimatedVisibility(alternative != null) {
                val alt = alternative ?: StampSlot.EXIT
                TextButton(onClick = { onStamp(alt, s.nowMinutes) }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(alt.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (alt == StampSlot.EXIT) R.string.stamp_skip_break else R.string.stamp_break_out))
                }
            }
        }
    }
}

/** Before the first stamp: what the day will look like. */
@Composable
private fun ReadyCard(s: TodaySnapshot) {
    val shift = s.shift
    val start = s.projection.plannedStart
    val end = s.projection.plannedEnd
    val c = StaccoTheme.colors
    StaccoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphBadge(
                icon = if (shift != null) Icons.Rounded.Schedule else Icons.Rounded.WbSunny,
                colors = if (shift != null) listOf(shiftColor(shift.color), MaterialTheme.colorScheme.tertiary) else listOf(MaterialTheme.colorScheme.primary, c.gold),
                size = 72.dp,
                periodMillis = 3200,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (shift != null && start != null && end != null) {
                    Text(
                        stringResource(R.string.ready_shift, shift.name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "${Fmt.time(start)} → ${Fmt.timeDay(end)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = shiftColor(shift.color),
                    )
                    val sub = when {
                        s.nowMinutes < start -> stringResource(R.string.ready_shift_in, Fmt.duration(start - s.nowMinutes))
                        s.nowMinutes < end -> stringResource(R.string.ready_shift_running, Fmt.duration(end - s.nowMinutes))
                        else -> stringResource(R.string.ready_shift_over)
                    }
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.hero_ready), style = MaterialTheme.typography.titleLarge)
                    val preview = io.github.perruzz.stacco.domain.DayCalculator.previewExit(
                        s.nowMinutes, s.projection.required, io.github.perruzz.stacco.domain.BreakRules(s.projection.breakInfo.deducted),
                    )
                    Text(
                        stringResource(R.string.hero_preview, Fmt.timeDay(preview)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalculationCard(s: TodaySnapshot) {
    val p = s.projection
    val b = p.breakInfo
    val c = StaccoTheme.colors
    StaccoCard {
        SectionLabel(stringResource(R.string.section_calc))
        Spacer(Modifier.height(8.dp))

        if (!p.isValid) {
            Hint(
                icon = Icons.Rounded.Warning,
                text = stringResource(
                    if (DayIssue.BREAK_NOT_CLOSED in p.issues) R.string.calc_issue_break else R.string.calc_issue_order,
                ),
                color = MaterialTheme.colorScheme.error,
            )
            return@StaccoCard
        }

        if (p.required == 0) {
            Hint(icon = Icons.Rounded.Info, text = stringResource(R.string.calc_extra_day))
            return@StaccoCard
        }

        val entry = s.record.entry ?: return@StaccoCard
        if (p.isShift) {
            val shift = s.shift
            val end = p.plannedEnd ?: 0
            InfoRow(
                icon = Icons.Rounded.Flag,
                iconTint = shift?.let { shiftColor(it.color) } ?: MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.calc_shift_end),
                value = Fmt.timeDay(end),
                emphasized = true,
                supporting = shift?.let { stringResource(R.string.calc_shift_sub, it.name, Fmt.time(it.start), Fmt.duration(p.required)) },
            )
            val projected = (end - entry - b.deducted) - p.required
            InfoRow(
                icon = Icons.Rounded.Savings,
                iconTint = deltaColor(projected),
                title = stringResource(R.string.calc_shift_delta),
                value = Fmt.duration(projected, signed = true),
                valueColor = deltaColor(projected),
                supporting = stringResource(R.string.calc_shift_delta_sub, Fmt.time(entry)),
            )
            if (b.deducted > 0 || !b.assumed) {
                InfoRow(
                    icon = Icons.Rounded.Restaurant,
                    iconTint = c.lunch,
                    title = stringResource(R.string.calc_break),
                    value = Fmt.duration(b.deducted),
                    supporting = breakSupport(b),
                )
            }
        } else {
            InfoRow(
                icon = Icons.Rounded.Flag,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.calc_standard),
                value = Fmt.timeDay(p.standardExit),
                emphasized = true,
                supporting = stringResource(R.string.calc_standard_sub, Fmt.duration(p.required), Fmt.duration(b.deducted)),
            )
            // As with shifts: no row when no break is due (days up to 6h) and none was stamped.
            if (b.deducted > 0 || !b.assumed) {
                InfoRow(
                    icon = Icons.Rounded.Restaurant,
                    iconTint = c.lunch,
                    title = stringResource(R.string.calc_break),
                    value = Fmt.duration(b.deducted),
                    supporting = breakSupport(b),
                )
            }

            val bankBefore = p.bankBefore
            val bankExit = p.bankExit
            val standard = p.standardExit
            if (bankExit != null && standard != null) {
                when {
                    bankBefore > 0 && bankExit < standard -> {
                        val flex = p.required - p.minimum
                        val capped = p.bankUsage >= flex && bankBefore > flex
                        InfoRow(
                            icon = Icons.Rounded.Savings,
                            iconTint = c.positive,
                            title = stringResource(R.string.calc_bank_early),
                            value = Fmt.timeDay(bankExit),
                            valueColor = c.positive,
                            supporting = stringResource(
                                R.string.calc_bank_early_sub,
                                Fmt.duration(p.bankUsage),
                                Fmt.duration(bankBefore - p.bankUsage),
                            ) + if (capped) stringResource(R.string.calc_bank_capped, Fmt.duration(flex)) else "",
                        )
                    }
                    bankBefore > 0 -> Hint(icon = Icons.Rounded.Info, text = stringResource(R.string.calc_bank_no_flex))
                    bankBefore < 0 -> {
                        val plan = s.alerts.filterIsInstance<BankAlert.Negative>().firstOrNull()
                        InfoRow(
                            icon = Icons.AutoMirrored.Rounded.TrendingUp,
                            iconTint = c.negative,
                            title = stringResource(R.string.calc_bank_recover),
                            value = Fmt.timeDay(bankExit),
                            valueColor = c.negative,
                            supporting = if (plan != null && plan.officeDaysLeft > 1) {
                                stringResource(R.string.calc_bank_recover_plan, Fmt.duration(-bankBefore), Fmt.duration(plan.perDay))
                            } else {
                                stringResource(R.string.calc_bank_recover_sub, Fmt.duration(-bankBefore))
                            },
                        )
                    }
                    else -> Unit
                }
            }
            val minimumExit = p.minimumExit
            if (p.minimum < p.required && minimumExit != null && minimumExit != bankExit) {
                InfoRow(
                    icon = Icons.Rounded.HourglassBottom,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = stringResource(R.string.calc_minimum),
                    value = Fmt.timeDay(minimumExit),
                    supporting = stringResource(R.string.calc_minimum_sub, Fmt.duration(p.minimum)),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        DayTimelineBar(
            record = s.record,
            now = s.nowMinutes,
            standardExit = p.standardExit,
            bankExit = p.bankExit,
        )
    }
}

@Composable
private fun breakSupport(b: io.github.perruzz.stacco.domain.BreakInfo): String = when {
    b.ongoing -> stringResource(R.string.calc_break_ongoing, Fmt.duration(b.actual ?: 0))
    b.assumed -> stringResource(R.string.calc_break_assumed)
    b.shortfall > 0 -> stringResource(R.string.calc_break_short, Fmt.duration(b.actual ?: 0), Fmt.duration(b.deducted))
    else -> stringResource(R.string.calc_break_ok)
}

@Composable
fun Hint(icon: ImageVector, text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun BankSummaryCard(s: TodaySnapshot, onOpen: () -> Unit) {
    val ledger = s.ledger
    StaccoCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Savings, tint = deltaColor(ledger.balance), container = deltaColor(ledger.balance).copy(alpha = 0.14f), size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.bank_card_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RollingText(Fmt.duration(ledger.balance, signed = true), style = MaterialTheme.typography.headlineMedium, color = deltaColor(ledger.balance))
            }
            ledger.todayDelta?.let { delta ->
                Pill(
                    text = stringResource(R.string.bank_card_today, Fmt.duration(delta, signed = true)),
                    color = deltaColor(delta).copy(alpha = 0.14f),
                    contentColor = deltaColor(delta),
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        s.alerts.forEach { alert -> AlertBanner(alert) }
    }
}

@Composable
fun AlertBanner(alert: BankAlert, modifier: Modifier = Modifier) {
    val c = StaccoTheme.colors
    when (alert) {
        is BankAlert.Expiry -> {
            val urgent = alert.inWindow || alert.atRisk
            val color = if (urgent) c.vacation else MaterialTheme.colorScheme.primary
            AlertRow(
                icon = Icons.Rounded.HourglassBottom,
                color = color,
                title = stringResource(R.string.alert_expiry, Fmt.duration(alert.balance), Fmt.dayMonth(alert.expiry)),
                body = when {
                    alert.maxPerDay <= 0 -> stringResource(R.string.alert_expiry_no_flex)
                    alert.atRisk -> pluralStringResource(R.plurals.alert_expiry_risk, alert.officeDaysLeft, alert.officeDaysLeft, Fmt.duration(alert.maxPerDay))
                    else -> pluralStringResource(R.plurals.alert_expiry_plan, alert.daysNeeded, alert.daysNeeded, Fmt.duration(alert.maxPerDay), alert.officeDaysLeft)
                },
                modifier = modifier,
            )
        }
        is BankAlert.Negative -> AlertRow(
            icon = Icons.Rounded.Warning,
            color = c.negative,
            title = stringResource(R.string.alert_negative, Fmt.duration(-alert.balance), Fmt.dayMonth(alert.monthEnd)),
            body = if (alert.officeDaysLeft > 0) {
                pluralStringResource(R.plurals.alert_negative_plan, alert.officeDaysLeft, Fmt.duration(alert.perDay), alert.officeDaysLeft)
            } else {
                stringResource(R.string.alert_negative_last)
            },
            modifier = modifier,
        )
    }
}

@Composable
internal fun AlertRow(icon: ImageVector, color: Color, title: String, body: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SmartDayCard(s: TodaySnapshot, onOffice: () -> Unit) {
    val c = StaccoTheme.colors
    StaccoCard(color = c.smartContainer.copy(alpha = 0.55f)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MorphBadge(icon = DayType.SMART.icon, colors = listOf(c.smart, MaterialTheme.colorScheme.tertiary), size = 132.dp)
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.smart_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.smart_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            SmartQuota(used = s.smartUsed, allowance = s.data.smart.allowance, period = s.data.smart.period, month = YearMonth.from(s.date))
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onOffice) {
                Icon(DayType.OFFICE.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.smart_switch))
            }
        }
    }
}

@Composable
private fun TravelCard(@Suppress("UNUSED_PARAMETER") s: TodaySnapshot, onChange: () -> Unit) {
    val c = StaccoTheme.colors
    StaccoCard(color = c.travelContainer.copy(alpha = 0.6f)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MorphBadge(icon = DayType.TRAVEL.icon, colors = listOf(c.travel, MaterialTheme.colorScheme.primary), size = 132.dp)
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.travel_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.travel_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onChange) { Text(stringResource(R.string.away_change)) }
        }
    }
}

@Composable
fun SmartQuota(used: Int, allowance: Int, period: SmartPeriod, month: YearMonth, modifier: Modifier = Modifier) {
    val c = StaccoTheme.colors
    val over = used > allowance
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (period == SmartPeriod.MONTH) {
                    stringResource(R.string.smart_quota_month, used, allowance, Fmt.monthName(month))
                } else {
                    stringResource(R.string.smart_quota_week, used, allowance)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (over) c.negative else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.smart_left, (allowance - used).coerceAtLeast(0)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (allowance > 0) (used.toFloat() / allowance).coerceIn(0f, 1f) else 1f },
            color = if (over) c.negative else c.smart,
            trackColor = c.smart.copy(alpha = 0.18f),
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun AwayCard(s: TodaySnapshot, onChange: () -> Unit) {
    val type = s.type ?: DayType.HOLIDAY
    val (title, body) = when (type) {
        DayType.VACATION -> stringResource(R.string.away_vacation_title) to stringResource(R.string.away_vacation_body)
        DayType.SICK -> stringResource(R.string.away_sick_title) to stringResource(R.string.away_sick_body)
        else -> stringResource(R.string.away_holiday_title) to (
            s.holiday?.let { stringResource(R.string.away_holiday_named, stringResource(it.label)) }
                ?: stringResource(R.string.away_holiday_body)
            )
    }
    StaccoCard(color = type.container().copy(alpha = 0.6f)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MorphBadge(icon = type.icon, colors = listOf(type.tint(), StaccoTheme.colors.gold), size = 132.dp)
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (type == DayType.VACATION && s.data.vacation.tracked) {
                val left = remember(s.data, s.date) { VacationCalculator.balance(s.data, s.date).available }
                Spacer(Modifier.height(12.dp))
                Pill(
                    stringResource(R.string.away_vacation_left, vacationHours(left)),
                    icon = DayType.VACATION.icon,
                    color = type.container(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onChange) { Text(stringResource(R.string.away_change)) }
        }
    }
}

@Composable
private fun RestDayCard(s: TodaySnapshot, onWork: () -> Unit) {
    val c = StaccoTheme.colors
    StaccoCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MorphBadge(icon = null.icon, colors = listOf(MaterialTheme.colorScheme.primary, c.auroraB), size = 132.dp)
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.rest_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.rest_body, s.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, LocalConfiguration.current.locales[0])),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onWork) {
                Icon(StampSlot.ENTRY.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.rest_work))
            }
        }
    }
}
