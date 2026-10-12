package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ChallengeMathTest {

    private val monday = LocalDate.of(2026, 10, 5)

    @Test
    fun picksThreeDistinctChallengesStablyPerWeek() {
        val history = (1L..28L).associate { monday.minusDays(it) to 8_000L }
        val first = ChallengeMath.week(history, monday, monday.plusDays(6), 10_000)
        val again = ChallengeMath.week(history, monday, monday.plusDays(6), 10_000)
        assertEquals(3, first.size)
        assertEquals(first, again)
        assertEquals(3, first.map { it.kind }.toSet().size)
        assertTrue(!(ChallengeKind.WeekTotal in first.map { it.kind } && ChallengeKind.BeatLastWeek in first.map { it.kind }))
    }

    @Test
    fun countsProgressOnlyUntilToday() {
        val history = (0L..6L).associate { monday.plusDays(it) to 20_000L }
        val week = ChallengeMath.week(history, monday, monday.plusDays(1), 10_000)
        week.filter { it.kind == ChallengeKind.GoalDays }.forEach { assertEquals(2L, it.progress) }
        week.filter { it.kind == ChallengeKind.WeekTotal }.forEach { assertEquals(40_000L, it.progress) }
    }

    @Test
    fun summaryCountsCompletedAcrossWeeks() {
        val history = (0L..20L).associate { monday.plusDays(it) to 30_000L }
        val summary = ChallengeMath.summary(history, monday.plusDays(20), 10_000, DayOfWeek.MONDAY)
        assertEquals(3, summary.previous.size)
        assertTrue(summary.completed >= 6)
    }
}
