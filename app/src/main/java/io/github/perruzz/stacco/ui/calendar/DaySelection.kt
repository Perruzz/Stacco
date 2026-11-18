package io.github.perruzz.stacco.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.ui.oncall.OnCallIcon
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.ui.bank.vacationHours
import io.github.perruzz.stacco.ui.components.bouncyClickable
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import io.github.perruzz.stacco.ui.theme.container
import io.github.perruzz.stacco.ui.theme.tint
import io.github.perruzz.stacco.ui.today.AutoTypeIcon
import io.github.perruzz.stacco.ui.today.icon
import io.github.perruzz.stacco.ui.today.label
import io.github.perruzz.stacco.ui.util.Fmt
import java.time.LocalDate

/** Most used first: a week of vacation is the typical reason to pick many days. */
private val SelectionTypes = listOf(DayType.VACATION, DayType.SMART, DayType.TRAVEL, DayType.OFFICE, DayType.SICK, DayType.HOLIDAY)

/** All days from [a] to [b], whatever their order. */
fun datesBetween(a: LocalDate, b: LocalDate): Set<LocalDate> {
    val (from, to) = if (a <= b) a to b else b to a
    return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toSet()
}

/**
 * Floating bar shown while picking days: how many are picked, what marking them as vacation would
 * leave, and one chip per day type to apply it to all of them at once.
 */
@Composable
fun SelectionBar(
    data: StaccoData,
    picked: Set<LocalDate>,
    onApply: (DayType?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** On-call duty on: marks (true) or unmarks the picked days, weekends and holidays included. */
    onOnCall: ((Boolean) -> Unit)? = null,
) {
    val calendar = remember(data) { WorkCalendar.from(data) }
    val count = picked.size
    val marked = remember(calendar, picked) { calendar.daysToMark(picked, DayType.VACATION) }
    val skipsDaysOff = marked.size < count

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = count,
                        transitionSpec = {
                            val up = targetState > initialState
                            (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith (slideOutVertically { if (up) -it else it } + fadeOut())
                        },
                        label = "picked-count",
                    ) { n ->
                        Text(
                            if (n == 0) stringResource(R.string.select_none) else pluralStringResource(R.plurals.select_count, n, n),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    val sorted = picked.sorted()
                    Text(
                        when {
                            sorted.isEmpty() -> stringResource(R.string.select_hint)
                            skipsDaysOff -> stringResource(R.string.select_days_off)
                            sorted.size == 1 -> Fmt.longDate(sorted.first())
                            else -> "${Fmt.dayMonth(sorted.first())} – ${Fmt.dayMonth(sorted.last())}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel))
                }
            }

            AnimatedVisibility(
                visible = data.vacation.tracked && count > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                val before = remember(data) { VacationCalculator.balance(data, LocalDate.now()).remaining }
                val cost = remember(data, marked) { VacationCalculator.costAsVacation(data, marked) }
                Text(
                    stringResource(R.string.select_vacation_preview, vacationHours(before), vacationHours(before - cost)),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (before - cost < 0) StaccoTheme.colors.negative else StaccoTheme.colors.vacation,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onOnCall != null) {
                    val allOnCall = count > 0 && picked.all { data.days[it.toString()]?.onCall == true }
                    TypeAction(
                        label = stringResource(if (allOnCall) R.string.select_on_call_off else R.string.select_on_call),
                        icon = OnCallIcon,
                        tint = StaccoTheme.colors.onCall,
                        container = StaccoTheme.colors.onCallContainer,
                        enabled = count > 0,
                        onClick = { onOnCall(!allOnCall) },
                    )
                }
                SelectionTypes.forEach { type ->
                    TypeAction(
                        label = stringResource(type.label),
                        icon = type.icon,
                        tint = type.tint(),
                        container = type.container(),
                        enabled = count > 0,
                        onClick = { onApply(type) },
                    )
                }
                TypeAction(
                    label = stringResource(R.string.type_auto),
                    icon = AutoTypeIcon,
                    tint = MaterialTheme.colorScheme.primary,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    enabled = count > 0,
                    onClick = { onApply(null) },
                )
            }
        }
    }
}

@Composable
private fun TypeAction(label: String, icon: ImageVector, tint: Color, container: Color, enabled: Boolean, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(250), label = "type-action")
    Row(
        Modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .bouncyClickable(enabled = enabled, pressed = 0.9f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
