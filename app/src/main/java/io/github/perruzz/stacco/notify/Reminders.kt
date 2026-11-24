package io.github.perruzz.stacco.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/**
 * When a reminder (time to leave, a shift about to start) goes off. With the "Alarms & reminders"
 * permission it is to the minute, Doze included. Without it, which is the default on Android 14+
 * and what is left once the user revokes it, Android only grants inexact alarms: one may go off
 * up to three quarters of its distance late, an hour at most. So the alarm is asked for early
 * enough to go off by the reminder time, and set again, closer, each time it goes off early:
 * the reminder comes at most [EARLY_MS] early, never late, also in Doze and in battery saver.
 */
internal object ReminderTiming {

    /** Without exact alarms, a reminder this close to its time is posted rather than waited for again. */
    const val EARLY_MS = 2 * 60_000L

    /** An exact alarm may be handed over a moment before its time. */
    private const val EXACT_SLACK_MS = 5_000L

    /** Android 12+ delivers an inexact alarm within an hour of its time. */
    private const val MAX_DELAY_MS = 60 * 60_000L

    /** How late Android may deliver an inexact alarm, as a share of how far ahead it was set. */
    private const val DELAY_SHARE = 0.75

    sealed interface Step {
        /** Not yet: an alarm at [at]. */
        data class Wait(val at: Long) : Step

        /** Time to post the reminder. */
        data object Post : Step

        /** Too late for it to make sense. */
        data object Skip : Step
    }

    /**
     * What to do at [now] about a reminder due at [trigger] that makes sense until [until]
     * (all in epoch milliseconds). [early] allows posting it a little early, as an inexact alarm
     * would hardly come back closer; the app's own minute tick comes back on time.
     */
    fun step(now: Long, trigger: Long, until: Long, exact: Boolean, early: Boolean = true): Step = when {
        now >= until -> Step.Skip
        trigger - now > (if (exact || !early) EXACT_SLACK_MS else EARLY_MS) -> Step.Wait(if (exact) trigger else inexactTrigger(now, trigger))
        else -> Step.Post
    }

    /**
     * The time to ask an inexact alarm for, set at [now], for it to go off by [target] at the
     * latest: the latest t for which t + min(0.75 × (t − now), 1 h) is still not after [target].
     */
    fun inexactTrigger(now: Long, target: Long): Long {
        val left = target - now
        if (left <= 0) return target
        return maxOf(now + (left / (1 + DELAY_SHARE)).toLong(), target - MAX_DELAY_MS)
    }
}

/** Sets reminder alarms: exact ones when Android allows them, the inexact kind that also runs in Doze otherwise. */
internal object ReminderAlarm {

    fun exactAllowed(alarms: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /**
     * Sets [pi] for [at]. An exact alarm refused all the same (the permission revoked a moment
     * ago) falls back to an inexact one instead of being lost.
     */
    fun set(context: Context, at: Long, exact: Boolean, pi: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        if (exact && runCatching { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }.isSuccess) return
        val now = System.currentTimeMillis()
        runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, if (exact) ReminderTiming.inexactTrigger(now, at) else at, pi) }
    }

    fun cancel(context: Context, pi: PendingIntent) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pi)
    }
}
