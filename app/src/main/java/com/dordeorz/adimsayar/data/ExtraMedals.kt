package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/** Medals added in 0.17: distance, water streak, sleep streak and finished virtual routes. */
object ExtraMedals {

    const val GOOD_NIGHT_MINUTES = 7 * 60L

    fun compute(
        days: Map<LocalDate, Long>,
        today: LocalDate,
        heightCm: Int,
        water: Map<LocalDate, Int>,
        waterGoal: Int,
        nights: Map<LocalDate, Long>,
        routesDone: Map<String, LocalDate>,
    ): List<Medal> {
        val result = ArrayList<Medal>()

        var km = 0.0
        val distanceEarned = HashMap<Long, LocalDate>()
        for (date in days.keys.filter { !it.isAfter(today) }.sorted()) {
            km += Distance.km(days[date] ?: 0L, heightCm)
            for (target in MedalGroup.Distance.targets) if (km >= target && target !in distanceEarned) distanceEarned[target] = date
        }
        result += medals(MedalGroup.Distance, km.toLong(), distanceEarned, IntArray(MedalGroup.Distance.targets.size))

        result += streakMedals(MedalGroup.WaterStreak, water.filterValues { it >= waterGoal }.keys, today)
        result += streakMedals(MedalGroup.SleepStreak, nights.filterValues { it >= GOOD_NIGHT_MINUTES }.keys, today)

        val finished = routesDone.values.sorted()
        val routeEarned = HashMap<Long, LocalDate>()
        MedalGroup.Route.targets.forEach { target -> finished.getOrNull(target.toInt() - 1)?.let { routeEarned[target] = it } }
        result += medals(MedalGroup.Route, finished.size.toLong(), routeEarned, IntArray(MedalGroup.Route.targets.size))
        return result
    }

    private fun streakMedals(group: MedalGroup, good: Set<LocalDate>, today: LocalDate): List<Medal> {
        val earned = HashMap<Long, LocalDate>()
        val times = IntArray(group.targets.size)
        var streak = 0L
        var longest = 0L
        val first = good.minOrNull() ?: return medals(group, 0L, earned, times)
        var date = first
        while (!date.isAfter(today)) {
            if (date in good) {
                streak++
                longest = maxOf(longest, streak)
                group.targets.forEachIndexed { i, target ->
                    if (streak == target) times[i]++
                    if (streak >= target && target !in earned) earned[target] = date
                }
            } else if (date != today) {
                streak = 0
            }
            date = date.plusDays(1)
        }
        return medals(group, longest, earned, times)
    }

    private fun medals(group: MedalGroup, progress: Long, earned: Map<Long, LocalDate>, times: IntArray): List<Medal> =
        group.targets.mapIndexed { tier, target -> Medal(group, tier, target, progress, earned[target], times[tier]) }

    /** Remembers the routes walked to the end, since only the current route is in the settings. */
    fun rememberRoute(context: Context, id: String, finishedOn: LocalDate) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(id)) return
        prefs.edit { putString(id, finishedOn.toString()) }
    }

    fun routesDone(context: Context): Map<String, LocalDate> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.mapNotNull { (id, value) ->
            (value as? String)?.let { runCatching { id to LocalDate.parse(it) }.getOrNull() }
        }.toMap()

    private const val PREFS = "routes_done"
}
