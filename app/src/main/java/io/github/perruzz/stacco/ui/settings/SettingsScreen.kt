package io.github.perruzz.stacco.ui.settings

import io.github.perruzz.stacco.ui.components.rememberSway
import io.github.perruzz.stacco.ui.components.readableWidth
import io.github.perruzz.stacco.ui.components.AdaptiveRow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import android.Manifest
import android.app.AlarmManager
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.rounded.History
import io.github.perruzz.stacco.data.Backup
import io.github.perruzz.stacco.domain.Schedule
import io.github.perruzz.stacco.domain.ScheduleHistory
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.WorkMode
import io.github.perruzz.stacco.domain.ShiftTemplate
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WorkHistory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.perruzz.stacco.BuildConfig
import io.github.perruzz.stacco.Links
import io.github.perruzz.stacco.notify.UpdateChecker
import io.github.perruzz.stacco.notify.UpdateResult
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.ExitTarget
import io.github.perruzz.stacco.domain.ExpiryPeriod
import io.github.perruzz.stacco.domain.SmartPeriod
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.ThemeMode
import io.github.perruzz.stacco.domain.WorkSettings
import io.github.perruzz.stacco.ui.LocalThemeSwitcher
import io.github.perruzz.stacco.ui.MainViewModel
import io.github.perruzz.stacco.ui.bank.VacationAnnualDialog
import io.github.perruzz.stacco.ui.components.ConfirmDialog
import io.github.perruzz.stacco.ui.components.DayOfYearPickerDialog
import io.github.perruzz.stacco.ui.components.DurationPickerDialog
import io.github.perruzz.stacco.ui.components.MorphBadge
import io.github.perruzz.stacco.ui.components.SectionLabel
import io.github.perruzz.stacco.ui.components.SegmentLabel
import io.github.perruzz.stacco.ui.components.SegmentPadding
import io.github.perruzz.stacco.ui.components.SegmentRowModifier
import io.github.perruzz.stacco.ui.components.StaccoCard
import io.github.perruzz.stacco.ui.components.StaccoDialog
import io.github.perruzz.stacco.ui.components.TextInputDialog
import io.github.perruzz.stacco.ui.components.bouncyClickable
import io.github.perruzz.stacco.ui.oncall.OnCallIcon
import io.github.perruzz.stacco.ui.oncall.OnCallRules
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import io.github.perruzz.stacco.ui.theme.deltaColor
import io.github.perruzz.stacco.ui.today.Appear
import io.github.perruzz.stacco.ui.today.icon
import io.github.perruzz.stacco.ui.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

