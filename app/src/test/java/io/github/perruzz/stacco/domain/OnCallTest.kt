package io.github.perruzz.stacco.domain

import io.github.perruzz.stacco.data.Backup
import io.github.perruzz.stacco.data.withDay
import io.github.perruzz.stacco.export.CsvLabels
import io.github.perruzz.stacco.export.MonthCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.random.Random

class OnCallTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun t(h: Int, m: Int = 0) = h * 60 + m
    private val h = 60
    private val rome = ZoneId.of("Europe/Rome")
    private var nextId = 1L
    private fun job(start: Int, end: Int?) = Intervention(id = nextId++, start = start, end = end)

    private fun onCall(days: Map<String, DayRecord>, adjustments: List<BankAdjustment> = emptyList()) = StaccoData(
        onboardingDone = true,
        onCall = OnCallSettings(enabled = true),
        vacation = VacationSettings(annualMinutes = 160 * h),
        days = days,
        adjustments = adjustments,
    )

    // --- Quarter hours, rounded up ------------------------------------------------------------

    @Test
    fun `interventions are credited by the quarter hour, rounded up`() {
        assertEquals(0, OnCall.credited(0))
        assertEquals(15, OnCall.credited(1))
        assertEquals(15, OnCall.credited(14))
        assertEquals(15, OnCall.credited(15))
        assertEquals(30, OnCall.credited(16))
        assertEquals(45, OnCall.credited(32))
        assertEquals(45, OnCall.credited(45))
        assertEquals(60, OnCall.credited(46))
        assertEquals(2 * h + 15, OnCall.credited(2 * h + 1))
    }

    @Test
    fun `each intervention is rounded on its own, across midnight and clock changes`() {
        val day = d("2026-11-18")
        // 21:40 → 22:12: 32 minutes, 45 credited.
        assertEquals(32, OnCall.realMinutes(day, job(t(21, 40), t(22, 12)), rome))
        assertEquals(45, OnCall.credited(day, job(t(21, 40), t(22, 12)), rome))
        // 23:30 → 00:40 of the next day: 70 minutes, 75 credited, all on the day it started.
        assertEquals(70, OnCall.realMinutes(day, job(t(23, 30), 1440 + t(0, 40)), rome))
        assertEquals(75, OnCall.credited(day, job(t(23, 30), 1440 + t(0, 40)), rome))
        // Two short ones: 15 + 15, not 30 rounded once.
        val two = onCall(mapOf("2026-11-18" to DayRecord(onCall = true, interventions = listOf(job(t(20), t(20, 10)), job(t(22), t(22, 10))))))
        assertEquals(30, RecoveryCalculator.compute(two, day, rome).balance)
        // A running timer earns nothing yet.
        assertNull(OnCall.realMinutes(day, job(t(23), null), rome))
        assertEquals(0, OnCall.credited(day, job(t(23), null), rome))
        // 01:30 → 03:30 on the spring-forward night is one real hour; on the autumn night three.
        assertEquals(60, OnCall.realMinutes(d("2026-03-29"), job(t(1, 30), t(3, 30)), rome))
        assertEquals(180, OnCall.realMinutes(d("2026-10-25"), job(t(1, 30), t(3, 30)), rome))
    }

    // --- Expiry: the end of the second month after -------------------------------------------

    @Test
    fun `hours expire at the end of the second month after the intervention`() {
        assertEquals(d("2027-01-31"), OnCall.expiryOf(d("2026-11-01")))
        assertEquals(d("2027-01-31"), OnCall.expiryOf(d("2026-11-30")))
        assertEquals(d("2027-02-28"), OnCall.expiryOf(d("2026-12-31")))
        assertEquals(d("2028-02-29"), OnCall.expiryOf(d("2027-12-15")))
        assertEquals(d("2027-03-31"), OnCall.expiryOf(d("2027-01-31")))
    }

    @Test
    fun `november hours can be taken until 31 january and are lost the day after`() {
        val data = onCall(mapOf("2026-11-18" to DayRecord(onCall = true, interventions = listOf(job(t(21, 40), t(22, 12))))))
        val lastDay = RecoveryCalculator.compute(data, d("2027-01-31"), rome)
        assertEquals(45, lastDay.balance)
        assertEquals(d("2027-01-31"), lastDay.nextExpiry?.expiry)
        assertEquals(45, lastDay.nextExpiry?.left)
        val after = RecoveryCalculator.compute(data, d("2027-02-01"), rome)
        assertEquals(0, after.balance)
        assertEquals(45, after.expired)
        assertNull(after.nextExpiry)
    }

    @Test
    fun `recovery takes the hours that expire first`() {
        val data = onCall(
            mapOf(
                "2026-11-10" to DayRecord(onCall = true, interventions = listOf(job(t(20), t(22)))), // 2h, until 31 Jan
                "2026-12-10" to DayRecord(onCall = true, interventions = listOf(job(t(20), t(22)))), // 2h, until 28 Feb
                "2027-01-12" to DayRecord(entry = t(8), exit = t(13), recoveryMinutes = 3 * h), // Tuesday: 3h taken
            ),
        )
        val january = RecoveryCalculator.compute(data, d("2027-01-20"), rome)
        assertEquals(1 * h, january.balance)
        assertEquals(listOf(YearMonth.of(2026, 12)), january.lots.map { it.month })
        assertEquals(1 * h, january.lots.single().left)
        // Nothing of November is left to expire; one hour of December is lost on 1 March.
        assertEquals(0, RecoveryCalculator.compute(data, d("2027-02-01"), rome).expired)
        val march = RecoveryCalculator.compute(data, d("2027-03-01"), rome)
        assertEquals(0, march.balance)
        assertEquals(1 * h, march.expired)
    }

    // --- Recovery covers the day: the hour bank stays as it is ----------------------------------

    private val bankAnchor = listOf(BankAdjustment(1, "2026-11-02", AdjustmentKind.SET_BALANCE, 2 * h))

    private fun earnedInNovember() = mapOf(
        // 19:00 → 23:30 (4h30) and 23:50 → 02:40 of the next night (2h50 → 3h).
        "2026-11-05" to DayRecord(onCall = true, interventions = listOf(job(t(19), t(23, 30)), job(t(23, 50), 1440 + t(2, 40)))),
        "2026-11-06" to DayRecord(onCall = true, interventions = listOf(job(t(18), t(21)))),
    )

    @Test
    fun `one hour worked plus seven of recovery is an even day and the bank does not move`() {
        // 8h office day: 08:00 → 09:00 worked, 7h of recovery.
        val days = earnedInNovember() + ("2026-11-18" to DayRecord(entry = t(8), exit = t(9), recoveryMinutes = 7 * h))
        val data = onCall(days, bankAnchor)
        val cal = WorkCalendar.from(data)
        val day = d("2026-11-18")
        assertEquals(1 * h, cal.required(day))
        assertEquals(0, cal.breakRules(day).min) // a 1h day has no lunch break
        assertEquals(0, cal.delta(day))
        assertEquals(2 * h, BankCalculator.compute(data, d("2026-11-30")).balance)
        // Vacation and permits are not touched.
        assertEquals(0, VacationCalculator.balance(data, d("2026-11-30")).used)
        // The account pays the 7h.
        assertEquals(7 * h, OnCall.recoveryCost(cal, day))
        val earned = RecoveryCalculator.compute(onCall(earnedInNovember()), day, rome).balance
        assertEquals(earned - 7 * h, RecoveryCalculator.compute(data, day, rome).balance)
    }

    @Test
    fun `with recovery staying longer adds nothing, leaving earlier costs the bank`() {
        val longer = onCall(earnedInNovember() + ("2026-11-18" to DayRecord(entry = t(8), exit = t(11), recoveryMinutes = 7 * h)), bankAnchor)
        assertEquals(0, WorkCalendar.from(longer).delta(d("2026-11-18")))
        assertEquals(2 * h, BankCalculator.compute(longer, d("2026-11-30")).balance)
        val shorter = onCall(earnedInNovember() + ("2026-11-18" to DayRecord(entry = t(8), exit = t(8, 30), recoveryMinutes = 7 * h)), bankAnchor)
        assertEquals(-30, WorkCalendar.from(shorter).delta(d("2026-11-18")))
        assertEquals(2 * h - 30, BankCalculator.compute(shorter, d("2026-11-30")).balance)
    }

    @Test
    fun `a whole day of recovery needs no stamps`() {
        val data = onCall(earnedInNovember() + ("2026-11-18" to DayRecord(recoveryMinutes = 8 * h)), bankAnchor)
        val cal = WorkCalendar.from(data)
        assertEquals(0, cal.required(d("2026-11-18")))
        assertNull(cal.delta(d("2026-11-18")))
        assertEquals(2 * h, BankCalculator.compute(data, d("2026-11-30")).balance)
        assertEquals(8 * h, OnCall.recoveryCost(cal, d("2026-11-18")))
    }

    @Test
    fun `recovery is only taken on office days and never beyond what the permit leaves`() {
        val data = onCall(
            mapOf(
                "2026-11-18" to DayRecord(leaveMinutes = 2 * h, recoveryMinutes = 7 * h), // 8h day: 2h permit, 6h recovery
                "2026-11-21" to DayRecord(recoveryMinutes = 4 * h), // Saturday
                "2026-11-19" to DayRecord(type = DayType.SMART, recoveryMinutes = 4 * h),
                "2026-12-08" to DayRecord(recoveryMinutes = 4 * h), // holiday
            ),
        )
        val cal = WorkCalendar.from(data)
        assertEquals(6 * h, OnCall.recoveryCost(cal, d("2026-11-18")))
        assertEquals(6 * h, OnCall.recoveryRoom(cal, d("2026-11-18")))
        assertEquals(0, cal.required(d("2026-11-18")))
        assertEquals(2 * h, VacationCalculator.balance(data, d("2026-12-31")).used)
        assertEquals(0, OnCall.recoveryCost(cal, d("2026-11-21")))
        assertEquals(0, OnCall.recoveryCost(cal, d("2026-11-19")))
        assertEquals(0, OnCall.recoveryCost(cal, d("2026-12-08")))
        assertEquals(0, OnCall.recoveryRoom(cal, d("2026-11-21")))
    }

    @Test
    fun `taking more than earned leaves the account in the red until new hours come`() {
        val data = onCall(
            mapOf(
                "2026-11-10" to DayRecord(entry = t(8), exit = t(14), recoveryMinutes = 2 * h),
                "2026-11-20" to DayRecord(onCall = true, interventions = listOf(job(t(20), t(23)))),
            ),
        )
        assertEquals(-2 * h, RecoveryCalculator.compute(data, d("2026-11-15"), rome).balance)
        val after = RecoveryCalculator.compute(data, d("2026-11-25"), rome)
        assertEquals(1 * h, after.balance)
        // What is left was earned in November.
        assertEquals(YearMonth.of(2026, 11), after.nextExpiry?.month)
        assertEquals(1 * h, after.nextExpiry?.left)
    }

    @Test
    fun `hours available on a day count what was earned and taken before it`() {
        val data = onCall(
            mapOf(
                "2026-11-10" to DayRecord(onCall = true, interventions = listOf(job(t(20), t(22)))),
                "2026-11-20" to DayRecord(entry = t(8), exit = t(15), recoveryMinutes = 1 * h),
            ),
        )
        assertEquals(0, RecoveryCalculator.availableOn(data, d("2026-11-05"), rome))
        assertEquals(2 * h, RecoveryCalculator.availableOn(data, d("2026-11-20"), rome))
        assertEquals(1 * h, RecoveryCalculator.availableOn(data, d("2026-11-21"), rome))
        assertEquals(0, RecoveryCalculator.availableOn(data, d("2027-02-01"), rome))
    }

    // --- Expiry warnings ------------------------------------------------------------------------

    @Test
    fun `expiring hours are warned about unless the recovery is already planned`() {
        val november = mapOf("2026-11-10" to DayRecord(onCall = true, interventions = listOf(job(t(20), t(22)))))
        val today = d("2027-01-26")
        val data = onCall(november)
        val ledger = RecoveryCalculator.compute(data, today, rome)
        val cal = WorkCalendar.from(data)
        val alert = AlertPlanner.planRecovery(ledger, today, todayDone = false, cal, data.work, data.bank)
        assertNotNull(alert)
        assertEquals(2 * h, alert!!.minutes)
        assertEquals(d("2027-01-31"), alert.expiry)
        assertEquals(1, alert.daysNeeded)
        assertEquals(4, alert.officeDaysLeft) // Tue 26 – Fri 29
        assertTrue(alert.inWindow)
        // Two hours of recovery already planned on the 28th: nothing will be lost.
        val planned = onCall(november + ("2027-01-28" to DayRecord(recoveryMinutes = 2 * h)))
        val plannedLedger = RecoveryCalculator.compute(planned, today, rome)
        assertEquals(2 * h, plannedLedger.planned)
        assertEquals(0, plannedLedger.nextExpiry?.atRisk)
        assertNull(AlertPlanner.planRecovery(plannedLedger, today, false, WorkCalendar.from(planned), planned.work, planned.bank))
    }

    // --- The night after an on-call day ---------------------------------------------------------

    @Test
    fun `an intervention in the early hours belongs to last night's on-call day`() {
        val data = onCall(mapOf("2026-11-17" to DayRecord(onCall = true)))
        assertEquals(d("2026-11-17") to 1440 + t(2, 10), OnCall.dutyDay(data, LocalDateTime.of(2026, 11, 18, 2, 10)))
        assertEquals(d("2026-11-18") to t(9, 30), OnCall.dutyDay(data, LocalDateTime.of(2026, 11, 18, 9, 30)))
        // On call today as well: the intervention is today's.
        val both = onCall(mapOf("2026-11-17" to DayRecord(onCall = true), "2026-11-18" to DayRecord(onCall = true)))
        assertEquals(d("2026-11-18") to t(2, 10), OnCall.dutyDay(both, LocalDateTime.of(2026, 11, 18, 2, 10)))
    }

    @Test
    fun `the running timer is found and shown on today`() {
        val data = onCall(
            mapOf("2026-11-17" to DayRecord(onCall = true, interventions = listOf(Intervention(id = 7, start = 1440 + t(2, 10), startSecond = 30)))),
        )
        val now = LocalDateTime.of(2026, 11, 18, 2, 42, 10)
        assertEquals(d("2026-11-17"), OnCall.running(data, now.toLocalDate())?.first)
        val s = Engine.snapshot(data, now)
        val state = s.onCall!!
        assertTrue(state.onDuty)
        assertEquals(31 * 60 + 40, state.runningSeconds) // 02:10:30 → 02:42:10
        assertEquals(d("2026-11-17"), state.dutyDate)
        // With the module off nothing of it reaches the screens.
        assertNull(Engine.snapshot(data.copy(onCall = OnCallSettings(enabled = false)), now).onCall)
    }

    @Test
    fun `a day only on call is kept, and the month adds up`() {
        val kept = StaccoData().withDay(d("2026-11-21")) { it.copy(onCall = true) }
        assertTrue(kept.days.containsKey("2026-11-21"))
        assertTrue(kept.withDay(d("2026-11-21")) { it.copy(onCall = false) }.days.isEmpty())

        val data = onCall(
            mapOf(
                "2026-11-14" to DayRecord(onCall = true, interventions = listOf(job(t(10), t(10, 32)), job(t(15), t(15, 15)))),
                "2026-11-15" to DayRecord(onCall = true),
                "2026-11-18" to DayRecord(entry = t(8), exit = t(9), recoveryMinutes = 7 * h),
            ),
        )
        assertEquals(OnCallMonth(days = 2, interventions = 2, real = 47, credited = 60, used = 7 * h), OnCall.month(data, YearMonth.of(2026, 11), rome))
    }

    // --- CSV ------------------------------------------------------------------------------------

    private val labels = CsvLabels(
        headers = listOf("Data", "Giorno", "Tipo", "Turno", "Entrata", "Inizio pausa", "Fine pausa", "Uscita", "Permesso", "Pausa", "Lavorato", "Previsto", "Saldo", "Note"),
        countedHeaders = listOf("Entrata conteggiata", "Uscita conteggiata"),
        onCallHeaders = listOf("Reperibile", "Interventi", "Accreditati", "Recupero"),
        yes = "Sì",
        onCallDays = "Giorni di reperibilità",
        creditedHours = "Ore intervento accreditate",
        recoveryHours = "Recupero interventi fruito",
        recoveryOn = { "Recupero interventi al $it" },
        type = { it?.name ?: "REST" },
        holiday = { it.name },
        date = { it.toString() },
        weekday = { it.dayOfWeek.name.take(3) },
        total = "Totale",
        officeDays = "In sede",
        smartDays = "Smart",
        travelDays = "Trasferta",
        vacationDays = "Ferie",
        sickDays = "Malattia",
        leaveHours = "Permessi",
        bankOn = { "Banca ore al $it" },
    )

    @Test
    fun `the csv shows on-call days, interventions and recovery`() {
        val data = onCall(
            mapOf(
                "2026-11-14" to DayRecord(onCall = true, interventions = listOf(job(t(10), t(10, 32)), job(t(15), t(15, 15)))),
                "2026-11-15" to DayRecord(onCall = true),
                "2026-11-18" to DayRecord(entry = t(8), exit = t(9), recoveryMinutes = 7 * h, note = "recupero"),
            ),
        ).copy(onCall = OnCallSettings(enabled = true))
        val rows = MonthCsv.build(data, YearMonth.of(2026, 11), labels, today = d("2026-11-30"))
            .removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }.map { it.split(";") }
        val header = rows.first()
        assertEquals(listOf("Saldo", "Reperibile", "Interventi", "Accreditati", "Recupero", "Note"), header.takeLast(6))
        fun row(date: String) = rows.first { it[0] == date }
        fun cell(date: String, name: String) = row(date)[header.indexOf(name)]
        assertEquals("Sì", cell("2026-11-14", "Reperibile"))
        assertEquals("0:47", cell("2026-11-14", "Interventi"))
        assertEquals("1:00", cell("2026-11-14", "Accreditati"))
        assertEquals("Sì", cell("2026-11-15", "Reperibile"))
        assertEquals("", cell("2026-11-15", "Interventi"))
        assertEquals("7:00", cell("2026-11-18", "Recupero"))
        assertEquals("1:00", cell("2026-11-18", "Previsto"))
        assertEquals("0:00", cell("2026-11-18", "Saldo"))
        assertEquals("recupero", cell("2026-11-18", "Note"))
        val total = rows.first { it[0] == "Totale" }
        assertEquals(listOf("2", "0:47", "1:00", "7:00"), total.subList(header.indexOf("Reperibile"), header.indexOf("Note")))
        assertTrue(listOf("Giorni di reperibilità", "2") in rows)
        assertTrue(listOf("Ore intervento accreditate", "1:00") in rows)
        assertTrue(listOf("Recupero interventi fruito", "7:00") in rows)
        assertTrue(listOf("Recupero interventi al 2026-11-30", "-6:00") in rows)
    }

    @Test
    fun `without on-call duty the csv does not change`() {
        val data = onCall(mapOf("2026-11-14" to DayRecord(onCall = true))).copy(onCall = OnCallSettings(enabled = false))
        val csv = MonthCsv.build(data, YearMonth.of(2026, 11), labels, today = d("2026-11-30"))
        assertEquals(labels.headers.joinToString(";"), csv.removePrefix("﻿").substringBefore("\r\n"))
        assertFalse("Reperibil" in csv)
    }

    // --- Backups --------------------------------------------------------------------------------

    @Test
    fun `on-call data survives a backup and old backups load without it`() {
        val data = onCall(
            mapOf(
                "2026-11-17" to DayRecord(onCall = true, interventions = listOf(Intervention(3, 1440 + t(2, 10), 1440 + t(2, 48), 12, "server down"))),
                "2026-11-18" to DayRecord(entry = t(8), exit = t(9), recoveryMinutes = 7 * h),
            ),
        ).copy(support = SupportPrompt(nextAt = "2026-12-12"), alerts = AlertsState(recoveryLastDate = "2026-11-18", recoveryPeriod = "2027-01-31"))
        assertEquals(data, Backup.decode(Backup.encode(data)))

        // 08:00 → 16:30 with the assumed 30m break: an even 8h day.
        val old = """{"schemaVersion":2,"onboardingDone":true,"work":{"dailyMinutes":480},"days":{"2026-11-16":{"entry":480,"exit":990,"leaveMinutes":0}}}"""
        val loaded = Backup.decode(old)!!
        assertEquals(OnCallSettings(), loaded.onCall)
        assertFalse(loaded.onCall.enabled)
        assertEquals(SupportPrompt(), loaded.support)
        val day = loaded.days.getValue("2026-11-16")
        assertFalse(day.onCall)
        assertTrue(day.interventions.isEmpty())
        assertEquals(0, day.recoveryMinutes)
        assertEquals(0, WorkCalendar.from(loaded).delta(d("2026-11-16")))
    }

    // --- Buy me a coffee ------------------------------------------------------------------------

    @Test
    fun `the coffee card waits two weeks, then comes back every 21 to 30 days`() {
        val installed = d("2026-11-01")
        val fresh = SupportPrompt()
        assertFalse(SupportRules.isDue(fresh, installed, d("2026-11-14")))
        assertTrue(SupportRules.isDue(fresh, installed, d("2026-11-15")))
        repeat(50) { seed ->
            val shown = SupportRules.postponed(fresh, d("2026-11-15"), Random(seed))
            val next = LocalDate.parse(shown.nextAt)
            assertTrue(next in d("2026-12-06")..d("2026-12-15"))
            assertFalse(SupportRules.isDue(shown, installed, next.minusDays(1)))
            assertTrue(SupportRules.isDue(shown, installed, next))
        }
        assertFalse(SupportRules.isDue(fresh.copy(dismissed = true), installed, d("2027-06-01")))
    }
}
