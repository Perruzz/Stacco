package io.github.perruzz.stacco.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.perruzz.stacco.data.StaccoRepository
import io.github.perruzz.stacco.domain.DayPhase
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.TodaySnapshot
import io.github.perruzz.stacco.stacco
import io.github.perruzz.stacco.tile.StampTileService
import io.github.perruzz.stacco.widget.StaccoWidget
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
 * do not wake the phone: Android hands them over as soon as it is awake, which is when a widget
 * or the shade can be looked at.
 * - Every few minutes while a working day runs: the progress of the live notification moves on
 *   (and turns into overtime on time), and a reminder whose exact alarm was cancelled together
 *   with the permission is set again.
 * - When the working day changes (midnight, or the end of a night still under way): the widget
 *   and the tile would otherwise show yesterday until the app is opened.
 */
internal object RefreshAlarms {

    private const val PROGRESS_MS = 10 * 60_000L

    /** Android 12+ never delivers an inexact window shorter than this. */
    private const val DAY_WINDOW_MS = 10 * 60_000L

    private const val CODE_PROGRESS = 4
    private const val CODE_DAY = 5

    private val running = setOf(DayPhase.MORNING, DayPhase.ON_BREAK, DayPhase.AFTERNOON)

    private fun intent(context: Context, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, LiveRefreshReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** When the next progress refresh is due, or null when no working day runs. */
    internal fun nextProgress(s: TodaySnapshot, now: Long): Long? {
        val settings = s.data.notifications
        val working = s.isOffice && s.projection.isValid && s.projection.phase in running
        if (!working || !(settings.liveNotification || settings.exitReminder)) return null
        val exit = s.targetExit?.let { Notifications.epochMillis(s.date, it) }?.takeIf { it > now + 30_000L }
        return minOf(now + PROGRESS_MS, exit ?: Long.MAX_VALUE)
    }

    fun schedule(context: Context, s: TodaySnapshot, now: LocalDateTime = LocalDateTime.now(), nowMillis: Long = System.currentTimeMillis()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val progress = intent(context, CODE_PROGRESS)
        val next = nextProgress(s, nowMillis)
        if (next != null) runCatching { alarms.set(AlarmManager.RTC, next, progress) } else alarms.cancel(progress)
        val change = Engine.nextDayChange(s.data, now).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching { alarms.setWindow(AlarmManager.RTC, change, DAY_WINDOW_MS, intent(context, CODE_DAY)) }
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(intent(context, CODE_PROGRESS))
        alarms.cancel(intent(context, CODE_DAY))
    }
}
