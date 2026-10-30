package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.core.content.edit
import com.dordeorz.adimsayar.background.Reminders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalDateTime

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
        if (days.isNotEmpty() && !XiaomiSteps.isEnabled(context)) {
            dao.add(days.mapKeys { it.key.toString() })
            dao.addHours(StepMath.splitByHour(interval, ZoneId.systemDefault()).mapKeys { it.key.toString() })
            Reminders.onTodaySteps(context, dao.getMerged(LocalDate.now().toString())?.steps ?: 0L)
        }
        _lastReadingWallMs.value = reading.wallMs
        interval
    }

    suspend fun replaceDays(days: Map<LocalDate, Long>) = mutex.withLock {
        dao.replace(days.mapKeys { it.key.toString() })
        _lastReadingWallMs.value = System.currentTimeMillis()
        Reminders.onTodaySteps(context, dao.getMerged(LocalDate.now().toString())?.steps ?: 0L)
    }

    suspend fun replaceBandDays(days: Map<LocalDate, Long>) = mutex.withLock {
        dao.upsertBand(days.map { (date, steps) -> BandSteps(date.toString(), steps) })
        Reminders.onTodaySteps(context, dao.getMerged(LocalDate.now().toString())?.steps ?: 0L)
    }

    suspend fun loadPhoneDays(from: LocalDate): Map<LocalDate, Long> =
        dao.loadPhoneFrom(from.toString()).associate { LocalDate.parse(it.date) to it.steps }

    suspend fun loadPhoneHours(date: LocalDate): LongArray? {
        val rows = dao.loadHours(date.atStartOfDay().toString(), date.plusDays(1).atStartOfDay().toString())
        if (rows.isEmpty()) return null
        val hours = LongArray(HOURS_PER_DAY)
        for (row in rows) hours[LocalDateTime.parse(row.hour).hour] += row.steps
        return hours
    }

    suspend fun loadPhoneHourMap(from: LocalDate): Map<LocalDateTime, Long> =
        dao.loadHours(from.atStartOfDay().toString(), LocalDate.now().plusDays(1).atStartOfDay().toString())
            .associate { LocalDateTime.parse(it.hour) to it.steps }

    suspend fun addPhoneHours(hours: Map<LocalDateTime, Long>): Long = mutex.withLock {
        if (hours.isEmpty()) return@withLock 0L
        dao.addHours(hours.mapKeys { it.key.toString() })
        val days = HashMap<String, Long>()
        for ((hour, steps) in hours) days[hour.toLocalDate().toString()] = (days[hour.toLocalDate().toString()] ?: 0L) + steps
        dao.add(days)
        hours.values.sum()
    }

    suspend fun earlyBirdDays(): Set<LocalDate> =
        StepMath.earlyBirdDays(loadBandHours(LocalDate.of(2000, 1, 1)), loadPhoneHourMap(LocalDate.of(2000, 1, 1)))

    suspend fun clearBandDays() = mutex.withLock { dao.clearBand() }

    suspend fun saveBandHours(file: Long, hours: Map<LocalDateTime, Long>) =
        dao.upsertBandHours(hours.map { (hour, steps) -> BandHour(hour.toString(), file, steps) })

    suspend fun loadBandHours(from: LocalDate): Map<LocalDateTime, Long> =
        dao.loadBandHours(from.atStartOfDay().toString()).associate { LocalDateTime.parse(it.hour) to it.steps }

    suspend fun loadHistory(): Map<LocalDate, Long> = dao.loadAll().associate { LocalDate.parse(it.date) to it.steps }

    /** Writes steps with water, weight and band sleep of each day; the import reads the step column. */
    suspend fun exportCsv(output: OutputStream): Int {
        val database = StepDatabase.get(context)
        val steps = dao.loadAll().associate { it.date to it.steps }
        val water = database.water().observeAll().first().associate { it.date to it.ml }
        val weights = database.weights().observeAll().first().associate { it.date to it.kg }
        val zone = ZoneId.systemDefault()
        val sleep = database.bandHealth().sleep(0L, Long.MAX_VALUE)
            .groupBy { Instant.ofEpochSecond(it.wake).atZone(zone).toLocalDate().toString() }
            .mapValues { (_, nights) -> nights.sumOf { it.minutes } }
        val dates = (steps.keys + water.keys + weights.keys + sleep.keys).sorted()
        output.bufferedWriter().use { writer ->
            writer.write(CSV_HEADER)
            writer.newLine()
            for (date in dates) {
                val kg = weights[date]?.let { String.format(java.util.Locale.ROOT, "%.1f", it) }.orEmpty()
                writer.write("$date,${steps[date] ?: 0L},${water[date]?.toString().orEmpty()},$kg,${sleep[date]?.toString().orEmpty()}")
                writer.newLine()
            }
        }
        return dates.size
    }

    suspend fun importCsv(input: InputStream): Int? = mutex.withLock {
        val parsed = HistoryImport.read(input, ZoneId.systemDefault()).mapKeys { it.key.toString() }
        if (parsed.isEmpty()) return@withLock null
        val changed = parsed.filter { (date, steps) -> steps > (dao.get(date)?.steps ?: 0L) }
        dao.replace(changed)
        changed.size
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
        private const val CSV_HEADER = "tarih,adim,su_ml,kilo_kg,uyku_dk"
        private const val KEY_COUNTER = "counter"
        private const val KEY_ELAPSED = "elapsed_ms"
        private const val KEY_WALL = "wall_ms"
        private const val KEY_BOOT = "boot_count"
        private const val NO_BOOT_COUNT = -1
        private const val HOURS_PER_DAY = 24

        @Volatile
        private var instance: StepRepository? = null

        fun get(context: Context): StepRepository = instance ?: synchronized(this) {
            instance ?: StepRepository(context.applicationContext).also { instance = it }
        }
    }
}
