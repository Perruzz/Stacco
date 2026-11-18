package io.github.perruzz.stacco.domain

import io.github.perruzz.stacco.data.Backup
import io.github.perruzz.stacco.export.CsvLabels
import io.github.perruzz.stacco.export.MonthCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Regression checks on how the 1.4 features (weekday hours, rounding, CSV) meet the 1.5 history. */
class CrossFeatureTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun t(h: Int, m: Int = 0) = h * 60 + m
    private val h = 60

    private val fullTime = Schedule(dailyMinutes = 8 * h, minDailyMinutes = 7 * h)

    /** From 1 July: 6h Monday to Friday, with a short 4h Friday. */
    private val shortFriday = Schedule(dailyMinutes = 6 * h, minDailyMinutes = 5 * h, dailyByDay = mapOf(5 to 4 * h))

    private fun midYear(days: Map<String, DayRecord>, vacation: VacationSettings = VacationSettings(annualMinutes = 160 * h)) = StaccoData(
        onboardingDone = true,
        work = WorkSettings().withSchedule(shortFriday),
        vacation = vacation,
        days = days,
        scheduleHistory = listOf(SchedulePeriod("2026-06-30", fullTime)),
    )

    // --- 1. History × hours per weekday × vacation and permits ------------------------------

    @Test
    fun `vacation and permits cost the weekday hours of their own period`() {
        val data = midYear(
            mapOf(
                "2026-06-26" to DayRecord(type = DayType.VACATION), // Friday, full time: 8h
                "2026-07-02" to DayRecord(type = DayType.VACATION), // Thursday, new contract: 6h
                "2026-07-03" to DayRecord(type = DayType.VACATION), // Friday, short: 4h
                "2026-06-24" to DayRecord(entry = t(8), exit = t(14), leaveMinutes = 2 * h), // permit, old: 2h
                "2026-07-10" to DayRecord(entry = t(8), exit = t(10), leaveMinutes = 2 * h), // permit, short Friday: 2h
            ),
        )
        val balance = VacationCalculator.balance(data, d("2026-12-31"))
        assertEquals(8 * h + 6 * h + 4 * h + 2 * h + 2 * h, balance.used)
        val cal = WorkCalendar.from(data)
        assertEquals(6 * h, cal.required(d("2026-06-24"))) // 8h - 2h of permit
        assertEquals(2 * h, cal.required(d("2026-07-10"))) // 4h - 2h of permit
        assertEquals(0, cal.delta(d("2026-07-10")))
        // Picking that Thursday and Friday as vacation in the calendar would take 6h + 4h.
        val fresh = midYear(emptyMap())
        assertEquals(10 * h, VacationCalculator.costAsVacation(fresh, listOf(d("2026-07-02"), d("2026-07-03"))))
    }

    @Test
    fun `a permit never costs more than the day it is taken on`() {
        // 8h of permit recorded on a Friday that, after the contract change, is a 4h day.
        val data = midYear(mapOf("2026-07-03" to DayRecord(leaveMinutes = 8 * h)))
        assertEquals(4 * h, VacationCalculator.balance(data, d("2026-12-31")).used)
        assertEquals(0, WorkCalendar.from(data).required(d("2026-07-03")))
    }

    @Test
    fun `a weekday dropped by the new contract costs nothing`() {
        val monToThu = Schedule(dailyMinutes = 6 * h, minDailyMinutes = 5 * h, workDays = setOf(1, 2, 3, 4))
        val data = StaccoData(
            work = WorkSettings().withSchedule(monToThu),
            vacation = VacationSettings(annualMinutes = 100 * h),
            days = mapOf("2026-11-13" to DayRecord(type = DayType.VACATION), "2026-11-20" to DayRecord(type = DayType.VACATION)),
            scheduleHistory = listOf(SchedulePeriod("2026-11-15", fullTime)),
        )
        // Friday 13 (old contract) 8h, Friday 20 is no longer a working day.
        assertEquals(8 * h, VacationCalculator.balance(data, d("2026-11-30")).used)
    }

    // --- 2. History × rounding × CSV export -----------------------------------------------------

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

    /** 8h until 15 Nov, 6h after; badge times rounded to the quarter hour (in up, out down). */
    private val roundedNovember = StaccoData(
        onboardingDone = true,
        work = WorkSettings(rounding = RoundingSettings(stepMinutes = 15)).withSchedule(Schedule(dailyMinutes = 6 * h, minDailyMinutes = 5 * h)),
        scheduleHistory = listOf(SchedulePeriod("2026-11-15", fullTime)),
        days = mapOf(
            "2026-11-10" to DayRecord(entry = t(8, 7), breakOut = t(12, 30), breakIn = t(13), exit = t(16, 52)), // 8:15-16:45: 8h
            "2026-11-17" to DayRecord(entry = t(8, 7), exit = t(14, 20)), // 8:15-14:15: 6h, no break due
            "2026-11-18" to DayRecord(entry = t(9), exit = t(15, 40)), // 9:00-15:30: 6h30
        ),
    )

    @Test
    fun `the csv of a month with a contract change and rounding matches the app to the minute`() {
        val month = YearMonth.of(2026, 11)
        val csv = MonthCsv.build(roundedNovember, month, labels, today = d("2026-11-30")).removePrefix("﻿")
        val rows = csv.split("\r\n").filter { it.isNotEmpty() }.map { it.split(";") }
        val header = rows.first()
        fun column(name: String) = header.indexOf(name)
        fun row(date: String) = rows.first { it[0] == date }

        val cal = WorkCalendar.from(roundedNovember)
        for (date in listOf("2026-11-10", "2026-11-17", "2026-11-18")) {
            val r = row(date)
            val result = cal.result(d(date))!!
            assertEquals(MonthCsv.hours(cal.required(d(date))), r[column("Previsto")])
            assertEquals(MonthCsv.hours(result.worked), r[column("Lavorato")])
            assertEquals(MonthCsv.hours(result.delta, signed = true), r[column("Saldo")])
            assertEquals(MonthCsv.clock(cal.counted(d(date))!!.entry), r[column("Entrata conteggiata")])
            assertEquals(MonthCsv.clock(cal.counted(d(date))!!.exit), r[column("Uscita conteggiata")])
        }
        // Real stamps next to the counted ones.
        assertEquals("08:07", row("2026-11-10")[column("Entrata")])
        assertEquals("08:15", row("2026-11-10")[column("Entrata conteggiata")])
        assertEquals("16:45", row("2026-11-10")[column("Uscita conteggiata")])
        assertEquals("8:00", row("2026-11-10")[column("Previsto")])
        assertEquals("6:00", row("2026-11-17")[column("Previsto")])
        assertEquals("+0:30", row("2026-11-18")[column("Saldo")])

        // Totals: the same sums as the calendar summary and the hour bank.
        val inMonth = roundedNovember.parsedDays().keys.filter { YearMonth.from(it) == month }
        val worked = inMonth.sumOf { cal.result(it)?.worked ?: 0 }
        val balance = inMonth.sumOf { cal.delta(it) ?: 0 }
        val total = rows.first { it[0] == "Totale" }
        assertEquals(MonthCsv.hours(worked), total[column("Lavorato")])
        assertEquals(MonthCsv.hours(balance, signed = true), total[column("Saldo")])
        val bank = BankCalculator.compute(roundedNovember, d("2026-11-30")).balance
        assertEquals(30, bank)
        assertEquals(listOf("Banca ore al 2026-11-30", "+0:30"), rows.first { it[0].startsWith("Banca ore") })
    }

    @Test
    fun `without rounding the csv keeps its usual columns`() {
        val plain = roundedNovember.copy(work = roundedNovember.work.copy(rounding = RoundingSettings()))
        val header = MonthCsv.build(plain, YearMonth.of(2026, 11), labels, today = d("2026-11-30"))
            .removePrefix("﻿").substringBefore("\r\n").split(";")
        assertEquals(labels.headers, header)
    }

    // --- 3. Backup of the new structure and history reset ---------------------------------------

    @Test
    fun `a 1_5 backup restores history, weekday hours, rounding and the bank`() {
        val data = roundedNovember.copy(
            work = roundedNovember.work.copy(dailyByDay = mapOf(5 to 4 * h)),
            scheduleHistory = listOf(
                SchedulePeriod("2026-06-30", Schedule(mode = WorkMode.SHIFTS)),
                SchedulePeriod("2026-11-15", fullTime.copy(dailyByDay = mapOf(5 to 7 * h))),
            ),
            vacation = VacationSettings(annualMinutes = 160 * h, carryOver = false, adjustments = mapOf("2026" to -90)),
            adjustments = listOf(
                BankAdjustment(1, "2026-11-01", AdjustmentKind.SET_BALANCE, 125),
                BankAdjustment(2, "2026-11-12", AdjustmentKind.MANUAL, -15, "permesso medico"),
            ),
        )
        val restored = Backup.decode(Backup.encode(data))!!
        assertEquals(data, restored)
        assertEquals(data.scheduleHistory, restored.scheduleHistory)
        assertEquals(mapOf(5 to 4 * h), restored.work.dailyByDay)
        assertEquals(RoundingSettings(stepMinutes = 15), restored.work.rounding)
        val day = d("2026-11-30")
        assertEquals(BankCalculator.compute(data, day).balance, BankCalculator.compute(restored, day).balance)
        assertEquals(VacationCalculator.balance(data, day), VacationCalculator.balance(restored, day))
        // Old Fridays keep their 7h, new ones 4h.
        assertEquals(7 * h, WorkCalendar.from(restored).required(d("2026-11-13")))
        assertEquals(4 * h, WorkCalendar.from(restored).required(d("2026-11-20")))
    }

    @Test
    fun `resetting the history recounts every day at once`() {
        val before = WorkCalendar.from(roundedNovember)
        assertEquals(8 * h, before.required(d("2026-11-10")))
        assertEquals(0, before.delta(d("2026-11-10")))
        val cleared = roundedNovember.copy(scheduleHistory = emptyList())
        val after = WorkCalendar.from(cleared)
        // No stale calendar from the cache: the old day is now measured against today's 6h.
        assertEquals(6 * h, after.required(d("2026-11-10")))
        assertEquals(2 * h, after.delta(d("2026-11-10")))
        assertNotEquals(
            BankCalculator.compute(roundedNovember, d("2026-11-30")).balance,
            BankCalculator.compute(cleared, d("2026-11-30")).balance,
        )
    }
}
