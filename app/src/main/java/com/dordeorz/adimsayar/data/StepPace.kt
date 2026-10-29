package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/** The user's own walking pace, from the busiest hours of the last two weeks, for "time left to the goal". */
object StepPace {

    private const val PREFS = "step_pace"
    private const val KEY_DAY = "day"
    private const val KEY_CADENCE = "cadence"
    private const val DAYS = 14L
    private const val BUSY_HOUR_STEPS = 2_000L
    private const val TOP_HOURS = 10
    private const val MINUTES_WALKED = 30.0
    const val DEFAULT_CADENCE = 100
    private val CADENCE_RANGE = 80..125

    /** Steps per minute while walking; worked out once a day. */
    suspend fun cadence(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        if (prefs.getLong(KEY_DAY, -1L) == today.toEpochDay()) return prefs.getInt(KEY_CADENCE, DEFAULT_CADENCE)
        val busy = DayHours.since(context, today.minusDays(DAYS)).values.filter { it >= BUSY_HOUR_STEPS }.sortedDescending().take(TOP_HOURS)
        // A busy hour is about half an hour of walking, so its steps over 30 minutes give the pace.
        val cadence = if (busy.isEmpty()) DEFAULT_CADENCE else (busy.average() / MINUTES_WALKED).toInt().coerceIn(CADENCE_RANGE)
        prefs.edit {
            putLong(KEY_DAY, today.toEpochDay())
            putInt(KEY_CADENCE, cadence)
        }
        return cadence
    }

    fun minutesFor(steps: Long, cadence: Int): Long = if (steps <= 0L) 0L else (steps + cadence - 1) / cadence

    /** The cached pace, for places that can't wait (widgets read it after the job worked it out). */
    fun cached(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CADENCE, DEFAULT_CADENCE)
}
