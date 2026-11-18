package io.github.perruzz.stacco.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.localized
import io.github.perruzz.stacco.domain.DayPhase
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.domain.TodaySnapshot
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.stacco
import io.github.perruzz.stacco.ui.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Runs [block] off the main thread while keeping the broadcast alive. */
internal fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/**
 * The "time to clock off" reminder. Scheduled with AlarmManager (exact when allowed, otherwise a
 * short window) and remembered once shown, so it is never lost nor shown twice for the same time.
 */
object ExitAlarmScheduler {

    private const val PREFS = "exit_reminder"
    private const val KEY_LAST = "last"
    private const val OVERDUE_GRACE_MS = 20 * 60_000L

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, ExitAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun active(s: TodaySnapshot): Boolean {
        val settings = s.data.notifications
        return settings.exitReminder && s.isOffice && s.projection.isValid && s.projection.required > 0 &&
            s.targetExit != null && s.projection.phase in setOf(DayPhase.MORNING, DayPhase.ON_BREAK, DayPhase.AFTERNOON)
    }

    /** Identifies one reminder: same day and same trigger minute. */
    private fun key(s: TodaySnapshot): String? =
        s.targetExit?.let { "${s.date}@${it - s.data.notifications.exitReminderLeadMinutes}" }

    private fun alreadyShown(context: Context, key: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null) == key

    private fun markShown(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_LAST, key) }
    }

    fun schedule(context: Context, s: TodaySnapshot) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        val key = key(s)
        if (!active(s) || key == null) {
            alarms.cancel(pi)
            return
        }
        if (alreadyShown(context, key)) return
        val target = s.targetExit ?: return
        val triggerAt = Notifications.epochMillis(s.date, target - s.data.notifications.exitReminderLeadMinutes)
        val now = System.currentTimeMillis()
        when {
            triggerAt > now -> {
                val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
                runCatching {
                    if (exact) {
                        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    } else {
                        alarms.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 2 * 60_000L, pi)
                    }
                }
            }
            // The moment has come (an inexact alarm may still be waiting): remind right now.
            now - triggerAt < OVERDUE_GRACE_MS -> {
                alarms.cancel(pi)
                show(context, s)
            }
            else -> Unit
        }
    }

    /** Posts the reminder if it still makes sense. */
    fun show(base: Context, s: TodaySnapshot) {
        val context = base.localized()
        val key = key(s) ?: return
        val target = s.targetExit ?: return
        if (!active(s) || alreadyShown(context, key)) return
        val minutesLeft = target - s.nowMinutes
        val title = if (minutesLeft > 0) {
            context.getString(R.string.notif_exit_soon, minutesLeft)
        } else {
            context.getString(R.string.notif_exit_now)
        }
        // Time that will actually be credited: the break that is going to be deducted is already removed.
        val b = s.projection.breakInfo
        val credited = (s.projection.workedSoFar - (b.deducted - (b.actual ?: 0)).coerceAtLeast(0)).coerceAtLeast(0)
        val text = context.getString(R.string.notif_exit_text, Fmt.time(target), Fmt.duration(credited))
        val builder = Notifications.base(context, Notifications.CHANNEL_EXIT)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_stat_stacco, context.getString(R.string.notif_action_exit), Notifications.stampAction(context, StampSlot.EXIT))
        Notifications.post(context, Notifications.ID_EXIT, builder)
        markShown(context, key)
    }
}

class ExitAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = async {
        val s = Engine.snapshot(context.stacco.repository.current())
        ExitAlarmScheduler.show(context, s)
        AppSync.refresh(context, s)
    }
}

