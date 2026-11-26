package io.github.perruzz.stacco.notify

import android.app.usage.UsageStatsManager
import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.NotificationSettings
import io.github.perruzz.stacco.domain.ShiftTemplate
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.WorkMode
import io.github.perruzz.stacco.domain.WorkSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What brings the widget, the tile, the live notification and the reminders up to date while
 * the app is closed: day changes, progress refreshes, the reminder of a given exit, the daily
 * bank warnings.
 */
class OutsideRefreshTest {

    private val monday = LocalDate.of(2026, 11, 23)
    private val tuesday = monday.plusDays(1)
    private fun data(vararg days: Pair<LocalDate, DayRecord>, work: WorkSettings = WorkSettings(), notifications: NotificationSettings = NotificationSettings()) =
        StaccoData(onboardingDone = true, work = work, notifications = notifications, days = days.associate { (d, r) -> d.toString() to r })

    private fun millis(at: LocalDateTime) = Notifications.epochMillis(at.toLocalDate(), at.hour * 60 + at.minute)

    // --- The day the widget and the tile show ---

    @Test
    fun `an ordinary day changes at midnight`() {
        val d = data(monday to DayRecord(entry = 480, exit = 1020))
        assertEquals(tuesday.atStartOfDay(), Engine.nextDayChange(d, monday.atTime(18, 0)))
        // Also in the morning, with nothing stamped yet.
        assertEquals(tuesday.atStartOfDay(), Engine.nextDayChange(d, monday.atTime(0, 0)))
    }

    @Test
    fun `a night shift under way changes when its grace ends, or at its exit`() {
        val night = ShiftTemplate("n", "Notte", 21 * 60, 7 * 60)
        val work = WorkSettings(mode = WorkMode.SHIFTS, shifts = listOf(night))
        val running = data(monday to DayRecord(shiftId = "n", entry = 21 * 60), work = work)
        // Monday's night stays the working day until 7:00 + 4 h on Tuesday.
        assertEquals(tuesday.atTime(11, 0), Engine.nextDayChange(running, tuesday.atTime(2, 0)))
        assertEquals(monday, Engine.activeDay(running, tuesday.atTime(10, 59)).date)
        assertEquals(tuesday, Engine.activeDay(running, tuesday.atTime(11, 0)).date)
        // Past it, the next change is midnight.
        assertEquals(tuesday.plusDays(1).atStartOfDay(), Engine.nextDayChange(running, tuesday.atTime(11, 0)))
        val stamped = data(monday to DayRecord(shiftId = "n", entry = 21 * 60, exit = 7 * 60 + 1440), work = work)
        assertEquals(tuesday.plusDays(1).atStartOfDay(), Engine.nextDayChange(stamped, tuesday.atTime(7, 5)))
    }

    // --- Progress of the live notification, and the safety net of the reminders ---

    private val day = monday to DayRecord(entry = 480)
    private val minute = RefreshAlarms.MINUTE_MS
    private val rationed = RefreshAlarms.RATIONED_MS

    @Test
    fun `while the day runs the bar comes back just past each minute, and at the exit`() {
        val morning = monday.atTime(10, 0)
        val s = Engine.snapshot(data(day), morning)
        // From the middle of a minute: a second past the next one, as the countdown passes it too.
        assertEquals(millis(morning) + minute + 1_000L, RefreshAlarms.nextProgress(s, millis(morning) + 25_000L, minute))
        assertEquals(millis(morning) + rationed + 1_000L, RefreshAlarms.nextProgress(s, millis(morning), rationed))
        val exit = s.targetExit!!
        val close = monday.atStartOfDay().plusMinutes(exit - 3L)
        assertEquals(Notifications.epochMillis(monday, exit), RefreshAlarms.nextProgress(Engine.snapshot(data(day), close), millis(close), rationed))
        // In overtime it goes on.
        val late = monday.atStartOfDay().plusMinutes(exit + 20L)
        assertEquals(millis(late) + minute + 1_000L, RefreshAlarms.nextProgress(Engine.snapshot(data(day), late), millis(late), minute))
    }

    @Test
    fun `with the live notification off it still comes back for the reminder`() {
        val noLive = NotificationSettings(liveNotification = false)
        val at = monday.atTime(10, 0)
        assertNotNull(RefreshAlarms.nextProgress(Engine.snapshot(data(day, notifications = noLive), at), millis(at), rationed))
        val neither = NotificationSettings(liveNotification = false, exitReminder = false)
        assertNull(RefreshAlarms.nextProgress(Engine.snapshot(data(day, notifications = neither), at), millis(at), rationed))
    }

    @Test
    fun `nothing comes back for a day over, not started or away`() {
        val at = monday.atTime(18, 0)
        assertNull(RefreshAlarms.nextProgress(Engine.snapshot(data(monday to DayRecord(entry = 480, exit = 1020)), at), millis(at), minute))
        assertNull(RefreshAlarms.nextProgress(Engine.snapshot(data(), monday.atTime(7, 0)), millis(monday.atTime(7, 0)), minute))
        assertNull(RefreshAlarms.nextProgress(Engine.snapshot(data(monday to DayRecord(type = DayType.SMART)), at), millis(at), minute))
    }

    // --- How often Android lets the bar move with the app closed ---

    private fun allowance(exact: Boolean = false, widget: Boolean = false, bucket: Int? = UsageStatsManager.STANDBY_BUCKET_WORKING_SET, powerSave: Boolean = false) =
        RefreshAlarms.Allowance(exact = exact, widget = widget, bucket = bucket, powerSave = powerSave)

