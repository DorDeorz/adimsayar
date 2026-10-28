package com.dordeorz.adimsayar.data

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

/**
 * Optional weekly goal adjustment (Settings → Goals → "Akıllı hedef", default off). Once per week,
 * on the first day of the week, the daily goal moves toward a little above the median of the last
 * 28 full days, by at most [MAX_CHANGE] of the current goal.
 */
object SmartGoal {

    const val WINDOW_DAYS = 28L
    const val MIN_DAYS = 14
    private const val PUSH = 1.1
    private const val MAX_CHANGE = 0.1
    private const val STEP = 500L
    private const val MIN_GOAL = 3_000L

    /** The goal for the coming week, or null when there is too little data or nothing changes. */
    fun suggest(history: Map<LocalDate, Long>, today: LocalDate, current: Long): Long? {
        val values = (1..WINDOW_DAYS).mapNotNull { history[today.minusDays(it)] }.filter { it > 0L }.sorted()
        if (values.size < MIN_DAYS) return null
        val median = if (values.size % 2 == 1) {
            values[values.size / 2].toDouble()
        } else {
            (values[values.size / 2 - 1] + values[values.size / 2]) / 2.0
        }
        val target = (median * PUSH).coerceIn(current * (1 - MAX_CHANGE), current * (1 + MAX_CHANGE))
        val rounded = ((target / STEP).roundToLong() * STEP)
            .coerceAtLeast(MIN_GOAL)
            .coerceIn(DAILY_GOAL_RANGE)
        return rounded.takeIf { it != current }
    }

    fun weekOf(date: LocalDate, weekStart: DayOfWeek): LocalDate = date.with(TemporalAdjusters.previousOrSame(weekStart))

    /** Applies this week's goal once; returns true when the goal changed. */
    suspend fun applyIfDue(context: Context, today: LocalDate = LocalDate.now()): Boolean {
        val store = SettingsStore.get(context)
        val settings = store.settings.value
        if (!settings.smartGoal) return false
        val week = weekOf(today, settings.weekStart)
        if (settings.smartGoalWeek >= week.toEpochDay()) return false
        val history = StepRepository.get(context).loadHistory()
        val next = suggest(history, week, settings.dailyGoal)
        store.applySmartGoal(week.toEpochDay(), next)
        return next != null
    }
}
