package io.github.perruzz.stacco.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.currentState
import androidx.datastore.preferences.core.longPreferencesKey
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import io.github.perruzz.stacco.MainActivity
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.localized
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
            // Changed by every refresh: the day may have changed while the data did not.
            val tick = currentState(TICK)
            val snapshot = data?.takeIf { it.onboardingDone }?.let { Engine.snapshot(it) }
            // Glance hands us a context in the phone's language, not necessarily Stacco's.
            val localized = remember(data, tick) { context.localized() }
            GlanceTheme(colors = WidgetColors) {
                WidgetBody(snapshot, ready = data != null, context = localized)
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val TICK = longPreferencesKey("tick")

        /**
         * Draws the widgets again. A widget composed a moment ago is still live and would only
         * follow a change of the data: a new tick makes it count the day again too (midnight,
         * a new language).
         */
        fun refresh(context: Context) {
            val app = context.applicationContext
            scope.launch {
                runCatching {
                    val now = System.currentTimeMillis()
                    GlanceAppWidgetManager(app).getGlanceIds(StaccoWidget::class.java).forEach { id ->
                        updateAppWidgetState(app, id) { it[TICK] = now }
                    }
                    StaccoWidget().updateAll(app)
                }
            }
        }
    }
}

class StaccoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StaccoWidget()
}

class StampWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Only what the widget shows: a button left from yesterday, or a double tap, stamps nothing.
        context.stacco.repository.quickStamp(LocalDateTime.now())
        StaccoWidget().updateAll(context)
    }
}

/** The line above the big value and the value itself. */
private fun widgetTexts(s: TodaySnapshot?, ready: Boolean, context: Context): Pair<String, String> = when {
    s == null -> (if (ready) context.getString(R.string.widget_setup) else "") to "Stacco"
    s.type == DayType.SMART -> context.getString(R.string.widget_today) to context.getString(R.string.type_smart)
    s.type == DayType.TRAVEL -> context.getString(R.string.widget_today) to context.getString(R.string.type_travel)
    s.isAway -> context.getString(R.string.widget_today) to context.getString(s.type.label)
    s.isRestDay && !s.record.hasStamps -> context.getString(R.string.widget_today) to context.getString(R.string.type_rest)
    // Before the shift starts: which shift, and when.
    s.projection.phase == DayPhase.NOT_STARTED && s.shift != null -> s.shift.name to
        "${Fmt.time(s.shift.start)}–${Fmt.time(s.shift.end)}"
    s.projection.phase == DayPhase.NOT_STARTED -> context.getString(R.string.hero_ready) to Fmt.time(null)
    s.projection.phase == DayPhase.DONE -> context.getString(R.string.hero_done) to
        (s.projection.result?.delta?.let { Fmt.duration(it, signed = true) } ?: "✓")
    s.projection.phase == DayPhase.ON_BREAK -> context.getString(R.string.widget_break) to
        Fmt.time(s.projection.earliestReturn ?: s.targetExit)
    else -> context.getString(R.string.widget_exit) to Fmt.timeDay(s.targetExit)
}

@Composable
private fun WidgetBody(s: TodaySnapshot?, ready: Boolean, context: Context) {
    val colors = GlanceTheme.colors
    val size = LocalSize.current
    val (label, big) = widgetTexts(s, ready, context)
    val next = s?.quickStampSlot
    val action = next?.let { context.getString(it.action) }
    val short = next?.let { context.getString(it.label) }
    val fit = widgetFit(size.width, size.height, context.resources.configuration.fontScale, big, button = action, shortButton = short)
    val shape = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // The colour resource of the preview in the widget picker (res/layout/widget_preview.xml):
        // the same theme colour given as day and night values came out lighter on the Pixel launcher.
        GlanceModifier.background(R.color.widget_background).cornerRadius(28.dp)
    } else {
        // Before Android 12 a widget has no rounded corners of its own: a rounded shape, tinted.
        GlanceModifier.background(ImageProvider(R.drawable.widget_shape), colorFilter = ColorFilter.tint(colors.widgetBackground))
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .then(shape)
            .padding(fit.padding)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (fit.header) {
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
        }
        Spacer(GlanceModifier.defaultWeight())
        if (fit.label && label.isNotEmpty()) {
            Text(label, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
        Text(big, style = TextStyle(color = colors.onSurface, fontSize = fit.bigSp.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        if (fit.bank && s != null) {
            Text(
                context.getString(R.string.widget_bank, Fmt.duration(s.ledger.balance, signed = true)),
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        if (next != null) {
            Spacer(GlanceModifier.height(fit.gap))
            Button(
                // Too long for the button ("Start lunch break" with large fonts): the stamp's short name.
                text = (if (fit.longButton) action else short).orEmpty(),
                // Centred in so many words: with a style of its own, before Android 12 the text
                // went to the top left corner of the button.
                style = TextStyle(fontSize = fit.buttonSp.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                onClick = actionRunCallback<StampWidgetAction>(),
                colors = ButtonDefaults.buttonColors(backgroundColor = colors.primary, contentColor = colors.onPrimary),
                // A full touch target, whatever the size of the widget.
                modifier = GlanceModifier.fillMaxWidth().height(WIDGET_BUTTON_HEIGHT.dp),
                maxLines = 1,
            )
        }
    }
}
