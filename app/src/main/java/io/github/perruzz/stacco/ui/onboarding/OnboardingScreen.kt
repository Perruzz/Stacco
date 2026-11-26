package io.github.perruzz.stacco.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.data.Backup
import io.github.perruzz.stacco.data.StaccoJson
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.ExpiryPeriod
import io.github.perruzz.stacco.domain.ShiftTemplate
import io.github.perruzz.stacco.domain.SmartPeriod
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.WorkMode
import io.github.perruzz.stacco.ui.MainViewModel
import io.github.perruzz.stacco.ui.bank.VacationAnnualDialog
import io.github.perruzz.stacco.ui.bank.VacationAvailableDialog
import io.github.perruzz.stacco.ui.bank.vacationDays
import io.github.perruzz.stacco.ui.bank.vacationHours
import io.github.perruzz.stacco.ui.components.AuroraBackground
import io.github.perruzz.stacco.ui.components.ConfettiBurst
import io.github.perruzz.stacco.ui.components.DayOfYearPickerDialog
import io.github.perruzz.stacco.ui.components.DurationPickerDialog
import io.github.perruzz.stacco.ui.components.IconBubble
import io.github.perruzz.stacco.ui.components.MorphBadge
import io.github.perruzz.stacco.ui.components.SegmentLabel
import io.github.perruzz.stacco.ui.components.SegmentPadding
import io.github.perruzz.stacco.ui.components.SegmentRowModifier
import io.github.perruzz.stacco.ui.components.StaccoCard
import io.github.perruzz.stacco.ui.components.bouncyClickable
import io.github.perruzz.stacco.ui.components.sweepCenter
import io.github.perruzz.stacco.ui.oncall.OnCallIcon
import io.github.perruzz.stacco.ui.oncall.OnCallRules
import io.github.perruzz.stacco.ui.settings.RoundingSection
import io.github.perruzz.stacco.ui.settings.SettingRow
import io.github.perruzz.stacco.ui.settings.ShiftEditorDialog
import io.github.perruzz.stacco.ui.settings.ShiftList
import io.github.perruzz.stacco.ui.settings.StepperRow
import io.github.perruzz.stacco.ui.settings.SwitchRow
import io.github.perruzz.stacco.ui.settings.WeekHoursDialog
import io.github.perruzz.stacco.ui.settings.WeekdayToggles
import io.github.perruzz.stacco.ui.settings.defaultShifts
import io.github.perruzz.stacco.ui.settings.exactAlarmsAllowed
import io.github.perruzz.stacco.ui.settings.label
import io.github.perruzz.stacco.ui.settings.notificationsAllowed
import io.github.perruzz.stacco.ui.settings.openExactAlarmSettings
import io.github.perruzz.stacco.ui.settings.scheduleSummary
import io.github.perruzz.stacco.ui.settings.weekHoursSummary
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import io.github.perruzz.stacco.ui.theme.deltaColor
import io.github.perruzz.stacco.ui.today.Appear
import io.github.perruzz.stacco.ui.today.icon
import io.github.perruzz.stacco.ui.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

internal enum class Step { WELCOME, NAME, MODE, HOURS, DAYS, RULES, SHIFTS, BANK, VACATION, SMART, ON_CALL, NOTIFY, DONE }

/**
 * The pages of the setup, one question each. Fixed hours ask for the hours, the working days
 * and the break; shifts for the shifts. Rounding, hour bank, vacation, remote work and on-call
 * duty come for both, and every number has a default: "Next" alone gets through.
 */
internal fun stepsFor(mode: WorkMode): List<Step> = buildList {
    add(Step.WELCOME)
    add(Step.NAME)
    add(Step.MODE)
    if (mode == WorkMode.SHIFTS) {
        add(Step.SHIFTS)
        add(Step.RULES)
    } else {
        add(Step.HOURS)
        add(Step.DAYS)
        add(Step.RULES)
    }
    addAll(listOf(Step.BANK, Step.VACATION, Step.SMART, Step.ON_CALL, Step.NOTIFY, Step.DONE))
}

