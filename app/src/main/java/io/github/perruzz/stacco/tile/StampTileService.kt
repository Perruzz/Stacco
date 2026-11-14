package io.github.perruzz.stacco.tile

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.DayPhase
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.stacco
import io.github.perruzz.stacco.ui.today.action
import io.github.perruzz.stacco.ui.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** Quick Settings tile: stamp the next badge time without opening the app. */
class StampTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            withContext(Dispatchers.Default) { applicationContext.stacco.repository.stampNow(LocalDateTime.now()) }
            refresh()
        }
    }

    private fun refresh() {
        scope.launch {
            val data = withContext(Dispatchers.Default) { applicationContext.stacco.repository.current() }
            val tile = qsTile ?: return@launch
            if (!data.onboardingDone) {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = getString(R.string.tile_label)
                tile.updateTile()
                return@launch
            }
            val s = Engine.snapshot(data)
            val next = s.nextSlot
            when {
                s.isSmart || s.isAway || s.isTravel -> {
                    tile.state = Tile.STATE_UNAVAILABLE
                    tile.label = getString(R.string.tile_label)
                    tile.setSubtitleCompat(getString(R.string.type_smart).takeIf { s.isSmart } ?: getString(R.string.widget_today))
                }
                next == null -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = getString(R.string.tile_label)
                    tile.setSubtitleCompat(getString(R.string.hero_done))
                }
                else -> {
                    val running = s.projection.phase in setOf(DayPhase.MORNING, DayPhase.ON_BREAK, DayPhase.AFTERNOON)
                    tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    tile.label = getString(next.action)
                    tile.setSubtitleCompat(
                        when {
                            running -> getString(R.string.live_exit_at, Fmt.timeDay(s.targetExit))
                            s.shift != null -> "${s.shift.name} ${Fmt.time(s.shift.start)}"
                            else -> getString(R.string.hero_ready)
                        },
                    )
                }
            }
            tile.updateTile()
        }
    }

    private fun Tile.setSubtitleCompat(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = text
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, StampTileService::class.java)) }
        }
    }
}
