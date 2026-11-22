package io.github.perruzz.stacco.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * The calculation swept across every combination that matters, checked against independent
 * figures (the time zone itself, plain arithmetic) rather than against its own formulas.
 */
class CalculationStressTest {

    private val rome = ZoneId.of("Europe/Rome")
    private var previousZone: TimeZone? = null

    @Before
    fun romeTime() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(rome))
    }

    @After
    fun restoreTime() {
        TimeZone.setDefault(previousZone)
    }

    private fun d(s: String) = LocalDate.parse(s)
    private fun t(h: Int, m: Int = 0) = h * 60 + m
    private val h = 60

    /** Real minutes between two clock times of [date]'s working day, as the time zone counts them. */
    private fun realMinutes(date: LocalDate, from: Int, to: Int): Int =
        Duration.between(at(date, from), at(date, to)).toMinutes().toInt()

    private fun at(date: LocalDate, minutes: Int) = date.atStartOfDay().plusMinutes(minutes.toLong()).atZone(rome)

    // --- 1. Nights across midnight and the two nights the clocks change ------------------------

    private val night = ShiftTemplate("n", "Notte", t(22), t(6))
    private val morning = ShiftTemplate("m", "Mattina", t(6), t(14))
    private val afternoon = ShiftTemplate("p", "Pomeriggio", t(14), t(22), breakMinutes = 30)
    private val shifts = WorkSettings(mode = WorkMode.SHIFTS, shifts = listOf(night, morning, afternoon))

    private fun nightOf(date: LocalDate, record: DayRecord) = StaccoData(work = shifts, days = mapOf(date.toString() to record))

    @Test
    fun `every night of two years counts its real hours, live and once closed`() {
        var date = d("2026-01-01")
        val changed = mutableListOf<LocalDate>()
        while (date.year < 2028) {
            val real = realMinutes(date, t(22), t(6) + 1440)
            if (real != 8 * h) changed += date
            val closed = nightOf(date, DayRecord(shiftId = "n", entry = t(22), exit = t(6) + 1440))
            val result = WorkCalendar.from(closed).result(date)!!
            assertEquals("$date", real, result.worked)
            assertEquals("$date", real - 8 * h, result.delta)
            assertEquals("$date", real - 8 * h, BankCalculator.compute(closed, date.plusDays(1)).balance)
            // Just before the exit the night is still the working day, and the live count matches the result.
            val open = nightOf(date, DayRecord(shiftId = "n", entry = t(22)))
            val atExit = Engine.snapshot(open, date.plusDays(1).atTime(6, 0))
            assertEquals("$date", date, atExit.date)
            assertEquals("$date", real, atExit.projection.workedSoFar)
            assertEquals("$date", 0, atExit.secondsTo(atExit.targetExit!!))
            date = date.plusDays(1)
        }
        assertEquals(listOf(d("2026-03-28"), d("2026-10-24"), d("2027-03-27"), d("2027-10-30")), changed)
    }

    @Test
    fun `countdown, live hours and the intervention timer run in real time on those nights`() {
        for ((day, nowAt) in listOf("2026-03-28" to listOf(t(1), t(4)), "2026-10-24" to listOf(t(1), t(2, 30), t(4)))) {
            val date = d(day)
            val data = nightOf(date, DayRecord(shiftId = "n", entry = t(22)))
            for (clock in nowAt) {
                val now = date.plusDays(1).atTime(clock / 60, clock % 60)
                val s = Engine.snapshot(data, now)
                assertEquals("$now", realMinutes(date, t(22), clock + 1440), s.projection.workedSoFar)
                assertEquals("$now", realMinutes(date, clock + 1440, t(6) + 1440) * 60, s.secondsTo(s.targetExit!!))
            }
        }
        // An intervention started at 01:30 of the autumn night, still running at 03:30: three hours.
        val onCall = StaccoData(
            onCall = OnCallSettings(enabled = true),
            days = mapOf("2026-10-24" to DayRecord(onCall = true, interventions = listOf(Intervention(1, t(1, 30) + 1440)))),
        )
        assertEquals(3 * 3600, Engine.snapshot(onCall, LocalDateTime.of(2026, 10, 25, 3, 30)).onCall!!.runningSeconds)
        // Once stopped it is credited the same three hours.
        val stopped = Intervention(1, t(1, 30) + 1440, t(3, 30) + 1440)
        assertEquals(3 * h, OnCall.credited(d("2026-10-24"), stopped, rome))
    }

    @Test
    fun `fixed hours across the night the clocks change end when the hours are done`() {
        val everyDay = WorkSettings(workDays = (1..7).toSet())
        for (day in listOf("2026-03-28", "2026-10-24", "2026-11-14")) {
            val date = d(day)
            val open = StaccoData(work = everyDay, days = mapOf(day to DayRecord(entry = t(22))))
            val exit = Engine.snapshot(open, date.atTime(23, 0)).targetExit!!
            // 8h of work and the 30 minutes of break, in real time.
            assertEquals(day, 8 * h + 30, realMinutes(date, t(22), exit))
            val closed = StaccoData(work = everyDay, days = mapOf(day to DayRecord(entry = t(22), exit = exit)))
            assertEquals(day, 0, WorkCalendar.from(closed).delta(date))
        }
    }

    @Test
    fun `an evening that runs past midnight stays one working day`() {
        val late = StaccoData(days = mapOf("2026-11-16" to DayRecord(entry = t(14))))
        // Expected end 22:30: the day stays the current one until 02:30, so the exit closes it.
        assertEquals(ActiveDay(d("2026-11-16"), t(0, 15) + 1440, 0), Engine.activeDay(late, LocalDateTime.of(2026, 11, 17, 0, 15)))
        assertEquals(d("2026-11-16"), Engine.activeDay(late, LocalDateTime.of(2026, 11, 17, 2, 29)).date)
        assertEquals(d("2026-11-17"), Engine.activeDay(late, LocalDateTime.of(2026, 11, 17, 2, 30)).date)
        // A day expected to end in the afternoon does not reach into the next one.
        val forgotten = StaccoData(days = mapOf("2026-11-16" to DayRecord(entry = t(8, 30))))
        assertEquals(d("2026-11-17"), Engine.activeDay(forgotten, LocalDateTime.of(2026, 11, 17, 0, 5)).date)
        // An afternoon shift with overtime: the same.
        val shift = StaccoData(work = shifts, days = mapOf("2026-11-16" to DayRecord(shiftId = "p", entry = t(14))))
        assertEquals(d("2026-11-16"), Engine.activeDay(shift, LocalDateTime.of(2026, 11, 17, 1, 0)).date)
    }

    @Test
    fun `a permit or recovery hours bring the end of a shift forward`() {
        fun exit(shiftId: String, entry: Int, leave: Int = 0, recovery: Int = 0): Int? {
            val data = StaccoData(
                work = shifts,
                onCall = OnCallSettings(enabled = recovery > 0),
                days = mapOf("2026-11-16" to DayRecord(shiftId = shiftId, entry = entry, leaveMinutes = leave, recoveryMinutes = recovery)),
            )
            return Engine.snapshot(data, LocalDateTime.of(2026, 11, 16, 0, 0).plusMinutes(entry + 10L)).targetExit
        }
        assertEquals(t(12), exit("m", t(6), leave = 2 * h)) // leaving two hours early
        assertEquals(t(14), exit("m", t(8), leave = 2 * h)) // coming two hours late
        assertEquals(t(11), exit("m", t(6), recovery = 3 * h))
        assertEquals(t(20), exit("p", t(14), leave = 2 * h)) // 5h30 of work and the 30-minute break
        // Without them a shift still ends when it ends, early or late arrival alike.
        assertEquals(t(14), exit("m", t(5, 50)))
        assertEquals(t(14), exit("m", t(6, 10)))
        assertEquals(t(6) + 1440, exit("n", t(22)))
    }

    // --- 2. Weekday hours, the minimum break and the six-hour rule, rounding --------------------

    @Test
    fun `the lunch break is due only past six hours, whatever made the day shorter`() {
        val friday = d("2026-11-20")
        for (planned in t(1)..t(10) step 15) {
            val work = WorkSettings(dailyByDay = mapOf(5 to planned), minBreakMinutes = 45)
            for (leave in 0..planned step 30) {
                fun day(record: DayRecord) = WorkCalendar(work, mapOf(friday to record.copy(leaveMinutes = leave)))
                val calendar = day(DayRecord())
                val required = calendar.required(friday)
                val label = "planned $planned, permit $leave"
                assertEquals(label, max(0, planned - leave), required)
                assertEquals(label, max(0, planned - 60 - leave), calendar.minimum(friday))
                assertEquals(label, if (required in 1..6 * h) 0 else 45, calendar.breakRules(friday).min)
                if (required == 0) continue
                val presence = required + calendar.breakRules(friday).min
                // Worked exactly, five minutes short, twenty minutes over.
                assertEquals(label, 0, day(DayRecord(entry = t(8), exit = t(8) + presence)).delta(friday))
                assertEquals(label, -5, day(DayRecord(entry = t(8), exit = t(8) + presence - 5)).delta(friday))
                assertEquals(label, if (leave > 0) 0 else 20, day(DayRecord(entry = t(8), exit = t(8) + presence + 20)).delta(friday))
                // A shorter break stamped still counts as the minimum; a longer one as it is.
                if (calendar.breakRules(friday).min > 0) {
                    val short = DayRecord(entry = t(8), breakOut = t(12), breakIn = t(12, 10), exit = t(8) + presence)
                    assertEquals(label, 0, day(short).delta(friday))
                    val long = DayRecord(entry = t(8), breakOut = t(12), breakIn = t(13), exit = t(8) + presence)
                    assertEquals(label, -15, day(long).delta(friday))
                }
            }
        }
    }

    @Test
    fun `rounding keeps every time on its step, within its tolerance, in order`() {
        for (step in listOf(5, 10, 15, 30)) for (tolerance in 0..min(step - 1, 15)) for (direction in RoundingDirection.entries) {
            var previous = Int.MIN_VALUE
            for (minutes in 0..2 * 1440) {
                val r = Rounding.round(minutes, step, direction, tolerance)
                val ok = r % step == 0 && abs(r - minutes) < step && r >= previous && when (direction) {
                    RoundingDirection.UP -> r >= minutes || minutes - r <= tolerance
                    RoundingDirection.DOWN -> r <= minutes || r - minutes <= tolerance
                    RoundingDirection.NEAREST -> abs(r - minutes) * 2 <= step
                }
                assertTrue("$minutes by $step $direction ±$tolerance -> $r", ok)
                previous = r
            }
        }
    }

    @Test
    fun `rounded badge times never leave a valid day out of the bank`() {
        val settings = RoundingSettings(stepMinutes = 15, toleranceMinutes = 5)
        val exits = listOf(t(12, 1), t(16), t(16, 7), t(16, 53), t(17, 59), t(23, 58), t(6, 3) + 1440)
        for (entry in t(6)..t(12)) for (exit in exits) {
            val record = if (entry < t(11, 50)) DayRecord(entry = entry, breakOut = t(12), breakIn = t(12, 30), exit = exit) else DayRecord(entry = entry, exit = exit)
            if (DayCalculator.issues(record).isNotEmpty()) continue
            assertTrue("$entry-$exit", DayCalculator.issues(Rounding.counted(record, settings)).isEmpty())
        }
    }

    // --- 3. Contract history, leave costs and recovery hours -----------------------------------

    @Test
    fun `a year of vacation and permits costs the hours of each day's own contract`() {
        // Until June: 8h Monday to Friday. From July: 6h Monday to Thursday, 4h on Friday.
        val data = StaccoData(
            work = WorkSettings().withSchedule(Schedule(dailyMinutes = 6 * h, minDailyMinutes = 5 * h, dailyByDay = mapOf(5 to 4 * h))),
            scheduleHistory = listOf(SchedulePeriod("2026-06-30", Schedule())),
            vacation = VacationSettings(annualMinutes = 400 * h),
            days = generateSequence(d("2026-01-01")) { it.plusDays(1) }.takeWhile { it.year == 2026 }.mapNotNull { date ->
                when (date.dayOfWeek.value) {
                    1, 5 -> date.toString() to DayRecord(type = DayType.VACATION)
                    3 -> date.toString() to DayRecord(leaveMinutes = 2 * h)
                    else -> null
                }
            }.toMap(),
        )
        var expected = 0
        for ((key, record) in data.days) {
            val date = d(key)
            if (ItalianHolidays.holiday(date) != null) continue
            val day = when {
                date <= d("2026-06-30") -> 8 * h
                date.dayOfWeek.value == 5 -> 4 * h
                else -> 6 * h
            }
            expected += if (record.type == DayType.VACATION) day else min(2 * h, day)
        }
        assertEquals(expected, VacationCalculator.balance(data, d("2026-12-31")).used)
    }

    @Test
    fun `a permit and recovery hours together never cover more than their day`() {
        val history = listOf(SchedulePeriod("2026-06-30", Schedule()))
        val fixed = WorkSettings().withSchedule(Schedule(dailyMinutes = 6 * h, minDailyMinutes = 5 * h, dailyByDay = mapOf(5 to 4 * h)))
        val days = listOf(
            Triple(fixed, d("2026-06-29"), 8 * h), // Monday, old contract
            Triple(fixed, d("2026-07-03"), 4 * h), // Friday, new contract
            Triple(fixed, d("2026-07-04"), 0), // Saturday
            Triple(shifts, d("2026-07-06"), 0), // shift day taken off the plan
        )
        for ((work, date, day) in days) for (leave in 0..8 * h step 30) for (recovery in 0..8 * h step 30) {
            val record = DayRecord(entry = t(8), exit = t(9), leaveMinutes = leave, recoveryMinutes = recovery)
            val calendar = WorkCalendar(work, mapOf(date to record), if (work === shifts) emptyList() else history)
            val label = "$date permit $leave recovery $recovery"
            val permit = VacationCalculator.permitHeld(calendar, date)
            val held = OnCall.recoveryHeld(calendar, date)
            assertEquals(label, min(leave, day), permit)
            assertEquals(label, min(leave + recovery, day), permit + held)
            assertEquals(label, permit + held, calendar.covered(date))
            assertEquals(label, day - permit - held, calendar.required(date))
            assertEquals(label, permit, VacationCalculator.cost(calendar, date))
            assertEquals(label, held, OnCall.recoveryCost(calendar, date))
            // The hour worked counts against what is left of the day (covered, it can only cost);
            // on a day off, a permit left behind holds nothing back: it is all overtime.
            val required = calendar.required(date)
            val worked = h - if (required > 0) calendar.breakRules(date).min else 0
            val expected = if (permit + held > 0) min(0, worked - required) else worked - required
            assertEquals(label, if (day == 0) h else expected, calendar.delta(date))
        }
    }

    // --- 4. On-call duty: H24, across midnight, two-month expiry, even days ---------------------

    @Test
    fun `round-the-clock duty - the early hours belong to the day before only when today is off`() {
        val days = (16..22).associate { "2026-11-$it" to DayRecord(onCall = true) }
        val data = StaccoData(onCall = OnCallSettings(enabled = true), days = days)
        for (day in 16..23) for (hour in 0..23) {
            val now = LocalDateTime.of(2026, 11, day, hour, 30)
            val expected = if (day == 23 && hour < 9) d("2026-11-22") to t(hour, 30) + 1440 else now.toLocalDate() to t(hour, 30)
            assertEquals("$now", expected, OnCall.dutyDay(data, now))
        }
    }

    @Test
    fun `a year of interventions and recovery - nothing is created, lost early or kept too long`() {
        val random = Random(2026)
        var id = 1L
        val days = mutableMapOf<String, DayRecord>()
        var date = d("2026-01-01")
        while (date <= d("2027-02-28")) {
            val weekend = date.dayOfWeek.value >= 6
            if (weekend && random.nextInt(3) == 0) {
                // A few interventions, some across midnight, some on the nights the clocks change.
                val interventions = (1..random.nextInt(1, 3)).map {
                    val start = t(random.nextInt(0, 24), random.nextInt(0, 60))
                    Intervention(id++, start + 1440 * random.nextInt(0, 2), null).let { i -> i.copy(end = i.start + random.nextInt(5, 240)) }
                }
                days[date.toString()] = DayRecord(onCall = true, interventions = interventions)
            } else if (!weekend && ItalianHolidays.holiday(date) == null && random.nextInt(6) == 0) {
                // An even day: the recovery covers part of it, the rest is worked exactly.
                val recovery = 30 * random.nextInt(1, 17)
                val required = 8 * h - recovery
                val presence = required + if (required > 6 * h) 30 else 0
                days[date.toString()] = DayRecord(entry = t(8), exit = t(8) + presence, recoveryMinutes = recovery)
            }
            date = date.plusDays(1)
        }
        val data = StaccoData(onCall = OnCallSettings(enabled = true), days = days)
        val calendar = WorkCalendar.from(data)
        val parsed = data.parsedDays()
        var expiredBefore = 0
        var today = d("2026-01-01")
        while (today <= d("2027-02-28")) {
            val ledger = RecoveryCalculator.compute(data, today, rome)
            val earned = parsed.filterKeys { it <= today }.entries.sumOf { (day, r) -> r.interventions.sumOf { OnCall.credited(day, it, rome) } }
            val used = parsed.keys.filter { it <= today }.sumOf { OnCall.recoveryCost(calendar, it) }
            assertEquals("$today", earned - used - ledger.expired, ledger.balance)
            assertTrue("$today", ledger.expired >= expiredBefore)
            assertTrue("$today", ledger.lots.all { !it.expiry.isBefore(today) && it.left in 1..it.earned })
            assertTrue("$today", ledger.lots.none { it.month < YearMonth.from(today).minusMonths(2) })
            assertEquals("$today", ledger.lots.sortedBy { it.expiry }, ledger.lots)
            expiredBefore = ledger.expired
            today = today.plusDays(1)
        }
        // Every even day stays even: the recovery account pays, the hour bank never moves.
        assertTrue(days.values.any { it.recoveryMinutes > 0 })
        assertEquals(BankLedger.EMPTY.balance, BankCalculator.compute(data, d("2027-02-28")).balance)
        assertTrue(BankCalculator.compute(data, d("2027-02-28")).movements.all { it.amount == 0 })
    }

    @Test
    fun `an even day with recovery never touches the bank, whatever the amount`() {
        val monday = d("2026-11-16")
        for (recovery in 30..8 * h step 30) {
            val required = 8 * h - recovery
            val presence = required + if (required > 6 * h) 30 else 0
            fun delta(extra: Int): Int? {
                val data = StaccoData(onCall = OnCallSettings(enabled = true), days = mapOf(monday.toString() to DayRecord(entry = t(8), exit = t(8) + presence + extra, recoveryMinutes = recovery).let { if (required == 0) it.copy(entry = null, exit = null) else it }))
                return BankCalculator.compute(data, monday).todayDelta
            }
            if (required == 0) {
                assertEquals(null, delta(0))
                continue
            }
            assertEquals("$recovery", 0, delta(0))
            assertEquals("$recovery", 0, delta(20))
            assertEquals("$recovery", -15, delta(-15))
        }
    }
}
