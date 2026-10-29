package com.dordeorz.adimsayar.data

import java.time.LocalDate

/** Average steps after long vs short nights. */
class SleepStepSplit(val goodNights: Int, val shortNights: Int, val goodSteps: Long, val shortSteps: Long)

/** Average sleep after days the goal was reached vs missed. */
class ActiveSleepSplit(val activeDays: Int, val restDays: Int, val activeSleep: Long, val restSleep: Long)

class SleepInsight(val steps: SleepStepSplit?, val sleep: ActiveSleepSplit?)

/** Sleep vs steps findings for the Health tab (idea 23). Nights are keyed by their wake-up date. */
object SleepInsights {

    const val GOOD_NIGHT_MINUTES = 7 * 60L
    const val MIN_GROUP = 3

    fun compute(nights: Map<LocalDate, SleepNight>, history: Map<LocalDate, Long>, today: LocalDate, goal: Long): SleepInsight {
        // Today's steps are not finished yet, so only full days count.
        val after = nights.mapNotNull { (date, night) ->
            val steps = history[date]?.takeIf { it > 0L && date.isBefore(today) } ?: return@mapNotNull null
            night.minutes to steps
        }
        val (good, short) = after.partition { it.first >= GOOD_NIGHT_MINUTES }
        val steps = if (good.size >= MIN_GROUP && short.size >= MIN_GROUP) {
            SleepStepSplit(good.size, short.size, average(good.map { it.second }), average(short.map { it.second }))
        } else {
            null
        }

        val before = history.mapNotNull { (date, steps) ->
            if (steps <= 0L || !date.isBefore(today)) return@mapNotNull null
            val night = nights[date.plusDays(1)] ?: return@mapNotNull null
            steps to night.minutes
        }
        val (active, rest) = before.partition { it.first >= goal }
        val sleep = if (active.size >= MIN_GROUP && rest.size >= MIN_GROUP) {
            ActiveSleepSplit(active.size, rest.size, average(active.map { it.second }), average(rest.map { it.second }))
        } else {
            null
        }
        return SleepInsight(steps, sleep)
    }

    private fun average(values: List<Long>): Long = Math.round(values.sum().toDouble() / values.size)
}