    private fun pace(a: RefreshAlarms.Allowance, live: Boolean = true) = RefreshAlarms.pace(a, live)

    @Test
    fun `the bar moves every minute when Android does not ration the app`() {
        val everyMinute = RefreshAlarms.Pace(minute, exact = false)
        // Exact alarms allowed in Doze are left out of the standby buckets, whatever the bucket.
        assertEquals(RefreshAlarms.Pace(minute, exact = true), pace(allowance(exact = true, bucket = UsageStatsManager.STANDBY_BUCKET_RARE)))
        // A widget on the home screen keeps the app active; an unrestricted battery exempts it; Android 8 has no buckets.
        assertEquals(everyMinute, pace(allowance(widget = true, bucket = UsageStatsManager.STANDBY_BUCKET_RARE)))
        assertEquals(everyMinute, pace(allowance(bucket = 5)))
        assertEquals(everyMinute, pace(allowance(bucket = null)))
        // Battery saver: every five minutes.
        assertEquals(RefreshAlarms.Pace(RefreshAlarms.SAVER_MS, exact = true), pace(allowance(exact = true, powerSave = true)))
        assertTrue(everyMinute.fast)
        assertTrue(RefreshAlarms.Pace(RefreshAlarms.SAVER_MS, exact = true).fast)
    }

    @Test
    fun `the buckets are those of Android`() {
        assertEquals(UsageStatsManager.STANDBY_BUCKET_ACTIVE, RefreshAlarms.BUCKET_ACTIVE)
        assertEquals(UsageStatsManager.STANDBY_BUCKET_WORKING_SET, RefreshAlarms.BUCKET_WORKING_SET)
    }

    @Test
    fun `rationed, the bar leaves the reminders their share of the alarms`() {
        // Six an hour, while the working set allows ten: the reminders keep the rest.
        for (bucket in listOf(UsageStatsManager.STANDBY_BUCKET_ACTIVE, UsageStatsManager.STANDBY_BUCKET_WORKING_SET)) {
            assertEquals(RefreshAlarms.Pace(rationed, exact = false), pace(allowance(bucket = bucket)))
        }
        // Two an hour or fewer: they are all the reminders'.
        for (bucket in listOf(UsageStatsManager.STANDBY_BUCKET_FREQUENT, UsageStatsManager.STANDBY_BUCKET_RARE, UsageStatsManager.STANDBY_BUCKET_RESTRICTED)) {
            assertNull("bucket $bucket", pace(allowance(bucket = bucket)))
        }
        // Without the live notification only the reminders' safety net is left, and rationed like them.
        assertEquals(RefreshAlarms.Pace(rationed, exact = false), pace(allowance(exact = true), live = false))
        assertNull(pace(allowance(bucket = UsageStatsManager.STANDBY_BUCKET_FREQUENT), live = false))
        assertFalse(RefreshAlarms.Pace(rationed, exact = false).fast)
    }

    // --- Which exit a reminder speaks of ---

    @Test
    fun `a reminder belongs to one exit time, and to no day that is over`() {
        val at = monday.atTime(10, 0)
        val key = ExitAlarmScheduler.key(Engine.snapshot(data(day), at))
        assertNotNull(key)
        // The exit moves (a longer break): the reminder already in the shade is out of date.
        val longBreak = monday to DayRecord(entry = 480, breakOut = 720, breakIn = 810)
        assertNotEquals(key, ExitAlarmScheduler.key(Engine.snapshot(data(longBreak), monday.atTime(14, 0))))
        assertNull(ExitAlarmScheduler.key(Engine.snapshot(data(monday to DayRecord(entry = 480, exit = 1020)), monday.atTime(17, 5))))
        assertNull(ExitAlarmScheduler.key(Engine.snapshot(data(day, notifications = NotificationSettings(exitReminder = false)), at)))
    }

    // --- Bank warnings ---

    @Test
    fun `bank warnings come once a day at most, more often close to the deadline`() {
        val period = "2026-11"
        fun due(last: LocalDate?, today: LocalDate, left: Int, workday: Boolean = true, lastPeriod: String? = period) =
            BankAlertNotifier.due(last?.toString(), lastPeriod, period, today, left, workday)
        assertTrue(due(null, monday, 10))
        assertFalse("twice the same day", due(monday, monday, 1))
        assertFalse("every other day while there is time", due(monday, tuesday, 5))
        assertTrue(due(monday, monday.plusDays(2), 5))
        assertTrue("daily in the last two office days", due(monday, tuesday, 2))
        assertFalse("not on a day off while there is time", due(null, monday.minusDays(1), 5, workday = false))
        assertTrue("on a day off when it is the last chance", due(null, monday.minusDays(1), 1, workday = false))
        assertTrue("a new period starts over", due(monday, monday, 5, lastPeriod = "2026-10"))
    }

    @Test
    fun `bank warnings stay in the morning and out of the night`() {
        assertEquals(monday.atTime(8, 40), BankAlertWorker.nextRun(monday.atTime(6, 0)))
        // A run just done (or one at any later hour) goes to the next morning: no drift through the day.
        assertEquals(tuesday.atTime(8, 40), BankAlertWorker.nextRun(monday.atTime(8, 41)))
        assertEquals(tuesday.atTime(8, 40), BankAlertWorker.nextRun(monday.atTime(23, 30)))
        assertFalse(BankAlertNotifier.daytime(monday.atTime(6, 59)))
        assertTrue(BankAlertNotifier.daytime(monday.atTime(7, 0)))
        assertTrue(BankAlertNotifier.daytime(monday.atTime(20, 59)))
        assertFalse(BankAlertNotifier.daytime(monday.atTime(21, 0)))
    }
}
