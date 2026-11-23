package io.github.perruzz.stacco.ui.bank

import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import io.github.perruzz.stacco.ui.components.MaxContentWidth
import io.github.perruzz.stacco.ui.components.AdaptiveRow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.HourglassDisabled
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.ui.calendar.DaySheet
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.ui.oncall.RecoveryCard
import io.github.perruzz.stacco.domain.AlertPlanner
import io.github.perruzz.stacco.domain.BankLedger
import io.github.perruzz.stacco.domain.Movement
import io.github.perruzz.stacco.domain.MovementKind
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.ui.MainViewModel
import io.github.perruzz.stacco.ui.components.ConfirmDialog
import io.github.perruzz.stacco.ui.components.DurationPickerDialog
import io.github.perruzz.stacco.ui.components.IconBubble
import io.github.perruzz.stacco.ui.components.RollingText
import io.github.perruzz.stacco.ui.components.SectionLabel
import io.github.perruzz.stacco.ui.components.SoftButton
import io.github.perruzz.stacco.ui.components.StaccoCard
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import io.github.perruzz.stacco.ui.theme.deltaColor
import io.github.perruzz.stacco.ui.today.AlertBanner
import io.github.perruzz.stacco.ui.today.Appear
import io.github.perruzz.stacco.ui.util.Fmt
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.min

