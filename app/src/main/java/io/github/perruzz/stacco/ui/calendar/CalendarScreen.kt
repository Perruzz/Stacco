package io.github.perruzz.stacco.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.DayCalculator
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.SmartPeriod
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.domain.parsedDays
import io.github.perruzz.stacco.export.MonthCsv
import io.github.perruzz.stacco.ui.MainViewModel
import io.github.perruzz.stacco.ui.components.RollingText
import io.github.perruzz.stacco.ui.components.SectionLabel
import io.github.perruzz.stacco.ui.components.StaccoCard
import io.github.perruzz.stacco.ui.components.bouncyClickable
import io.github.perruzz.stacco.ui.components.pressScale
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import io.github.perruzz.stacco.ui.theme.container
import io.github.perruzz.stacco.ui.theme.deltaColor
import io.github.perruzz.stacco.ui.theme.shiftColor
import io.github.perruzz.stacco.ui.theme.shiftContainer
import io.github.perruzz.stacco.ui.theme.tint
import io.github.perruzz.stacco.ui.today.Appear
import io.github.perruzz.stacco.ui.today.Hint
import io.github.perruzz.stacco.ui.today.SmartQuota
import io.github.perruzz.stacco.ui.today.icon
import io.github.perruzz.stacco.ui.today.label
import io.github.perruzz.stacco.ui.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

private const val PAGES = 241
private const val CENTER = 120
private val CELL_HEIGHT = 54.dp

