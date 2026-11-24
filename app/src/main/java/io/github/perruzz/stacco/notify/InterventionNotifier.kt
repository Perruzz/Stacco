package io.github.perruzz.stacco.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.OnCall
import io.github.perruzz.stacco.domain.TodaySnapshot
import io.github.perruzz.stacco.localized
import io.github.perruzz.stacco.stacco
import io.github.perruzz.stacco.ui.util.Fmt
import java.time.LocalDateTime

/**
 * The ongoing notification of a running on-call intervention: a timer counting up, on the lock
 * screen too, with a "Stop" action, so that a night intervention is closed without opening the app.
 */
object InterventionNotifier {

    fun update(base: Context, s: TodaySnapshot) {
        val context = base.localized()
        val (date, intervention) = s.onCall?.running ?: run {
            Notifications.cancelAlone(context, Notifications.ID_INTERVENTION, Notifications.ID_INTERVENTION_SUMMARY)
            return
        }
        val started = Notifications.epochMillis(date, intervention.start) + intervention.startSecond * 1000L
        val builder = Notifications.base(context, Notifications.CHANNEL_ON_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle(context.getString(R.string.intervention_running))
            .setContentText(context.getString(R.string.intervention_notif_text, Fmt.time(intervention.start)))
            .setWhen(started)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(context.getString(R.string.intervention_chip))
            // Android 14+ lets ongoing notifications be swiped away: it comes back while the timer runs.
            .setDeleteIntent(InterventionReceiver.intent(context, InterventionReceiver.ACTION_REPOST))
            .addAction(R.drawable.ic_stat_stacco, context.getString(R.string.intervention_stop), InterventionReceiver.intent(context, InterventionReceiver.ACTION_STOP))
        Notifications.postAlone(
            context, Notifications.ID_INTERVENTION, Notifications.ID_INTERVENTION_SUMMARY, Notifications.GROUP_INTERVENTION, Notifications.CHANNEL_ON_CALL, builder,
        )
    }
}

/** "Stop" on the intervention notification; it also puts the notification back if swiped away. */
class InterventionReceiver : BroadcastReceiver() {
    override fun onReceive(base: Context, intent: Intent?) = async {
        val context = base.localized()
        val repository = context.stacco.repository
        if (intent?.action == ACTION_STOP) {
            val change = repository.stopIntervention(LocalDateTime.now())
            if (change != null) {
                val real = OnCall.realMinutes(change.date, change.intervention) ?: 0
                val builder = Notifications.base(context, Notifications.CHANNEL_ON_CALL)
                    .setContentTitle(context.getString(R.string.intervention_done_title))
                    .setContentText(context.getString(R.string.intervention_done, Fmt.duration(real), Fmt.duration(OnCall.credited(real))))
                    .setSilent(true)
                    .setAutoCancel(true)
                    .setTimeoutAfter(10 * 60_000L)
                Notifications.post(context, Notifications.ID_INTERVENTION_DONE, builder)
            }
        }
        AppSync.sync(context)
    }

    companion object {
        const val ACTION_STOP = "io.github.perruzz.stacco.action.STOP_INTERVENTION"
        const val ACTION_REPOST = "io.github.perruzz.stacco.action.REPOST_INTERVENTION"

        fun intent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            if (action == ACTION_STOP) 40 else 41,
            Intent(context, InterventionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
