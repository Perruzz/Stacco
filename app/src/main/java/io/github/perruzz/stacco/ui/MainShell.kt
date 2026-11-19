package io.github.perruzz.stacco.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.ui.oncall.OnCallIcon
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.ui.bank.BankScreen
import io.github.perruzz.stacco.ui.calendar.CalendarScreen
import io.github.perruzz.stacco.ui.components.AuroraBackground
import io.github.perruzz.stacco.ui.components.ConfettiBurst
import io.github.perruzz.stacco.ui.components.bouncyClickable
import io.github.perruzz.stacco.ui.settings.SettingsScreen
import io.github.perruzz.stacco.ui.today.TodayScreen
import io.github.perruzz.stacco.ui.bank.vacationHours
import io.github.perruzz.stacco.ui.today.AutoTypeIcon
import io.github.perruzz.stacco.ui.today.label
import io.github.perruzz.stacco.ui.today.icon
import io.github.perruzz.stacco.ui.today.stampedMessage
import io.github.perruzz.stacco.ui.util.Fmt
import kotlinx.coroutines.delay

enum class MainTab(val icon: ImageVector, val label: Int) {
    TODAY(Icons.Rounded.WbTwilight, R.string.tab_today),
    CALENDAR(Icons.Rounded.CalendarMonth, R.string.tab_calendar),
    BANK(Icons.Rounded.Savings, R.string.tab_bank),
    SETTINGS(Icons.Rounded.Tune, R.string.tab_settings),
}

data class ToastData(
    val id: Long,
    val icon: ImageVector,
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

@Stable
class ToastState {
    var current by mutableStateOf<ToastData?>(null)
        internal set

    fun show(icon: ImageVector, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        current = ToastData(System.nanoTime(), icon, message, actionLabel, onAction)
    }

    fun dismiss() {
        current = null
    }
}

@Composable
fun MainShell(viewModel: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(MainTab.TODAY) }
    BackHandler(enabled = tab != MainTab.TODAY) { tab = MainTab.TODAY }
    val toast = remember { ToastState() }
    var confetti by remember { mutableIntStateOf(0) }
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Stamped -> {
                    toast.show(
                        icon = event.slot.icon,
                        message = resources.getString(event.slot.stampedMessage, Fmt.time(event.minutes)),
                        actionLabel = resources.getString(R.string.action_undo),
                        onAction = { viewModel.undoStamp(event.date, event.previous) },
                    )
                    if (event.slot == StampSlot.EXIT) confetti++
                }
                is UiEvent.DayCompleted -> toast.show(
                    icon = Icons.Rounded.CheckCircle,
                    message = resources.getString(R.string.toast_day_done, Fmt.duration(event.worked), Fmt.duration(event.delta, signed = true)),
                )
                is UiEvent.Info -> toast.show(Icons.Rounded.CheckCircle, resources.getString(event.message))
                is UiEvent.DaysMarked -> {
                    val type = event.type
                    val base = if (type == null) {
                        resources.getQuantityString(R.plurals.days_reset, event.count, event.count)
                    } else {
                        resources.getQuantityString(R.plurals.days_marked, event.count, resources.getString(type.label), event.count)
                    }
                    val touchedVacation = type == DayType.VACATION || event.previous.values.any { it?.type == DayType.VACATION }
                    val left = event.vacationLeft
                    toast.show(
                        icon = type?.icon ?: AutoTypeIcon,
                        message = if (touchedVacation && left != null) {
                            resources.getString(R.string.days_marked_vacation_left, base, vacationHours(left))
                        } else {
                            base
                        },
                        actionLabel = resources.getString(R.string.action_undo),
                        onAction = { viewModel.restoreDays(event.previous) },
                    )
                }
                is UiEvent.OnCallMarked -> toast.show(
                    icon = OnCallIcon,
                    message = resources.getQuantityString(if (event.on) R.plurals.on_call_marked else R.plurals.on_call_unmarked, event.count, event.count),
                    actionLabel = resources.getString(R.string.action_undo),
                    onAction = { viewModel.restoreDays(event.previous) },
                )
                is UiEvent.InterventionStopped -> {
                    val id = event.id
                    toast.show(
                        icon = OnCallIcon,
                        message = resources.getString(R.string.intervention_done_toast, Fmt.duration(event.real), Fmt.duration(event.credited)),
                        actionLabel = if (id != null) resources.getString(R.string.action_undo) else null,
                        onAction = if (id != null) ({ viewModel.resumeIntervention(event.date, id) }) else null,
                    )
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(320, delayMillis = 70)) + slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { dir * it / 7 } +
                    scaleIn(tween(380), initialScale = 0.97f)) togetherWith
                    (fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { -dir * it / 10 })
            },
            label = "tabs",
        ) { current ->
            when (current) {
                MainTab.TODAY -> TodayScreen(viewModel, onOpenTab = { tab = it })
                MainTab.CALENDAR -> CalendarScreen(viewModel)
                MainTab.BANK -> BankScreen(viewModel)
                MainTab.SETTINGS -> SettingsScreen(viewModel)
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
        ConfettiBurst(trigger = confetti)
        ToastHost(
            state = toast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 92.dp, start = 16.dp, end = 16.dp),
        )
        StaccoNavBar(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Content scrolls under a soft fade instead of colliding with the status bar icons. */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val color = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .height(top + 18.dp)
            .background(
                Brush.verticalGradient(
                    0f to color.copy(alpha = 0.94f),
                    0.6f to color.copy(alpha = 0.75f),
                    1f to color.copy(alpha = 0f),
                ),
            ),
    )
}

@Composable
private fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val current = state.current
    LaunchedEffect(current?.id) {
        if (current != null) {
            delay(4_200)
            if (state.current?.id == current.id) state.dismiss()
        }
    }
    AnimatedContent(
        targetState = current,
        transitionSpec = {
            (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 500f)) { it } + fadeIn()) togetherWith
                (slideOutVertically(tween(220)) { it / 2 } + fadeOut(tween(180)))
        },
        modifier = modifier,
        label = "toast",
    ) { data ->
        if (data != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 8.dp,
                modifier = Modifier.widthIn(max = 520.dp),
            ) {
                Row(
                    Modifier.padding(start = 18.dp, end = if (data.actionLabel != null) 6.dp else 20.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(data.icon, contentDescription = null, tint = MaterialTheme.colorScheme.inversePrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        data.message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(vertical = 8.dp),
                    )
                    if (data.actionLabel != null && data.onAction != null) {
                        TextButton(onClick = { data.onAction.invoke(); state.dismiss() }) {
                            Text(data.actionLabel, color = MaterialTheme.colorScheme.inversePrimary)
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.size(0.dp))
        }
    }
}

@Composable
private fun StaccoNavBar(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp, start = 12.dp, end = 12.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            MainTab.entries.forEach { tab ->
                NavItem(tab = tab, selected = tab == selected, onClick = { onSelect(tab) })
            }
        }
    }
}

@Composable
private fun NavItem(tab: MainTab, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        spring(stiffness = 500f),
        label = "nav-bg",
    )
    val foreground by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "nav-fg",
    )
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            bounce.animateTo(1.28f, spring(stiffness = 900f))
            bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 420f))
        }
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .bouncyClickable(pressed = 0.9f, onClick = onClick)
            .animateContentSize(spring(dampingRatio = 0.75f, stiffness = 500f))
            .padding(horizontal = if (selected) 18.dp else 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = stringResource(tab.label),
            tint = foreground,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value },
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(stringResource(tab.label), style = MaterialTheme.typography.labelLarge, color = foreground, maxLines = 1)
        }
    }
}
