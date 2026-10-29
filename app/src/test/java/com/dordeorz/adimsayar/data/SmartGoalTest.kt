package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class SmartGoalTest {

    private val today = LocalDate.of(2026, 11, 2)

    private fun days(count: Int, steps: (Int) -> Long) = (1..count).associate { today.minusDays(it.toLong()) to steps(it) }

    @Test
    fun tooFewDaysGivesNoSuggestion() {
        assertNull(SmartGoal.suggest(days(SmartGoal.MIN_DAYS - 1) { 12_000L }, today, 10_000L))
    }

    @Test
    fun raisesAtMostTenPercent() {
        assertEquals(11_000L, SmartGoal.suggest(days(28) { 20_000L }, today, 10_000L))
    }

    @Test
    fun lowersAtMostTenPercent() {
        assertEquals(9_000L, SmartGoal.suggest(days(28) { 2_000L }, today, 10_000L))
    }

    @Test
    fun movesTowardMedianPlusTenPercent() {
        assertEquals(9_500L, SmartGoal.suggest(days(28) { 8_700L }, today, 9_000L))
    }

    @Test
    fun unchangedGoalGivesNull() {
        assertNull(SmartGoal.suggest(days(28) { 9_100L }, today, 10_000L))
    }

    @Test
    fun ignoresTodayAndEmptyDays() {
        val history = days(28) { if (it % 2 == 0) 0L else 20_000L } + (today to 50_000L)
        assertEquals(11_000L, SmartGoal.suggest(history, today, 10_000L))
    }

    @Test
    fun neverBelowMinimum() {
        assertEquals(3_000L, SmartGoal.suggest(days(28) { 100L }, today, 3_000L) ?: 3_000L)
    }

    @Test
    fun weekStartsOnSettingDay() {
        val wednesday = LocalDate.of(2026, 10, 28)
        assertEquals(LocalDate.of(2026, 10, 26), SmartGoal.weekOf(wednesday, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 10, 25), SmartGoal.weekOf(wednesday, DayOfWeek.SUNDAY))
    }
}
