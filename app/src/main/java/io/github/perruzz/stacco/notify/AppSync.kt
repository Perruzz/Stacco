package io.github.perruzz.stacco.notify

import android.content.Context
import io.github.perruzz.stacco.data.StaccoRepository
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.TodaySnapshot
import io.github.perruzz.stacco.tile.StampTileService
import io.github.perruzz.stacco.widget.StaccoWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Keeps everything that lives outside the app UI (reminder alarm, live notification, widget,
 * quick settings tile) in sync with the data, for as long as the process is alive.
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
                    if (!data.onboardingDone) {
                        Notifications.cancel(app, Notifications.ID_LIVE)
                        Notifications.cancel(app, Notifications.ID_INTERVENTION)
                        return@collectLatest
                    }
                    val snapshot = Engine.snapshot(data)
                    val dataChanged = data !== lastData
                    lastData = data
                    refresh(app, snapshot, updateWidget = dataChanged || minute % 15 == 0L)
                }
        }
    }

    fun refresh(context: Context, snapshot: TodaySnapshot, updateWidget: Boolean = true) {
        runCatching { ExitAlarmScheduler.schedule(context, snapshot) }
        runCatching { ShiftStartScheduler.schedule(context, snapshot.data) }
        runCatching { LiveDayNotifier.update(context, snapshot) }
        runCatching { InterventionNotifier.update(context, snapshot) }
        if (updateWidget) {
            runCatching { StaccoWidget.refresh(context) }
            runCatching { StampTileService.requestUpdate(context) }
        }
    }
}
