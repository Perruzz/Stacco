package io.github.perruzz.stacco.notify

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.localized
import io.github.perruzz.stacco.domain.AlertsState
import io.github.perruzz.stacco.domain.BankAlert
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.Periods
import io.github.perruzz.stacco.domain.RecoveryAlert
import io.github.perruzz.stacco.domain.TodaySnapshot
import io.github.perruzz.stacco.stacco
import io.github.perruzz.stacco.ui.util.Fmt
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Runs once a day (morning): warns about a negative balance before the month closes and about
 * positive hours that are going to expire at the end of the period.
 */
class BankAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = applicationContext.stacco.repository
        val data = repository.current()
        if (!data.onboardingDone) return Result.success()
        val snapshot = Engine.snapshot(data)
        AppSync.refresh(applicationContext, snapshot)
        if (!data.notifications.bankAlerts) return Result.success()

        val newState = BankAlertNotifier.notify(applicationContext, snapshot)
        if (newState != data.alerts) repository.update { it.copy(alerts = newState) }
        return Result.success()
    }

    companion object {
        private const val NAME = "stacco-bank-alerts"

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(8, 40)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<BankAlertWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

object BankAlertNotifier {

    /** Posts the due notifications and returns the updated bookkeeping. */
    fun notify(base: Context, s: TodaySnapshot): AlertsState {
        val context = base.localized()
        val locale = context.resources.configuration.locales[0]
        var state = s.data.alerts
        val today = s.date
        for (alert in s.alerts) {
            if (!alert.inWindow) continue
            when (alert) {
                is BankAlert.Negative -> {
                    val key = YearMonth.from(today).toString()
                    if (!due(state.negativeLastDate, state.negativePeriod, key, today, alert.officeDaysLeft, s.scheduled)) continue
                    val text = if (alert.officeDaysLeft > 0) {
                        context.resources.getQuantityString(R.plurals.alert_negative_plan, alert.officeDaysLeft, Fmt.duration(alert.perDay), alert.officeDaysLeft)
                    } else {
                        context.getString(R.string.alert_negative_last)
                    }
                    val builder = Notifications.base(context, Notifications.CHANNEL_BANK)
                        .setContentTitle(context.getString(R.string.notif_negative_title, Fmt.duration(-alert.balance), Fmt.dayMonth(alert.monthEnd, locale)))
                        .setContentText(text)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                        .setAutoCancel(true)
                    Notifications.post(context, Notifications.ID_NEGATIVE, builder)
                    state = state.copy(negativeLastDate = today.toString(), negativePeriod = key)
                }
                is BankAlert.Expiry -> {
                    val key = Periods.expiryKey(today, s.data.bank.positiveExpiry)
                    if (!due(state.expiryLastDate, state.expiryPeriod, key, today, alert.officeDaysLeft, s.scheduled)) continue
                    val text = when {
                        alert.maxPerDay <= 0 -> context.getString(R.string.alert_expiry_no_flex)
                        alert.atRisk -> context.resources.getQuantityString(R.plurals.alert_expiry_risk, alert.officeDaysLeft, alert.officeDaysLeft, Fmt.duration(alert.maxPerDay))
                        else -> context.resources.getQuantityString(R.plurals.alert_expiry_plan, alert.daysNeeded, alert.daysNeeded, Fmt.duration(alert.maxPerDay), alert.officeDaysLeft)
                    }
                    val builder = Notifications.base(context, Notifications.CHANNEL_BANK)
                        .setContentTitle(context.getString(R.string.notif_expiry_title, Fmt.duration(alert.balance), Fmt.dayMonth(alert.expiry, locale)))
                        .setContentText(text)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                        .setAutoCancel(true)
                    Notifications.post(context, Notifications.ID_EXPIRY, builder)
                    state = state.copy(expiryLastDate = today.toString(), expiryPeriod = key)
                }
            }
        }
        // On-call recovery hours that are going to be lost (the module must be on).
        val recovery = s.onCall?.alert
        if (recovery != null && recovery.inWindow) {
            val key = recovery.expiry.toString()
            if (due(state.recoveryLastDate, state.recoveryPeriod, key, today, recovery.officeDaysLeft, s.scheduled)) {
                val text = recoveryAlertText(context, recovery)
                val builder = Notifications.base(context, Notifications.CHANNEL_BANK)
                    .setContentTitle(context.getString(R.string.recovery_alert_title, Fmt.duration(recovery.minutes), Fmt.dayMonth(recovery.expiry, locale)))
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                Notifications.post(context, Notifications.ID_RECOVERY, builder)
                state = state.copy(recoveryLastDate = today.toString(), recoveryPeriod = key)
            }
        }
        return state
    }

    /** "3 office days left to take them as recovery", as in the app. */
    fun recoveryAlertText(context: Context, alert: RecoveryAlert): String =
        if (alert.officeDaysLeft > 0) {
            context.resources.getQuantityString(R.plurals.recovery_alert_days, alert.officeDaysLeft, alert.officeDaysLeft)
        } else {
            context.getString(R.string.recovery_alert_last)
        }

    /**
     * Not more than once a day; every other day while there is time, daily in the last two office days.
     * Weekends are skipped unless the deadline is imminent.
     */
    private fun due(lastDate: String?, lastPeriod: String?, period: String, today: LocalDate, officeDaysLeft: Int, workday: Boolean): Boolean {
        if (!workday && officeDaysLeft > 1) return false
        val last = lastDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (last == null || lastPeriod != period) return true
        if (last == today) return false
        return officeDaysLeft <= 2 || ChronoUnit.DAYS.between(last, today) >= 2
    }
}