/** Shift mode: a reminder before the next planned shift starts. */
object ShiftStartScheduler {

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        2,
        Intent(context, ShiftStartReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The next shift (today or tomorrow) not stamped yet, with its date. */
    fun nextShift(data: io.github.perruzz.stacco.domain.StaccoData, now: LocalDateTime): Pair<java.time.LocalDate, io.github.perruzz.stacco.domain.ShiftTemplate>? {
        val calendar = WorkCalendar.from(data)
        val nowMinutes = now.hour * 60 + now.minute
        for (offset in 0..1L) {
            val date = now.toLocalDate().plusDays(offset)
            val shift = calendar.shift(date) ?: continue
            if (calendar.plannedStart(date) == null) continue
            if (data.days[date.toString()]?.entry != null) continue
            if (offset == 0L && shift.start <= nowMinutes) continue
            return date to shift
        }
        return null
    }

    fun schedule(context: Context, data: io.github.perruzz.stacco.domain.StaccoData, now: LocalDateTime = LocalDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        val settings = data.notifications
        // Shifts are looked up day by day: the mode may change on a given date (contract history).
        val next = if (settings.shiftStartReminder) nextShift(data, now) else null
        if (next == null) {
            alarms.cancel(pi)
            return
        }
        val (date, shift) = next
        val triggerAt = Notifications.epochMillis(date, shift.start - settings.shiftStartLeadMinutes)
        if (triggerAt <= System.currentTimeMillis()) {
            alarms.cancel(pi)
            return
        }
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        runCatching {
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarms.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 5 * 60_000L, pi)
            }
        }
    }
}

class ShiftStartReceiver : BroadcastReceiver() {
    override fun onReceive(base: Context, intent: Intent?) = async {
        val context = base.localized()
        val data = context.stacco.repository.current()
        val now = LocalDateTime.now()
        val (date, shift) = ShiftStartScheduler.nextShift(data, now) ?: return@async
        val minutesLeft = (Notifications.epochMillis(date, shift.start) - System.currentTimeMillis()) / 60_000L
        if (minutesLeft in 0..120) {
            val builder = Notifications.base(context, Notifications.CHANNEL_EXIT)
                .setContentTitle(context.getString(R.string.notif_shift_title, shift.name, Fmt.time(shift.start)))
                .setContentText(context.getString(R.string.notif_shift_text, minutesLeft.toInt().coerceAtLeast(1)))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
            Notifications.post(context, Notifications.ID_SHIFT_START, builder)
        }
        ShiftStartScheduler.schedule(context, data)
    }
}

/** Handles the "Stamp" buttons of notifications. */
class StampActionReceiver : BroadcastReceiver() {
    override fun onReceive(base: Context, intent: Intent?) = async {
        val context = base.localized()
        val slot = intent?.getStringExtra(EXTRA_SLOT)?.let { runCatching { StampSlot.valueOf(it) }.getOrNull() }
        val repository = context.stacco.repository
        val now = LocalDateTime.now()
        val stamp = repository.stampAt(now, slot)
        Notifications.cancel(context, Notifications.ID_EXIT)
        val data = repository.current()
        if (stamp?.slot == StampSlot.EXIT) {
            val calendar = WorkCalendar.from(data)
            val result = calendar.result(stamp.date)
            if (result != null) {
                val builder = Notifications.base(context, Notifications.CHANNEL_BANK)
                    .setContentTitle(context.getString(R.string.notif_day_done_title))
                    .setContentText(context.getString(R.string.notif_day_done_text, Fmt.duration(result.worked), Fmt.duration(result.delta, signed = true)))
                    .setAutoCancel(true)
                Notifications.post(context, Notifications.ID_DAY_DONE, builder)
            }
        }
        AppSync.refresh(context, Engine.snapshot(data))
    }

    companion object {
        const val ACTION = "io.github.perruzz.stacco.action.STAMP_FROM_NOTIFICATION"
        const val EXTRA_SLOT = "slot"
    }
}

/** The live notification was swiped away (Android 14+ allows it): put it straight back. */
class LiveDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = async {
        val data = context.stacco.repository.current()
        if (data.onboardingDone) LiveDayNotifier.update(context, Engine.snapshot(data))
    }
}

/** Keeps alarms and notifications right after reboots, updates and clock changes. */
class SystemEventsReceiver : BroadcastReceiver() {
    private val handled = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
    )

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in handled) return
        refresh(context)
    }

    private fun refresh(context: Context) = async {
        val data = context.stacco.repository.current()
        if (data.onboardingDone) AppSync.refresh(context, Engine.snapshot(data))
        BankAlertWorker.schedule(context)
    }
}

/** Periodic refresh of the live notification progress while a workday is running. */
class LiveRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = async {
        val data = context.stacco.repository.current()
        if (data.onboardingDone) AppSync.refresh(context, Engine.snapshot(data))
    }
}
