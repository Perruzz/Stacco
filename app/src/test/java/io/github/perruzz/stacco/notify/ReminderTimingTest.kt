package io.github.perruzz.stacco.notify

import io.github.perruzz.stacco.notify.ReminderTiming.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reminders without exact alarms (the default on Android 14+, or once the user revokes them):
 * Android may deliver an inexact alarm up to 3/4 of its distance late, an hour at most.
 */
class ReminderTimingTest {

    private val minute = 60_000L
    private val start = 1_795_000_000_000L

    /** The latest Android may deliver an inexact alarm set at [now] for [at]. */
    private fun latest(now: Long, at: Long) = at + minOf(((at - now) * 0.75).toLong(), 60 * minute)

    /** Follows the alarms of one reminder, each delivered by [deliver], until it is posted. */
    private fun follow(trigger: Long, deliver: (now: Long, at: Long) -> Long): Pair<Long, Int> {
        var now = start
        var alarms = 0
        while (true) {
            val step = ReminderTiming.step(now, trigger, trigger + 60 * minute, exact = false)
            if (step == Step.Post) return now to alarms
            val wait = step as Step.Wait
            assertTrue("an alarm in the past", wait.at >= now)
            now = deliver(now, wait.at)
            alarms++
            assertTrue("more than 10 alarms for one reminder", alarms <= 10)
        }
    }

    @Test
    fun `an exact reminder waits for its minute and comes then`() {
        val trigger = start + 300 * minute
        assertEquals(Step.Wait(trigger), ReminderTiming.step(start, trigger, trigger + 60 * minute, exact = true))
        assertTrue(ReminderTiming.step(trigger - 30_000, trigger, trigger + 60 * minute, exact = true) is Step.Wait)
        assertEquals(Step.Post, ReminderTiming.step(trigger, trigger, trigger + 60 * minute, exact = true))
        assertEquals(Step.Post, ReminderTiming.step(trigger + 59 * minute, trigger, trigger + 60 * minute, exact = true))
    }

    @Test
    fun `without exact alarms a reminder is never late, however far ahead it was set`() {
        for (ahead in listOf(1L, 3, 10, 45, 80, 140, 300, 540, 900)) {
            val trigger = start + ahead * minute
            // Android as late as it may be: the alarm still goes off by the reminder time.
            val (late, _) = follow(trigger) { now, at -> latest(now, at) }
            assertTrue("set $ahead min ahead: posted ${(late - trigger) / 1000}s late", late <= trigger)
            assertTrue(trigger - late <= ReminderTiming.EARLY_MS)
        }
    }

    @Test
    fun `an alarm that goes off early is set again closer, a few times at most`() {
        for (ahead in listOf(10L, 60, 300, 540, 900)) {
            val trigger = start + ahead * minute
            // The phone woke up for something else right at the start of every window.
            val (early, alarms) = follow(trigger) { _, at -> at }
            assertTrue("set $ahead min ahead: ${(trigger - early) / 1000}s early", trigger - early <= ReminderTiming.EARLY_MS)
            assertTrue("set $ahead min ahead: $alarms alarms", alarms <= 7)
        }
    }

    @Test
    fun `a reminder is dropped once it no longer makes sense`() {
        val trigger = start + 10 * minute
        assertEquals(Step.Skip, ReminderTiming.step(trigger + 60 * minute, trigger, trigger + 60 * minute, exact = false))
        assertEquals(Step.Skip, ReminderTiming.step(trigger + 2 * minute, trigger, trigger, exact = true))
    }

    @Test
    fun `an inexact alarm due now or past is asked for its own time`() {
        assertEquals(start, ReminderTiming.inexactTrigger(start, start))
        assertEquals(start - minute, ReminderTiming.inexactTrigger(start, start - minute))
    }

    @Test
    fun `the app's minute tick waits for the reminder's own minute`() {
        val trigger = start + 30 * minute
        // From an alarm: posted up to two minutes early, as another alarm could come back late.
        assertEquals(Step.Post, ReminderTiming.step(trigger - 90_000, trigger, trigger + 60 * minute, exact = false))
        // From the minute tick: the next tick comes in a minute, on time.
        assertTrue(ReminderTiming.step(trigger - 90_000, trigger, trigger + 60 * minute, exact = false, early = false) is Step.Wait)
        assertEquals(Step.Post, ReminderTiming.step(trigger + 200, trigger, trigger + 60 * minute, exact = false, early = false))
    }
}
