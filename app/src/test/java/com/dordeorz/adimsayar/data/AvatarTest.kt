package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class AvatarTest {

    private val today = LocalDate.of(2026, 10, 12)
    private val yesterday = today.minusDays(1)
    private val goal = 10_000L

    private fun mood(todaySteps: Long, yesterdaySteps: Long, hour: Int = 12, frozen: Boolean = false) = Avatar.mood(
        mapOf(today to todaySteps, yesterday to yesterdaySteps),
        if (frozen) setOf(yesterday) else emptySet(),
        today,
        hour,
        goal,
        longestStreak = 3,
    )

    @Test
    fun moodFollowsTodayAndYesterday() {
        assertEquals(BirdMood.Excited, mood(10_000, 0))
        assertEquals(BirdMood.Sad, mood(2_000, 3_000))
        assertEquals(BirdMood.Sleepy, mood(500, 12_000))
        assertEquals(BirdMood.Calm, mood(3_000, 12_000))
        assertEquals(BirdMood.Happy, mood(6_000, 3_000))
        assertEquals(BirdMood.Sleepy, mood(4_000, 12_000, hour = 5))
    }

    @Test
    fun frozenYesterdayIsNotSad() {
        assertEquals(BirdMood.Calm, mood(2_000, 3_000, frozen = true))
    }

    @Test
    fun stageAndItemsComeFromAchievements() {
        val days = (0L until 20L).associate { today.minusDays(it) to 21_000L }
        val achievements = AchievementMath.compute(days, today, goal, 70_000L, DayOfWeek.MONDAY)
        val bird = Avatar.state(achievements, days, emptySet(), today, 12, goal)
        assertEquals(BirdStage.Chick, bird.stage)
        assertEquals(setOf(BirdItem.Scarf, BirdItem.Cap), bird.items)
        assertEquals(AchievementMath.levelStart(BirdStage.Fledgling.minLevel) - 420_000L, bird.stepsToNext)
    }
}
