package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class MonthReportTest {

    @Test
    fun currentMonthStopsAtToday() {
        val month = YearMonth.of(2026, 10)
        val today = LocalDate.of(2026, 10, 10)
        val history = (1..10).associate { month.atDay(it) to it * 1_000L } + (month.minusMonths(1).atDay(5) to 50_000L)
        val report = MonthReport.build(month, today, history, mapOf(month.atDay(2) to 500, month.atDay(3) to 1_500), emptyList(), 8_000L)
        assertEquals(10, report.days.size)
        assertEquals(55_000L, report.total)
        assertEquals(5_500L, report.average)
        assertEquals(3, report.goalDays)
        assertEquals(10_000L, report.best!!.steps)
        assertEquals(50_000L, report.previousTotal)
        assertEquals(1_000, report.waterAverage)
        assertEquals(listOf(month.atDay(1) to 28_000L, month.atDay(8) to 27_000L), report.weeks)
    }

    @Test
    fun goalDaysUseEachDaysOwnGoal() {
        val month = YearMonth.of(2026, 10)
        // 3 October 2026 is a Saturday, 4 October a Sunday, 5 October a Monday.
        val history = mapOf(month.atDay(3) to 6_500L, month.atDay(4) to 5_000L, month.atDay(5) to 6_500L, month.atDay(6) to 9_000L)
        val goalOf = { date: LocalDate -> if (date.dayOfWeek >= DayOfWeek.SATURDAY) 6_000L else 8_000L }
        val report = MonthReport.build(month, LocalDate.of(2026, 10, 6), history, emptyMap(), emptyList(), 8_000L, goalOf)
        assertEquals(listOf(month.atDay(3), month.atDay(6)), report.days.filter { it.reached }.map { it.date })
        assertEquals(2, report.goalDays)
        assertEquals(1, MonthReport.build(month, LocalDate.of(2026, 10, 6), history, emptyMap(), emptyList(), 8_000L).goalDays)
    }

    @Test
    fun weightNeedsTwoEntriesForChange() {
        val month = YearMonth.of(2026, 9)
        val one = MonthReport.build(month, LocalDate.of(2026, 10, 28), emptyMap(), emptyMap(), listOf(Weight(month.atDay(3), 80.0)), 10_000L)
        assertEquals(80.0, one.weightStart!!.kg, 0.0)
        assertNull(one.weightEnd)
        assertEquals(30, one.days.size)
        assertNull(one.best)
        assertNull(one.previousTotal)
    }
}
