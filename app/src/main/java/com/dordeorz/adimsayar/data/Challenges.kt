package com.dordeorz.adimsayar.data

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

enum class ChallengeKind { WeekTotal, GoalDays, BigDay, EveryDay, Weekend, BeatLastWeek }

data class Challenge(val kind: ChallengeKind, val target: Long, val progress: Long, val threshold: Long = 0L) {
    val done: Boolean get() = progress >= target
}

data class ChallengeSummary(val current: List<Challenge>, val previous: List<Challenge>, val completed: Int)

object ChallengeMath {

    private const val PER_WEEK = 3
    private const val BASELINE_DAYS = 28L
    private const val WEEK_DAYS = 7L

    fun summary(days: Map<LocalDate, Long>, today: LocalDate, dailyGoal: Long, firstDay: DayOfWeek): ChallengeSummary {
        val start = weekStartOf(today, firstDay)
        val first = days.filterValues { it > 0L }.keys.filter { !it.isAfter(today) }.minOrNull()?.let { weekStartOf(it, firstDay) } ?: start
        var completed = 0
        var week = first
        while (!week.isAfter(start)) {
            completed += week(days, week, today, dailyGoal).count { it.done }
            week = week.plusWeeks(1)
        }
        val previous = if (first < start) week(days, start.minusWeeks(1), today, dailyGoal) else emptyList()
        return ChallengeSummary(week(days, start, today, dailyGoal), previous, completed)
    }

    fun week(days: Map<LocalDate, Long>, start: LocalDate, today: LocalDate, dailyGoal: Long): List<Challenge> {
        fun steps(date: LocalDate) = if (date.isAfter(today)) 0L else days[date] ?: 0L
        val before = (1L..BASELINE_DAYS).map { steps(start.minusDays(it)) }
        val average = before.sum() / BASELINE_DAYS
        val baseline = if (average > 0L) average else dailyGoal * 6 / 10
        val goalDaysBefore = (before.count { it >= dailyGoal } + 2) / 4
        val lastWeek = (1L..WEEK_DAYS).sumOf { steps(start.minusDays(it)) }
        val week = (0L until WEEK_DAYS).map { start.plusDays(it) }
        val weekSteps = week.map { steps(it) }
        val total = weekSteps.sum()
        val weekend = week.filter { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }.sumOf { steps(it) }
        val everyDay = roundTo((baseline * 6 / 10).coerceIn(2_000L, dailyGoal.coerceAtLeast(2_000L)), 500L)

        val candidates = buildList {
            add(Challenge(ChallengeKind.WeekTotal, roundTo((baseline * WEEK_DAYS * 11 / 10).coerceAtLeast(20_000L), 1_000L), total))
            add(Challenge(ChallengeKind.GoalDays, (goalDaysBefore + 1L).coerceIn(2L, WEEK_DAYS), weekSteps.count { it >= dailyGoal }.toLong()))
            add(Challenge(ChallengeKind.BigDay, roundTo(maxOf(dailyGoal * 13 / 10, baseline * 16 / 10), 500L), weekSteps.max()))
            add(Challenge(ChallengeKind.EveryDay, WEEK_DAYS, weekSteps.count { it >= everyDay }.toLong(), everyDay))
            add(Challenge(ChallengeKind.Weekend, roundTo((baseline * 2 * 12 / 10).coerceAtLeast(6_000L), 1_000L), weekend))
            if (lastWeek > 0L) add(Challenge(ChallengeKind.BeatLastWeek, lastWeek + 1L, total))
        }.shuffled(Random(start.toEpochDay()))

        val picked = ArrayList<Challenge>()
        for (challenge in candidates) {
            if (picked.size == PER_WEEK) break
            val similar = setOf(ChallengeKind.WeekTotal, ChallengeKind.BeatLastWeek)
            if (challenge.kind in similar && picked.any { it.kind in similar }) continue
            picked += challenge
        }
        return picked.sortedBy { it.kind.ordinal }
    }

    private fun roundTo(value: Long, step: Long): Long = ((value + step / 2) / step * step).coerceAtLeast(step)
}
