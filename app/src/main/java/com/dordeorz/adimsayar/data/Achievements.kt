package com.dordeorz.adimsayar.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class MedalGroup(val targets: List<Long>) {
    DailySteps(listOf(5_000L, 10_000L, 15_000L, 20_000L, 30_000L)),
    Streak(listOf(3L, 7L, 14L, 30L, 100L)),
    WeeklyGoal(listOf(1L, 4L, 12L, 26L, 52L)),
    Total(listOf(100_000L, 500_000L, 1_000_000L, 2_500_000L, 5_000_000L, 10_000_000L)),
    EarlyBird(listOf(1L, 7L, 30L, 100L, 365L)),
}

const val EARLY_BIRD_HOUR = 8
const val EARLY_BIRD_STEPS = 1_000L

data class Medal(
    val group: MedalGroup,
    val tier: Int,
    val target: Long,
    val progress: Long,
    val earnedOn: LocalDate?,
    val times: Int = 0,
) {
    val earned: Boolean get() = earnedOn != null
}

data class WeekSteps(val start: LocalDate, val steps: Long)

data class MonthSteps(val month: YearMonth, val steps: Long)

data class Records(
    val total: Long,
    val activeDays: Int,
    val goalDays: Int,
    val bestDay: DaySteps?,
    val bestWeek: WeekSteps?,
    val bestMonth: MonthSteps?,
    val currentStreak: Int,
    val longestStreak: Int,
    val metWeeks: Int,
)

data class Level(val number: Int, val from: Long, val to: Long)

data class Achievements(val records: Records, val medals: List<Medal>, val level: Level)

fun weekStartOf(date: LocalDate, firstDay: DayOfWeek = DayOfWeek.MONDAY): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(firstDay))

object AchievementMath {

    fun compute(
        days: Map<LocalDate, Long>,
        today: LocalDate,
        dailyGoal: Long,
        weeklyGoal: Long,
        firstDay: DayOfWeek = DayOfWeek.MONDAY,
        earlyBirdDays: Set<LocalDate>? = null,
    ): Achievements {
        val first = days.keys.filter { !it.isAfter(today) }.minOrNull() ?: today
        var total = 0L
        var activeDays = 0
        var goalDays = 0
        var bestDay: DaySteps? = null
        var bestWeek: WeekSteps? = null
        var streak = 0
        var longestStreak = 0
        var bestMonth: MonthSteps? = null
        var month = YearMonth.from(first)
        var monthSum = 0L
        var weekStart = weekStartOf(first, firstDay)
        var weekSum = 0L
        var weekMet = false
        var metWeeks = 0
        var earlyDays = 0
        val earned = HashMap<Pair<MedalGroup, Long>, LocalDate>()
        val dailyTimes = IntArray(MedalGroup.DailySteps.targets.size)
        val streakTimes = IntArray(MedalGroup.Streak.targets.size)

        fun earn(group: MedalGroup, value: Long, date: LocalDate) {
            for (target in group.targets) {
                val key = group to target
                if (value >= target && key !in earned) earned[key] = date
            }
        }

        var date = first
        while (!date.isAfter(today)) {
            val steps = days[date] ?: 0L
            if (YearMonth.from(date) != month) {
                month = YearMonth.from(date)
                monthSum = 0L
            }
            if (weekStartOf(date, firstDay) != weekStart) {
                weekStart = weekStartOf(date, firstDay)
                weekSum = 0L
                weekMet = false
            }
            total += steps
            weekSum += steps
            monthSum += steps
            if (steps > 0L) activeDays++
            if (steps > (bestDay?.steps ?: 0L)) bestDay = DaySteps(date, steps)
            if (weekSum > (bestWeek?.steps ?: 0L)) bestWeek = WeekSteps(weekStart, weekSum)
            if (monthSum > (bestMonth?.steps ?: 0L)) bestMonth = MonthSteps(month, monthSum)
            if (steps >= dailyGoal) {
                goalDays++
                streak++
                longestStreak = maxOf(longestStreak, streak)
            } else if (date != today) {
                streak = 0
            }
            if (!weekMet && weekSum >= weeklyGoal) {
                weekMet = true
                metWeeks++
                earn(MedalGroup.WeeklyGoal, metWeeks.toLong(), date)
            }
            earn(MedalGroup.DailySteps, steps, date)
            earn(MedalGroup.Streak, streak.toLong(), date)
            MedalGroup.DailySteps.targets.forEachIndexed { i, target -> if (steps >= target) dailyTimes[i]++ }
            if (steps >= dailyGoal) {
                MedalGroup.Streak.targets.forEachIndexed { i, target -> if (streak.toLong() == target) streakTimes[i]++ }
            }
            earn(MedalGroup.Total, total, date)
            if (earlyBirdDays != null && date in earlyBirdDays) {
                earlyDays++
                earn(MedalGroup.EarlyBird, earlyDays.toLong(), date)
            }
            date = date.plusDays(1)
        }

        val progress = mapOf(
            MedalGroup.DailySteps to (bestDay?.steps ?: 0L),
            MedalGroup.Streak to longestStreak.toLong(),
            MedalGroup.WeeklyGoal to metWeeks.toLong(),
            MedalGroup.Total to total,
            MedalGroup.EarlyBird to earlyDays.toLong(),
        )
        val groups = MedalGroup.entries.filter { it != MedalGroup.EarlyBird || earlyBirdDays != null }
        val medals = groups.flatMap { group ->
            group.targets.mapIndexed { tier, target ->
                val times = when (group) {
                    MedalGroup.DailySteps -> dailyTimes[tier]
                    MedalGroup.Streak -> streakTimes[tier]
                    MedalGroup.WeeklyGoal -> (metWeeks / target).toInt()
                    MedalGroup.EarlyBird -> (earlyDays / target).toInt()
                    MedalGroup.Total -> 0
                }
                Medal(group, tier, target, progress.getValue(group), earned[group to target], times)
            }
        }
        val records = Records(
            total = total,
            activeDays = activeDays,
            goalDays = goalDays,
            bestDay = bestDay,
            bestWeek = bestWeek,
            bestMonth = bestMonth,
            currentStreak = streak,
            longestStreak = longestStreak,
            metWeeks = metWeeks,
        )
        return Achievements(records, medals, level(total))
    }

    fun level(total: Long): Level {
        var number = 0
        while (levelStart(number + 1) <= total) number++
        return Level(number, levelStart(number), levelStart(number + 1))
    }

    private fun levelStart(level: Int): Long = LEVEL_STEP * level * (level + 1) / 2

    private const val LEVEL_STEP = 25_000L
}
