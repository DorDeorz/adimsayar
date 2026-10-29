package com.dordeorz.adimsayar.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Short notes on days ("had a match"), shown as a dot on the calendar. */
object DayNotes {

    const val MAX_LENGTH = 120

    fun observe(context: Context): Flow<Map<LocalDate, String>> =
        StepDatabase.get(context).extras().observeNotes().map { rows -> rows.associate { LocalDate.parse(it.date) to it.text } }

    suspend fun set(context: Context, date: LocalDate, text: String) {
        val dao = StepDatabase.get(context).extras()
        val clean = text.trim().take(MAX_LENGTH)
        if (clean.isEmpty()) dao.deleteNote(date.toString()) else dao.upsertNote(DayNote(date.toString(), clean))
    }
}

/** Day counts the user corrected by hand. */
object DayOverrides {

    fun observe(context: Context): Flow<Map<LocalDate, Long>> =
        StepDatabase.get(context).extras().observeOverrides().map { rows -> rows.associate { LocalDate.parse(it.date) to it.steps } }

    /** Sets the count of [date]; null goes back to the counted value. */
    suspend fun set(context: Context, date: LocalDate, steps: Long?) {
        val dao = StepDatabase.get(context).extras()
        if (steps == null) dao.deleteOverride(date.toString()) else dao.upsertOverride(DayOverride(date.toString(), steps.coerceIn(0L, MAX_STEPS)))
    }

    const val MAX_STEPS = 200_000L
}

/** Deletes everything, or the days of a date range. */
object DataEraser {

    private val DATE_TABLES = listOf("daily_steps", "band_steps", "water", "streak_freezes", "day_notes", "day_overrides", "floors", "weights")
    private val HOUR_TABLES = listOf("hourly_steps", "band_hours")

    suspend fun eraseAll(context: Context) {
        val database = StepDatabase.get(context)
        database.runInTransaction {
            val db = database.openHelper.writableDatabase
            for (table in StepDatabase.TABLES) db.execSQL("DELETE FROM `$table`")
        }
        StepRepository.get(context).resetReading()
    }

    /** Deletes the days [from]..[to], both included. Returns false when the range is empty. */
    fun eraseRange(context: Context, from: LocalDate, to: LocalDate): Boolean {
        if (to < from) return false
        val database = StepDatabase.get(context)
        val zone = java.time.ZoneId.systemDefault()
        val startMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val first = from.toString()
        val last = to.toString()
        val hourFrom = from.atStartOfDay().toString()
        val hourTo = to.plusDays(1).atStartOfDay().toString()
        database.runInTransaction {
            val db = database.openHelper.writableDatabase
            for (table in DATE_TABLES) db.execSQL("DELETE FROM `$table` WHERE date >= ? AND date <= ?", arrayOf(first, last))
            for (table in HOUR_TABLES) db.execSQL("DELETE FROM `$table` WHERE hour >= ? AND hour < ?", arrayOf(hourFrom, hourTo))
            db.execSQL("DELETE FROM band_samples WHERE minute >= ? AND minute < ?", arrayOf(startMs / SECOND_MS, endMs / SECOND_MS))
            db.execSQL("DELETE FROM band_sleep WHERE wake >= ? AND wake < ?", arrayOf(startMs / SECOND_MS, endMs / SECOND_MS))
            db.execSQL("DELETE FROM walks WHERE start >= ? AND start < ?", arrayOf(startMs, endMs))
        }
        return true
    }

    private const val SECOND_MS = 1_000L
}
