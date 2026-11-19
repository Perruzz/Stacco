package io.github.perruzz.stacco.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.perruzz.stacco.MainActivity
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.localized
import io.github.perruzz.stacco.domain.StampSlot
import java.time.LocalDate
import java.time.ZoneId

object Notifications {
    const val CHANNEL_EXIT = "stacco_exit"

    /**
     * Default importance (but without sound or vibration) so the countdown also shows on the lock
     * screen: Pixels hide "silent" (low importance) notifications there by default. A channel's
     * importance cannot be raised once created, hence the new id replacing [LEGACY_CHANNEL_LIVE].
     */
    const val CHANNEL_LIVE = "stacco_live_v2"
    private const val LEGACY_CHANNEL_LIVE = "stacco_live"
    const val CHANNEL_BANK = "stacco_bank"
    const val CHANNEL_UPDATES = "stacco_updates"

    /** On-call interventions: the running timer (silent, on the lock screen too) and its result. */
    const val CHANNEL_ON_CALL = "stacco_on_call"

    const val ID_EXIT = 1001
    const val ID_LIVE = 1002
    const val ID_NEGATIVE = 1003
    const val ID_EXPIRY = 1004
    const val ID_DAY_DONE = 1005
    const val ID_SHIFT_START = 1006
    const val ID_UPDATE = 1007
    const val ID_INTERVENTION = 1008
    const val ID_INTERVENTION_DONE = 1009
    const val ID_RECOVERY = 1010

    /** The workday and the intervention timer each live in a group of their own: see [postAlone]. */
    const val ID_LIVE_SUMMARY = 1011
    const val ID_INTERVENTION_SUMMARY = 1012
    const val GROUP_LIVE = "stacco_live"
    const val GROUP_INTERVENTION = "stacco_intervention"

    const val COLOR = 0xFF1F6E52.toInt()

    fun createChannels(base: Context) {
        val context = base.localized()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_EXIT, context.getString(R.string.channel_exit), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_exit_desc)
                    enableVibration(true)
                },
                NotificationChannel(CHANNEL_LIVE, context.getString(R.string.channel_live), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_live_desc)
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_BANK, context.getString(R.string.channel_bank), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_bank_desc)
                },
                NotificationChannel(CHANNEL_UPDATES, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_updates_desc)
                },
                NotificationChannel(CHANNEL_ON_CALL, context.getString(R.string.channel_on_call), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_on_call_desc)
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            ),
        )
        manager.deleteNotificationChannel(LEGACY_CHANNEL_LIVE)
    }

    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canPost(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    /**
     * Posts an ongoing notification as the only child of a group of its own, with a silent summary
     * that Android never shows for a group of one. Android 16+ bundles two or more notifications of
     * an app that have no group (or a group without a summary) into one collapsed group: with an
     * intervention running during the working day, both timers and their buttons ended up hidden
     * behind it.
     */
    fun postAlone(context: Context, id: Int, summaryId: Int, group: String, channel: String, builder: NotificationCompat.Builder) {
        post(context, id, builder.setGroup(group))
        post(
            context,
            summaryId,
            base(context, channel)
                .setGroup(group)
                .setGroupSummary(true)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC),
        )
    }

    fun cancelAlone(context: Context, id: Int, summaryId: Int) {
        cancel(context, id)
        cancel(context, summaryId)
    }

    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun stampAction(context: Context, slot: StampSlot): PendingIntent = PendingIntent.getBroadcast(
        context,
        100 + slot.ordinal,
        Intent(context, StampActionReceiver::class.java)
            .setAction(StampActionReceiver.ACTION)
            .putExtra(StampActionReceiver.EXTRA_SLOT, slot.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * The instant of [minutes] (clock time, possibly past midnight) of [date]. Computed on the
     * local clock: on daylight-saving days midnight + N physical minutes would be an hour off.
     */
    fun epochMillis(date: LocalDate, minutes: Int): Long =
        date.atStartOfDay().plusMinutes(minutes.toLong()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun base(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_stacco)
            .setColor(COLOR)
            .setContentIntent(openApp(context))
}
