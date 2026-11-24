package io.github.perruzz.stacco.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import io.github.perruzz.stacco.BuildConfig
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.localized
import io.github.perruzz.stacco.domain.DayPhase
import io.github.perruzz.stacco.domain.ShiftTemplate
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.domain.TodaySnapshot
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.stacco
import io.github.perruzz.stacco.ui.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Runs [block] off the main thread while keeping the broadcast alive. A failure is logged, not
 * thrown: an exception escaping a background broadcast would crash the app with nobody using it.
 */
internal fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.w("Stacco", "${javaClass.simpleName} failed", e)
        } finally {
            pending.finish()
        }
    }
}

/**
 * The "time to clock off" reminder. Its alarm goes off at the reminder time (see [ReminderTiming]
 * for the case without exact alarms), and every refresh of the app checks it again, so it is not
 * lost when an alarm is (after a reboot, a revoked permission, an alarm Android held back). It is
 * remembered once shown, never shown twice for the same time, and taken back from the shade once
 * it no longer fits: its button would stamp an exit that is not the one it speaks of.
 */
object ExitAlarmScheduler {

    private const val PREFS = "exit_reminder"

    /** The last reminder shown: day and minute it was due. */
    private const val KEY_LAST = "last"

    /** The reminder that is (as far as the app knows) in the shade. */
    private const val KEY_SHOWING = "showing"

    /** Still worth posting this long after its time: an alarm held back, the app opened after a reboot. */
    private const val LATE_MS = 60 * 60_000L

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, ExitAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun active(s: TodaySnapshot): Boolean {
        val settings = s.data.notifications
        return settings.exitReminder && s.isOffice && s.projection.isValid && s.projection.required > 0 &&
            s.targetExit != null && s.projection.phase in setOf(DayPhase.MORNING, DayPhase.ON_BREAK, DayPhase.AFTERNOON)
    }

    /** Identifies one reminder: same day and same trigger minute. */
    internal fun key(s: TodaySnapshot): String? =
        s.targetExit?.takeIf { active(s) }?.let { "${s.date}@${it - s.data.notifications.exitReminderLeadMinutes}" }

    /** [early]: see [ReminderTiming.step]. */
    fun schedule(context: Context, s: TodaySnapshot, early: Boolean = true, now: Long = System.currentTimeMillis()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        val prefs = prefs(context)
        val key = key(s)
        // One in the shade for another time (the exit moved) or for a day that is over is out of date.
        val showing = prefs.getString(KEY_SHOWING, null)
        if (showing != null && showing != key) {
            Notifications.cancel(context, Notifications.ID_EXIT)
            prefs.edit { remove(KEY_SHOWING) }
        }
        val target = s.targetExit
        if (key == null || target == null) {
            alarms.cancel(pi)
            return
        }
        if (prefs.getString(KEY_LAST, null) == key) return
        val trigger = Notifications.epochMillis(s.date, target - s.data.notifications.exitReminderLeadMinutes)
        val exact = ReminderAlarm.exactAllowed(alarms)
        when (val step = ReminderTiming.step(now, trigger, trigger + LATE_MS, exact, early)) {
            is ReminderTiming.Step.Wait -> ReminderAlarm.set(context, step.at, exact, pi)
            ReminderTiming.Step.Post -> {
                alarms.cancel(pi)
                show(context, s, key)
            }
            ReminderTiming.Step.Skip -> alarms.cancel(pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
        Notifications.cancel(context, Notifications.ID_EXIT)
        prefs(context).edit { remove(KEY_SHOWING) }
    }

    private fun show(base: Context, s: TodaySnapshot, key: String) {
        val context = base.localized()
        val target = s.targetExit ?: return
        // Rounded: an alarm a few seconds early still says "in 5 min", not 6.
        val minutesLeft = Math.floorDiv(s.secondsTo(target) + 30, 60)
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
            .addAction(R.drawable.ic_stat_stacco, context.getString(R.string.notif_action_exit), Notifications.stampAction(context, StampSlot.EXIT, s.date))
        // With notifications off nothing is remembered: allowed again in time, it still comes.
        if (Notifications.post(context, Notifications.ID_EXIT, builder)) {
            prefs(context).edit {
                putString(KEY_LAST, key)
                putString(KEY_SHOWING, key)
            }
        }
    }
}

class ExitAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = async {
        AppSync.sync(context)
    }
}

/** Shift mode: a reminder before the next planned shift starts, taken back once it has started. */
object ShiftStartScheduler {