@Composable
fun CalendarScreen(viewModel: MainViewModel) {
    val dataState by viewModel.data.collectAsStateWithLifecycle()
    val data = dataState ?: return
    val today by viewModel.currentDate.collectAsStateWithLifecycle()
    val baseMonth = remember { YearMonth.now() }
    val calendar = remember(data) { WorkCalendar.from(data) }
    val firstDow = remember { Engine.firstDayOfWeek() }
    val pager = rememberPagerState(initialPage = CENTER) { PAGES }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    // Shift planning: when a shift (or rest) is picked, tapping days assigns it.
    var painting by remember { mutableStateOf(false) }
    var paintShift by remember { mutableStateOf<String?>(null) }
    // Many days at once: press and drag across them (or tap them after "select"), then pick a type.
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(emptySet<LocalDate>()) }
    var dragAnchor by remember { mutableStateOf<LocalDate?>(null) }
    var dragBase by remember { mutableStateOf(emptySet<LocalDate>()) }
    var dragRemoving by remember { mutableStateOf(false) }
    // Month export: the month is remembered while the system file picker is open.
    val context = LocalContext.current
    var exporting by remember { mutableStateOf<YearMonth?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val target = exporting
        exporting = null
        if (uri != null && target != null) {
            scope.launch {
                val csv = MonthCsv.build(data, target, csvLabels(context))
                val ok = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } != null }.getOrDefault(false)
                }
                viewModel.notify(if (ok) R.string.export_month_done else R.string.backup_error)
            }
        }
    }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val month = baseMonth.plusMonths((pager.currentPage - CENTER).toLong())

    fun endSelection() {
        selecting = false
        picked = emptySet()
        dragAnchor = null
    }
    BackHandler(enabled = selecting) { endSelection() }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Room to scroll the calendar above the selection bar.
    val barSpace by animateDpAsState(if (selecting) 230.dp else 0.dp, spring(dampingRatio = 0.85f, stiffness = 300f), label = "bar-space")

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = bottom + 120.dp + barSpace),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Appear(0) {
                Column {
                    Text(stringResource(R.string.calendar_title), style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_prev))
                        }
                        AnimatedContent(
                            targetState = month,
                            transitionSpec = {
                                val dir = if (targetState > initialState) 1 else -1
                                (slideInHorizontally { dir * it / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -dir * it / 2 } + fadeOut())
                            },
                            modifier = Modifier.weight(1f),
                            label = "month",
                        ) { m ->
                            Text(
                                Fmt.monthYear(m),
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        FilledTonalIconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next))
                        }
                    }
                    AnimatedVisibility(month != baseMonth, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                            io.github.perruzz.stacco.ui.components.Pill(
                                text = stringResource(R.string.calendar_back_today),
                                icon = Icons.Rounded.Today,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = { scope.launch { pager.animateScrollToPage(CENTER) } },
                            )
                        }
                    }
                }
            }

            if (calendar.isShiftMode && data.work.shifts.isEmpty()) {
                Appear(1) { Hint(icon = Icons.Rounded.Info, text = stringResource(R.string.calendar_no_shifts)) }
            }
            if (calendar.isShiftMode && data.work.shifts.isNotEmpty()) {
                Appear(1) {
                    ShiftPlanner(
                        data = data,
                        painting = painting,
                        selected = paintShift,
                        onPick = { id ->
                            if (painting && paintShift == id) {
                                painting = false
                            } else {
                                endSelection()
                                painting = true
                                paintShift = id
                            }
                        },
                    )
                }
            }

            Appear(1) {
                StaccoCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 16.dp)) {
                    WeekdayHeader(firstDow)
                    Spacer(Modifier.height(8.dp))
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
                        val pageMonth = baseMonth.plusMonths((page - CENTER).toLong())
                        MonthGrid(
                            month = pageMonth,
                            data = data,
                            calendar = calendar,
                            today = today,
                            firstDow = firstDow,
                            picked = picked,
                            onDayClick = { date ->
                                when {
                                    selecting -> {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                                        picked = if (date in picked) picked - date else picked + date
                                    }
                                    painting -> {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                                        val current = data.days[date.toString()]?.shiftId
                                        viewModel.setShift(date, if (current == paintShift) null else paintShift)
                                    }
                                    else -> selected = date
                                }
                            },
                            onRangeStart = { date ->
                                painting = false
                                selecting = true
                                dragAnchor = date
                                dragBase = picked
                                dragRemoving = date in picked
                                picked = if (dragRemoving) picked - date else picked + date
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            },
                            onRangeExtend = { date ->
                                dragAnchor?.let { anchor ->
                                    val range = datesBetween(anchor, date)
                                    val next = if (dragRemoving) dragBase - range else dragBase + range
                                    if (next != picked) {
                                        picked = next
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                                    }
                                }
                            },
                            onRangeEnd = { dragAnchor = null },
                        isRanging = { dragAnchor != null },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (calendar.isShiftMode) ShiftLegend(data) else Legend()
                    AnimatedVisibility(!selecting, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                            io.github.perruzz.stacco.ui.components.Pill(
                                text = stringResource(R.string.select_days),
                                icon = Icons.Rounded.EditCalendar,
                                onClick = {
                                    painting = false
                                    selecting = true
                                },
                            )
                        }
                    }
                }
            }

            Appear(2) {
                MonthSummary(month, data, calendar, onExport = {
                    exporting = month
                    exportLauncher.launch("stacco-$month.csv")
                })
            }
            Appear(3) { OfficeDaysList(month, data, calendar, onOpen = { selected = it }) }
        }

        AnimatedVisibility(
            visible = selecting,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = bottom + 96.dp),
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            SelectionBar(
                data = data,
                picked = picked,
                onApply = { type ->
                    viewModel.setDayTypes(picked, type)
                    endSelection()
                },
                onClose = { endSelection() },
            )
        }
    }

    selected?.let { date ->
        DaySheet(date = date, data = data, viewModel = viewModel, onDismiss = { selected = null })
    }
}

