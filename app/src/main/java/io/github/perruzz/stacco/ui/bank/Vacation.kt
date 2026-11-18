package io.github.perruzz.stacco.ui.bank

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.VacationBalance
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.VacationSettings
import io.github.perruzz.stacco.ui.components.DurationPickerDialog
import io.github.perruzz.stacco.ui.components.IconBubble
import io.github.perruzz.stacco.ui.components.RollingText
import io.github.perruzz.stacco.ui.components.SectionLabel
import io.github.perruzz.stacco.ui.components.SoftButton
import io.github.perruzz.stacco.ui.components.StaccoCard
import io.github.perruzz.stacco.ui.settings.SwitchRow
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import io.github.perruzz.stacco.ui.today.icon
import io.github.perruzz.stacco.ui.util.Fmt
import java.time.LocalDate

/** Vacation is counted in hours: an empty balance reads "0h", not "0m". */
fun vacationHours(minutes: Int, signed: Boolean = false): String = if (minutes == 0) "0h" else Fmt.duration(minutes, signed)

/** "12" or "12,5" days (rounded to half a day). */
@Composable
fun vacationDays(minutes: Int, dayMinutes: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val days = Math.round(minutes.toDouble() / dayMinutes.coerceAtLeast(1) * 2) / 2.0
    return String.format(locale, if (days % 1.0 == 0.0) "%.0f" else "%.1f", days)
}

/** Yearly vacation hours: what is left today, what was taken and what is already planned. */
@Composable
fun VacationCard(data: StaccoData, onSetAvailable: () -> Unit, onAnnual: () -> Unit, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val balance = remember(data, today) { VacationCalculator.balance(data, today) }
    val dayMinutes = VacationCalculator.typicalDay(data.work)
    val c = StaccoTheme.colors
    val tint = c.vacation

    StaccoCard(modifier, color = c.vacationContainer.copy(alpha = 0.32f)) {
        SectionLabel(stringResource(R.string.vacation_title, balance.year))
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = data.vacation.tracked,
            transitionSpec = { fadeIn(tween(300, delayMillis = 90)) togetherWith fadeOut(tween(150)) },
            label = "vacation-tracked",
        ) { tracked ->
            if (!tracked) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(DayType.VACATION.icon, tint = tint, container = tint.copy(alpha = 0.16f), size = 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(R.string.vacation_setup), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    FilledTonalButton(onClick = onAnnual) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.vacation_setup_action))
                    }
                }
            } else {
                VacationDetails(balance, dayMinutes, tint, onSetAvailable, onAnnual)
            }
        }
    }
}

@Composable
private fun VacationDetails(balance: VacationBalance, dayMinutes: Int, tint: Color, onSetAvailable: () -> Unit, onAnnual: () -> Unit) {
    val count = remember { Animatable(balance.available.toFloat()) }
    LaunchedEffect(balance.available) {
        count.animateTo(balance.available.toFloat(), tween(900, easing = FastOutSlowInEasing))
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(DayType.VACATION.icon, tint = tint, container = tint.copy(alpha = 0.16f), size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.vacation_available), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    vacationHours(count.value.toInt()),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (balance.available < 0) StaccoTheme.colors.negative else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.vacation_days_approx, vacationDays(balance.available, dayMinutes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        VacationBar(balance, tint)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VacationStat(stringResource(R.string.vacation_used), vacationHours(balance.used), tint, Modifier.weight(1f))
            VacationStat(stringResource(R.string.vacation_planned), vacationHours(balance.planned), tint.copy(alpha = 0.7f), Modifier.weight(1f))
            VacationStat(
                stringResource(R.string.vacation_after_planned),
                vacationHours(balance.remaining),
                if (balance.remaining < 0) StaccoTheme.colors.negative else MaterialTheme.colorScheme.primary,
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        val parts = buildList {
            add(stringResource(R.string.vacation_annual_short, vacationHours(balance.annual)))
            if (balance.carriedOver != 0) add(stringResource(R.string.vacation_carried_short, Fmt.duration(balance.carriedOver, signed = true)))
            if (balance.adjustment != 0) add(stringResource(R.string.vacation_adjust_short, Fmt.duration(balance.adjustment, signed = true)))
            if (balance.leave > 0) add(stringResource(R.string.vacation_leave_note, Fmt.duration(balance.leave)))
        }
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton(stringResource(R.string.vacation_set_available), Icons.Rounded.EditCalendar, onSetAvailable, Modifier.weight(1f))
            SoftButton(stringResource(R.string.vacation_annual), Icons.Rounded.Tune, onAnnual, Modifier.weight(1f))
        }
    }
}

/** Taken (solid) and planned (lighter) hours over the hours of the year. */
@Composable
private fun VacationBar(balance: VacationBalance, tint: Color) {
    val total = maxOf(balance.granted, balance.used + balance.planned, 1).toFloat()
    val used by animateFloatAsState((balance.used / total).coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = 120f), label = "vacation-used")
    val planned by animateFloatAsState((balance.planned / total).coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = 120f), label = "vacation-planned")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = r)
        if (planned > 0f) {
            drawRoundRect(tint.copy(alpha = 0.45f), size = Size(size.width * (used + planned), size.height), cornerRadius = r)
        }
        if (used > 0f) {
            drawRoundRect(tint, topLeft = Offset.Zero, size = Size(size.width * used, size.height), cornerRadius = r)
        }
    }
}

@Composable
private fun VacationStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RollingText(value, style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** Yearly vacation hours and whether leftovers move to the next year. */
@Composable
fun VacationAnnualDialog(settings: VacationSettings, dayMinutes: Int, onDismiss: () -> Unit, onConfirm: (minutes: Int, carryOver: Boolean) -> Unit) {
    var carry by remember { mutableStateOf(settings.carryOver) }
    DurationPickerDialog(
        title = stringResource(R.string.vacation_annual_title),
        subtitle = stringResource(R.string.vacation_annual_sub),
        initialMinutes = settings.annualMinutes.takeIf { it > 0 } ?: (20 * dayMinutes),
        icon = DayType.VACATION.icon,
        tint = StaccoTheme.colors.vacation,
        maxHours = 400,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(it, carry) },
        extraContent = { value ->
            Text(
                stringResource(R.string.vacation_days_approx, vacationDays(value, dayMinutes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                icon = Icons.Rounded.EventRepeat,
                title = stringResource(R.string.vacation_carry),
                subtitle = stringResource(R.string.vacation_carry_sub),
                checked = carry,
                onCheckedChange = { carry = it },
            )
        },
    )
}

/** Aligns today's available vacation hours with the payslip. */
@Composable
fun VacationAvailableDialog(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    DurationPickerDialog(
        title = stringResource(R.string.vacation_set_title),
        subtitle = stringResource(R.string.vacation_set_sub),
        initialMinutes = current,
        icon = Icons.Rounded.EditCalendar,
        tint = StaccoTheme.colors.vacation,
        allowNegative = true,
        maxHours = 400,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
