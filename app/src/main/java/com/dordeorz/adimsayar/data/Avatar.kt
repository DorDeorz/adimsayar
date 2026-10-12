package com.dordeorz.adimsayar.data

import java.time.LocalDate

enum class BirdStage(val minLevel: Int) { Egg(0), Hatchling(1), Chick(3), Fledgling(6), Bird(10), Elder(15) }

enum class BirdMood { Sleepy, Sad, Calm, Happy, Excited }

enum class BirdItem(val group: MedalGroup, val tier: Int) {
    Scarf(MedalGroup.Streak, 1),
    Ribbon(MedalGroup.WeeklyGoal, 1),
    Sun(MedalGroup.EarlyBird, 1),
    Cap(MedalGroup.DailySteps, 3),
    Crown(MedalGroup.Total, 2),
}

class BirdState(
    val stage: BirdStage,
    val mood: BirdMood,
    val items: Set<BirdItem>,
    val nextStage: BirdStage?,
    val stepsToNext: Long,
    val stageFraction: Float,
    val level: Int = 0,
    val levelFraction: Float = 0f,
    val stepsToNextLevel: Long = 0L,
    val leveledUpToday: Boolean = false,
)

object Avatar {

    const val WAKE_HOUR = 7

    fun state(
        achievements: Achievements,
        days: Map<LocalDate, Long>,
        freezes: Set<LocalDate>,
        today: LocalDate,
        hour: Int,
        dailyGoal: Long,
    ): BirdState {
        val total = achievements.records.total
        val level = achievements.level
        val stage = BirdStage.entries.last { achievements.level.number >= it.minLevel }
        val next = BirdStage.entries.firstOrNull { it.minLevel > stage.minLevel }
        val from = AchievementMath.levelStart(stage.minLevel)
        val to = next?.let { AchievementMath.levelStart(it.minLevel) }
        val items = BirdItem.entries.filter { item ->
            achievements.medals.any { it.group == item.group && it.tier == item.tier && it.earned }
        }.toSet()
        return BirdState(
            stage = stage,
            mood = mood(days, freezes, today, hour, dailyGoal, achievements.records.longestStreak),
            items = items,
            nextStage = next,
            stepsToNext = if (to == null) 0L else (to - total).coerceAtLeast(0L),
            stageFraction = if (to == null) 1f else ((total - from).toFloat() / (to - from)).coerceIn(0f, 1f),
            level = level.number,
            levelFraction = ((total - level.from).toFloat() / (level.to - level.from)).coerceIn(0f, 1f),
            stepsToNextLevel = (level.to - total).coerceAtLeast(0L),
            leveledUpToday = AchievementMath.level(total - (days[today] ?: 0L)).number < level.number,
        )
    }

    fun mood(days: Map<LocalDate, Long>, freezes: Set<LocalDate>, today: LocalDate, hour: Int, dailyGoal: Long, longestStreak: Int): BirdMood {
        val steps = days[today] ?: 0L
        val yesterday = today.minusDays(1)
        val missedYesterday = (days[yesterday] ?: 0L) < dailyGoal && yesterday !in freezes
        return when {
            steps >= dailyGoal -> BirdMood.Excited
            missedYesterday && longestStreak > 0 && steps * 2 < dailyGoal && hour >= WAKE_HOUR -> BirdMood.Sad
            hour < WAKE_HOUR || steps * 10 < dailyGoal -> BirdMood.Sleepy
            steps * 2 < dailyGoal -> BirdMood.Calm
            else -> BirdMood.Happy
        }
    }
}
