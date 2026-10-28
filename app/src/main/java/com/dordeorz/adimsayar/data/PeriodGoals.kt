package com.dordeorz.adimsayar.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Progress of a month or year goal, with the day it is reached at the current pace. */
data class PeriodProgress(val done: Long, val goal: Long, val end: LocalDate, val finish: LocalDate?) {
    val reached: Boolean get() = done >= goal
    val fraction: Float get() = (done.toFloat() / goal).coerceIn(0f, 1f)
    val onTrack: Boolean get() = reached || (finish != null && finish <= end)
}

object PeriodGoals {

    fun progress(days: Map<LocalDate, Long>, today: LocalDate, goal: Long, start: LocalDate, end: LocalDate): PeriodProgress {
        var done = 0L
        var date = start
        while (!date.isAfter(today) && !date.isAfter(end)) {
            done += days[date] ?: 0L
            date = date.plusDays(1)
        }
        val elapsed = ChronoUnit.DAYS.between(start, today) + 1
        val perDay = if (elapsed > 0) done / elapsed else 0L
        val finish = when {
            done >= goal -> today
            perDay <= 0L -> null
            else -> today.plusDays((goal - done + perDay - 1) / perDay)
        }
        return PeriodProgress(done, goal, end, finish)
    }

    fun month(days: Map<LocalDate, Long>, today: LocalDate, goal: Long): PeriodProgress =
        progress(days, today, goal, today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))

    fun year(days: Map<LocalDate, Long>, today: LocalDate, goal: Long): PeriodProgress =
        progress(days, today, goal, today.withDayOfYear(1), today.withDayOfYear(today.lengthOfYear()))
}