@Composable
fun BankScreen(viewModel: MainViewModel) {
    val dataState by viewModel.data.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val todayDone by viewModel.todayDone.collectAsStateWithLifecycle()
    val recovery by viewModel.recovery.collectAsStateWithLifecycle()
    val data = dataState ?: return
    val today = LocalDate.now()

    var setting by remember { mutableStateOf(false) }
    var editingVacation by remember { mutableStateOf(false) }
    var settingVacation by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Movement?>(null) }
    var range by remember { mutableIntStateOf(30) }
    var fixing by remember { mutableStateOf<LocalDate?>(null) }
    // Before the working day under way (yesterday's during a night shift): those days are over.
    val toFix = remember(data) { Engine.daysToFix(data, Engine.activeDay(data, LocalDateTime.now()).date) }

    val alerts = remember(ledger, data, todayDone) {
        AlertPlanner.plan(
            balance = ledger.balance,
            today = today,
            todayDone = todayDone,
            calendar = WorkCalendar.from(data),
            work = data.work,
            bank = data.bank,
        )
    }
    val recoveryAlert = remember(recovery, data, todayDone) {
        AlertPlanner.planRecovery(recovery, today, todayDone, WorkCalendar.from(data), data.work, data.bank)
    }
    val movements = remember(ledger) { ledger.movements.asReversed() }
    val grouped = remember(movements) { movements.groupBy { YearMonth.from(it.date) } }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // On a tablet or in landscape the cards stay within the readable width, centred.
    var listWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val side = with(density) { maxOf(20.dp, (listWidth.toDp() - MaxContentWidth) / 2 + 20.dp) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { listWidth = it.width },
        contentPadding = PaddingValues(start = side, end = side, top = top + 12.dp, bottom = bottom + 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "title") {
            Appear(0) { Text(stringResource(R.string.bank_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() }) }
        }
        item(key = "balance") {
            Appear(1) {
                BalanceHero(ledger, onSet = { setting = true }, onAdd = { adding = true })
            }
        }
        if (toFix.isNotEmpty()) {
            item(key = "to-fix") {
                Appear(2) { DaysToFixCard(toFix, today, onOpen = { fixing = it }, modifier = Modifier.animateItem()) }
            }
        }
        item(key = "chart") {
            Appear(2) {
                StaccoCard {
                    SectionLabel(stringResource(R.string.bank_trend)) {
                        SingleChoiceSegmentedButtonRow {
                            listOf(30, 90).forEachIndexed { i, days ->
                                SegmentedButton(
                                    selected = range == days,
                                    onClick = { range = days },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                    icon = {},
                                ) { Text(stringResource(R.string.bank_days, days), style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    BalanceChart(ledger = ledger, days = range, today = today)
                }
            }
        }
        item(key = "deadlines") {
            Appear(3) {
                StaccoCard {
                    SectionLabel(stringResource(R.string.bank_deadlines))
                    if (alerts.isEmpty()) {
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = StaccoTheme.colors.gold)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(if (ledger.balance == 0) R.string.bank_all_good_zero else R.string.bank_all_good),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    alerts.forEach { AlertBanner(it) }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        rulesSummary(data.bank.positiveExpiry.name, data.bank.negativeMustBeRecovered),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (data.onCall.enabled) {
            item(key = "recovery") {
                Appear(4) { RecoveryCard(data, recovery, recoveryAlert) }
            }
        }
        item(key = "vacation") {
            Appear(4) {
                VacationCard(data, onSetAvailable = { settingVacation = true }, onAnnual = { editingVacation = true })
            }
        }
        item(key = "movements-title") {
            Appear(5) { SectionLabel(stringResource(R.string.bank_movements), Modifier.padding(top = 8.dp)) }
        }
        if (movements.isEmpty()) {
            item(key = "empty") {
                Appear(6) {
                    Text(
                        stringResource(R.string.bank_no_movements),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    )
                }
            }
        }
        grouped.forEach { (month, list) ->
            item(key = "m-$month") {
                Text(
                    Fmt.monthYear(month),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .animateItem()
                        .padding(top = 4.dp),
                )
            }
            items(list, key = { "${it.date}-${it.kind}-${it.adjustmentId}-${it.balanceAfter}-${it.amount}" }) { movement ->
                MovementRow(
                    movement = movement,
                    onDelete = if (movement.adjustmentId != null) ({ deleting = movement }) else null,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    fixing?.let { date ->
        DaySheet(date = date, data = data, viewModel = viewModel, onDismiss = { fixing = null })
    }
    if (setting) {
        DurationPickerDialog(
            title = stringResource(R.string.bank_set_title),
            subtitle = stringResource(R.string.bank_set_sub),
            initialMinutes = ledger.balance,
            icon = Icons.Rounded.Tune,
            allowNegative = true,
            maxHours = 300,
            onDismiss = { setting = false },
            onConfirm = {
                viewModel.setBalance(it)
                setting = false
            },
        )
    }
    if (editingVacation) {
        VacationAnnualDialog(
            settings = data.vacation,
            dayMinutes = VacationCalculator.typicalDay(data.work),
            onDismiss = { editingVacation = false },
            onConfirm = { minutes, carry ->
                viewModel.updateData { it.copy(vacation = it.vacation.copy(annualMinutes = minutes, carryOver = carry)) }
                editingVacation = false
            },
        )
    }
    if (settingVacation) {
        VacationAvailableDialog(
            current = VacationCalculator.balance(data, today).available,
            onDismiss = { settingVacation = false },
            onConfirm = {
                viewModel.setVacationAvailable(it)
                settingVacation = false
            },
        )
    }
    if (adding) {
        AdjustmentDialog(
            onDismiss = { adding = false },
            onConfirm = { minutes, note ->
                viewModel.addAdjustment(LocalDate.now(), minutes, note)
                adding = false
            },
        )
    }
    deleting?.let { m ->
        ConfirmDialog(
            title = stringResource(R.string.bank_delete_title),
            text = stringResource(R.string.bank_delete_text, Fmt.duration(m.amount, signed = true)),
            confirmLabel = stringResource(R.string.action_delete),
            icon = Icons.Rounded.DeleteOutline,
            destructive = true,
            onDismiss = { deleting = null },
            onConfirm = {
                m.adjustmentId?.let(viewModel::deleteAdjustment)
                deleting = null
            },
        )
    }
}

@Composable
private fun rulesSummary(expiry: String, negative: Boolean): String {
    val expiryText = when (expiry) {
        "NEVER" -> stringResource(R.string.rules_expiry_never)
        "MONTHLY" -> stringResource(R.string.expiry_monthly)
        "BIMONTHLY" -> stringResource(R.string.expiry_bimonthly)
        "QUARTERLY" -> stringResource(R.string.expiry_quarterly)
        "FOUR_MONTHLY" -> stringResource(R.string.expiry_four_monthly)
        "SEMIANNUAL" -> stringResource(R.string.expiry_semiannual)
        else -> stringResource(R.string.expiry_annual)
    }
    val negativeText = stringResource(if (negative) R.string.rules_negative_monthly else R.string.rules_negative_free)
    return if (expiry == "NEVER") "$expiryText · $negativeText" else stringResource(R.string.rules_expiry, expiryText.lowercase()) + " · " + negativeText
}

@Composable
private fun BalanceHero(ledger: BankLedger, onSet: () -> Unit, onAdd: () -> Unit) {
    val color = deltaColor(ledger.balance)
    val count = remember { Animatable(0f) }
    LaunchedEffect(ledger.balance) {
        count.animateTo(ledger.balance.toFloat(), tween(900, easing = FastOutSlowInEasing))
    }
    StaccoCard(color = color.copy(alpha = 0.10f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Savings, tint = color, container = color.copy(alpha = 0.16f), size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.bank_balance), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    Fmt.duration(count.value.toInt(), signed = true),
                    style = MaterialTheme.typography.displayMedium,
                    color = color,
                )
            }
        }
        ledger.todayDelta?.let {
            Text(
                stringResource(R.string.bank_today_delta, Fmt.duration(it, signed = true)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        // Side by side when both fit whole, one under the other with large fonts on a narrow phone.
        AdaptiveRow {
            SoftButton(stringResource(R.string.bank_set), Icons.Rounded.EditCalendar, onSet)
            SoftButton(stringResource(R.string.bank_add), Icons.Rounded.Add, onAdd)
        }
    }
}

/** Area chart of the end-of-day balance, drawn with a sweeping reveal. */
@Composable
private fun BalanceChart(ledger: BankLedger, days: Int, today: LocalDate) {
    val firstKnown = ledger.daily.firstOrNull()?.first
    val rangeStart = today.minusDays(days.toLong() - 1)
    val start = if (firstKnown != null && firstKnown.isAfter(rangeStart)) firstKnown else rangeStart
    val span = java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt() + 1
    val byDate = remember(ledger) { ledger.daily.toMap() }
    val points = remember(ledger, days) {
        var last = ledger.daily.lastOrNull { it.first.isBefore(start) }?.second ?: 0
        (0 until span).map { i ->
            val d = start.plusDays(i.toLong())
            byDate[d]?.let { last = it }
            last
        }
    }
    if (firstKnown == null || points.size < 2) {
        Text(
            stringResource(R.string.bank_chart_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
        )
        return
    }
    val positive = StaccoTheme.colors.positive
    val negative = StaccoTheme.colors.negative
    val axis = MaterialTheme.colorScheme.outlineVariant
    val reveal = remember(days) { Animatable(0f) }
    LaunchedEffect(days, ledger) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
    }
    val maxV = max(60, points.maxOrNull() ?: 0)
    val minV = min(-60, points.minOrNull() ?: 0).let { if (points.all { p -> p >= 0 }) min(0, it / 4) else it }

    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp),
        ) {
            val w = size.width
            val h = size.height
            val span = (maxV - minV).toFloat().coerceAtLeast(1f)
            fun y(v: Int) = h - (v - minV) / span * h
            val step = if (points.size > 1) w / (points.size - 1) else w
            val zeroY = y(0)

            drawLine(axis, Offset(0f, zeroY), Offset(w, zeroY), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))

            val line = Path()
            points.forEachIndexed { i, v ->
                val x = i * step
                if (i == 0) line.moveTo(x, y(v)) else {
                    val px = (i - 1) * step
                    val midX = (px + x) / 2f
                    line.cubicTo(midX, y(points[i - 1]), midX, y(v), x, y(v))
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(w, zeroY)
                lineTo(0f, zeroY)
                close()
            }
            val lastValue = points.lastOrNull() ?: 0
            val mainColor = if (lastValue < 0) negative else positive
            clipRect(right = w * reveal.value) {
                drawPath(
                    area,
                    Brush.verticalGradient(
                        listOf(positive.copy(alpha = 0.28f), positive.copy(alpha = 0.04f), negative.copy(alpha = 0.04f), negative.copy(alpha = 0.28f)),
                        startY = 0f,
                        endY = h,
                    ),
                )
                drawPath(line, mainColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (reveal.value > 0.98f && points.isNotEmpty()) {
                val end = Offset(w, y(lastValue))
                drawCircle(mainColor.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = end)
                drawCircle(mainColor, radius = 5.dp.toPx(), center = end)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Text(Fmt.dayMonth(start), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.relative_today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MovementRow(movement: Movement, onDelete: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = StaccoTheme.colors
    val (icon, tint, title) = when (movement.kind) {
        MovementKind.DAY -> Triple(
            if (movement.amount >= 0) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
            deltaColor(movement.amount),
            stringResource(R.string.movement_day),
        )
        MovementKind.MANUAL -> Triple(Icons.Rounded.Tune, MaterialTheme.colorScheme.tertiary, movement.note.ifBlank { stringResource(R.string.movement_manual) })
        MovementKind.SET_BALANCE -> Triple(Icons.Rounded.EditCalendar, MaterialTheme.colorScheme.primary, stringResource(R.string.movement_set, Fmt.duration(movement.balanceAfter, signed = true)))
        MovementKind.EXPIRY -> Triple(Icons.Rounded.HourglassDisabled, c.vacation, stringResource(R.string.movement_expiry))
        MovementKind.NEGATIVE_RESET -> Triple(Icons.AutoMirrored.Rounded.Undo, c.negative, stringResource(R.string.movement_negative_reset))
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, tint = tint, container = tint.copy(alpha = 0.14f), size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.movement_meta, Fmt.shortDate(movement.date), Fmt.duration(movement.balanceAfter, signed = true)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (movement.kind != MovementKind.SET_BALANCE || movement.amount != 0) {
            Text(
                Fmt.duration(movement.amount, signed = true),
                style = MaterialTheme.typography.titleMedium,
                color = deltaColor(movement.amount),
                modifier = Modifier
                    .background(deltaColor(movement.amount).copy(alpha = 0.10f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AdjustmentDialog(onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit) {
    var note by remember { mutableStateOf("") }
    DurationPickerDialog(
        title = stringResource(R.string.bank_add_title),
        subtitle = stringResource(R.string.bank_add_sub),
        initialMinutes = 30,
        icon = Icons.Rounded.Add,
        allowNegative = true,
        maxHours = 48,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(it, note) },
        extraContent = {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(60) },
                label = { Text(stringResource(R.string.bank_add_note)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
