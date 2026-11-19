package io.github.perruzz.stacco.domain

import io.github.perruzz.stacco.data.Backup
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

/** Regression checks on how on-call duty and its recovery hours (1.6) meet the earlier features. */
class OnCallCrossTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun t(h: Int, m: Int = 0) = h * 60 + m
    private val h = 60
    private val rome = ZoneId.of("Europe/Rome")
    private var nextId = 1L
    private fun job(start: Int, end: Int?) = Intervention(id = nextId++, start = start, end = end)

    private val fullTime = Schedule(dailyMinutes = 8 * h, minDailyMinutes = 7 * h)

    /** From 1 July: 6h Monday to Thursday and a short 4h Friday. */
    private val shortFriday = Schedule(dailyMinutes = 6 * h, minDailyMinutes = 5 * h, dailyByDay = mapOf(5 to 4 * h))

    /** Saturday 20 June: 9h of interventions, to be taken by the end of August. */
    private val june = mapOf("2026-06-20" to DayRecord(onCall = true, interventions = listOf(job(t(8), t(17)))))

    private fun midYear(days: Map<String, DayRecord>) = StaccoData(
        onboardingDone = true,
        onCall = OnCallSettings(enabled = true),
        work = WorkSettings().withSchedule(shortFriday),
        vacation = VacationSettings(annualMinutes = 160 * h),
        days = june + days,
        scheduleHistory = listOf(SchedulePeriod("2026-06-30", fullTime)),
    )

    // --- 1. Recovery × contract history × weekday hours × permits --------------------------------

    @Test
    fun `a whole day of recovery is that day's own hours`() {
        val cal = WorkCalendar.from(midYear(emptyMap()))
        assertEquals(8 * h, OnCall.recoveryRoom(cal, d("2026-06-26"))) // Friday, old contract
        assertEquals(6 * h, OnCall.recoveryRoom(cal, d("2026-07-02"))) // Thursday, new contract
        assertEquals(4 * h, OnCall.recoveryRoom(cal, d("2026-07-03"))) // short Friday
        assertEquals(0, OnCall.recoveryRoom(cal, d("2026-07-04"))) // Saturday

        // Taken in full it covers exactly that day: nothing to work, the bank does not move.
        val data = midYear(mapOf("2026-07-03" to DayRecord(recoveryMinutes = 4 * h)))
        val taken = WorkCalendar.from(data)
        assertEquals(0, taken.required(d("2026-07-03")))
        assertEquals(4 * h, OnCall.recoveryCost(taken, d("2026-07-03")))
        assertEquals(5 * h, RecoveryCalculator.compute(data, d("2026-07-31"), rome).balance)
        assertEquals(BankLedger.EMPTY, BankCalculator.compute(data, d("2026-07-31")))

        // With shifts the whole day is the planned shift.
        val night = ShiftTemplate(id = "N", name = "Notte", start = t(22), end = t(6))
        val shifts = StaccoData(
            onCall = OnCallSettings(enabled = true),
            work = WorkSettings(mode = WorkMode.SHIFTS, shifts = listOf(night)),
            days = mapOf("2026-11-18" to DayRecord(shiftId = "N")),
        )
        assertEquals(8 * h, OnCall.recoveryRoom(WorkCalendar.from(shifts), d("2026-11-18")))
    }

    @Test
    fun `a permit and recovery on a 4h day cover it exactly once`() {
        // Short Friday: 2h of permit and 2h of recovery, nothing worked.
        val data = midYear(mapOf("2026-07-10" to DayRecord(leaveMinutes = 2 * h, recoveryMinutes = 2 * h)))
        val cal = WorkCalendar.from(data)
        val day = d("2026-07-10")
        assertEquals(0, cal.required(day))
        assertEquals(2 * h, OnCall.recoveryRoom(cal, day)) // "whole day" once the permit is in
        assertEquals(2 * h, OnCall.recoveryCost(cal, day))
        assertEquals(2 * h, VacationCalculator.cost(cal, day))
        assertEquals(2 * h, VacationCalculator.balance(data, d("2026-12-31")).used)
        assertEquals(7 * h, RecoveryCalculator.compute(data, d("2026-07-31"), rome).balance)
        assertNull(cal.delta(day))
        assertEquals(BankLedger.EMPTY, BankCalculator.compute(data, d("2026-07-31")))
        // Stamping in anyway adds nothing to the bank.
        val stamped = midYear(mapOf("2026-07-10" to DayRecord(entry = t(8), exit = t(9), leaveMinutes = 2 * h, recoveryMinutes = 2 * h)))
        assertEquals(0, WorkCalendar.from(stamped).delta(day))

        // Whole day of recovery picked first (4h), then a 2h permit: the permit goes first and the
        // recovery takes only what is left, as it is shown on the day.
        val reversed = midYear(mapOf("2026-07-10" to DayRecord(leaveMinutes = 2 * h, recoveryMinutes = 4 * h)))
        val rc = WorkCalendar.from(reversed)
        assertEquals(0, rc.required(day))
        assertEquals(2 * h, VacationCalculator.cost(rc, day))
        assertEquals(2 * h, OnCall.recoveryCost(rc, day))
        assertEquals(2 * h, VacationCalculator.permitHeld(rc, day))
        assertEquals(2 * h, OnCall.recoveryHeld(rc, day))
        assertEquals(7 * h, RecoveryCalculator.compute(reversed, d("2026-07-31"), rome).balance)
    }

    @Test
    fun `a contract change never makes recovery take more than the new day`() {
        // Full time all along: a whole 8h day of recovery on Friday 3 July.
        val before = StaccoData(
            onboardingDone = true,
            onCall = OnCallSettings(enabled = true),
            work = WorkSettings().withSchedule(fullTime),
            days = june + ("2026-07-03" to DayRecord(recoveryMinutes = 8 * h)),
        )
        val friday = d("2026-07-03")
        assertEquals(8 * h, OnCall.recoveryCost(WorkCalendar.from(before), friday))
        assertEquals(1 * h, RecoveryCalculator.compute(before, d("2026-07-31"), rome).balance)

        // From 1 July the short Friday applies (as the repository records the change).
        fun changed(next: Schedule) = before.copy(
            work = before.work.withSchedule(next),
            scheduleHistory = ScheduleHistory.change(before.scheduleHistory, before.work.schedule, next, d("2026-07-01")),
        )
        val shorter = changed(shortFriday)
        val cal = WorkCalendar.from(shorter)
        assertEquals(4 * h, OnCall.recoveryCost(cal, friday))
        assertEquals(4 * h, OnCall.recoveryHeld(cal, friday))
        assertEquals(0, cal.required(friday))
        assertEquals(5 * h, RecoveryCalculator.compute(shorter, d("2026-07-31"), rome).balance)
        assertEquals(BankLedger.EMPTY, BankCalculator.compute(shorter, d("2026-07-31")))

        // With a permit as well, the two together still fill just the new day.
        val both = changed(shortFriday).let { it.copy(days = it.days + ("2026-07-03" to DayRecord(leaveMinutes = 2 * h, recoveryMinutes = 6 * h))) }
        val bc = WorkCalendar.from(both)
        assertEquals(2 * h, VacationCalculator.cost(bc, friday))
        assertEquals(2 * h, OnCall.recoveryCost(bc, friday))

        // Friday no longer a working day: the recovery hours come back.
        val noFriday = changed(Schedule(dailyMinutes = 6 * h, minDailyMinutes = 5 * h, workDays = setOf(1, 2, 3, 4)))
        assertEquals(0, OnCall.recoveryCost(WorkCalendar.from(noFriday), friday))
        assertEquals(9 * h, RecoveryCalculator.compute(noFriday, d("2026-07-31"), rome).balance)
    }

    // --- 2. Intervention timer × working day ----------------------------------------------------

    @Test
    fun `an intervention across midnight at the end of a month counts for the month it started in`() {
        // Timer started at 23:45 on Monday 30 November (not on call before), stopped at 00:10.
        val start = LocalDateTime.of(2026, 11, 30, 23, 45)
        val (day, minutes) = OnCall.dutyDay(StaccoData(onCall = OnCallSettings(enabled = true)), start)
        assertEquals(d("2026-11-30") to t(23, 45), day to minutes)
        val end = OnCall.minutesOf(day, LocalDateTime.of(2026, 12, 1, 0, 10))
        assertEquals(1440 + t(0, 10), end)
        val data = StaccoData(
            onCall = OnCallSettings(enabled = true),
            days = mapOf("2026-11-30" to DayRecord(onCall = true, interventions = listOf(Intervention(1, minutes, end)))),
        )
        val ledger = RecoveryCalculator.compute(data, d("2026-12-01"), rome)
        assertEquals(30, ledger.balance) // 25 minutes, 30 credited
        assertEquals(YearMonth.of(2026, 11), ledger.nextExpiry?.month)
        assertEquals(d("2027-01-31"), ledger.nextExpiry?.expiry)
        assertEquals(30, RecoveryCalculator.compute(data, d("2027-01-31"), rome).balance)
        assertEquals(0, RecoveryCalculator.compute(data, d("2027-02-01"), rome).balance)
        assertEquals(30, OnCall.month(data, YearMonth.of(2026, 11), rome).credited)
        assertEquals(0, OnCall.month(data, YearMonth.of(2026, 12), rome).credited)

        // New Year's Eve: December's hours, until the end of February.
        val newYear = StaccoData(
            onCall = OnCallSettings(enabled = true),
            days = mapOf("2026-12-31" to DayRecord(onCall = true, interventions = listOf(Intervention(2, t(23, 45), 1440 + t(0, 10))))),
        )
        assertEquals(d("2027-02-28"), RecoveryCalculator.compute(newYear, d("2027-01-01"), rome).nextExpiry?.expiry)

        // After midnight of an on-call 30 November (1 December not on call) it is still that night.
        val night = StaccoData(onCall = OnCallSettings(enabled = true), days = mapOf("2026-11-30" to DayRecord(onCall = true)))
        assertEquals(d("2026-11-30") to 1440 + t(0, 30), OnCall.dutyDay(night, LocalDateTime.of(2026, 12, 1, 0, 30)))
    }

    @Test
    fun `a running intervention leaves the working day alone`() {
        val base = StaccoData(
            onboardingDone = true,
            onCall = OnCallSettings(enabled = true),
            days = mapOf("2026-11-18" to DayRecord(entry = t(8), onCall = true)),
        )
        val now = LocalDateTime.of(2026, 11, 18, 10, 20, 5)
        val alone = Engine.snapshot(base, now)
        val withTimer = base.copy(
            days = mapOf("2026-11-18" to DayRecord(entry = t(8), onCall = true, interventions = listOf(Intervention(5, t(10), startSecond = 0)))),
        )
        val s = Engine.snapshot(withTimer, now)
        // Same working day, same exit, same notification…
        assertEquals(alone.projection, s.projection)
        assertEquals(alone.targetExit, s.targetExit)
        assertEquals(alone.nextSlot, s.nextSlot)
        assertEquals(DayPhase.MORNING, s.projection.phase)
        // …and the intervention timer next to it.
        assertEquals(20 * 60 + 5, s.onCall?.runningSeconds)
        // Leaving the office does not stop the intervention, stopping it does not touch the stamps.
        val left = withTimer.copy(days = mapOf("2026-11-18" to withTimer.days.getValue("2026-11-18").copy(exit = t(16, 30))))
        val after = Engine.snapshot(left, LocalDateTime.of(2026, 11, 18, 16, 31))
        assertEquals(DayPhase.DONE, after.projection.phase)
        assertNotNull(after.onCall?.running)
    }

    // --- 3. Backups -------------------------------------------------------------------------------

    @Test
    fun `a backup brings back on-call duty as it was`() {
        val data = StaccoData(
            onboardingDone = true,
            onCall = OnCallSettings(enabled = true),
            vacation = VacationSettings(annualMinutes = 160 * h),
            days = mapOf(
                "2026-11-14" to DayRecord(onCall = true, interventions = listOf(Intervention(1, t(10), t(10, 32), note = "VPN"), Intervention(2, t(23, 45), 1440 + t(0, 10)))),
                "2026-11-15" to DayRecord(onCall = true),
                "2026-11-16" to DayRecord(entry = t(8), exit = t(12), leaveMinutes = 2 * h, recoveryMinutes = 2 * h),
                "2026-11-17" to DayRecord(onCall = true, interventions = listOf(Intervention(3, 1440 + t(2, 10), startSecond = 12))),
                "2026-11-27" to DayRecord(recoveryMinutes = 30), // planned
            ),
            alerts = AlertsState(recoveryLastDate = "2026-11-16", recoveryPeriod = "2027-01-31"),
            support = SupportPrompt(nextAt = "2026-12-20", dismissed = true),
        )
        val restored = Backup.decode(Backup.encode(data))!!
        assertEquals(data, restored)
        val today = d("2026-11-18")
        assertEquals(RecoveryCalculator.compute(data, today, rome), RecoveryCalculator.compute(restored, today, rome))
        assertEquals(OnCall.month(data, YearMonth.of(2026, 11), rome), OnCall.month(restored, YearMonth.of(2026, 11), rome))
        val now = LocalDateTime.of(2026, 11, 18, 2, 30)
        assertEquals(Engine.snapshot(data, now).onCall, Engine.snapshot(restored, now).onCall)
        // On a new phone the coffee card stays away: "don't show again" holds, and the new install
        // gets its own two quiet weeks.
        assertFalse(SupportRules.isDue(restored.support, installed = today, today = d("2027-06-01")))
        val shown = restored.support.copy(dismissed = false)
        assertFalse(SupportRules.isDue(shown, installed = today, today = d("2026-12-01")))
        assertTrue(SupportRules.isDue(shown, installed = today, today = d("2026-12-20")))
    }

    @Test
    fun `restoring a backup keeps the coffee card choices made on this phone`() {
        val never = SupportPrompt(nextAt = "2026-12-01", dismissed = true)
        val old = SupportPrompt(nextAt = "2026-10-01")
        assertEquals(never, SupportRules.restored(current = never, backup = old))
        assertEquals(never, SupportRules.restored(current = old, backup = never))
        // Shown yesterday here: an older backup does not bring it back tomorrow.
        val shownHere = SupportPrompt(nextAt = "2026-12-12")
        assertEquals(shownHere, SupportRules.restored(current = shownHere, backup = old))
        assertEquals(shownHere, SupportRules.restored(current = SupportPrompt(), backup = shownHere))
        assertEquals(SupportPrompt(), SupportRules.restored(SupportPrompt(), SupportPrompt()))
    }

    // --- 4. CSV × calendar × hour bank × recovery account -----------------------------------------

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

    /** "7:45", "-0:15", "+1:30" or "" as minutes. */
    private fun mins(text: String): Int {
        if (text.isEmpty()) return 0
        val negative = text.startsWith("-")
        val (hh, mm) = text.trimStart('-', '+').split(":").map { it.toInt() }
        return (hh * 60 + mm) * if (negative) -1 else 1
    }

    /** November 2026 with a bit of everything, rounding included; the contract changes on the 16th. */
    private fun mixedMonth(): StaccoData = StaccoData(
        onboardingDone = true,
        onCall = OnCallSettings(enabled = true),
        work = WorkSettings(rounding = RoundingSettings(stepMinutes = 15, toleranceMinutes = 5)).withSchedule(Schedule(dailyMinutes = 8 * h, minDailyMinutes = 7 * h, dailyByDay = mapOf(5 to 6 * h))),
        scheduleHistory = listOf(SchedulePeriod("2026-11-15", fullTime)),
        vacation = VacationSettings(annualMinutes = 160 * h),
        adjustments = listOf(
            BankAdjustment(1, "2026-10-31", AdjustmentKind.SET_BALANCE, 3 * h),
            BankAdjustment(2, "2026-11-13", AdjustmentKind.MANUAL, 30, "correzione"),
        ),
        days = mapOf(
            // October: 2h earned, to be taken by 31 December (taken first).
            "2026-10-10" to DayRecord(onCall = true, interventions = listOf(job(t(9), t(11)))),
            "2026-11-02" to DayRecord(entry = t(8, 3), breakOut = t(12, 30), breakIn = t(13), exit = t(17, 10)),
            "2026-11-03" to DayRecord(type = DayType.SMART),
            "2026-11-04" to DayRecord(type = DayType.VACATION),
            "2026-11-05" to DayRecord(entry = t(8), exit = t(14), leaveMinutes = 2 * h),
            "2026-11-06" to DayRecord(entry = t(8), exit = t(16, 30), onCall = true, interventions = listOf(job(t(21), t(22, 20)))),
            "2026-11-07" to DayRecord(onCall = true, interventions = listOf(job(t(10), t(10, 32)), job(t(14), t(18)), job(t(23, 45), 1440 + t(0, 10)))),
            "2026-11-08" to DayRecord(onCall = true),
            "2026-11-09" to DayRecord(entry = t(8), exit = t(12), recoveryMinutes = 4 * h),
            "2026-11-10" to DayRecord(entry = t(8), exit = t(12), leaveMinutes = 2 * h, recoveryMinutes = 2 * h),
            "2026-11-11" to DayRecord(entry = t(7, 58), breakOut = t(12), breakIn = t(12, 40), exit = t(16, 41)),
            "2026-11-12" to DayRecord(leaveMinutes = 8 * h, note = "permesso"), // a whole day of permit
            "2026-11-13" to DayRecord(entry = t(9), exit = t(17, 30)),
            "2026-11-16" to DayRecord(entry = t(8), exit = t(16, 45)),
            "2026-11-20" to DayRecord(entry = t(8), exit = t(14)), // short Friday: 6h, no break
            "2026-11-21" to DayRecord(onCall = true, interventions = listOf(job(t(8), t(14)))),
            "2026-11-22" to DayRecord(onCall = true, interventions = listOf(job(t(2), t(3, 10)))),
            "2026-11-23" to DayRecord(entry = t(8), exit = t(15), recoveryMinutes = 90),
            "2026-11-24" to DayRecord(type = DayType.TRAVEL),
            "2026-11-25" to DayRecord(type = DayType.SICK),
            "2026-11-26" to DayRecord(entry = t(8), exit = t(18)),
            "2026-11-27" to DayRecord(recoveryMinutes = 6 * h), // short Friday: a whole day of recovery
            // On call on the last night: one across midnight, one after it (still that night).
            "2026-11-30" to DayRecord(entry = t(8), exit = t(16, 30), onCall = true, interventions = listOf(job(t(23, 45), 1440 + t(0, 10)), job(1440 + t(0, 30), 1440 + t(1)))),
            // December: a recovery already planned, which November must not count.
            "2026-12-04" to DayRecord(recoveryMinutes = 1 * h),
        ),
    )

    @Test
    fun `the csv of a mixed month adds up with the calendar, the bank and the recovery account`() {
        val data = mixedMonth()
        val month = YearMonth.of(2026, 11)
        val today = d("2026-11-30")
        val rows = MonthCsv.build(data, month, labels, today = today)
            .removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }.map { it.split(";") }
        val header = rows.first()
        val dayRows = rows.drop(1).takeWhile { it[0].isNotEmpty() }
        assertEquals(30, dayRows.size)
        fun col(name: String) = header.indexOf(name).also { assertTrue("missing $name", it >= 0) }
        fun sum(name: String) = dayRows.sumOf { mins(it[col(name)]) }
        val total = rows.first { it[0] == "Totale" }
        fun footer(label: String) = rows.first { it[0] == label }[1]

        // Every column adds up to its total.
        assertEquals(sum("Lavorato"), mins(total[col("Lavorato")]))
        assertEquals(sum("Previsto"), mins(total[col("Previsto")]))
        assertEquals(sum("Saldo"), mins(total[col("Saldo")]))
        assertEquals(sum("Interventi"), mins(total[col("Interventi")]))
        assertEquals(sum("Accreditati"), mins(total[col("Accreditati")]))
        assertEquals(sum("Recupero"), mins(total[col("Recupero")]))
        assertEquals(sum("Permesso"), mins(footer("Permessi")))
        val onCallDays = dayRows.count { it[col("Reperibile")] == "Sì" }
        assertEquals(onCallDays.toString(), total[col("Reperibile")])
        assertEquals(onCallDays.toString(), footer("Giorni di reperibilità"))
        assertEquals(sum("Accreditati"), mins(footer("Ore intervento accreditate")))
        assertEquals(sum("Recupero"), mins(footer("Recupero interventi fruito")))

        // The calendar summary (same sums as CalendarScreen.MonthSummary).
        val cal = WorkCalendar.from(data)
        val inMonth = data.parsedDays().filterKeys { YearMonth.from(it) == month }
        assertEquals(inMonth.keys.sumOf { cal.delta(it) ?: 0 }, sum("Saldo"))
        assertEquals(inMonth.keys.sumOf { if (cal.effectiveType(it) == DayType.OFFICE) cal.result(it)?.worked ?: 0 else 0 }, sum("Lavorato"))
        assertEquals(inMonth.count { (date, r) -> cal.effectiveType(date) == DayType.OFFICE && r.hasStamps }.toString(), footer("In sede"))
        val duty = OnCall.month(data, month)
        assertEquals(duty.days, onCallDays)
        assertEquals(duty.real, sum("Interventi"))
        assertEquals(duty.credited, sum("Accreditati"))
        assertEquals(duty.used, sum("Recupero"))

        // The hour bank: the days of the month are its day movements, and the balance matches.
        val ledger = BankCalculator.compute(data, today)
        val dayMoves = ledger.movements.filter { YearMonth.from(it.date) == month && it.kind == MovementKind.DAY }.sumOf { it.amount }
        assertEquals(dayMoves, sum("Saldo"))
        assertEquals(3 * h + 30 + sum("Saldo"), ledger.balance)
        assertEquals(mins(footer("Banca ore al 2026-11-30")), ledger.balance)

        // Permits: the vacation hours of the year count exactly the permit column.
        assertEquals(sum("Permesso"), VacationCalculator.balance(data, today).leave)

        // The recovery account: October's 2h plus what November earned, minus what it took.
        val recovery = RecoveryCalculator.compute(data, today)
        assertEquals(2 * h + sum("Accreditati") - sum("Recupero"), recovery.balance)
        assertEquals(mins(footer("Recupero interventi al 2026-11-30")), recovery.balance)
        assertEquals(1 * h, recovery.planned) // 4 December, not in November
        assertEquals(listOf(YearMonth.of(2026, 11)), recovery.lots.map { it.month }) // October went first
        assertEquals(sum("Accreditati"), recovery.lots.single().earned)

        // A few rows checked by hand.
        fun row(date: String) = dayRows.first { it[0] == date }
        assertEquals("1:20", row("2026-11-06")[col("Interventi")])
        assertEquals("1:30", row("2026-11-06")[col("Accreditati")])
        assertEquals("4:57", row("2026-11-07")[col("Interventi")]) // 32m + 4h + 25m
        assertEquals("5:15", row("2026-11-07")[col("Accreditati")]) // 45m + 4h + 30m
        assertEquals("2:00", row("2026-11-10")[col("Recupero")])
        assertEquals("2:00", row("2026-11-10")[col("Permesso")])
        assertEquals("0:00", row("2026-11-10")[col("Saldo")])
        assertEquals("8:00", row("2026-11-12")[col("Permesso")])
        assertEquals("", row("2026-11-12")[col("Saldo")])
        assertEquals("6:00", row("2026-11-27")[col("Recupero")])
        assertEquals("", row("2026-11-27")[col("Previsto")])
        assertEquals("+0:45", row("2026-11-02")[col("Saldo")]) // 08:03 → 08:00, 17:10 → 17:15
        assertEquals("+2:35", total[col("Saldo")])
        assertEquals("15:00", footer("Ore intervento accreditate"))
        assertEquals("13:30", footer("Recupero interventi fruito"))
        assertEquals("3:30", footer("Recupero interventi al 2026-11-30"))
        assertEquals("12", footer("In sede"))
        assertEquals("1:00", row("2026-11-30")[col("Accreditati")]) // 25m → 30m, 30m → 30m
    }
}
