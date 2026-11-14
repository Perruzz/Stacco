package io.github.perruzz.stacco.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import io.github.perruzz.stacco.ui.settings.defaultShifts
import io.github.perruzz.stacco.ui.settings.ShiftList
import io.github.perruzz.stacco.ui.settings.ShiftEditorDialog
import io.github.perruzz.stacco.ui.components.bouncyClickable
import io.github.perruzz.stacco.ui.components.IconBubble
import io.github.perruzz.stacco.domain.WorkMode
import io.github.perruzz.stacco.domain.ShiftTemplate
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.foundation.border
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.ExpiryPeriod
import io.github.perruzz.stacco.domain.SmartPeriod
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.ui.MainViewModel
import io.github.perruzz.stacco.ui.components.AuroraBackground
import io.github.perruzz.stacco.ui.components.ConfettiBurst
import io.github.perruzz.stacco.ui.components.DurationPickerDialog
import io.github.perruzz.stacco.ui.components.MorphBadge
import io.github.perruzz.stacco.ui.components.StaccoCard
import io.github.perruzz.stacco.ui.settings.SettingRow
import io.github.perruzz.stacco.ui.settings.StepperRow
import io.github.perruzz.stacco.ui.settings.SwitchRow
import io.github.perruzz.stacco.ui.settings.label
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import io.github.perruzz.stacco.ui.theme.deltaColor
import io.github.perruzz.stacco.ui.util.Fmt
import kotlinx.coroutines.delay

private enum class Step { WELCOME, NAME, MODE, HOURS, BREAK, SHIFTS, BANK, SMART, NOTIFY, DONE }

private fun stepsFor(mode: WorkMode): List<Step> =
    if (mode == WorkMode.SHIFTS) {
        listOf(Step.WELCOME, Step.NAME, Step.MODE, Step.SHIFTS, Step.BANK, Step.SMART, Step.NOTIFY, Step.DONE)
    } else {
        listOf(Step.WELCOME, Step.NAME, Step.MODE, Step.HOURS, Step.BREAK, Step.BANK, Step.SMART, Step.NOTIFY, Step.DONE)
    }

private enum class Field { DAILY, MINIMUM, MIN_BREAK, BALANCE }