/** The title of a page, also what TalkBack says when the page changes. */
private fun titleOf(step: Step, mode: WorkMode): Int = when (step) {
    Step.WELCOME -> R.string.app_name
    Step.NAME -> R.string.ob_name_title
    Step.MODE -> R.string.ob_mode_title
    Step.HOURS -> R.string.ob_work_title
    Step.DAYS -> R.string.ob_days_title
    Step.RULES -> if (mode == WorkMode.SHIFTS) R.string.ob_rounding_title else R.string.ob_rules_title
    Step.SHIFTS -> R.string.ob_shifts_title
    Step.BANK -> R.string.ob_bank_title
    Step.VACATION -> R.string.ob_vacation_title
    Step.SMART -> R.string.ob_smart_title
    Step.ON_CALL -> R.string.ob_on_call_title
    Step.NOTIFY -> R.string.ob_notif_title
    Step.DONE -> R.string.ob_done_title
}

/**
 * What the setup gathered: the settings (name included), the hour bank balance and the vacation
 * hours left today, as on the payslip (null: the whole year's).
 */
internal data class SetupAnswers(val data: StaccoData, val balance: Int, val vacationLeft: Int?)

private enum class Field { DAILY, MINIMUM, MIN_BREAK, BALANCE }

private enum class Picker { WEEK, PATRON, VACATION, VACATION_LEFT }

private val DraftSaver = Saver<StaccoData, String>(
    save = { StaccoJson.encodeToString(StaccoData.serializer(), it) },
    restore = { runCatching { StaccoJson.decodeFromString(StaccoData.serializer(), it) }.getOrNull() },
)

@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var restoreFailed by rememberSaveable { mutableStateOf(false) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use(Backup::read) }.getOrNull()
                }
                // Restored, the app opens on its data straight away: there is nothing left to set up.
                restoreFailed = text == null || !viewModel.importJson(text)
            }
        }
    }
    OnboardingFlow(
        onFinish = { viewModel.completeOnboarding(it.data, it.balance, it.vacationLeft) },
        onRestore = {
            restoreFailed = false
            restore.launch(arrayOf("application/json", "text/plain", "*/*"))
        },
        restoreFailed = restoreFailed,
    )
}