@Composable
private fun WeekdayHeader(firstDow: DayOfWeek) {
    Row(Modifier.fillMaxWidth()) {
        repeat(7) { i ->
            val dow = firstDow.plus(i.toLong())
            Text(
                Fmt.weekdayNarrow(dow),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    data: StaccoData,
    calendar: WorkCalendar,
    today: LocalDate,
    firstDow: DayOfWeek,
    picked: Set<LocalDate>,
    onDayClick: (LocalDate) -> Unit,
    onRangeStart: (LocalDate) -> Unit,
    onRangeExtend: (LocalDate) -> Unit,
    onRangeEnd: () -> Unit,
    isRanging: () -> Boolean,
) {
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - firstDow.value + 7) % 7
    val start = first.minusDays(offset.toLong())
    val extendRange by rememberUpdatedState(onRangeExtend)
    val endRange by rememberUpdatedState(onRangeEnd)
    val ranging by rememberUpdatedState(isRanging)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.pointerInput(start) {
            // Press and hold a day, then drag across the others to pick them all.
            val gapX = 5.dp.toPx()
            val rowStep = (CELL_HEIGHT + 6.dp).toPx()
            fun dayAt(p: Offset): LocalDate {
                val cell = (size.width - 6 * gapX) / 7f
                val col = (p.x / (cell + gapX)).toInt().coerceIn(0, 6)
                val row = (p.y / rowStep).toInt().coerceIn(0, 5)
                return start.plusDays((row * 7 + col).toLong())
            }
            // The long press itself is handled by the day (so that letting go is not also a tap);
            // from then on the finger picks every day it crosses, until it is lifted.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    // After its long press the day consumes the moves: read them anyway.
                    if (ranging() && change.pressed && change.positionChangedIgnoreConsumed()) {
                        change.consume()
                        extendRange(dayAt(change.position))
                    }
                } while (event.changes.any { it.pressed })
                if (ranging()) endRange()
            }
        },
    ) {
        repeat(6) { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(7) { dow ->
                    val index = week * 7 + dow
                    val date = start.plusDays(index.toLong())
                    DayCell(
                        date = date,
                        inMonth = date.month == month.month,
                        data = data,
                        calendar = calendar,
                        today = today,
                        index = index,
                        picked = date in picked,
                        onClick = { onDayClick(date) },
                        onLongClick = { onRangeStart(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    data: StaccoData,
    calendar: WorkCalendar,
    today: LocalDate,
    index: Int,
    picked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val clickHaptics = LocalHapticFeedback.current
    val record = data.days[date.toString()]
    val type = calendar.effectiveType(date)
    val explicitOrWorked = record?.type != null || record?.hasStamps == true
    val delta = calendar.delta(date)
    val isToday = date == today
    val scheduled = calendar.isScheduled(date)
    val holiday = calendar.holiday(date)

    val shift = calendar.shift(date)
    val background = when {
        !inMonth -> Color.Transparent
        shift != null && type == DayType.OFFICE -> shiftContainer(shift.color)
        type == DayType.OFFICE && explicitOrWorked -> type.container()
        type != null && type != DayType.OFFICE -> type.container()
        scheduled -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    val appear = remember(date) { Animatable(0f) }
    LaunchedEffect(date) {
        delay(index * 9L)
        appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
    }
    // A little pop when a shift is (re)assigned, a type changes or the day is picked.
    val pop = remember(date) { Animatable(1f) }
    LaunchedEffect(shift?.id, type, picked) {
        if (appear.value >= 0.99f) {
            pop.snapTo(0.8f)
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
        }
    }
    val cellColor by animateColorAsState(background, tween(350), label = "cell-color")
    val pick by animateFloatAsState(if (picked) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "cell-pick")
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .height(CELL_HEIGHT)
            .graphicsLayer {
                val v = appear.value
                alpha = (if (inMonth) 1f else 0.32f) * v.coerceIn(0f, 1f)
                scaleX = (0.6f + 0.4f * v) * pop.value
                scaleY = scaleX
            }
            .clip(RoundedCornerShape(16.dp))
            .background(cellColor)
            .then(if (isToday) Modifier.border(2.dp, primary, RoundedCornerShape(16.dp)) else Modifier)
            .then(if (pick > 0.01f) Modifier.border(3.dp, primary.copy(alpha = pick), RoundedCornerShape(16.dp)) else Modifier)
            .pressScale(interaction, 0.88f)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = inMonth,
                hapticFeedbackEnabled = false,
                onLongClick = onLongClick,
                onClick = {
                    clickHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (pick > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(15.dp)
                    .graphicsLayer {
                        scaleX = pick
                        scaleY = pick
                    }
                    .background(primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(11.dp))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                color = when {
                    isToday -> MaterialTheme.colorScheme.primary
                    scheduled || explicitOrWorked -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            )
            when {
                shift != null && type == DayType.OFFICE -> Text(
                    if (delta != null && delta != 0) Fmt.compact(delta) else shift.code,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (delta != null && delta != 0) deltaColor(delta) else shiftColor(shift.color),
                    maxLines = 1,
                )
                delta != null -> Text(
                    Fmt.compact(delta),
                    style = MaterialTheme.typography.labelSmall,
                    color = deltaColor(delta),
                    maxLines = 1,
                )
                type != null && type != DayType.OFFICE -> Icon(type.icon, contentDescription = null, tint = type.tint(), modifier = Modifier.size(14.dp))
                record?.hasStamps == true -> Box(
                    Modifier
                        .size(6.dp)
                        .background(StaccoTheme.colors.office, CircleShape),
                )
                holiday != null -> Icon(DayType.HOLIDAY.icon, contentDescription = null, tint = DayType.HOLIDAY.tint(), modifier = Modifier.size(14.dp))
                else -> Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun Legend() {
    // Wraps whole entries onto a second line with large fonts instead of breaking the words.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(DayType.OFFICE, DayType.SMART, DayType.TRAVEL, DayType.VACATION, DayType.SICK, DayType.HOLIDAY).forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(type.tint(), CircleShape),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(type.shortLabel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

private val DayType.shortLabel: Int
    get() = when (this) {
        DayType.OFFICE -> R.string.type_office_short
        DayType.SMART -> R.string.type_smart_short
        DayType.TRAVEL -> R.string.type_travel_short
        DayType.VACATION -> R.string.type_vacation
        DayType.SICK -> R.string.type_sick
        DayType.HOLIDAY -> R.string.type_holiday_short
    }

@Composable
private fun MonthSummary(month: YearMonth, data: StaccoData, calendar: WorkCalendar, onExport: () -> Unit) {
    val days = remember(data) { data.parsedDays() }
    val inMonth = days.filterKeys { YearMonth.from(it) == month }
    val office = inMonth.count { (date, record) -> calendar.effectiveType(date) == DayType.OFFICE && record.hasStamps }
    val smart = inMonth.count { it.value.type == DayType.SMART }
    val travel = inMonth.count { it.value.type == DayType.TRAVEL }
    val vacation = inMonth.count { it.value.type == DayType.VACATION }
    val sick = inMonth.count { it.value.type == DayType.SICK }
    val bank = inMonth.keys.sumOf { calendar.delta(it) ?: 0 }
    val worked = inMonth.entries.sumOf { (date, record) ->
        if (calendar.effectiveType(date) == DayType.OFFICE) calendar.result(date)?.worked ?: 0 else 0
    }
    val planned = inMonth.keys.sumOf { calendar.required(it) }
    val c = StaccoTheme.colors

    StaccoCard {
        val exportDescription = stringResource(R.string.export_month_desc)
        SectionLabel(stringResource(R.string.calendar_summary, Fmt.monthName(month))) {
            FilledTonalButton(
                onClick = onExport,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(32.dp)
                    .semantics { contentDescription = exportDescription },
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.export_month), style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (calendar.isShiftMode) {
            ShiftCounts(month, data, calendar)
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(DayType.OFFICE.icon, c.office, stringResource(if (calendar.isShiftMode) R.string.calendar_shifts_worked else R.string.type_office), office.toString(), Modifier.weight(1f))
            if (calendar.isShiftMode) {
                StatTile(Icons.Rounded.Today, MaterialTheme.colorScheme.tertiary, stringResource(R.string.calendar_planned_hours), Fmt.duration(planned), Modifier.weight(1f))
            } else {
                StatTile(DayType.SMART.icon, c.smart, stringResource(R.string.type_smart_short), smart.toString(), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(DayType.TRAVEL.icon, c.travel, stringResource(R.string.type_travel), travel.toString(), Modifier.weight(1f))
            StatTile(DayType.VACATION.icon, c.vacation, stringResource(R.string.calendar_absences), (vacation + sick).toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        StatTile(Icons.Rounded.Savings, deltaColor(bank), stringResource(R.string.calendar_bank_month), Fmt.duration(bank, signed = true), Modifier.fillMaxWidth())
        if (worked > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.calendar_worked_month, Fmt.duration(worked)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (calendar.isShiftMode && data.smart.allowance == 0) {
            // Shift workers usually have no remote days: nothing to show.
        } else if (data.smart.period == SmartPeriod.MONTH && data.smart.allowance > 0) {
            Spacer(Modifier.height(14.dp))
            SmartQuota(used = smart, allowance = data.smart.allowance, period = SmartPeriod.MONTH, month = month)
        } else if (data.smart.period == SmartPeriod.WEEK) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.calendar_smart_week_rule, data.smart.allowance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, tint: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            RollingText(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun OfficeDaysList(month: YearMonth, data: StaccoData, calendar: WorkCalendar, onOpen: (LocalDate) -> Unit) {
    val days = remember(data) { data.parsedDays() }
    val entries = days.filter { (date, record) -> YearMonth.from(date) == month && record.hasStamps }
        .toSortedMap()
    if (entries.isEmpty()) return
    // Days before the one in progress (a night shift may still be running from yesterday).
    val activeDate = Engine.activeDay(data, LocalDateTime.now()).date
    StaccoCard {
        SectionLabel(stringResource(R.string.calendar_office_list))
        Spacer(Modifier.height(6.dp))
        entries.forEach { (date, record) ->
            val required = calendar.required(date)
            val rules = calendar.breakRules(date)
            val result = calendar.result(date)
            val b = DayCalculator.breakInfo(record, rules, required, null)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .bouncyClickable(pressed = 0.98f) { onOpen(date) }
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.weekdayNarrow(date.dayOfWeek), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "${Fmt.time(record.entry)} → ${Fmt.timeDay(record.exit)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val shiftName = calendar.shift(date)?.name
                    val breakText = if (shiftName != null && b.deducted == 0) {
                        stringResource(R.string.calendar_shift_line, shiftName)
                    } else if (b.assumed && b.deducted == 0) {
                        null // no break due on the day (up to 6h) and none stamped
                    } else if (b.assumed) {
                        stringResource(R.string.calendar_break_assumed, Fmt.duration(b.deducted))
                    } else {
                        stringResource(R.string.calendar_break, Fmt.time(record.breakOut), Fmt.time(record.breakIn))
                    }
                    if (breakText != null) {
                        Text(breakText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (result != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Fmt.duration(result.worked), style = MaterialTheme.typography.labelLarge)
                        Text(
                            Fmt.duration(result.delta, signed = true),
                            style = MaterialTheme.typography.labelMedium,
                            color = deltaColor(result.delta),
                            modifier = Modifier
                                .background(deltaColor(result.delta).copy(alpha = 0.12f), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                } else if (record.exit == null && date < activeDate) {
                    Text(stringResource(R.string.calendar_missing_exit), style = MaterialTheme.typography.labelMedium, color = StaccoTheme.colors.vacation)
                } else if (date < activeDate && DayCalculator.issues(record).isNotEmpty()) {
                    Text(stringResource(R.string.calendar_fix), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(stringResource(R.string.calendar_incomplete), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}


/** Pick a shift, then tap the days of the month to plan it. */
@Composable
private fun ShiftPlanner(data: StaccoData, painting: Boolean, selected: String?, onPick: (String?) -> Unit) {
    StaccoCard(color = if (painting) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f)) {
        SectionLabel(stringResource(R.string.calendar_plan_title))
        Spacer(Modifier.height(4.dp))
        AnimatedContent(targetState = painting, label = "plan-hint") { active ->
            Text(
                stringResource(if (active) R.string.calendar_plan_active else R.string.calendar_plan_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        io.github.perruzz.stacco.ui.today.ShiftSelector(
            shifts = data.work.shifts,
            selected = if (painting) selected else "__none__",
            onSelect = onPick,
        )
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun ShiftLegend(data: StaccoData) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        data.work.shifts.take(5).forEach { shift ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(shiftColor(shift.color), CircleShape),
                )
                Spacer(Modifier.width(4.dp))
                Text(shift.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** How many of each shift in the month. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ShiftCounts(month: YearMonth, data: StaccoData, calendar: WorkCalendar) {
    val days = remember(data) { data.parsedDays() }
    val counts = data.work.shifts.map { shift ->
        shift to days.count { (date, record) -> YearMonth.from(date) == month && record.shiftId == shift.id && calendar.effectiveType(date) == DayType.OFFICE }
    }
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        counts.forEach { (shift, count) ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(shiftContainer(shift.color))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(shift.name, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                RollingText(count.toString(), style = MaterialTheme.typography.titleMedium, color = shiftColor(shift.color))
            }
        }
    }
}
