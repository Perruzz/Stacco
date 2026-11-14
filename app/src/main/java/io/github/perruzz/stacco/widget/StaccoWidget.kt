package io.github.perruzz.stacco.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.perruzz.stacco.MainActivity
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.DayPhase
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.TodaySnapshot
import io.github.perruzz.stacco.stacco
import io.github.perruzz.stacco.ui.theme.DarkColors
import io.github.perruzz.stacco.ui.theme.LightColors
import io.github.perruzz.stacco.ui.today.action
import io.github.perruzz.stacco.ui.today.label
import io.github.perruzz.stacco.ui.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private val WidgetColors = ColorProviders(light = LightColors, dark = DarkColors)

/** Home screen widget: today's exit time, the hour bank and a one-tap stamp button. */
class StaccoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.stacco.repository
        provideContent {
            val data by repository.data.collectAsState(initial = null)
            val snapshot = data?.takeIf { it.onboardingDone }?.let { Engine.snapshot(it) }
            GlanceTheme(colors = WidgetColors) {
                WidgetBody(snapshot, ready = data != null)
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun refresh(context: Context) {
            val app = context.applicationContext
            scope.launch { runCatching { StaccoWidget().updateAll(app) } }
        }
    }
}

class StaccoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StaccoWidget()
}

class StampWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.stacco.repository.stampNow(LocalDateTime.now())
        StaccoWidget().updateAll(context)
    }
}

@Composable
private fun WidgetBody(s: TodaySnapshot?, ready: Boolean) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(colors.widgetBackground)
            .cornerRadius(28.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_stat_stacco),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.primary),
                modifier = GlanceModifier.size(18.dp),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                "Stacco",
                style = TextStyle(color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.defaultWeight())

        val (label, big) = when {
            s == null -> (if (ready) context.getString(R.string.widget_setup) else "") to "Stacco"
            s.type == DayType.SMART -> context.getString(R.string.widget_today) to context.getString(R.string.type_smart)
            s.type == DayType.TRAVEL -> context.getString(R.string.widget_today) to context.getString(R.string.type_travel)
            s.isAway -> context.getString(R.string.widget_today) to context.getString(s.type.label)
            s.isRestDay && !s.record.hasStamps -> context.getString(R.string.widget_today) to context.getString(R.string.type_rest)
            s.projection.phase == DayPhase.NOT_STARTED -> context.getString(R.string.hero_ready) to Fmt.time(null)
            s.projection.phase == DayPhase.DONE -> context.getString(R.string.hero_done) to
                (s.projection.result?.delta?.let { Fmt.duration(it, signed = true) } ?: "✓")
            s.projection.phase == DayPhase.ON_BREAK -> context.getString(R.string.widget_break) to
                Fmt.time(s.projection.earliestReturn ?: s.targetExit)
            s.projection.phase == DayPhase.NOT_STARTED && s.shift != null -> s.shift.name to
                "${Fmt.time(s.shift.start)}–${Fmt.time(s.shift.end)}"
            else -> context.getString(R.string.widget_exit) to Fmt.timeDay(s.targetExit)
        }
        if (label.isNotEmpty()) {
            Text(label, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
        Text(big, style = TextStyle(color = colors.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        if (s != null) {
            Text(
                context.getString(R.string.widget_bank, Fmt.duration(s.ledger.balance, signed = true)),
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }

        val next = s?.nextSlot
        val canStamp = s != null && next != null && !s.isSmart && !s.isAway && !s.isTravel
        if (canStamp) {
            Spacer(GlanceModifier.height(8.dp))
            Button(
                text = context.getString(next.action),
                onClick = actionRunCallback<StampWidgetAction>(),
                colors = ButtonDefaults.buttonColors(backgroundColor = colors.primary, contentColor = colors.onPrimary),
                modifier = GlanceModifier.fillMaxWidth(),
            )
        }
    }
}
