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
    Glasses(MedalGroup.Distance, 1),
    Flower(MedalGroup.WaterStreak, 1),
    Nightcap(MedalGroup.SleepStreak, 1),
    Backpack(MedalGroup.Route, 0),
}

/** Bird kinds; each opens at a bird level. */
enum class BirdSpecies(val minLevel: Int) { Sparrow(0), Owl(3), Parrot(6), Eagle(10) }

/** Feather colours bought with seeds. */
enum class BirdColor(val cost: Long) { Default(0), Sky(30), Rose(30), Mint(30), Plum(60), Night(100) }

/** Songs bought with seeds. */
enum class BirdSound(val cost: Long) { Chirp(0), Trill(20), Tweet(20), Coo(40) }

data class BirdState(
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
    val species: BirdSpecies = BirdSpecies.Sparrow,
    val color: BirdColor = BirdColor.Default,
) {
    /** The look picked in the bird dialog: kind, colour and the accessories not hidden. */
    fun styled(extras: ExtraSettings): BirdState = copy(
        species = BirdSpecies.entries.firstOrNull { it.name == extras.birdSpecies && level >= it.minLevel } ?: BirdSpecies.Sparrow,
        color = BirdColor.entries.firstOrNull { it.name == extras.birdColor && it.name in Seeds.owned(extras) } ?: BirdColor.Default,
        items = items.filterNot { it.name in extras.hiddenItems }.toSet(),
    )
}

/** Seeds: one for every 1,000 steps walked, spent on colours and songs or fed to the bird. */
object Seeds {

    const val STEPS_PER_SEED = 1_000L

    fun available(totalSteps: Long, extras: ExtraSettings): Long = (totalSteps / STEPS_PER_SEED - extras.seedsSpent).coerceAtLeast(0L)

    /** Looks that need no seeds plus the ones bought. */
    fun owned(extras: ExtraSettings): Set<String> =
        extras.unlockedLooks + BirdColor.entries.filter { it.cost == 0L }.map { it.name } + BirdSound.entries.filter { it.cost == 0L }.map { it.name }
}

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
