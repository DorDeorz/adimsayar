package com.dordeorz.adimsayar.data

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import com.dordeorz.adimsayar.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DayDetail(val hours: LongArray, val walking: Long, val running: Long)

object XiaomiSteps {

    private val URI: Uri = Uri.parse("content://com.miui.providers.steps/item")
    private val PROJECTION = arrayOf(COLUMN_BEGIN, COLUMN_END, COLUMN_STEPS)
    private const val COLUMN_BEGIN = "_begin_time"
    private const val COLUMN_END = "_end_time"
    private const val COLUMN_STEPS = "_steps"
    private const val COLUMN_MODE = "_mode"
    private const val MODE_WALKING = 2
    private const val MODE_RUNNING = 3
    private val DETAIL_PROJECTION = arrayOf(COLUMN_BEGIN, COLUMN_STEPS, COLUMN_MODE)
    private const val SETTINGS_PREFS = "settings"
    private const val KEY_ENABLED = "xiaomi_source"

    @Volatile
    private var lastLoggedToday: Long? = null

    fun isXiaomi(): Boolean = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    fun isEnabled(context: Context): Boolean =
        isXiaomi() && context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun isAvailable(context: Context): Boolean {
        if (!isXiaomi()) return false
        return try {
            context.contentResolver.query(URI, PROJECTION, null, null, null)?.use { true } ?: false
        } catch (e: RuntimeException) {
            false
        }
    }

    suspend fun sync(context: Context, source: ReadSource): Long? {
        val log = ReadingLog.get(context)
        val days = try {
            dailyTotals(context)
        } catch (e: SecurityException) {
            log.failure(source, R.string.log_xiaomi_denied)
            return null
        } catch (e: RuntimeException) {
            log.note(source, context.getString(R.string.log_xiaomi_error, e.javaClass.simpleName))
            return null
        }
        if (days == null) {
            log.failure(source, R.string.log_xiaomi_missing)
            return null
        }
        StepRepository.get(context).replaceDays(days)
        val today = days[LocalDate.now()] ?: 0L
        if (source != ReadSource.App || today != lastLoggedToday) {
            lastLoggedToday = today
            log.note(source, context.getString(R.string.log_xiaomi_synced, today, days.size))
        }
        return today
    }

    fun dayDetail(context: Context, date: LocalDate): DayDetail? {
        val zone = ZoneId.systemDefault()
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return try {
            context.contentResolver.query(URI, DETAIL_PROJECTION, null, null, null)?.use {
                val begin = it.getColumnIndexOrThrow(COLUMN_BEGIN)
                val steps = it.getColumnIndexOrThrow(COLUMN_STEPS)
                val mode = it.getColumnIndexOrThrow(COLUMN_MODE)
                val hours = LongArray(24)
                var walking = 0L
                var running = 0L
                while (it.moveToNext()) {
                    val start = it.getLong(begin)
                    if (start < from || start >= to) continue
                    val count = it.getLong(steps)
                    hours[Instant.ofEpochMilli(start).atZone(zone).hour] += count
                    when (it.getInt(mode)) {
                        MODE_WALKING -> walking += count
                        MODE_RUNNING -> running += count
                    }
                }
                DayDetail(hours, walking, running)
            }
        } catch (e: RuntimeException) {
            null
        }
    }

    fun earlyBirdDays(context: Context): Set<LocalDate>? {
        val zone = ZoneId.systemDefault()
        return try {
            context.contentResolver.query(URI, DETAIL_PROJECTION, null, null, null)?.use {
                val begin = it.getColumnIndexOrThrow(COLUMN_BEGIN)
                val steps = it.getColumnIndexOrThrow(COLUMN_STEPS)
                val mornings = HashMap<LocalDate, Long>()
                while (it.moveToNext()) {
                    val start = Instant.ofEpochMilli(it.getLong(begin)).atZone(zone)
                    if (start.hour >= EARLY_BIRD_HOUR) continue
                    val day = start.toLocalDate()
                    mornings[day] = (mornings[day] ?: 0L) + it.getLong(steps)
                }
                mornings.filterValues { count -> count >= EARLY_BIRD_STEPS }.keys
            }
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun dailyTotals(context: Context): Map<LocalDate, Long>? {
        val zone = ZoneId.systemDefault()
        val cursor = context.contentResolver.query(URI, PROJECTION, null, null, null) ?: return null
        return cursor.use {
            val begin = it.getColumnIndexOrThrow(COLUMN_BEGIN)
            val end = it.getColumnIndexOrThrow(COLUMN_END)
            val steps = it.getColumnIndexOrThrow(COLUMN_STEPS)
            val totals = HashMap<LocalDate, Long>()
            while (it.moveToNext()) {
                val interval = StepInterval(it.getLong(steps), it.getLong(begin), it.getLong(end))
                for ((day, count) in StepMath.splitByDay(interval, zone)) {
                    totals[day] = (totals[day] ?: 0L) + count
                }
            }
            totals
        }
    }
}
