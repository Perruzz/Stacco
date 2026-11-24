package io.github.perruzz.stacco.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.data.day
import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.Intervention
import io.github.perruzz.stacco.domain.NotificationSettings
import io.github.perruzz.stacco.domain.OnCallSettings
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.domain.WorkSettings
import io.github.perruzz.stacco.localized
import io.github.perruzz.stacco.stacco
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What the app leaves outside itself while a working day runs: the live notifications, the
 * reminder of the exit and the stamp buttons. The app's own data is replaced for each test and
 * put back afterwards (on an emulator: never run on a phone in use).
 */
@RunWith(AndroidJUnit4::class)
class OutsideAppTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository get() = context.stacco.repository
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val text = context.localized()
    private lateinit var saved: StaccoData

    @Before
    fun setUp() = runBlocking {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        context.getSharedPreferences("exit_reminder", Context.MODE_PRIVATE).edit().clear().commit()
        saved = repository.current()
    }

    @After
    fun tearDown() = runBlocking {
        repository.update { saved }
        AppSync.sync(context)
    }

    private val now: LocalDateTime get() = LocalDateTime.now()
    private val minutes: Int get() = now.hour * 60 + now.minute

    /** Every day a working day of [daily] minutes, with no holidays in the way. */
    private fun data(today: DayRecord, daily: Int = 480, notifications: NotificationSettings = NotificationSettings(), onCall: Boolean = false) = StaccoData(
        onboardingDone = true,
        work = WorkSettings(dailyMinutes = daily, minDailyMinutes = minOf(daily, 420), workDays = (1..7).toSet(), italianHolidays = false),
        notifications = notifications,
        onCall = OnCallSettings(enabled = onCall),
        days = mapOf(LocalDate.now().toString() to today),
    )

    private suspend fun use(data: StaccoData) {
        repository.update { data }
        AppSync.sync(context)
    }

    private fun shown(id: Int): StatusBarNotification? = manager.activeNotifications.firstOrNull { it.id == id }

    private fun Notification.actionTitles(): List<String> = actions.orEmpty().map { it.title.toString() }

    /** Waits up to [ms] for [condition], for work done by a broadcast receiver. */
    private suspend fun eventually(ms: Long = 5_000, condition: suspend () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            delay(100)
        }
        return condition()
    }

    @Test
    fun theWorkdayAndAnInterventionKeepTheirOwnNotifications() = runBlocking {
        val entry = (minutes - 60).coerceAtLeast(0)
        val intervention = Intervention(id = 1L, start = (minutes - 10).coerceAtLeast(0))
        use(data(DayRecord(entry = entry, onCall = true, interventions = listOf(intervention)), onCall = true))
        // Android 16+ bundles notifications that have no group of their own a moment later.
        delay(2_000)
        val live = shown(Notifications.ID_LIVE)
        val running = shown(Notifications.ID_INTERVENTION)
        assertNotNull("the workday notification", live)
        assertNotNull("the intervention timer", running)
        assertNotNull(shown(Notifications.ID_LIVE_SUMMARY))
        assertNotNull(shown(Notifications.ID_INTERVENTION_SUMMARY))
        assertNotEquals(live!!.groupKey, running!!.groupKey)
        for (n in listOf(live, running)) {
            assertTrue(n.groupKey, "Aggregate" !in n.groupKey)
            assertTrue("ongoing", n.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        }
        // Their buttons are there, not hidden in a bundle.
        assertTrue(live.notification.actionTitles().toString(), text.getString(R.string.stamp_exit) in live.notification.actionTitles())
        assertEquals(listOf(text.getString(R.string.intervention_stop)), running.notification.actionTitles())
        // The countdown to the exit and the timer counting up.
        assertTrue(live.notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue(running.notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
    }

    @Test
    fun theExitReminderLeavesTheShadeOnceTheExitIsStamped() = runBlocking {
        // An hour's working day whose exit is two minutes away: the reminder (five minutes ahead) is due.
        assumeTrue("the working day would start yesterday", minutes >= 60)
        val entry = minutes - 58
        val settings = NotificationSettings(exitReminderLeadMinutes = 5)
        use(data(DayRecord(entry = entry), daily = 60, notifications = settings))
        val reminder = shown(Notifications.ID_EXIT)
        assertNotNull("the reminder", reminder)
        assertEquals(listOf(text.getString(R.string.notif_action_exit)), reminder!!.notification.actionTitles())
        // Not posted again for the same exit.
        Notifications.cancel(context, Notifications.ID_EXIT)
        AppSync.sync(context)
        assertNull(shown(Notifications.ID_EXIT))
        // Posted again, the exit stamped in the app: its button would stamp it a second time.
        context.getSharedPreferences("exit_reminder", Context.MODE_PRIVATE).edit().clear().commit()
        AppSync.sync(context)
        assertNotNull(shown(Notifications.ID_EXIT))
        use(data(DayRecord(entry = entry, exit = minutes), daily = 60, notifications = settings))
        assertNull("still in the shade", shown(Notifications.ID_EXIT))
        assertNull(shown(Notifications.ID_LIVE))
    }

    @Test
    fun aStampButtonOfAnotherDayStampsNothing() = runBlocking {
        val today = LocalDate.now()
        val entry = (minutes - 30).coerceAtLeast(0)
        use(data(DayRecord(entry = entry)))
        Notifications.stampAction(context, StampSlot.EXIT, today.minusDays(1)).send()
        assertDayUnchanged(today, entry)
        // Today's button does stamp the exit.
        Notifications.stampAction(context, StampSlot.EXIT, today).send()
        assertTrue("today's button", eventually { repository.current().day(today).exit != null })
        // The notification of the finished day replaces the live one.
        assertTrue(eventually { shown(Notifications.ID_LIVE) == null && shown(Notifications.ID_DAY_DONE) != null })
    }

    private suspend fun assertDayUnchanged(date: LocalDate, entry: Int) {
        assertTrueFor(1_500) { repository.current().day(date) == DayRecord(entry = entry) }
    }

    /** [condition] holds all along [ms], while a broadcast receiver could still change it. */
    private suspend fun assertTrueFor(ms: Long, condition: suspend () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            assertTrue(condition())
            delay(100)
        }
    }
}
