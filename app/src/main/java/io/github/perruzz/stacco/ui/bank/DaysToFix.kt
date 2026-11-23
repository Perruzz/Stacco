package io.github.perruzz.stacco.ui.bank

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.DayFix
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.ui.components.IconBubble
import io.github.perruzz.stacco.ui.components.StaccoCard
import io.github.perruzz.stacco.ui.components.bouncyClickable
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import io.github.perruzz.stacco.ui.today.icon
import io.github.perruzz.stacco.ui.util.Fmt
import java.time.LocalDate

/** Days shown before "show all": the most recent ones. */
private const val COLLAPSED = 3

/**
 * The days the balance leaves out because their stamps are incomplete or do not add up, however
 * old (Today only points out the last two weeks): each one opens its day to be fixed.
 */
@Composable
fun DaysToFixCard(
    days: List<Pair<LocalDate, DayFix>>,
    today: LocalDate,
    onOpen: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var all by rememberSaveable { mutableStateOf(false) }
    val c = StaccoTheme.colors
    StaccoCard(modifier = modifier, color = c.vacationContainer.copy(alpha = 0.55f)) {
        Row(verticalAlignment = Alignment.Top) {
            IconBubble(Icons.Rounded.EventBusy, tint = c.vacation, container = c.vacation.copy(alpha = 0.16f), size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.days_to_fix_title, days.size, days.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.days_to_fix_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.animateContentSize(spring(dampingRatio = 0.85f, stiffness = 400f))) {
            (if (all) days else days.take(COLLAPSED)).forEach { (date, kind) ->
                DayToFixRow(date, kind, today, onClick = { onOpen(date) })
            }
        }
        if (days.size > COLLAPSED) {
            TextButton(onClick = { all = !all }, modifier = Modifier.align(Alignment.End)) {
                Text(if (all) stringResource(R.string.days_to_fix_less) else stringResource(R.string.days_to_fix_more, days.size))
            }
        }
    }
}

@Composable
private fun DayToFixRow(date: LocalDate, kind: DayFix, today: LocalDate, onClick: () -> Unit) {
    val missingExit = kind == DayFix.MISSING_EXIT
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .bouncyClickable(pressed = 0.98f, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (missingExit) StampSlot.EXIT.icon else Icons.Rounded.Warning,
            contentDescription = null,
            tint = StaccoTheme.colors.vacation,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // A day of another year says so.
            Text(
                if (date.year == today.year) Fmt.longDate(date) else Fmt.dayMonthYear(date),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(if (missingExit) R.string.calendar_missing_exit else R.string.days_to_fix_inconsistent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp),
        )
    }
}