@Composable
internal fun OnboardingFlow(onFinish: (SetupAnswers) -> Unit, onRestore: () -> Unit, restoreFailed: Boolean = false) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    // Saved with the page: after a rotation or a theme change the choices made so far are kept
    // (and the page index keeps pointing to the same step of the same flow).
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(StaccoData()) }
    var name by rememberSaveable { mutableStateOf("") }
    var balance by rememberSaveable { mutableIntStateOf(0) }
    var vacationLeft by rememberSaveable { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<Field?>(null) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confetti by remember { mutableIntStateOf(0) }
    var editingShift by remember { mutableStateOf<ShiftTemplate?>(null) }
    var addingShift by remember { mutableStateOf(false) }
    // Read again when the user comes back from the system settings.
    var permissionCheck by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        permissionCheck++
        onPauseOrDispose { }
    }
    val notificationsOn = remember(permissionCheck) { notificationsAllowed(context) }
    val exactOn = remember(permissionCheck) { exactAlarmsAllowed(context) }
    val defaults = defaultShifts()
    val steps = stepsFor(draft.work.mode)
    val step = steps[page.coerceIn(0, steps.lastIndex)]
    val next = { page = (page + 1).coerceAtMost(stepsFor(draft.work.mode).lastIndex) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionCheck++
        // Allowed: the exact reminders are offered on the same page. Refused: no point in them.
        if (!granted || exactAlarmsAllowed(context)) next()
    }
    val askNotifications = step == Step.NOTIFY && !notificationsOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    BackHandler(enabled = page > 0) { page-- }
    LaunchedEffect(step) { if (step == Step.DONE) confetti++ }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(intensity = 1.3f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
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
                val shown = steps[current.coerceIn(0, steps.lastIndex)]
                val mode = draft.work.mode
                val pane = stringResource(titleOf(shown, mode))
                Column(
                    Modifier
                        .fillMaxSize()
                        // TalkBack says the title of the new page as it comes in.
                        .semantics { paneTitle = pane }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (shown) {
                        Step.WELCOME -> WelcomePage()
                        Step.NAME -> Page(Icons.Rounded.WavingHand, titleOf(shown, mode), R.string.ob_name_body) {
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
                        Step.MODE -> Page(Icons.Rounded.Diversity3, titleOf(shown, mode), R.string.ob_mode_body) {
                            ModeChoice(
                                mode = draft.work.mode,
                                onSelect = { chosen ->
                                    draft = draft.copy(
                                        work = draft.work.copy(
                                            mode = chosen,
                                            shifts = if (chosen == WorkMode.SHIFTS && draft.work.shifts.isEmpty()) defaults else draft.work.shifts,
                                        ),
                                        // Shift workers rarely work remotely: start from zero.
                                        smart = draft.smart.copy(allowance = if (chosen == WorkMode.SHIFTS) 0 else 10),
                                    )
                                },
                            )
                        }
                        Step.SHIFTS -> Page(Icons.Rounded.Bedtime, titleOf(shown, mode), R.string.ob_shifts_body) {
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
                        Step.HOURS -> Page(Icons.Rounded.Schedule, titleOf(shown, mode), R.string.ob_work_body) {
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
                        Step.DAYS -> Page(Icons.Rounded.DateRange, titleOf(shown, mode), R.string.ob_days_body) {
                            val work = draft.work
                            StaccoCard {
                                Text(
                                    stringResource(R.string.settings_workdays),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                                )
                                val first = remember { Engine.firstDayOfWeek() }
                                WeekdayToggles(
                                    selected = work.workDays,
                                    order = (0 until 7).map { first.plus(it.toLong()).value },
                                    labels = { Fmt.weekdayNarrow(DayOfWeek.of(it)).take(2) },
                                    onToggle = { day ->
                                        // The last working day cannot be removed: a schedule needs at least one.
                                        val days = draft.work.workDays.toMutableSet().apply { if (!add(day) && size > 1) remove(day) }
                                        draft = draft.copy(work = draft.work.copy(workDays = days))
                                    },
                                )
                                Spacer(Modifier.height(8.dp))
                                SettingRow(
                                    icon = Icons.Rounded.ViewWeek,
                                    title = stringResource(R.string.settings_daily_by_day),
                                    subtitle = weekHoursSummary(work),
                                    onClick = { picker = Picker.WEEK },
                                )
                                SwitchRow(
                                    icon = Icons.Rounded.Celebration,
                                    title = stringResource(R.string.settings_holidays),
                                    subtitle = stringResource(R.string.settings_holidays_sub),
                                    checked = work.italianHolidays,
                                    onCheckedChange = { on -> draft = draft.copy(work = draft.work.copy(italianHolidays = on)) },
                                )
                                SettingRow(
                                    icon = Icons.Rounded.LocationCity,
                                    title = stringResource(R.string.settings_patron),
                                    subtitle = stringResource(R.string.settings_patron_sub),
                                    value = patronLabel(work.patronDay),
                                    onClick = { picker = Picker.PATRON },
                                )
                            }
                        }
                        Step.RULES -> {
                            val shifts = mode == WorkMode.SHIFTS
                            Page(if (shifts) Icons.Rounded.Timer else Icons.Rounded.Restaurant, titleOf(shown, mode), if (shifts) R.string.ob_rounding_body else R.string.ob_rules_body) {
                                if (!shifts) {
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
                                    Spacer(Modifier.height(16.dp))
                                }
                                StaccoCard {
                                    RoundingSection(draft.work.rounding) { rules -> draft = draft.copy(work = draft.work.copy(rounding = rules)) }
                                }
                            }
                        }
                        Step.BANK -> Page(Icons.Rounded.Savings, titleOf(shown, mode), R.string.ob_bank_body) {
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
                                AnimatedVisibility(draft.bank.negativeMustBeRecovered, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                    Column(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) {
                                        Text(
                                            stringResource(R.string.settings_negative_close),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        TwoChoices(
                                            first = stringResource(R.string.settings_negative_reset),
                                            second = stringResource(R.string.settings_negative_carry),
                                            firstSelected = draft.bank.resetNegativeAtMonthEnd,
                                            onSelect = { reset -> draft = draft.copy(bank = draft.bank.copy(resetNegativeAtMonthEnd = reset)) },
                                        )
                                    }
                                }
                            }
                        }
                        Step.VACATION -> {
                            val annual = draft.vacation.annualMinutes
                            val dayMinutes = VacationCalculator.typicalDay(draft.work)
                            val tint = StaccoTheme.colors.vacation
                            Page(DayType.VACATION.icon, titleOf(shown, mode), R.string.ob_vacation_body) {
                                StaccoCard {
                                    SettingRow(
                                        icon = DayType.VACATION.icon,
                                        title = stringResource(R.string.settings_vacation),
                                        subtitle = if (annual > 0) {
                                            stringResource(R.string.vacation_days_approx, vacationDays(annual, dayMinutes))
                                        } else {
                                            stringResource(R.string.settings_vacation_sub)
                                        },
                                        value = if (annual > 0) vacationHours(annual) else "—",
                                        tint = tint,
                                        onClick = { picker = Picker.VACATION },
                                    )
                                    SettingRow(
                                        icon = Icons.Rounded.EditCalendar,
                                        title = stringResource(R.string.vacation_set_title),
                                        subtitle = stringResource(R.string.vacation_set_sub),
                                        value = (vacationLeft ?: annual.takeIf { it > 0 })?.let { vacationHours(it) } ?: "—",
                                        tint = tint,
                                        onClick = { picker = Picker.VACATION_LEFT },
                                    )
                                    SwitchRow(
                                        icon = Icons.Rounded.EventRepeat,
                                        title = stringResource(R.string.vacation_carry),
                                        subtitle = stringResource(R.string.vacation_carry_sub),
                                        checked = draft.vacation.carryOver,
                                        tint = tint,
                                        onCheckedChange = { on -> draft = draft.copy(vacation = draft.vacation.copy(carryOver = on)) },
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Explainer(stringResource(R.string.ob_vacation_explainer))
                            }
                        }
                        Step.SMART -> Page(Icons.Rounded.Home, titleOf(shown, mode), R.string.ob_smart_body) {
                            StaccoCard {
                                StepperRow(
                                    icon = Icons.Rounded.Home,
                                    title = stringResource(R.string.settings_smart_days),
                                    value = draft.smart.allowance,
                                    range = 0..31,
                                    tint = StaccoTheme.colors.smart,
                                    onValueChange = { draft = draft.copy(smart = draft.smart.copy(allowance = it)) },
                                )
                                TwoChoices(
                                    first = stringResource(R.string.settings_smart_month),
                                    second = stringResource(R.string.settings_smart_week),
                                    firstSelected = draft.smart.period == SmartPeriod.MONTH,
                                    onSelect = { month ->
                                        draft = draft.copy(smart = draft.smart.copy(period = if (month) SmartPeriod.MONTH else SmartPeriod.WEEK))
                                    },
                                )
                            }
                        }
                        Step.ON_CALL -> Page(OnCallIcon, titleOf(shown, mode), R.string.ob_on_call_body) {
                            val on = draft.onCall.enabled
                            val set = { enabled: Boolean -> draft = draft.copy(onCall = draft.onCall.copy(enabled = enabled)) }
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ChoiceCard(
                                    icon = Icons.Rounded.EventAvailable,
                                    title = stringResource(R.string.ob_on_call_no),
                                    body = stringResource(R.string.ob_on_call_no_desc),
                                    selected = !on,
                                    onClick = { set(false) },
                                )
                                ChoiceCard(
                                    icon = OnCallIcon,
                                    title = stringResource(R.string.ob_on_call_yes),
                                    body = stringResource(R.string.ob_on_call_yes_desc),
                                    selected = on,
                                    tint = StaccoTheme.colors.onCall,
                                    onClick = { set(true) },
                                )
                            }
                            AnimatedVisibility(on, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                Column {
                                    Spacer(Modifier.height(16.dp))
                                    StaccoCard { OnCallRules() }
                                    Spacer(Modifier.height(12.dp))
                                    Explainer(stringResource(R.string.ob_on_call_explainer))
                                }
                            }
                        }
                        Step.NOTIFY -> Page(Icons.Rounded.NotificationsActive, titleOf(shown, mode), R.string.ob_notif_body) {
                            StaccoCard {
                                PermissionRow(
                                    icon = Icons.Rounded.NotificationsActive,
                                    title = stringResource(R.string.ob_perm_notifications),
                                    subtitle = stringResource(R.string.ob_perm_notifications_sub),
                                    granted = notificationsOn,
                                )
                                // Android 12+: "Alarms & reminders" is off until the user turns it on.
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    PermissionRow(
                                        icon = Icons.Rounded.AlarmOn,
                                        title = stringResource(R.string.ob_perm_exact),
                                        subtitle = stringResource(R.string.ob_perm_exact_sub),
                                        granted = exactOn,
                                        action = stringResource(R.string.ob_perm_allow),
                                        onAction = { openExactAlarmSettings(context) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Explainer(stringResource(R.string.ob_notif_explainer))
                        }
                        Step.DONE -> DonePage(name, draft, balance, vacationLeft)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            // Bottom action. The numbers can be skipped with one tap: the hour bank starts from zero,
            // vacation is not counted (both can be set later in the Bank tab or in Settings).
            val isLast = step == Step.DONE
            val startFromZero: () -> Unit = {
                balance = 0
                next()
            }
            val noVacation: () -> Unit = {
                draft = draft.copy(vacation = draft.vacation.copy(annualMinutes = 0))
                vacationLeft = null
                next()
            }
            val skip: (() -> Unit)? = when {
                step == Step.BANK -> startFromZero
                step == Step.VACATION -> noVacation
                askNotifications -> next
                else -> null
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = {
                        when {
                            isLast -> onFinish(SetupAnswers(draft.copy(profile = draft.profile.copy(name = name.trim())), balance, vacationLeft))
                            askNotifications -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> next()
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
                                askNotifications -> R.string.ob_enable_notifications
                                isLast -> R.string.ob_enter
                                else -> R.string.ob_next
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        if (askNotifications) Icons.Rounded.NotificationsActive else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                    )
                }
                when {
                    step == Step.WELCOME -> {
                        TextButton(onClick = onRestore) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ob_restore), textAlign = TextAlign.Center)
                        }
                        AnimatedVisibility(restoreFailed) {
                            Text(
                                stringResource(R.string.backup_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                    skip != null -> TextButton(onClick = skip) {
                        Text(
                            stringResource(
                                when (step) {
                                    Step.BANK -> R.string.ob_bank_skip
                                    Step.VACATION -> R.string.ob_vacation_skip
                                    else -> R.string.ob_skip
                                },
                            ),
                            textAlign = TextAlign.Center,
                        )
                    }
                    page in 1 until steps.lastIndex -> Text(
                        stringResource(R.string.ob_changeable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
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

    when (picker) {
        Picker.WEEK -> WeekHoursDialog(
            work = draft.work,
            onDismiss = { picker = null },
            onSave = { hours ->
                draft = draft.copy(work = draft.work.copy(dailyByDay = hours))
                picker = null
            },
        )
        Picker.PATRON -> DayOfYearPickerDialog(
            title = stringResource(R.string.settings_patron),
            initial = draft.work.patronDay,
            icon = Icons.Rounded.LocationCity,
            subtitle = stringResource(R.string.settings_patron_sub),
            onDismiss = { picker = null },
            onConfirm = { value ->
                draft = draft.copy(work = draft.work.copy(patronDay = value))
                picker = null
            },
            onClear = if (draft.work.patronDay > 0) {
                {
                    draft = draft.copy(work = draft.work.copy(patronDay = 0))
                    picker = null
                }
            } else {
                null
            },
        )
        Picker.VACATION -> VacationAnnualDialog(
            settings = draft.vacation,
            dayMinutes = VacationCalculator.typicalDay(draft.work),
            onDismiss = { picker = null },
            onConfirm = { minutes, carry ->
                draft = draft.copy(vacation = draft.vacation.copy(annualMinutes = minutes, carryOver = carry))
                picker = null
            },
        )
        Picker.VACATION_LEFT -> VacationAvailableDialog(
            current = vacationLeft ?: draft.vacation.annualMinutes,
            onDismiss = { picker = null },
            onConfirm = { minutes ->
                vacationLeft = minutes
                picker = null
            },
        )
        null -> Unit
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
            subtitle = if (field == Field.BALANCE) stringResource(R.string.bank_set_sub) else null,
            initialMinutes = initial,
            maxHours = max,
            allowNegative = field == Field.BALANCE,
            onDismiss = { editing = null },
            onConfirm = { v ->
                draft = when (field) {
                    // Keep the room the hour bank had (8h/7h -> 6h/5h).
                    Field.DAILY -> draft.copy(work = work.copy(dailyMinutes = v, minDailyMinutes = (v - work.flexMinutes).coerceAtLeast(0)))
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
private fun patronLabel(patronDay: Int): String =
    if (patronDay > 0) Fmt.dayMonth(LocalDate.of(2024, patronDay / 100, patronDay % 100)) else stringResource(R.string.settings_patron_none)

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
        ChoiceCard(
            icon = Icons.Rounded.Schedule,
            title = stringResource(R.string.mode_fixed),
            body = stringResource(R.string.mode_fixed_desc),
            selected = mode == WorkMode.FIXED,
            onClick = { onSelect(WorkMode.FIXED) },
        )
        ChoiceCard(
            icon = Icons.Rounded.Bedtime,
            title = stringResource(R.string.mode_shifts),
            body = stringResource(R.string.mode_shifts_desc),
            selected = mode == WorkMode.SHIFTS,
            onClick = { onSelect(WorkMode.SHIFTS) },
        )
    }
}

/** One of a few exclusive choices, as a big card: TalkBack reads it as a radio button. */
@Composable
private fun ChoiceCard(
    icon: ImageVector,
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val border by animateColorAsState(if (selected) tint else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), label = "choice-border")
    val background by animateColorAsState(
        if (selected) tint.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f),
        label = "choice-bg",
    )
    val scale by animateFloatAsState(if (selected) 1.02f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "choice-scale")
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(24.dp))
            .bouncyClickable(pressed = 0.97f, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, tint = tint, container = tint.copy(alpha = 0.14f), size = 48.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = tint, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Two exclusive options side by side; with large fonts their labels go on two lines, never cut. */
@Composable
private fun TwoChoices(first: String, second: String, firstSelected: Boolean, onSelect: (first: Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(SegmentRowModifier) {
        listOf(first to true, second to false).forEachIndexed { index, (label, isFirst) ->
            SegmentedButton(
                selected = firstSelected == isFirst,
                onClick = { onSelect(isFirst) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                modifier = Modifier.fillMaxHeight(),
                contentPadding = SegmentPadding,
                icon = {},
            ) { SegmentLabel(label) }
        }
    }
}

/** A permission the app works better with: its state, and the button that asks for it. */
@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val state = stringResource(if (granted) R.string.a11y_permission_on else R.string.a11y_permission_off)
    SettingRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = Modifier.semantics(mergeDescendants = true) { stateDescription = state },
        trailing = {
            AnimatedContent(
                targetState = granted,
                transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeIn()) togetherWith fadeOut(tween(120)) },
                label = "permission",
            ) { on ->
                when {
                    on -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    action != null && onAction != null -> FilledTonalButton(onClick = onAction) { Text(action) }
                    else -> Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            }
        },
    )
}

@Composable
private fun ProgressDots(count: Int, current: Int) {
    val description = stringResource(R.string.a11y_step, current + 1, count)
    Row(
        Modifier
            .animateContentSize()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
    // In landscape (or on short screens) a smaller badge leaves room for the title and the choices.
    val compact = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < 560.dp
    Spacer(Modifier.height(if (compact) 4.dp else 24.dp))
    MorphBadge(
        icon = icon,
        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
        size = if (compact) 64.dp else 128.dp,
    )
    Spacer(Modifier.height(if (compact) 12.dp else 28.dp))
    Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
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
    SingleChoiceSegmentedButtonRow(SegmentRowModifier) {
        options.forEachIndexed { index, period ->
            SegmentedButton(
                selected = selected == period,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.fillMaxHeight(),
                contentPadding = SegmentPadding,
                icon = {},
            ) {
                SegmentLabel(stringResource(period.shortLabel), MaterialTheme.typography.labelMedium)
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
                    brush = androidx.compose.ui.graphics.Brush.sweepGradient(listOf(primary, tertiary, primary), sweepCenter(c)),
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
        // One word that cannot wrap: with very large fonts on a narrow phone it shrinks instead of being cut.
        val display = MaterialTheme.typography.displayLarge
        Text(
            "Stacco",
            style = display,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = display.fontSize),
            modifier = Modifier.semantics { heading() },
        )
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

/** The last page: a celebration, and what was set up, line by line. */
@Composable
private fun DonePage(name: String, draft: StaccoData, balance: Int, vacationLeft: Int?) {
    Spacer(Modifier.height(32.dp))
    MorphBadge(
        icon = Icons.Rounded.Celebration,
        colors = listOf(MaterialTheme.colorScheme.primary, StaccoTheme.colors.gold),
        size = 128.dp,
        periodMillis = 1600,
    )
    Spacer(Modifier.height(28.dp))
    Text(
        if (name.isBlank()) stringResource(R.string.ob_done_title) else stringResource(R.string.ob_done_title_name, name.trim()),
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(10.dp))
    Text(
        stringResource(R.string.ob_done_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    val c = StaccoTheme.colors
    val work = draft.work
    val vacation = vacationLeft ?: draft.vacation.annualMinutes.takeIf { it > 0 }
    val lines = buildList {
        if (work.mode == WorkMode.SHIFTS) {
            add(Triple(Icons.Rounded.Bedtime, pluralStringResource(R.plurals.ob_summary_shifts, work.shifts.size, work.shifts.size), MaterialTheme.colorScheme.primary))
        } else {
            add(Triple(Icons.Rounded.Schedule, scheduleSummary(work.schedule), MaterialTheme.colorScheme.primary))
        }
        add(Triple(Icons.Rounded.Savings, stringResource(R.string.ob_summary_bank, Fmt.duration(balance, signed = true)), if (balance == 0) MaterialTheme.colorScheme.primary else deltaColor(balance)))
        if (vacation != null) add(Triple(DayType.VACATION.icon, stringResource(R.string.ob_summary_vacation, vacationHours(vacation)), c.vacation))
        if (draft.onCall.enabled) add(Triple(OnCallIcon, stringResource(R.string.ob_summary_on_call), c.onCall))
    }
    StaccoCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            lines.forEachIndexed { index, (icon, text, tint) ->
                Appear(index) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(icon, tint = tint, container = tint.copy(alpha = 0.13f), size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
