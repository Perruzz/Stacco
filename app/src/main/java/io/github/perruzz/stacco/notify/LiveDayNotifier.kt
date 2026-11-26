package io.github.perruzz.stacco.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.localized
import io.github.perruzz.stacco.domain.DayPhase
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.domain.TodaySnapshot
import io.github.perruzz.stacco.ui.util.Fmt

/**
 * The ongoing "workday" notification: a countdown to the exit, a segmented progress bar
 * (work / lunch / work) and quick stamp actions. On Android 16+ it is promoted to a Live Update,
 * so the countdown also lives in the status bar. It is shown on the lock screen too and, since
 * Android 14+ lets users swipe away even ongoing notifications, it comes straight back if
 * dismissed by mistake (it can be turned off in the app settings).
 */
object LiveDayNotifier {

    private const val WORK_COLOR = 0xFF2A8460.toInt()
    private const val BREAK_COLOR = 0xFFC8954C.toInt()
    private const val POINT_COLOR = 0xFFE9B53F.toInt()

    fun update(base: Context, s: TodaySnapshot) {
        val context = base.localized()
        val p = s.projection
        val target = s.targetExit
        val entry = s.record.entry
        val running = p.phase in setOf(DayPhase.MORNING, DayPhase.ON_BREAK, DayPhase.AFTERNOON)
        val active = s.data.notifications.liveNotification && s.isOffice && running && p.isValid &&
            target != null && entry != null
        if (!active) {
            Notifications.cancelAlone(context, Notifications.ID_LIVE, Notifications.ID_LIVE_SUMMARY)
            return
        }

        val overtime = s.nowMinutes >= target && p.phase != DayPhase.ON_BREAK
        val targetMillis = Notifications.epochMillis(s.date, target)
        val builder = Notifications.base(context, Notifications.CHANNEL_LIVE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setColorized(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    context, 3, Intent(context, LiveDismissReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setRequestPromotedOngoing(true)

        when {
            p.phase == DayPhase.ON_BREAK -> {
                val out = s.record.breakOut ?: s.nowMinutes
                val back = p.earliestReturn
                builder.setContentTitle(context.getString(R.string.live_break_title))
                    .setContentText(
                        if (back != null) {
                            context.getString(R.string.live_break_text, Fmt.time(back), Fmt.timeDay(target))
                        } else {
                            context.getString(R.string.live_exit_at, Fmt.timeDay(target))
                        },
                    )
                    .setWhen(Notifications.epochMillis(s.date, out))
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(false)
                    .setShortCriticalText(context.getString(R.string.live_chip_break))
                    .addAction(R.drawable.ic_stat_stacco, context.getString(R.string.stamp_break_in), Notifications.stampAction(context, StampSlot.BREAK_IN, s.date))
            }
            overtime -> {
                builder.setContentTitle(context.getString(R.string.live_overtime_title))
                    .setContentText(context.getString(R.string.live_overtime_text, Fmt.timeDay(target)))
                    .setWhen(targetMillis)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(false)
                    .setShortCriticalText(context.getString(R.string.live_chip_go))
                    .addAction(R.drawable.ic_stat_stacco, context.getString(R.string.stamp_exit), Notifications.stampAction(context, StampSlot.EXIT, s.date))
            }
            else -> {
                val shift = s.shift
                builder.setContentTitle(context.getString(R.string.live_exit_at, Fmt.timeDay(target)))
                    .setContentText(
                        when {
                            shift != null -> context.getString(R.string.live_text_shift, shift.name, Fmt.time(entry))
                            s.record.breakIn != null -> context.getString(R.string.live_text_back, Fmt.time(s.record.breakIn))
                            else -> context.getString(R.string.live_text_working, Fmt.time(entry), Fmt.duration(p.breakInfo.deducted))
                        },
                    )
                    .setWhen(targetMillis)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                if (s.record.breakOut == null && s.expectsBreak) {
                    builder.addAction(R.drawable.ic_stat_stacco, context.getString(R.string.stamp_break_out), Notifications.stampAction(context, StampSlot.BREAK_OUT, s.date))
                }
                builder.addAction(R.drawable.ic_stat_stacco, context.getString(R.string.stamp_exit), Notifications.stampAction(context, StampSlot.EXIT, s.date))
            }
        }

        // Segmented progress: morning work, lunch, afternoon work.
        val total = (target - entry).coerceAtLeast(1)
        val style = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_stat_stacco))
        val out = s.record.breakOut
        if (out != null && out in (entry + 1) until target) {
            val back = (s.record.breakIn ?: maxOf(s.nowMinutes, out + p.breakInfo.deducted)).coerceIn(out, target)
            style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(out - entry).setColor(WORK_COLOR))
            if (back > out) style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(back - out).setColor(BREAK_COLOR))
            if (target > back) style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(target - back).setColor(WORK_COLOR))
        } else {
            style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(total).setColor(WORK_COLOR))
        }
        val other = if (target == p.standardExit) p.bankExit else p.standardExit
        if (other != null && other in (entry + 1) until target) {
            style.addProgressPoint(NotificationCompat.ProgressStyle.Point(other - entry).setColor(POINT_COLOR))
        }
        style.setProgress((s.nowMinutes - entry).coerceIn(0, total))
        builder.setStyle(style)

        // Android has no progress that moves by itself (only the countdown does): the bar moves on
        // when the notification is posted again, by the minute tick of the app or, with the app
        // closed, by the refresh alarms of AppSync (as often as Android allows, see RefreshAlarms.pace).
        Notifications.postAlone(context, Notifications.ID_LIVE, Notifications.ID_LIVE_SUMMARY, Notifications.GROUP_LIVE, Notifications.CHANNEL_LIVE, builder)
    }
}