    private const val PREFS = "shift_reminder"
    private const val KEY_LAST = "last"
    private const val KEY_SHOWING = "showing"

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        2,
        Intent(context, ShiftStartReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The next shift (today or tomorrow) not stamped yet, with its date. */
    fun nextShift(data: StaccoData, now: LocalDateTime): Pair<LocalDate, ShiftTemplate>? {
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

    /** Identifies one reminder: the day, the shift and its start. */
    private fun key(date: LocalDate, shift: ShiftTemplate) = "$date@${shift.id}@${shift.start}"

    /** [early]: see [ReminderTiming.step]. */
    fun schedule(
        context: Context,
        data: StaccoData,
        early: Boolean = true,
        now: LocalDateTime = LocalDateTime.now(),
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        val prefs = prefs(context)
        val settings = data.notifications
        // Shifts are looked up day by day: the mode may change on a given date (contract history).
        val next = if (settings.shiftStartReminder) nextShift(data, now) else null
        val key = next?.let { (date, shift) -> key(date, shift) }
        // The shift has started or has been stamped: its reminder has done its job.
        val showing = prefs.getString(KEY_SHOWING, null)
        if (showing != null && showing != key) {
            Notifications.cancel(context, Notifications.ID_SHIFT_START)
            prefs.edit { remove(KEY_SHOWING) }
        }
        if (next == null || key == null) {
            alarms.cancel(pi)
            return
        }
        if (prefs.getString(KEY_LAST, null) == key) return
        val (date, shift) = next
        val start = Notifications.epochMillis(date, shift.start)
        val trigger = Notifications.epochMillis(date, shift.start - settings.shiftStartLeadMinutes)
        val exact = ReminderAlarm.exactAllowed(alarms)
        when (val step = ReminderTiming.step(nowMillis, trigger, start, exact, early)) {
            is ReminderTiming.Step.Wait -> ReminderAlarm.set(context, step.at, exact, pi)
            ReminderTiming.Step.Post -> {
                alarms.cancel(pi)
                show(context, shift, start, nowMillis, key)
            }
            ReminderTiming.Step.Skip -> alarms.cancel(pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
        Notifications.cancel(context, Notifications.ID_SHIFT_START)
        prefs(context).edit { remove(KEY_SHOWING) }
    }

    private fun show(base: Context, shift: ShiftTemplate, start: Long, now: Long, key: String) {
        val context = base.localized()
        // Rounded: an alarm a few seconds early still says "in 30 minutes", not 31.
        val minutesLeft = ((start - now + 30_000) / 60_000).toInt().coerceAtLeast(1)
        val builder = Notifications.base(context, Notifications.CHANNEL_EXIT)
            .setContentTitle(context.getString(R.string.notif_shift_title, shift.name, Fmt.time(shift.start)))
            .setContentText(context.resources.getQuantityString(R.plurals.notif_shift_starts, minutesLeft, minutesLeft))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            // Gone by itself when the shift starts, even if the app is not running then.
            .setTimeoutAfter((start - now).coerceAtLeast(60_000L))
        if (Notifications.post(context, Notifications.ID_SHIFT_START, builder)) {
            prefs(context).edit {
                putString(KEY_LAST, key)
                putString(KEY_SHOWING, key)
            }
        }
    }
}

class ShiftStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = async {
        AppSync.sync(context)
    }
}

/** Handles the "Stamp" buttons of notifications. */
class StampActionReceiver : BroadcastReceiver() {
    override fun onReceive(base: Context, intent: Intent?) = async {
        val context = base.localized()
        val slot = intent?.getStringExtra(EXTRA_SLOT)?.let { runCatching { StampSlot.valueOf(it) }.getOrNull() }
        val date = intent?.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val repository = context.stacco.repository
        // A button that no longer fits the day (already stamped in the app, another day) stamps nothing:
        // the refresh below puts the notification back in line with the day.
        val stamp = slot?.let { repository.quickStamp(LocalDateTime.now(), it, date) }
        val data = repository.current()
        if (stamp?.slot == StampSlot.EXIT) {
            Notifications.cancel(context, Notifications.ID_EXIT)
            val result = WorkCalendar.from(data).result(stamp.date)
            if (result != null) {
                val builder = Notifications.base(context, Notifications.CHANNEL_BANK)
                    .setContentTitle(context.getString(R.string.notif_day_done_title))
                    .setContentText(context.getString(R.string.notif_day_done_text, Fmt.duration(result.worked), Fmt.duration(result.delta, signed = true)))
                    .setAutoCancel(true)
                Notifications.post(context, Notifications.ID_DAY_DONE, builder)
            }
        }
        AppSync.sync(context)
    }

    companion object {
        const val ACTION = "io.github.perruzz.stacco.action.STAMP_FROM_NOTIFICATION"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_DATE = "date"
    }
}

/** The live notification was swiped away (Android 14+ allows it): put it straight back. */
class LiveDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = async {
        AppSync.sync(context, updateWidget = false)
    }
}

/** Keeps alarms and notifications right after reboots, updates, clock and language changes. */
class SystemEventsReceiver : BroadcastReceiver() {
    private val handled = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_LOCALE_CHANGED,
        // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (Android 12+): the permission was granted.
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
    )

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action !in handled) return
        async {
            // The names of the notification categories, in the system settings, follow the language.
            if (action == Intent.ACTION_LOCALE_CHANGED) Notifications.createChannels(context)
            // Updated: "a new version is available" no longer is.
            if (action == Intent.ACTION_MY_PACKAGE_REPLACED && (!BuildConfig.GITHUB_UPDATES || UpdateChecker.cached(context) == null)) {
                Notifications.cancel(context, Notifications.ID_UPDATE)
            }
            AppSync.sync(context)
            BankAlertWorker.schedule(context)
        }
    }
}

/**
 * Brings the app back to refresh what lives outside it: every few minutes while a working day
 * runs, and when the day changes (see [RefreshAlarms]).
 */
class LiveRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = async {
        AppSync.sync(context)
    }
}
