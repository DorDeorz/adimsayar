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
    fun goalDaysUseEachDaysOwnGoal() {
        // 7,000 steps every day: below the 10,000 goal, above a weekend goal of 6,000.
        val history = (-28L..140L).associate { monday.plusDays(it) to 7_000L }
        val weekendGoal = { date: LocalDate -> if (date.dayOfWeek >= DayOfWeek.SATURDAY) 6_000L else 10_000L }
        val start = (0L..19L).map { monday.plusWeeks(it) }
            .first { week -> ChallengeMath.week(history, week, week.plusDays(6), 10_000).any { it.kind == ChallengeKind.GoalDays } }
        val plain = ChallengeMath.week(history, start, start.plusDays(6), 10_000).first { it.kind == ChallengeKind.GoalDays }
        val weekend = ChallengeMath.week(history, start, start.plusDays(6), 10_000, weekendGoal).first { it.kind == ChallengeKind.GoalDays }
        assertEquals(0L, plain.progress)
        assertEquals(2L, weekend.progress)
        // Only Saturday has passed: one goal day so far.
        val saturday = ChallengeMath.week(history, start, start.plusDays(5), 10_000, weekendGoal).first { it.kind == ChallengeKind.GoalDays }
        assertEquals(1L, saturday.progress)
    }

    @Test
    fun summaryCountsCompletedAcrossWeeks() {
        val history = (0L..20L).associate { monday.plusDays(it) to 30_000L }
        val summary = ChallengeMath.summary(history, monday.plusDays(20), 10_000, DayOfWeek.MONDAY)
        assertEquals(3, summary.previous.size)
        assertTrue(summary.completed >= 6)
    }
}
