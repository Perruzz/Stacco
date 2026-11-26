package io.github.perruzz.stacco.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.content.edit
import io.github.perruzz.stacco.data.StaccoRepository
import io.github.perruzz.stacco.domain.DayPhase
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.TodaySnapshot
import io.github.perruzz.stacco.stacco
import io.github.perruzz.stacco.tile.StampTileService
import io.github.perruzz.stacco.widget.StaccoWidget
import io.github.perruzz.stacco.widget.StaccoWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Keeps everything that lives outside the app UI (reminder alarms, live notifications, widget,
 * quick settings tile) in sync with the data: on every change and every minute while the process
 * is alive, and through [RefreshAlarms] when it is not.
 */
object AppSync {

    private val minutes = flow {
        while (true) {
            emit(System.currentTimeMillis() / 60_000L)
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    fun start(context: Context, repository: StaccoRepository, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch {
            var lastData: Any? = null
            combine(repository.data, minutes) { data, minute -> data to minute }
                .collectLatest { (data, minute) ->
                    val dataChanged = data !== lastData
                    lastData = data
                    if (!data.onboardingDone && !dataChanged) return@collectLatest
                    // Ticking every minute, a reminder can wait for its own minute.
                    sync(app, repository, updateWidget = dataChanged || minute % 15 == 0L, early = false)
                }
        }
    }

    /** One refresh at a time, each with the data as it is when its turn comes. */
    private val lock = Mutex()

    /**
     * Brings everything outside the app in line with the data as it is now. Refreshes run one at
     * a time and each reads the data when its turn comes: one computed a moment before a stamp
     * would otherwise put back the live notification of a day just closed, and an exact alarm
     * going off on the minute, together with the app's own minute tick, would post the same
     * reminder twice.
     */
    suspend fun sync(
        context: Context,
        repository: StaccoRepository = context.stacco.repository,
        updateWidget: Boolean = true,
        early: Boolean = true,
    ) {
        lock.withLock {
            val data = repository.current()
            if (data.onboardingDone) refresh(context, Engine.snapshot(data), updateWidget, early) else clear(context)
        }
    }

    /** [early]: a reminder due within a couple of minutes is posted now (see [ReminderTiming.step]). */
    private fun refresh(context: Context, snapshot: TodaySnapshot, updateWidget: Boolean, early: Boolean) {
        runCatching { ExitAlarmScheduler.schedule(context, snapshot, early) }
        runCatching { ShiftStartScheduler.schedule(context, snapshot.data, early) }
        runCatching { LiveDayNotifier.update(context, snapshot) }
        runCatching { InterventionNotifier.update(context, snapshot) }
        runCatching { RefreshAlarms.schedule(context, snapshot) }
        if (updateWidget) {
            runCatching { BankAlertNotifier.withdrawStale(context, snapshot) }
            runCatching { StaccoWidget.refresh(context) }
            runCatching { StampTileService.requestUpdate(context) }
        }
    }

    /** Before the setup and after a reset: nothing of a working day is left outside the app. */
    private fun clear(context: Context) {
        runCatching { Notifications.cancelAlone(context, Notifications.ID_LIVE, Notifications.ID_LIVE_SUMMARY) }
        runCatching { Notifications.cancelAlone(context, Notifications.ID_INTERVENTION, Notifications.ID_INTERVENTION_SUMMARY) }
        runCatching { ExitAlarmScheduler.cancel(context) }
        runCatching { ShiftStartScheduler.cancel(context) }
        runCatching { RefreshAlarms.cancel(context) }
        runCatching { StaccoWidget.refresh(context) }
        runCatching { StampTileService.requestUpdate(context) }
    }
}

/**
 * Alarms that bring the app back to refresh what lives outside it when nothing else would. They
 * do not wake the phone: one that falls due with the screen off is handed over the moment the
 * screen turns on (AlarmManagerService delivers the waiting non-waking alarms then), which is
 * when the lock screen, the shade or a widget can be looked at.
 * - While a working day runs: the bar of the live notification moves on (and turns into overtime
 *   on time), and a reminder whose exact alarm was cancelled together with the permission is set
 *   again. How often depends on what Android allows the app, see [pace].
 * - When the working day changes (midnight, or the end of a night still under way): the widget
 *   and the tile would otherwise show yesterday until the app is opened.
 *
 * A broadcast for the screen turning on would not help: it cannot be declared in the manifest,
 * and since Android 14 one registered by a cached app waits until the app is active again.
 */
internal object RefreshAlarms {

    /** The bar moves a minute at a time: coming back more often would show nothing new. */
    internal const val MINUTE_MS = 60_000L

    /** Battery saver on: the bar still moves, five times less often. */
    internal const val SAVER_MS = 5 * 60_000L

    /** Rationed by Android: six an hour leave the reminders their share of the ten allowed. */
    internal const val RATIONED_MS = 10 * 60_000L

    /** UsageStatsManager.STANDBY_BUCKET_ACTIVE and _WORKING_SET (Android 9+): the lower, the freer. */
    internal const val BUCKET_ACTIVE = 10
    internal const val BUCKET_WORKING_SET = 20

    /** With the bar moving every minute, the widget and the tile are still redrawn every ten. */
    private const val WIDGET_MS = 9 * 60_000L

    /** Android 12+ never delivers an inexact window shorter than this. */
    private const val DAY_WINDOW_MS = 10 * 60_000L

    private const val CODE_PROGRESS = 4
    private const val CODE_DAY = 5

    /** Behind an exact progress alarm: it goes off only if that one never does (see [schedule]). */
    private const val CODE_SAFETY = 6

    /** Set on a progress alarm of the minute-by-minute pace (see [LiveRefreshReceiver]). */
    internal const val EXTRA_FAST = "fast"

    private const val PREFS = "refresh_alarms"
    private const val KEY_WIDGET = "widget_at"

    private val running = setOf(DayPhase.MORNING, DayPhase.ON_BREAK, DayPhase.AFTERNOON)

    private fun intent(context: Context, code: Int, fast: Boolean = false): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, LiveRefreshReceiver::class.java).putExtra(EXTRA_FAST, fast),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** How often the progress comes back, and whether its alarm can be the exact kind. */
    internal data class Pace(val intervalMs: Long, val exact: Boolean) {
        val fast: Boolean get() = intervalMs < RATIONED_MS
    }

    /**
     * What Android lets the app do in the background, read at each refresh.
     * [exact]: exact alarms that also run in Doze ("Alarms & reminders", Android 12+).
     * [widget]: a Stacco widget on a home screen. [bucket]: the App Standby bucket, null before
     * Android 9, which has none.
     */
    internal data class Allowance(val exact: Boolean, val widget: Boolean, val bucket: Int?, val powerSave: Boolean) {
        companion object {
            fun read(context: Context, alarms: AlarmManager): Allowance = Allowance(
                exact = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarms.canScheduleExactAlarms(),
                widget = runCatching {
                    AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, StaccoWidgetReceiver::class.java)).isNotEmpty()
                }.getOrDefault(false),
                bucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    context.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket
                } else {
                    null
                },
                powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true,
            )
        }
    }

    /**
     * How often the app may come back while a working day runs. Android rations the alarms of an
     * app by its App Standby bucket, the non-waking ones included: 10 an hour in the "working
     * set", 2 in "frequent", 1 in "rare". Without exact alarms the reminders draw on the same
     * allowance, so the bar must not use it up: a burst of refreshes would hold back the "time
     * to leave" reminder for up to an hour. Never rationed, so the bar moves every minute:
     * - exact alarms allowed in Doze, which Android leaves out of the buckets (the reminders too);
     * - a widget on the home screen, which keeps the app in the active bucket at least, or an
     *   unrestricted battery (the exempted bucket), or Android 8, which has no buckets.
     * Null: no periodic refresh, the two alarms an hour or fewer are left to the reminders.
     */
    internal fun pace(a: Allowance, live: Boolean): Pace? {
        val bucket = a.bucket ?: BUCKET_ACTIVE
        val unrationed = a.exact || a.widget || a.bucket == null || bucket < BUCKET_ACTIVE
        val allowed = unrationed || bucket <= BUCKET_WORKING_SET
        return when {
            !allowed -> null
            // Without the live notification nothing moves: only the reminders' safety net is left.
            !live || !unrationed -> Pace(RATIONED_MS, exact = false)
            else -> Pace(if (a.powerSave) SAVER_MS else MINUTE_MS, exact = a.exact)
        }
    }

    /**
     * When the next progress refresh is due, or null when no working day runs: a second past the
     * minute (the bar moves with the countdown), and at the exit to turn into overtime on time.
     */
    internal fun nextProgress(s: TodaySnapshot, now: Long, intervalMs: Long): Long? {
        val settings = s.data.notifications
        val working = s.isOffice && s.projection.isValid && s.projection.phase in running
        if (!working || !(settings.liveNotification || settings.exitReminder)) return null
        val exit = s.targetExit?.let { Notifications.epochMillis(s.date, it) }?.takeIf { it > now + 30_000L }
        return minOf(now - now % MINUTE_MS + intervalMs + 1_000L, exit ?: Long.MAX_VALUE)
    }

    fun schedule(context: Context, s: TodaySnapshot, now: LocalDateTime = LocalDateTime.now(), nowMillis: Long = System.currentTimeMillis()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pace = pace(Allowance.read(context, alarms), s.data.notifications.liveNotification)
        val next = pace?.let { nextProgress(s, nowMillis, it.intervalMs) }
        val progress = intent(context, CODE_PROGRESS, fast = pace?.fast == true)
        val safety = intent(context, CODE_SAFETY)
        // Exact and allowed in Doze, yet not waking: with the screen off it waits for the phone to be awake.
        val exact = pace != null && next != null && pace.exact &&
            runCatching { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC, next, progress) }.isSuccess
        when {
            pace == null || next == null -> {
                alarms.cancel(progress)
                alarms.cancel(safety)
            }
            // Revoking "Alarms & reminders" removes the exact alarms of the app, this one and the
            // reminder's with it, and tells nobody: an inexact one, pushed on at every refresh,
            // brings both back (inexact) should the exact one stop.
            exact -> runCatching { alarms.set(AlarmManager.RTC, next + RATIONED_MS, safety) }
            else -> {
                alarms.cancel(safety)
                runCatching { alarms.set(AlarmManager.RTC, next, progress) }
            }
        }
        val change = Engine.nextDayChange(s.data, now).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching { alarms.setWindow(AlarmManager.RTC, change, DAY_WINDOW_MS, intent(context, CODE_DAY)) }
    }

    /** Whether a minute-by-minute refresh should redraw the widget and the tile too (every ten minutes). */
    fun widgetDue(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_WIDGET, 0L)
        val due = now - last >= WIDGET_MS || now < last
        if (due) prefs.edit { putLong(KEY_WIDGET, now) }
        return due
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(intent(context, CODE_PROGRESS))
        alarms.cancel(intent(context, CODE_DAY))
        alarms.cancel(intent(context, CODE_SAFETY))
    }
}
