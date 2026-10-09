package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

data class DaySteps(val date: LocalDate, val steps: Long)

class StepRepository private constructor(private val context: Context) {

    private val dao = StepDatabase.get(context).dailySteps()
    private val counterPrefs = context.getSharedPreferences(COUNTER_PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _lastReadingWallMs = MutableStateFlow(loadReading()?.wallMs)

    val lastReadingWallMs: StateFlow<Long?> = _lastReadingWallMs.asStateFlow()

    suspend fun record(reading: CounterReading): StepInterval = mutex.withLock {
        val interval = StepMath.interval(loadReading(), reading)
        if (interval.kind == ReadingKind.Stale) return@withLock interval
        saveReading(reading)
        val days = StepMath.splitByDay(interval, ZoneId.systemDefault())
        if (days.isNotEmpty() && !XiaomiSteps.isEnabled(context)) dao.add(days.mapKeys { it.key.toString() })
        _lastReadingWallMs.value = reading.wallMs
        interval
    }

    suspend fun replaceDays(days: Map<LocalDate, Long>) = mutex.withLock {
        dao.replace(days.mapKeys { it.key.toString() })
        _lastReadingWallMs.value = System.currentTimeMillis()
    }

    suspend fun resetReading() = mutex.withLock {
        counterPrefs.edit(commit = true) { clear() }
    }

    suspend fun today(): Long = loadWeek(LocalDate.now()).last().steps

    fun observeWeek(today: LocalDate): Flow<List<DaySteps>> =
        dao.observeFrom(weekStart(today).toString()).map { fillWeek(it, today) }

    suspend fun loadWeek(today: LocalDate): List<DaySteps> =
        fillWeek(dao.loadFrom(weekStart(today).toString()), today)

    fun observeHistory(): Flow<Map<LocalDate, Long>> =
        dao.observeAll().map { rows -> rows.associate { LocalDate.parse(it.date) to it.steps } }

    private fun weekStart(today: LocalDate) = today.minusDays(6)

    private fun fillWeek(rows: List<DailySteps>, today: LocalDate): List<DaySteps> {
        val byDate = rows.associate { it.date to it.steps }
        return (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            DaySteps(date, byDate[date.toString()] ?: 0L)
        }
    }

    private fun loadReading(): CounterReading? {
        if (!counterPrefs.contains(KEY_COUNTER) || !counterPrefs.contains(KEY_BOOT)) return null
        return CounterReading(
            counter = counterPrefs.getLong(KEY_COUNTER, 0L),
            elapsedMs = counterPrefs.getLong(KEY_ELAPSED, 0L),
            wallMs = counterPrefs.getLong(KEY_WALL, 0L),
            bootCount = counterPrefs.getInt(KEY_BOOT, NO_BOOT_COUNT).takeIf { it != NO_BOOT_COUNT },
        )
    }

    private fun saveReading(reading: CounterReading) {
        counterPrefs.edit(commit = true) {
            putLong(KEY_COUNTER, reading.counter)
            putLong(KEY_ELAPSED, reading.elapsedMs)
            putLong(KEY_WALL, reading.wallMs)
            putInt(KEY_BOOT, reading.bootCount ?: NO_BOOT_COUNT)
        }
    }

    companion object {
        const val COUNTER_PREFS = "counter"
        private const val KEY_COUNTER = "counter"
        private const val KEY_ELAPSED = "elapsed_ms"
        private const val KEY_WALL = "wall_ms"
        private const val KEY_BOOT = "boot_count"
        private const val NO_BOOT_COUNT = -1

        @Volatile
        private var instance: StepRepository? = null

        fun get(context: Context): StepRepository = instance ?: synchronized(this) {
            instance ?: StepRepository(context.applicationContext).also { instance = it }
        }
    }
}