private enum class DurationField { DAILY, MINIMUM, MIN_BREAK, BALANCE }

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val dataState by viewModel.data.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val data = dataState ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editingDuration by remember { mutableStateOf<DurationField?>(null) }
    var editingName by remember { mutableStateOf(false) }
    var editingPatron by remember { mutableStateOf(false) }
    var editingVacation by remember { mutableStateOf(false) }
    var editingWeek by remember { mutableStateOf(false) }
    var pickingScheduleDate by remember { mutableStateOf(false) }
    var showingHistory by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var choosingExpiry by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<String?>(null) }
    var editingShift by remember { mutableStateOf<ShiftTemplate?>(null) }
    var addingShift by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    fun update(transform: (StaccoData) -> StaccoData) = viewModel.updateData(transform)

    val notificationsGranted = remember(refresh) { notificationsAllowed(context) }
    val exactAlarms = remember(refresh) { exactAlarmsAllowed(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        // The live notification shows up now, not at the next minute.
        if (granted) viewModel.refreshOutside()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.exportJson()
                val ok = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }.isSuccess
                }
                viewModel.notify(if (ok) R.string.backup_export_ok else R.string.backup_error)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use(Backup::read) }.getOrNull()
                }
                if (text == null) viewModel.notify(R.string.backup_error) else confirmImport = text
            }
        }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Schedule edits (mode, hours, weekdays) are a draft until the user says from when they apply.
    val pendingSchedule by viewModel.pendingSchedule.collectAsStateWithLifecycle()
    val work = pendingSchedule?.let { data.work.withSchedule(it) } ?: data.work

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = bottom + 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            var i = 0
            Appear(i++) { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() }) }

            Appear(i++) {
                StaccoCard {
                    SectionLabel(stringResource(R.string.settings_profile))
                    SettingRow(
                        icon = Icons.Rounded.Badge,
                        title = stringResource(R.string.settings_name),
                        subtitle = data.profile.name.ifBlank { stringResource(R.string.settings_name_empty) },
                        onClick = { editingName = true },
                    )
                }
            }

            Appear(i++) {
                val defaults = defaultShifts()
                WorkModeCard(
                    mode = work.mode,
                    onChange = { mode ->
                        if (mode == WorkMode.SHIFTS && data.work.shifts.isEmpty()) update { it.copy(work = it.work.copy(shifts = defaults)) }
                        viewModel.editSchedule { it.copy(mode = mode) }
                    },
                )
            }

            if (data.scheduleHistory.isNotEmpty()) {
                Appear(i++) {
                    StaccoCard {
                        SettingRow(
                            icon = Icons.Rounded.History,
                            title = stringResource(R.string.schedule_history),
                            subtitle = ScheduleHistory.currentSince(data.scheduleHistory)
                                ?.let { stringResource(R.string.schedule_history_since, Fmt.dayMonthYear(it)) },
                            onClick = { showingHistory = true },
                        )
                    }
                }
            }

            Appear(i++) {
                AnimatedContent(
                    targetState = work.mode,
                    transitionSpec = {
                        (fadeIn(tween(300, delayMillis = 120)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 300f)) { it / 6 }) togetherWith
                            (fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 8 }) using
                            SizeTransform(clip = false)
                    },
                    label = "work-mode",
                ) { mode ->
                    if (mode == WorkMode.SHIFTS) {
                        StaccoCard {
                            SectionLabel(stringResource(R.string.settings_shifts))
                            Text(
                                stringResource(R.string.settings_shifts_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                            )
                            ShiftList(
                                shifts = work.shifts,
                                onEdit = { editingShift = it },
                                onAdd = { addingShift = true },
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            StaccoCard {
                                SectionLabel(stringResource(R.string.settings_work))
                                SettingRow(
                                    icon = Icons.Rounded.Schedule,
                                    title = stringResource(R.string.settings_daily),
                                    subtitle = stringResource(R.string.settings_daily_sub),
                                    value = Fmt.duration(work.dailyMinutes),
                                    onClick = { editingDuration = DurationField.DAILY },
                                )
                                SettingRow(
                                    icon = Icons.Rounded.HourglassBottom,
                                    title = stringResource(R.string.settings_minimum),
                                    subtitle = stringResource(R.string.settings_minimum_sub, Fmt.duration((work.dailyMinutes - work.minDailyMinutes).coerceAtLeast(0))),
                                    value = Fmt.duration(work.minDailyMinutes),
                                    onClick = { editingDuration = DurationField.MINIMUM },
                                )
                                SettingRow(
                                    icon = Icons.Rounded.ViewWeek,
                                    title = stringResource(R.string.settings_daily_by_day),
                                    subtitle = weekHoursSummary(work),
                                    onClick = { editingWeek = true },
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.settings_workdays), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                                val first = remember { io.github.perruzz.stacco.domain.Engine.firstDayOfWeek() }
                                WeekdayToggles(
                                    selected = work.workDays,
                                    order = (0 until 7).map { first.plus(it.toLong()).value },
                                    labels = { Fmt.weekdayNarrow(DayOfWeek.of(it)).take(2) },
                                    onToggle = { day ->
                                        viewModel.editSchedule { schedule ->
                                            // The last working day cannot be removed: a schedule needs at least one.
                                            val days = schedule.workDays.toMutableSet().apply { if (!add(day) && size > 1) remove(day) }
                                            schedule.copy(workDays = days)
                                        }
                                    },
                                )
                                Spacer(Modifier.height(8.dp))
                                SwitchRow(
                                    icon = Icons.Rounded.Celebration,
                                    title = stringResource(R.string.settings_holidays),
                                    subtitle = stringResource(R.string.settings_holidays_sub),
                                    checked = work.italianHolidays,
                                    onCheckedChange = { on -> update { it.copy(work = it.work.copy(italianHolidays = on)) } },
                                )
                                SettingRow(
                                    icon = Icons.Rounded.LocationCity,
                                    title = stringResource(R.string.settings_patron),
                                    subtitle = stringResource(R.string.settings_patron_sub),
                                    value = if (work.patronDay > 0) {
                                        Fmt.dayMonth(LocalDate.of(2024, work.patronDay / 100, work.patronDay % 100))
                                    } else {
                                        stringResource(R.string.settings_patron_none)
                                    },
                                    onClick = { editingPatron = true },
                                )
                            }
                            StaccoCard {
                                SectionLabel(stringResource(R.string.settings_break))
                                SettingRow(
                                    icon = Icons.Rounded.Coffee,
                                    title = stringResource(R.string.settings_min_break),
                                    subtitle = stringResource(R.string.settings_min_break_sub),
                                    value = Fmt.duration(work.minBreakMinutes),
                                    tint = StaccoTheme.colors.lunch,
                                    onClick = { editingDuration = DurationField.MIN_BREAK },
                                )
                            }
                        }
                    }
                }
            }

            Appear(i++) {
                StaccoCard {
                    RoundingSection(work.rounding) { rules -> update { it.copy(work = it.work.copy(rounding = rules)) } }
                }
            }

            Appear(i++) {
                StaccoCard {
                    SectionLabel(stringResource(R.string.settings_bank))
                    SettingRow(
                        icon = Icons.Rounded.Savings,
                        title = stringResource(R.string.settings_balance),
                        subtitle = stringResource(R.string.settings_balance_sub),
                        value = Fmt.duration(ledger.balance, signed = true),
                        tint = deltaColor(ledger.balance).takeIf { ledger.balance != 0 } ?: MaterialTheme.colorScheme.primary,
                        onClick = { editingDuration = DurationField.BALANCE },
                    )
                    SettingRow(
                        icon = Icons.Rounded.HourglassTop,
                        title = stringResource(R.string.settings_expiry),
                        subtitle = stringResource(R.string.settings_expiry_sub),
                        value = stringResource(data.bank.positiveExpiry.label),
                        onClick = { choosingExpiry = true },
                    )
                    SwitchRow(
                        icon = Icons.AutoMirrored.Rounded.TrendingDown,
                        title = stringResource(R.string.settings_negative),
                        subtitle = stringResource(R.string.settings_negative_sub),
                        checked = data.bank.negativeMustBeRecovered,
                        tint = StaccoTheme.colors.negative,
                        onCheckedChange = { on -> update { it.copy(bank = it.bank.copy(negativeMustBeRecovered = on)) } },
                    )
                    AnimatedVisibility(data.bank.negativeMustBeRecovered, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(Modifier.padding(start = 54.dp, bottom = 8.dp)) {
                            Text(stringResource(R.string.settings_negative_close), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = data.bank.resetNegativeAtMonthEnd,
                                    onClick = { update { it.copy(bank = it.bank.copy(resetNegativeAtMonthEnd = true)) } },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                ) { Text(stringResource(R.string.settings_negative_reset)) }
                                SegmentedButton(
                                    selected = !data.bank.resetNegativeAtMonthEnd,
                                    onClick = { update { it.copy(bank = it.bank.copy(resetNegativeAtMonthEnd = false)) } },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                ) { Text(stringResource(R.string.settings_negative_carry)) }
                            }
                        }
                    }
                    StepperRow(
                        icon = Icons.Rounded.EventRepeat,
                        title = stringResource(R.string.settings_lead),
                        subtitle = stringResource(R.string.settings_lead_sub),
                        value = data.bank.alertLeadDays,
                        range = 0..15,
                        onValueChange = { v -> update { it.copy(bank = it.bank.copy(alertLeadDays = v)) } },
                    )
                    LeadExample(work = work, lead = data.bank.alertLeadDays)
                }
            }

            Appear(i++) {
                StaccoCard {
                    SectionLabel(stringResource(R.string.settings_vacation_section))
                    SettingRow(
                        icon = DayType.VACATION.icon,
                        title = stringResource(R.string.settings_vacation),
                        subtitle = stringResource(R.string.settings_vacation_sub),
                        value = if (data.vacation.annualMinutes > 0) Fmt.duration(data.vacation.annualMinutes) else "—",
                        tint = StaccoTheme.colors.vacation,
                        onClick = { editingVacation = true },
                    )
                    SwitchRow(
                        icon = Icons.Rounded.EventRepeat,
                        title = stringResource(R.string.vacation_carry),
                        subtitle = stringResource(R.string.vacation_carry_sub),
                        checked = data.vacation.carryOver,
                        tint = StaccoTheme.colors.vacation,
                        onCheckedChange = { on -> update { it.copy(vacation = it.vacation.copy(carryOver = on)) } },
                    )
                }
            }

            // On-call duty: one switch, off by default; nothing of it shows anywhere until it is on.
            Appear(i++) {
                StaccoCard {
                    SectionLabel(stringResource(R.string.settings_on_call_section))
                    SwitchRow(
                        icon = OnCallIcon,
                        title = stringResource(R.string.settings_on_call),
                        subtitle = stringResource(R.string.settings_on_call_sub),
                        checked = data.onCall.enabled,
                        tint = StaccoTheme.colors.onCall,
                        onCheckedChange = { on -> viewModel.setOnCallEnabled(on) },
                    )
                    AnimatedVisibility(data.onCall.enabled, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        OnCallRules(Modifier.padding(start = 54.dp, top = 4.dp, bottom = 8.dp))
                    }
                }
            }

            Appear(i++) {
                StaccoCard {
                    SectionLabel(stringResource(R.string.settings_smart))
                    StepperRow(
                        icon = Icons.Rounded.Home,
                        title = stringResource(R.string.settings_smart_days),
                        subtitle = stringResource(R.string.settings_smart_days_sub),
                        value = data.smart.allowance,
                        range = 0..31,
                        tint = StaccoTheme.colors.smart,
                        onValueChange = { v -> update { it.copy(smart = it.smart.copy(allowance = v)) } },
                    )
                    SingleChoiceSegmentedButtonRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        SegmentedButton(
                            selected = data.smart.period == SmartPeriod.MONTH,
                            onClick = { update { it.copy(smart = it.smart.copy(period = SmartPeriod.MONTH)) } },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text(stringResource(R.string.settings_smart_month)) }
                        SegmentedButton(
                            selected = data.smart.period == SmartPeriod.WEEK,
                            onClick = { update { it.copy(smart = it.smart.copy(period = SmartPeriod.WEEK)) } },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text(stringResource(R.string.settings_smart_week)) }
                    }
                }
            }

            Appear(i++) {
                StaccoCard {
                    SectionLabel(stringResource(R.string.settings_notifications))
                    if (!notificationsGranted) {
                        PermissionBanner(
                            text = stringResource(R.string.settings_notif_denied),
                            action = stringResource(R.string.settings_notif_grant),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    openAppNotificationSettings(context)
                                }
                            },
                        )
                    }
                    SwitchRow(
                        icon = Icons.Rounded.AlarmOn,
                        title = stringResource(R.string.settings_exit_reminder),
                        subtitle = stringResource(R.string.settings_exit_reminder_sub),
                        checked = data.notifications.exitReminder,
                        onCheckedChange = { on -> update { it.copy(notifications = it.notifications.copy(exitReminder = on)) } },
                    )
                    AnimatedVisibility(data.notifications.exitReminder, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(Modifier.padding(start = 54.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.settings_exit_lead), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SingleChoiceSegmentedButtonRow(SegmentRowModifier) {
                                val options = listOf(0, 5, 10, 15)
                                options.forEachIndexed { index, minutes ->
                                    SegmentedButton(
                                        selected = data.notifications.exitReminderLeadMinutes == minutes,
                                        onClick = { update { it.copy(notifications = it.notifications.copy(exitReminderLeadMinutes = minutes)) } },
                                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                                        modifier = Modifier.fillMaxHeight(),
                                        contentPadding = SegmentPadding,
                                        icon = {},
                                    ) {
                                        SegmentLabel(
                                            if (minutes == 0) stringResource(R.string.settings_exit_lead_now) else "${minutes}m",
                                            MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                            Text(stringResource(R.string.settings_exit_target), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SingleChoiceSegmentedButtonRow(SegmentRowModifier) {
                                SegmentedButton(
                                    selected = data.notifications.exitTarget == ExitTarget.STANDARD,
                                    onClick = { update { it.copy(notifications = it.notifications.copy(exitTarget = ExitTarget.STANDARD)) } },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                    modifier = Modifier.fillMaxHeight(),
                                ) { SegmentLabel(stringResource(R.string.target_standard)) }
                                SegmentedButton(
                                    selected = data.notifications.exitTarget == ExitTarget.BANK,
                                    onClick = { update { it.copy(notifications = it.notifications.copy(exitTarget = ExitTarget.BANK)) } },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                    modifier = Modifier.fillMaxHeight(),
                                ) { SegmentLabel(stringResource(R.string.target_bank_long)) }
                            }
                            if (!exactAlarms) {
                                PermissionBanner(
                                    text = stringResource(R.string.settings_exact_denied),
                                    action = stringResource(R.string.settings_exact_grant),
                                    onClick = { openExactAlarmSettings(context) },
                                    urgent = false,
                                )
                            }
                        }
                    }
                    AnimatedVisibility(work.mode == WorkMode.SHIFTS, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        SwitchRow(
                            icon = Icons.Rounded.AlarmOn,
                            title = stringResource(R.string.settings_shift_reminder),
                            subtitle = pluralStringResource(R.plurals.settings_shift_reminder_sub, data.notifications.shiftStartLeadMinutes, data.notifications.shiftStartLeadMinutes),
                            checked = data.notifications.shiftStartReminder,
                            onCheckedChange = { on -> update { it.copy(notifications = it.notifications.copy(shiftStartReminder = on)) } },
                        )
                    }
                    SwitchRow(
                        icon = Icons.Rounded.Timer,
                        title = stringResource(R.string.settings_live),
                        subtitle = stringResource(R.string.settings_live_sub),
                        checked = data.notifications.liveNotification,
                        onCheckedChange = { on -> update { it.copy(notifications = it.notifications.copy(liveNotification = on)) } },
                    )
                    SwitchRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = stringResource(R.string.settings_bank_alerts),
                        subtitle = stringResource(R.string.settings_bank_alerts_sub),
                        checked = data.notifications.bankAlerts,
                        onCheckedChange = { on -> update { it.copy(notifications = it.notifications.copy(bankAlerts = on)) } },
                    )
                    // Google Play updates the app by itself: the check on GitHub is only in the GitHub build.
                    if (BuildConfig.GITHUB_UPDATES) {
                        SwitchRow(
                            icon = Icons.Rounded.SystemUpdate,
                            title = stringResource(R.string.settings_updates),
                            subtitle = stringResource(R.string.settings_updates_sub),
                            checked = data.notifications.updateCheck,
                            onCheckedChange = { on -> update { it.copy(notifications = it.notifications.copy(updateCheck = on)) } },
                        )
                    }
                }
            }

            Appear(i++) {
                AppearanceCard(
                    data,
                    onDynamic = { on -> update { it.copy(appearance = it.appearance.copy(dynamicColor = on)) } },
                    onLanguage = { viewModel.refreshOutside(languageChanged = true) },
                )
            }

            Appear(i++) { DonateCard(onClick = { openUrl(context, Links.DONATE) }) }

            Appear(i++) {
                StaccoCard {
                    SectionLabel(stringResource(R.string.settings_data))
                    SettingRow(
                        icon = Icons.Rounded.Upload,
                        title = stringResource(R.string.settings_export),
                        subtitle = stringResource(R.string.settings_export_sub),
                        onClick = { exportLauncher.launch("stacco-backup-${LocalDate.now()}.json") },
                    )
                    SettingRow(
                        icon = Icons.Rounded.Download,
                        title = stringResource(R.string.settings_import),
                        subtitle = stringResource(R.string.settings_import_sub),
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.settings_reset_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    HoldToConfirmButton(
                        text = stringResource(R.string.settings_reset),
                        holdingText = stringResource(R.string.settings_reset_holding),
                        onConfirmed = { viewModel.reset() },
                        actionLabel = stringResource(R.string.a11y_erase_everything),
                    )
                }
            }

            Appear(i++) { AboutCard(onGithub = { openUrl(context, Links.GITHUB) }) }
        }

        AnimatedVisibility(
            visible = pendingSchedule != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = bottom + 96.dp),
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            SchedulePendingBar(
                onToday = { viewModel.applySchedule(LocalDate.now()) },
                onPickDate = { pickingScheduleDate = true },
                onDiscard = { viewModel.discardSchedule() },
            )
        }
    }

    if (pickingScheduleDate) {
        ScheduleDatePicker(
            onDismiss = { pickingScheduleDate = false },
            onApply = { from ->
                viewModel.applySchedule(from)
                pickingScheduleDate = false
            },
        )
    }

    if (showingHistory) {
        ScheduleHistoryDialog(
            data = data,
            onDismiss = { showingHistory = false },
            onClear = {
                showingHistory = false
                confirmClearHistory = true
            },
        )
    }

    if (confirmClearHistory) {
        ConfirmDialog(
            title = stringResource(R.string.schedule_history_clear),
            text = stringResource(R.string.schedule_history_clear_text),
            confirmLabel = stringResource(R.string.action_confirm),
            icon = Icons.Rounded.History,
            destructive = true,
            onDismiss = { confirmClearHistory = false },
            onConfirm = {
                viewModel.clearScheduleHistory()
                confirmClearHistory = false
            },
        )
    }

    if (editingWeek) {
        WeekHoursDialog(
            work = work,
            onDismiss = { editingWeek = false },
            onSave = { hours ->
                viewModel.editSchedule { it.copy(dailyByDay = hours) }
                editingWeek = false
            },
        )
    }

    if (editingVacation) {
        VacationAnnualDialog(
            settings = data.vacation,
            dayMinutes = VacationCalculator.typicalDay(data.work),
            onDismiss = { editingVacation = false },
            onConfirm = { minutes, carry ->
                update { it.copy(vacation = it.vacation.copy(annualMinutes = minutes, carryOver = carry)) }
                editingVacation = false
            },
        )
    }

    if (editingPatron) {
        DayOfYearPickerDialog(
            title = stringResource(R.string.settings_patron),
            initial = data.work.patronDay,
            icon = Icons.Rounded.LocationCity,
            subtitle = stringResource(R.string.settings_patron_sub),
            onDismiss = { editingPatron = false },
            onConfirm = { value ->
                update { it.copy(work = it.work.copy(patronDay = value)) }
                editingPatron = false
            },
            onClear = if (data.work.patronDay > 0) {
                {
                    update { it.copy(work = it.work.copy(patronDay = 0)) }
                    editingPatron = false
                }
            } else {
                null
            },
        )
    }

    editingDuration?.let { field ->
        val (title, initial, max) = when (field) {
            DurationField.DAILY -> Triple(R.string.settings_daily, work.dailyMinutes, 16)
            DurationField.MINIMUM -> Triple(R.string.settings_minimum, work.minDailyMinutes, 16)
            DurationField.MIN_BREAK -> Triple(R.string.settings_min_break, work.minBreakMinutes, 4)
            DurationField.BALANCE -> Triple(R.string.settings_balance, ledger.balance, 300)
        }
        DurationPickerDialog(
            title = stringResource(title),
            subtitle = if (field == DurationField.BALANCE) stringResource(R.string.bank_set_sub) else null,
            initialMinutes = initial,
            maxHours = max,
            allowNegative = field == DurationField.BALANCE,
            icon = when (field) {
                DurationField.DAILY -> Icons.Rounded.Schedule
                DurationField.MINIMUM -> Icons.Rounded.HourglassBottom
                DurationField.MIN_BREAK -> Icons.Rounded.Coffee
                DurationField.BALANCE -> Icons.Rounded.Savings
            },
            onDismiss = { editingDuration = null },
            onConfirm = { value ->
                when (field) {
                    DurationField.BALANCE -> viewModel.setBalance(value)
                    DurationField.DAILY, DurationField.MINIMUM -> viewModel.editSchedule { it.withDuration(field, value) }
                    else -> update { it.copy(work = it.work.withDuration(field, value)) }
                }
                editingDuration = null
            },
        )
    }

    if (addingShift || editingShift != null) {
        val initial = editingShift
        ShiftEditorDialog(
            initial = initial,
            onDismiss = {
                addingShift = false
                editingShift = null
            },
            onSave = { shift ->
                viewModel.saveShiftTemplate(shift)
                addingShift = false
                editingShift = null
            },
            onDelete = if (initial != null && work.shifts.size > 1) {
                {
                    viewModel.deleteShiftTemplate(initial.id)
                    editingShift = null
                }
            } else {
                null
            },
        )
    }

    if (editingName) {
        TextInputDialog(
            title = stringResource(R.string.settings_name),
            initial = data.profile.name,
            label = stringResource(R.string.settings_name_label),
            icon = Icons.Rounded.Badge,
            onDismiss = { editingName = false },
            onConfirm = { name ->
                update { it.copy(profile = it.profile.copy(name = name)) }
                editingName = false
            },
        )
    }

    if (choosingExpiry) {
        StaccoDialog(onDismiss = { choosingExpiry = false }) {
            Text(stringResource(R.string.settings_expiry), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.settings_expiry_dialog), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            ExpiryPeriod.entries.forEach { period ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .bouncyClickable(pressed = 0.98f) {
                            update { it.copy(bank = it.bank.copy(positiveExpiry = period)) }
                            choosingExpiry = false
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = data.bank.positiveExpiry == period, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(period.label), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    confirmImport?.let { text ->
        ConfirmDialog(
            title = stringResource(R.string.settings_import),
            text = stringResource(R.string.backup_import_confirm),
            confirmLabel = stringResource(R.string.settings_import_action),
            icon = Icons.Rounded.Download,
            destructive = true,
            onDismiss = { confirmImport = null },
            onConfirm = {
                confirmImport = null
                scope.launch {
                    val ok = viewModel.importJson(text)
                    viewModel.notify(if (ok) R.string.backup_import_ok else R.string.backup_error)
                }
            },
        )
    }
}

private fun Schedule.withDuration(field: DurationField, value: Int): Schedule = when (field) {
    // New daily hours keep the room the hour bank had: 8h/7h becomes 6h/5h, not 6h/6h.
    DurationField.DAILY -> value.coerceAtLeast(0).let { daily ->
        copy(dailyMinutes = daily, minDailyMinutes = (daily - (dailyMinutes - minDailyMinutes).coerceAtLeast(0)).coerceAtLeast(0))
    }
    DurationField.MINIMUM -> copy(minDailyMinutes = value.coerceIn(0, dailyMinutes))
    else -> this
}

private fun WorkSettings.withDuration(field: DurationField, value: Int): WorkSettings = when (field) {
    DurationField.DAILY -> copy(dailyMinutes = value.coerceAtLeast(0), minDailyMinutes = minDailyMinutes.coerceAtMost(value.coerceAtLeast(0)))
    DurationField.MINIMUM -> copy(minDailyMinutes = value.coerceIn(0, dailyMinutes))
    DurationField.MIN_BREAK -> copy(minBreakMinutes = value)
    DurationField.BALANCE -> this
}

val ExpiryPeriod.label: Int
    get() = when (this) {
        ExpiryPeriod.NEVER -> R.string.expiry_never
        ExpiryPeriod.MONTHLY -> R.string.expiry_monthly
        ExpiryPeriod.BIMONTHLY -> R.string.expiry_bimonthly
        ExpiryPeriod.QUARTERLY -> R.string.expiry_quarterly
        ExpiryPeriod.FOUR_MONTHLY -> R.string.expiry_four_monthly
        ExpiryPeriod.SEMIANNUAL -> R.string.expiry_semiannual
        ExpiryPeriod.ANNUAL -> R.string.expiry_annual
    }

@Composable
private fun PermissionBanner(text: String, action: String, onClick: () -> Unit, urgent: Boolean = true) {
    val container = if (urgent) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    val onContainer = if (urgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val accent = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val onAccent = if (urgent) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (urgent) Icons.Rounded.Warning else Icons.Rounded.AlarmOn, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = onContainer, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
            modifier = Modifier.align(Alignment.End),
        ) { Text(action) }
    }
}

@Composable
private fun AppearanceCard(data: StaccoData, onDynamic: (Boolean) -> Unit, onLanguage: () -> Unit) {
    val switcher = LocalThemeSwitcher.current
    StaccoCard {
        SectionLabel(stringResource(R.string.settings_appearance))
        Spacer(Modifier.height(12.dp))
        // Three in a row; with large fonts on a narrow phone one goes below ("Siste…" otherwise).
        AdaptiveRow {
            listOf(
                Triple(ThemeMode.SYSTEM, R.string.theme_system, Icons.Rounded.BrightnessAuto),
                Triple(ThemeMode.LIGHT, R.string.theme_light, Icons.Rounded.LightMode),
                Triple(ThemeMode.DARK, R.string.theme_dark, Icons.Rounded.DarkMode),
            ).forEach { (mode, label, icon) ->
                var center by remember { mutableStateOf(Offset.Unspecified) }
                OptionCard(
                    title = stringResource(label),
                    icon = icon,
                    selected = data.appearance.theme == mode,
                    onClick = {
                        if (data.appearance.theme != mode) switcher?.switch(mode, center)
                    },
                    modifier = Modifier
                        .onGloballyPositioned { center = it.boundsInRoot().center },
                    preview = { ThemePreview(mode, icon) },
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                icon = Icons.Rounded.Palette,
                title = stringResource(R.string.settings_dynamic),
                subtitle = stringResource(R.string.settings_dynamic_sub),
                checked = data.appearance.dynamicColor,
                onCheckedChange = onDynamic,
            )
        }
        LanguageRow(onChanged = onLanguage)
    }
}

/** Per-app language (Android 13+): the phone can stay in English while Stacco speaks Italian. */
@Composable
private fun LanguageRow(onChanged: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val manager = remember { context.getSystemService(LocaleManager::class.java) } ?: return
    var current by remember { mutableStateOf(manager.applicationLocales.toLanguageTags().substringBefore('-')) }
    Spacer(Modifier.height(4.dp))
    SettingRow(
        icon = Icons.Rounded.Language,
        title = stringResource(R.string.settings_language),
        subtitle = stringResource(R.string.settings_language_sub),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val options = listOf("" to stringResource(R.string.theme_system), "it" to "Italiano", "en" to "English")
        options.forEachIndexed { index, (tag, label) ->
            SegmentedButton(
                selected = current == tag,
                onClick = {
                    current = tag
                    manager.applicationLocales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                    // Notifications, widget and tile speak the new language too.
                    onChanged()
                },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun ThemePreview(mode: ThemeMode, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val light = Color(0xFFF5FAF5)
    val dark = Color(0xFF0E1411)
    val green = Color(0xFF3FA37A)
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(52.dp)) {
            when (mode) {
                ThemeMode.LIGHT -> drawCircle(light)
                ThemeMode.DARK -> drawCircle(dark)
                ThemeMode.SYSTEM -> {
                    drawArc(light, 90f, 180f, useCenter = true)
                    drawArc(dark, -90f, 180f, useCenter = true)
                }
            }
            drawCircle(green, radius = size.minDimension * 0.18f, center = Offset(size.width * 0.5f, size.height * 0.5f))
        }
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun DonateCard(onClick: () -> Unit) {
    // A few swings when the card shows up, then still (it kept Settings redrawing at 120 fps).
    val sway = rememberSway(amplitude = 9f, swingMillis = 900)
    val tilt by sway
    val scale = 1f + 0.1f * kotlin.math.abs(tilt) / 9f
    StaccoCard(
        color = Color.Transparent,
        border = false,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        onClick = onClick,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF1F6E52), Color(0xFF2F6B66), Color(0xFF3B6470))))
                .padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(54.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Coffee,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                rotationZ = tilt
                                scaleX = scale
                                scaleY = scale
                            },
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.donate_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(stringResource(R.string.donate_body), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@Composable
private fun AboutCard(onGithub: () -> Unit) {
    StaccoCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MorphBadge(
                icon = Icons.Rounded.WorkHistory,
                colors = listOf(MaterialTheme.colorScheme.primary, StaccoTheme.colors.auroraA),
                size = 72.dp,
            )
            Spacer(Modifier.height(10.dp))
            Text("Stacco", style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (BuildConfig.GITHUB_UPDATES) UpdateRow()
            SettingRow(icon = Icons.Rounded.Code, title = stringResource(R.string.about_github), subtitle = "github.com/Perruzz/Stacco", onClick = onGithub)
            SettingRow(icon = Icons.Rounded.AutoAwesome, title = stringResource(R.string.about_fonts), subtitle = stringResource(R.string.about_fonts_sub))
            SettingRow(icon = Icons.Rounded.DateRange, title = stringResource(R.string.about_privacy), subtitle = stringResource(R.string.about_privacy_sub))
        }
    }
}

/** "Check for updates" — or, once a newer version is known, a shortcut to it. */
@Composable
private fun UpdateRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf(UpdateChecker.cached(context)) }
    var result by remember { mutableStateOf<UpdateResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    val update = available
    SettingRow(
        icon = if (update != null) Icons.Rounded.NewReleases else Icons.Rounded.SystemUpdate,
        title = if (update != null) stringResource(R.string.update_available, update.version) else stringResource(R.string.update_check),
        subtitle = when {
            update != null -> stringResource(R.string.update_available_sub)
            checking -> stringResource(R.string.update_checking)
            result == UpdateResult.UpToDate -> stringResource(R.string.update_latest)
            result == UpdateResult.Failed -> stringResource(R.string.update_failed)
            else -> stringResource(R.string.update_check_sub)
        },
        tint = if (update != null) StaccoTheme.colors.gold else MaterialTheme.colorScheme.primary,
        onClick = {
            when {
                update != null -> openUrl(context, update.pageUrl)
                !checking -> {
                    checking = true
                    scope.launch {
                        val outcome = UpdateChecker.check(context, notify = false)
                        if (outcome is UpdateResult.Available) available = outcome.update
                        result = outcome
                        checking = false
                    }
                }
            }
        },
        trailing = if (checking) {
            { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
        } else {
            null
        },
    )
}

internal fun notificationsAllowed(context: Context): Boolean {
    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return granted && androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
}

internal fun exactAlarmsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

internal fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private fun openAppNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}


@Composable
private fun WorkModeCard(mode: WorkMode, onChange: (WorkMode) -> Unit) {
    StaccoCard {
        SectionLabel(stringResource(R.string.settings_mode))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OptionCard(
                title = stringResource(R.string.mode_fixed),
                icon = Icons.Rounded.Schedule,
                selected = mode == WorkMode.FIXED,
                onClick = { onChange(WorkMode.FIXED) },
                modifier = Modifier.weight(1f),
            )
            OptionCard(
                title = stringResource(R.string.mode_shifts),
                icon = Icons.Rounded.Bedtime,
                selected = mode == WorkMode.SHIFTS,
                onClick = { onChange(WorkMode.SHIFTS) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        AnimatedContent(targetState = mode, label = "mode-desc") { m ->
            Text(
                stringResource(if (m == WorkMode.SHIFTS) R.string.mode_shifts_desc else R.string.mode_fixed_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Turns the abstract "alert margin" into a concrete example. */
@Composable
private fun LeadExample(work: io.github.perruzz.stacco.domain.WorkSettings, lead: Int) {
    val flex = if (work.mode == WorkMode.SHIFTS) 0 else (work.dailyMinutes - work.minDailyMinutes).coerceAtLeast(0)
    val text = if (flex > 0) {
        val hours = 5 * flex
        stringResource(R.string.settings_lead_example, Fmt.duration(hours), Fmt.duration(flex), 5, 5 + lead)
    } else if (lead == 0) {
        stringResource(R.string.settings_lead_example_zero)
    } else {
        pluralStringResource(R.plurals.settings_lead_example_simple, lead, lead)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 54.dp, top = 2.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            .padding(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
