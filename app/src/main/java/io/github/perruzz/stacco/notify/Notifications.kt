package io.github.perruzz.stacco.notify

import android.Manifest
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
import io.github.perruzz.stacco.domain.StampSlot
import java.time.LocalDate
import java.time.ZoneId

object Notifications {
    const val CHANNEL_EXIT = "stacco_exit"
    const val CHANNEL_LIVE = "stacco_live"
    const val CHANNEL_BANK = "stacco_bank"

    const val ID_EXIT = 1001
    const val ID_LIVE = 1002
    const val ID_NEGATIVE = 1003
    const val ID_EXPIRY = 1004
    const val ID_DAY_DONE = 1005

    const val COLOR = 0xFF1F6E52.toInt()

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_EXIT, context.getString(R.string.channel_exit), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_exit_desc)
                    enableVibration(true)
                },
                NotificationChannel(CHANNEL_LIVE, context.getString(R.string.channel_live), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_live_desc)
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_BANK, context.getString(R.string.channel_bank), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_bank_desc)
                },
            ),
        )
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

    fun epochMillis(date: LocalDate, minutes: Int): Long =
        date.atStartOfDay(ZoneId.systemDefault()).plusMinutes(minutes.toLong()).toInstant().toEpochMilli()

    fun base(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_stacco)
            .setColor(COLOR)
            .setContentIntent(openApp(context))
}
