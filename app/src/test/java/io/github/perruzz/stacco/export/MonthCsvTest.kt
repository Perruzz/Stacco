package io.github.perruzz.stacco.export

import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.StaccoData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthCsvTest {

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

    private val data = StaccoData(
        onboardingDone = true,
        days = mapOf(
            "2026-11-16" to DayRecord(entry = 8 * 60 + 40, breakOut = 12 * 60 + 35, breakIn = 13 * 60 + 10, exit = 17 * 60 + 31, note = "riunione; lunga"),
            "2026-11-17" to DayRecord(type = DayType.SMART),
            "2026-11-20" to DayRecord(type = DayType.VACATION),
        ),
    )

    private fun lines() = MonthCsv.build(data, YearMonth.of(2026, 11), labels, today = LocalDate.parse("2026-11-30"))
        .removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }

    @Test
    fun `one row per day plus headers and totals`() {
        val lines = lines()
        assertEquals("Data;Giorno;Tipo;Turno;Entrata;Inizio pausa;Fine pausa;Uscita;Permesso;Pausa;Lavorato;Previsto;Saldo;Note", lines.first())
        val monday = lines.first { it.startsWith("2026-11-16") }
        // 8:40 -> 17:31 with 35m of lunch: 8h16, +16 over the 8h day; the note keeps its semicolon.
        assertEquals("2026-11-16;MON;OFFICE;;08:40;12:35;13:10;17:31;;0:35;8:16;8:00;+0:16;\"riunione; lunga\"", monday)
        assertTrue(lines.any { it.startsWith("2026-11-17;TUE;SMART;") })
        assertTrue(lines.any { it.startsWith("2026-11-01;SUN;ALL_SAINTS;") })
        // Header + 30 days + an empty separator row + totals row + 6 counters + bank line.
        assertEquals(1 + 30 + 1 + 1 + 6 + 1, lines.size)
    }

    @Test
    fun `totals match the days`() {
        val lines = lines()
        assertTrue(lines.any { it.startsWith("Totale;") && it.contains(";8:16;") && it.contains(";+0:16;") })
        assertTrue("In sede;1" in lines)
        assertTrue("Smart;1" in lines)
        assertTrue("Ferie;1" in lines)
        assertTrue("Banca ore al 2026-11-30;+0:16" in lines)
    }

    @Test
    fun `hours and clock formats`() {
        assertEquals("8:05", MonthCsv.hours(485))
        assertEquals("-1:00", MonthCsv.hours(-60, signed = true))
        assertEquals("+0:30", MonthCsv.hours(30, signed = true))
        assertEquals("0:00", MonthCsv.hours(0, signed = true))
        assertEquals("07:00 (+1)", MonthCsv.clock(1440 + 7 * 60))
        assertEquals("", MonthCsv.clock(null))
    }
}