@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(StaccoData()) }
    var name by rememberSaveable { mutableStateOf("") }
    var balance by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Field?>(null) }
    var confetti by remember { mutableIntStateOf(0) }
    var editingShift by remember { mutableStateOf<ShiftTemplate?>(null) }
    var addingShift by remember { mutableStateOf(false) }
    val defaults = defaultShifts()
    val steps = stepsFor(draft.work.mode)
    val step = steps[page.coerceIn(0, steps.lastIndex)]
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { page++ }

    BackHandler(enabled = page > 0) { page-- }
    LaunchedEffect(step) { if (step == Step.DONE) confetti++ }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(intensity = 1.3f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Top bar: back + progress dots
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp)) {
                    if (page > 0) {
                        IconButton(onClick = { page-- }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                ProgressDots(count = steps.size, current = page)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 260f)) { dir * it / 3 } + fadeIn(tween(350, delayMillis = 80))) togetherWith
                        (slideOutHorizontally(tween(260)) { -dir * it / 4 } + fadeOut(tween(200)))
                },
                modifier = Modifier.weight(1f),
                label = "onboarding",
            ) { current ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (steps[current.coerceIn(0, steps.lastIndex)]) {
                        Step.WELCOME -> WelcomePage()
                        Step.NAME -> Page(Icons.Rounded.WavingHand, R.string.ob_name_title, R.string.ob_name_body) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it.take(40) },
                                label = { Text(stringResource(R.string.settings_name_label)) },
                                singleLine = true,
                                shape = RoundedCornerShape(20.dp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Step.MODE -> Page(Icons.Rounded.Diversity3, R.string.ob_mode_title, R.string.ob_mode_body) {
                            ModeChoice(
                                mode = draft.work.mode,
                                onSelect = { mode ->
                                    draft = draft.copy(
                                        work = draft.work.copy(
                                            mode = mode,
                                            shifts = if (mode == WorkMode.SHIFTS && draft.work.shifts.isEmpty()) defaults else draft.work.shifts,
                                        ),
                                        // Shift workers rarely work remotely: start from zero.
                                        smart = draft.smart.copy(allowance = if (mode == WorkMode.SHIFTS) 0 else 10),
                                    )
                                },
                            )
                        }
                        Step.SHIFTS -> Page(Icons.Rounded.Bedtime, R.string.ob_shifts_title, R.string.ob_shifts_body) {
                            StaccoCard {
                                ShiftList(
                                    shifts = draft.work.shifts,
                                    onEdit = { editingShift = it },
                                    onAdd = { addingShift = true },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Explainer(stringResource(R.string.ob_shifts_explainer))
                        }
                        Step.HOURS -> Page(Icons.Rounded.Schedule, R.string.ob_work_title, R.string.ob_work_body) {
                            StaccoCard {
                                SettingRow(
                                    icon = Icons.Rounded.Schedule,
                                    title = stringResource(R.string.settings_daily),
                                    value = Fmt.duration(draft.work.dailyMinutes),
                                    onClick = { editing = Field.DAILY },
                                )
                                SettingRow(
                                    icon = Icons.Rounded.HourglassBottom,
                                    title = stringResource(R.string.settings_minimum),
                                    value = Fmt.duration(draft.work.minDailyMinutes),
                                    onClick = { editing = Field.MINIMUM },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Explainer(
                                stringResource(
                                    R.string.ob_work_explainer,
                                    Fmt.duration(draft.work.dailyMinutes),
                                    Fmt.duration(draft.work.minDailyMinutes),
                                    Fmt.duration((draft.work.dailyMinutes - draft.work.minDailyMinutes).coerceAtLeast(0)),
                                ),
                            )
                        }
                        Step.BREAK -> Page(Icons.Rounded.Restaurant, R.string.ob_break_title, R.string.ob_break_body) {
                            StaccoCard {
                                SettingRow(
                                    icon = Icons.Rounded.Coffee,
                                    title = stringResource(R.string.settings_min_break),
                                    value = Fmt.duration(draft.work.minBreakMinutes),
                                    tint = StaccoTheme.colors.lunch,
                                    onClick = { editing = Field.MIN_BREAK },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Explainer(stringResource(R.string.ob_break_explainer, Fmt.duration(draft.work.minBreakMinutes)))
                        }
                        Step.BANK -> Page(Icons.Rounded.Savings, R.string.ob_bank_title, R.string.ob_bank_body) {
                            StaccoCard {
                                SettingRow(
                                    icon = Icons.Rounded.Savings,
                                    title = stringResource(R.string.settings_balance),
                                    value = Fmt.duration(balance, signed = true),
                                    tint = if (balance == 0) MaterialTheme.colorScheme.primary else deltaColor(balance),
                                    onClick = { editing = Field.BALANCE },
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.settings_expiry),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                                )
                                ExpiryChips(draft.bank.positiveExpiry) { p -> draft = draft.copy(bank = draft.bank.copy(positiveExpiry = p)) }
                                Spacer(Modifier.height(8.dp))
                                SwitchRow(
                                    icon = Icons.AutoMirrored.Rounded.TrendingDown,
                                    title = stringResource(R.string.settings_negative),
                                    subtitle = stringResource(R.string.settings_negative_sub),
                                    checked = draft.bank.negativeMustBeRecovered,
                                    tint = StaccoTheme.colors.negative,
                                    onCheckedChange = { draft = draft.copy(bank = draft.bank.copy(negativeMustBeRecovered = it)) },
                                )
                            }
                        }
                        Step.SMART -> Page(Icons.Rounded.Home, R.string.ob_smart_title, R.string.ob_smart_body) {
                            StaccoCard {
                                StepperRow(
                                    icon = Icons.Rounded.Home,
                                    title = stringResource(R.string.settings_smart_days),
                                    value = draft.smart.allowance,
                                    range = 0..31,
                                    tint = StaccoTheme.colors.smart,
                                    onValueChange = { draft = draft.copy(smart = draft.smart.copy(allowance = it)) },
                                )
                                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = draft.smart.period == SmartPeriod.MONTH,
                                        onClick = { draft = draft.copy(smart = draft.smart.copy(period = SmartPeriod.MONTH)) },
                                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                                    ) { Text(stringResource(R.string.settings_smart_month)) }
                                    SegmentedButton(
                                        selected = draft.smart.period == SmartPeriod.WEEK,
                                        onClick = { draft = draft.copy(smart = draft.smart.copy(period = SmartPeriod.WEEK)) },
                                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                                    ) { Text(stringResource(R.string.settings_smart_week)) }
                                }
                            }
                        }
                        Step.NOTIFY -> Page(Icons.Rounded.NotificationsActive, R.string.ob_notif_title, R.string.ob_notif_body) {
                            Explainer(stringResource(R.string.ob_notif_explainer))
                        }
                        Step.DONE -> DonePage(name)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            // Bottom action
            val isLast = step == Step.DONE
            val isNotifications = step == Step.NOTIFY
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = {
                        when {
                            isLast -> viewModel.completeOnboarding(
                                draft.copy(profile = draft.profile.copy(name = name.trim())),
                                balance,
                            )
                            isNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> page++
                        }
                    },
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .animateContentSize(),
                ) {
                    Text(
                        stringResource(
                            when {
                                page == 0 -> R.string.ob_start
                                isNotifications -> R.string.ob_enable_notifications
                                isLast -> R.string.ob_enter
                                else -> R.string.ob_next
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        if (isNotifications) Icons.Rounded.NotificationsActive else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                    )
                }
                if (isNotifications) {
                    TextButton(onClick = { page++ }) { Text(stringResource(R.string.ob_skip)) }
                } else if (page in 1 until steps.size - 1) {
                    Text(
                        stringResource(R.string.ob_changeable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
        ConfettiBurst(trigger = confetti)
    }

    if (addingShift || editingShift != null) {
        OnboardingShiftEditor(
            initial = editingShift,
            shifts = draft.work.shifts,
            onDismiss = {
                addingShift = false
                editingShift = null
            },
            onChange = { list ->
                draft = draft.copy(work = draft.work.copy(shifts = list))
                addingShift = false
                editingShift = null
            },
        )
    }

    editing?.let { field ->
        val work = draft.work
        val (title, initial, max) = when (field) {
            Field.DAILY -> Triple(R.string.settings_daily, work.dailyMinutes, 16)
            Field.MINIMUM -> Triple(R.string.settings_minimum, work.minDailyMinutes, 16)
            Field.MIN_BREAK -> Triple(R.string.settings_min_break, work.minBreakMinutes, 4)
            Field.BALANCE -> Triple(R.string.settings_balance, balance, 300)
        }
        DurationPickerDialog(
            title = stringResource(title),
            initialMinutes = initial,
            maxHours = max,
            allowNegative = field == Field.BALANCE,
            onDismiss = { editing = null },
            onConfirm = { v ->
                draft = when (field) {
                    Field.DAILY -> draft.copy(work = work.copy(dailyMinutes = v, minDailyMinutes = work.minDailyMinutes.coerceAtMost(v)))
                    Field.MINIMUM -> draft.copy(work = work.copy(minDailyMinutes = v.coerceAtMost(work.dailyMinutes)))
                    Field.MIN_BREAK -> draft.copy(work = work.copy(minBreakMinutes = v))
                    Field.BALANCE -> draft.also { balance = v }
                }
                editing = null
            },
        )
    }
}

@Composable
private fun OnboardingShiftEditor(
    initial: ShiftTemplate?,
    shifts: List<ShiftTemplate>,
    onDismiss: () -> Unit,
    onChange: (List<ShiftTemplate>) -> Unit,
) {
    ShiftEditorDialog(
        initial = initial,
        onDismiss = onDismiss,
        onSave = { shift ->
            onChange(if (shifts.any { it.id == shift.id }) shifts.map { if (it.id == shift.id) shift else it } else shifts + shift)
        },
        onDelete = if (initial != null && shifts.size > 1) {
            { onChange(shifts.filterNot { it.id == initial.id }) }
        } else {
            null
        },
    )
}

/** Fixed hours or shifts: two big cards. */
@Composable
private fun ModeChoice(mode: WorkMode, onSelect: (WorkMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ModeCard(
            icon = Icons.Rounded.Schedule,
            title = stringResource(R.string.mode_fixed),
            body = stringResource(R.string.mode_fixed_desc),
            selected = mode == WorkMode.FIXED,
            onClick = { onSelect(WorkMode.FIXED) },
        )
        ModeCard(
            icon = Icons.Rounded.Bedtime,
            title = stringResource(R.string.mode_shifts),
            body = stringResource(R.string.mode_shifts_desc),
            selected = mode == WorkMode.SHIFTS,
            onClick = { onSelect(WorkMode.SHIFTS) },
        )
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), label = "mode-border")
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f),
        label = "mode-bg",
    )
    val scale by animateFloatAsState(if (selected) 1.02f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "mode-scale")
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(24.dp))
            .bouncyClickable(pressed = 0.97f, onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, tint = MaterialTheme.colorScheme.primary, container = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), size = 48.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.animation.AnimatedVisibility(selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ProgressDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 26.dp else 8.dp, spring(dampingRatio = 0.6f, stiffness = 400f), label = "dot-w")
            val color by animateColorAsState(
                if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dot-c",
            )
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun Page(icon: ImageVector, title: Int, body: Int, content: @Composable () -> Unit) {
    Spacer(Modifier.height(24.dp))
    MorphBadge(
        icon = icon,
        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
        size = 128.dp,
    )
    Spacer(Modifier.height(28.dp))
    Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text(
        stringResource(body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    content()
}

@Composable
private fun Explainer(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun ExpiryChips(selected: ExpiryPeriod, onSelect: (ExpiryPeriod) -> Unit) {
    val options = listOf(ExpiryPeriod.NEVER, ExpiryPeriod.MONTHLY, ExpiryPeriod.QUARTERLY, ExpiryPeriod.SEMIANNUAL, ExpiryPeriod.ANNUAL)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, period ->
            SegmentedButton(
                selected = selected == period,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) {
                Text(stringResource(period.shortLabel), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

private val ExpiryPeriod.shortLabel: Int
    get() = when (this) {
        ExpiryPeriod.NEVER -> R.string.expiry_never_short
        ExpiryPeriod.MONTHLY -> R.string.expiry_monthly_short
        ExpiryPeriod.QUARTERLY -> R.string.expiry_quarterly_short
        ExpiryPeriod.SEMIANNUAL -> R.string.expiry_semiannual_short
        ExpiryPeriod.ANNUAL -> R.string.expiry_annual_short
        else -> label
    }

/** The welcome hero: the Stacco ring drawing itself, with a leaf sprouting at the end. */
@Composable
private fun WelcomePage() {
    val draw = remember { Animatable(0f) }
    val leaf = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        draw.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
        leaf.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
    }
    LaunchedEffect(Unit) {
        delay(500)
        text.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val float = rememberInfiniteTransition(label = "float")
    val bob by float.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "bob")
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val leafColor = StaccoTheme.colors.positive

    Spacer(Modifier.height(40.dp))
    Box(
        Modifier
            .size(220.dp)
            .graphicsLayer { translationY = kotlin.math.sin(bob * 2 * Math.PI).toFloat() * 8.dp.toPx() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(200.dp)) {
            val stroke = size.minDimension * 0.09f
            val r = size.minDimension / 2f - stroke
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(primary.copy(alpha = 0.10f), radius = r + stroke * 1.4f, center = c)
            rotate(-90f, c) {
                drawArc(
                    brush = androidx.compose.ui.graphics.Brush.sweepGradient(listOf(primary, tertiary, primary), c),
                    startAngle = 0f,
                    sweepAngle = 300f * draw.value,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            // Leaf in the gap, sprouting outwards at ~10 o'clock.
            val angle = Math.toRadians(-120.0)
            val base = Offset(c.x + (r * kotlin.math.cos(angle)).toFloat(), c.y + (r * kotlin.math.sin(angle)).toFloat())
            val scale = leaf.value
            if (scale > 0f) {
                val dir = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
                val len = r * 0.55f * scale
                val tip = base + dir * len
                val normal = Offset(-dir.y, dir.x)
                val mid = base + dir * (len / 2f)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(base.x, base.y)
                    quadraticTo((mid + normal * len * 0.45f).x, (mid + normal * len * 0.45f).y, tip.x, tip.y)
                    quadraticTo((mid - normal * len * 0.45f).x, (mid - normal * len * 0.45f).y, base.x, base.y)
                    close()
                }
                drawPath(path, leafColor)
            }
            // Clock hand
            val handAngle = Math.toRadians(-90.0 + 300.0 * draw.value)
            drawLine(
                primary,
                c,
                Offset(c.x + (r * 0.45f * kotlin.math.cos(handAngle)).toFloat(), c.y + (r * 0.45f * kotlin.math.sin(handAngle)).toFloat()),
                strokeWidth = stroke * 0.6f,
                cap = StrokeCap.Round,
            )
            drawCircle(primary, radius = stroke * 0.55f, center = c)
        }
    }
    Spacer(Modifier.height(24.dp))
    Column(
        Modifier.graphicsLayer {
            alpha = text.value
            translationY = (1f - text.value) * 30.dp.toPx()
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Stacco", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ob_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.ob_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DonePage(name: String) {
    Spacer(Modifier.height(48.dp))
    MorphBadge(
        icon = Icons.Rounded.Celebration,
        colors = listOf(MaterialTheme.colorScheme.primary, StaccoTheme.colors.gold),
        size = 150.dp,
        periodMillis = 1600,
    )
    Spacer(Modifier.height(32.dp))
    Text(
        if (name.isBlank()) stringResource(R.string.ob_done_title) else stringResource(R.string.ob_done_title_name, name.trim()),
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        stringResource(R.string.ob_done_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
