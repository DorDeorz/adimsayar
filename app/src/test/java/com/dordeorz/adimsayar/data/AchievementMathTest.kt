package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class AchievementMathTest {

    private val monday = LocalDate.of(2026, 10, 5)

    private fun days(vararg steps: Long): Map<LocalDate, Long> =
        steps.withIndex().associate { (i, s) -> monday.plusDays(i.toLong()) to s }

    private fun medal(result: Achievements, group: MedalGroup, target: Long) =
        result.medals.first { it.group == group && it.target == target }

    @Test
    fun emptyHistoryEarnsNothing() {
        val result = AchievementMath.compute(emptyMap(), monday, 10_000, 70_000)
        assertEquals(0L, result.records.total)
        assertNull(result.records.bestDay)
        assertTrue(result.medals.none { it.earned })
    }

    @Test
    fun streakCountsConsecutiveGoalDaysAndKeepsWhileTodayIsOpen() {
        val result = AchievementMath.compute(days(12_000, 11_000, 10_000, 3_000), monday.plusDays(3), 10_000, 70_000)
        assertEquals(3, result.records.currentStreak)
        assertEquals(3, result.records.longestStreak)
        assertEquals(monday.plusDays(2), medal(result, MedalGroup.Streak, 3).earnedOn)
        assertFalse(medal(result, MedalGroup.Streak, 7).earned)
    }

    @Test
    fun missedPastDayBreaksStreak() {
        val result = AchievementMath.compute(days(12_000, 11_000, 0, 10_500, 2_000), monday.plusDays(5), 10_000, 70_000)
        assertEquals(0, result.records.currentStreak)
        assertEquals(2, result.records.longestStreak)
        assertEquals(3, result.records.goalDays)
    }

    @Test
    fun dailyMedalDateIsFirstDayReachingTarget() {
        val result = AchievementMath.compute(days(4_000, 16_000, 31_000), monday.plusDays(2), 10_000, 70_000)
        assertEquals(monday.plusDays(1), medal(result, MedalGroup.DailySteps, 15_000).earnedOn)
        assertEquals(monday.plusDays(2), medal(result, MedalGroup.DailySteps, 30_000).earnedOn)
        assertEquals(DaySteps(monday.plusDays(2), 31_000), result.records.bestDay)
    }

    @Test
    fun weeklyGoalCountsEachMondayWeekOnce() {
        val steps = LongArray(14) { 6_000 }
        val result = AchievementMath.compute(days(*steps), monday.plusDays(13), 10_000, 40_000)
        assertEquals(2, result.records.metWeeks)
        assertEquals(monday.plusDays(6), medal(result, MedalGroup.WeeklyGoal, 1).earnedOn)
        assertEquals(WeekSteps(monday, 42_000), result.records.bestWeek)
    }

    @Test
    fun goalChangeAppliesRetroactively() {
        val history = days(8_000, 8_000, 8_000)
        val strict = AchievementMath.compute(history, monday.plusDays(2), 10_000, 70_000)
        val relaxed = AchievementMath.compute(history, monday.plusDays(2), 8_000, 70_000)
        assertFalse(medal(strict, MedalGroup.Streak, 3).earned)
        assertTrue(medal(relaxed, MedalGroup.Streak, 3).earned)
    }

    @Test
    fun totalMedalUsesRunningSum() {
        val result = AchievementMath.compute(days(60_000, 50_000), monday.plusDays(1), 10_000, 70_000)
        assertEquals(monday.plusDays(1), medal(result, MedalGroup.Total, 100_000).earnedOn)
        assertEquals(110_000L, result.records.total)
    }

    @Test
    fun levelsGrowWithTotalSteps() {
        assertEquals(Level(0, 0, 25_000), AchievementMath.level(0))
        assertEquals(Level(1, 25_000, 75_000), AchievementMath.level(25_000))
        assertEquals(Level(2, 75_000, 150_000), AchievementMath.level(149_999))
        assertEquals(10, AchievementMath.level(1_400_000).number)
    }

    @Test
    fun weekCanStartOnSunday() {
        val steps = LongArray(7) { 10_000 }
        val result = AchievementMath.compute(days(*steps), monday.plusDays(6), 10_000, 60_000, DayOfWeek.SUNDAY)
        assertEquals(WeekSteps(monday.minusDays(1), 60_000), result.records.bestWeek)
        assertEquals(monday.plusDays(5), medal(result, MedalGroup.WeeklyGoal, 1).earnedOn)
    }

    @Test
    fun bestMonthSumsCalendarMonth() {
        val history = mapOf(LocalDate.of(2026, 9, 30) to 5_000L, LocalDate.of(2026, 10, 1) to 3_000L, LocalDate.of(2026, 10, 2) to 4_000L)
        val result = AchievementMath.compute(history, LocalDate.of(2026, 10, 2), 10_000, 70_000)
        assertEquals(MonthSteps(YearMonth.of(2026, 10), 7_000), result.records.bestMonth)
    }

    @Test
    fun earlyBirdMedalsNeedHourlyData() {
        val result = AchievementMath.compute(days(5_000, 6_000), monday.plusDays(1), 10_000, 70_000)
        assertTrue(result.medals.none { it.group == MedalGroup.EarlyBird })
    }

    @Test
    fun earlyBirdCountsMorningDaysUpToToday() {
        val mornings = (0L..7L).map { monday.plusDays(it) }.toSet()
        val result = AchievementMath.compute(days(*LongArray(7) { 4_000 }), monday.plusDays(6), 10_000, 70_000, earlyBirdDays = mornings)
        assertEquals(monday, medal(result, MedalGroup.EarlyBird, 1).earnedOn)
        assertEquals(monday.plusDays(6), medal(result, MedalGroup.EarlyBird, 7).earnedOn)
        assertEquals(7L, medal(result, MedalGroup.EarlyBird, 30).progress)
        assertFalse(medal(result, MedalGroup.EarlyBird, 30).earned)
    }

    @Test
    fun medalsCountHowManyTimesTheyWereEarned() {
        val result = AchievementMath.compute(
            days(6_000, 11_000, 12_000, 13_000, 2_000, 10_000, 10_000, 10_000),
            monday.plusDays(7),
            10_000,
            30_000,
        )
        assertEquals(7, medal(result, MedalGroup.DailySteps, 5_000).times)
        assertEquals(6, medal(result, MedalGroup.DailySteps, 10_000).times)
        assertEquals(2, medal(result, MedalGroup.Streak, 3).times)
        assertEquals(1, medal(result, MedalGroup.WeeklyGoal, 1).times)
        assertEquals(0, medal(result, MedalGroup.Total, 100_000).times)
    }
}
