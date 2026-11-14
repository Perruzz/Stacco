package io.github.perruzz.stacco.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WorkCalendarTest {

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `easter dates`() {
        assertEquals(d("2024-03-31"), ItalianHolidays.easterSunday(2024))
        assertEquals(d("2025-04-20"), ItalianHolidays.easterSunday(2025))
        assertEquals(d("2026-04-05"), ItalianHolidays.easterSunday(2026))
        assertEquals(d("2027-03-28"), ItalianHolidays.easterSunday(2027))
    }

    @Test
    fun `italian holidays`() {
        assertEquals(Holiday.EASTER_MONDAY, ItalianHolidays.holiday(d("2026-04-06")))
        assertEquals(Holiday.IMMACULATE, ItalianHolidays.holiday(d("2026-12-08")))
        assertEquals(Holiday.ST_FRANCIS, ItalianHolidays.holiday(d("2026-10-04")))
        assertNull(ItalianHolidays.holiday(d("2025-10-04")))
        assertNull(ItalianHolidays.holiday(d("2026-11-14")))
    }

    @Test
    fun `scheduled days and types`() {
        val days = mapOf(
            d("2026-11-16") to DayRecord(type = DayType.SMART),
            d("2026-11-21") to DayRecord(entry = 540, exit = 720), // worked on a Saturday
        )
        val cal = WorkCalendar(WorkSettings(), days)
        assertTrue(cal.isScheduled(d("2026-11-13"))) // Friday
        assertFalse(cal.isScheduled(d("2026-11-14"))) // Saturday
        assertEquals(DayType.SMART, cal.effectiveType(d("2026-11-16")))
        assertEquals(0, cal.required(d("2026-11-16")))
        assertEquals(DayType.OFFICE, cal.effectiveType(d("2026-11-21")))
        assertEquals(180, cal.delta(d("2026-11-21")))
        assertEquals(DayType.HOLIDAY, cal.effectiveType(d("2026-12-08")))
        assertNull(cal.effectiveType(d("2026-11-15")))
    }

    @Test
    fun `office days left in a range`() {
        val days = mapOf(d("2026-11-26") to DayRecord(type = DayType.SMART))
        val cal = WorkCalendar(WorkSettings(), days)
        // 23..30 November: Mon-Fri except the smart Thursday, plus Monday 30.
        assertEquals(5, cal.officeDays(d("2026-11-23"), d("2026-11-30")))
    }

    @Test
    fun `smart quota`() {
        val days = mapOf(
            d("2026-11-02") to DayRecord(type = DayType.SMART),
            d("2026-11-03") to DayRecord(type = DayType.SMART),
            d("2026-12-01") to DayRecord(type = DayType.SMART),
        )
        val cal = WorkCalendar(WorkSettings(), days)
        val month = cal.smartRange(d("2026-11-20"), SmartPeriod.MONTH, DayOfWeek.MONDAY)
        assertEquals(2, cal.smartUsed(month))
        val week = cal.smartRange(d("2026-11-04"), SmartPeriod.WEEK, DayOfWeek.MONDAY)
        assertEquals(2, cal.smartUsed(week))
    }

    @Test
    fun `expiry periods`() {
        assertEquals(d("2026-12-31"), Periods.expiryEnd(d("2026-11-14"), ExpiryPeriod.QUARTERLY))
        assertEquals(d("2026-03-31"), Periods.expiryEnd(d("2026-01-10"), ExpiryPeriod.QUARTERLY))
        assertEquals(d("2026-11-30"), Periods.expiryEnd(d("2026-11-14"), ExpiryPeriod.MONTHLY))
        assertEquals(d("2026-12-31"), Periods.expiryEnd(d("2026-07-01"), ExpiryPeriod.SEMIANNUAL))
        assertNull(Periods.expiryEnd(d("2026-07-01"), ExpiryPeriod.NEVER))
        assertTrue(Periods.isExpiryEnd(d("2026-09-30"), ExpiryPeriod.QUARTERLY))
        assertFalse(Periods.isExpiryEnd(d("2026-10-31"), ExpiryPeriod.QUARTERLY))
    }
}
