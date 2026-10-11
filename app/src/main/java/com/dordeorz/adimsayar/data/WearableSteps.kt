package com.dordeorz.adimsayar.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.dordeorz.adimsayar.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object WearableSteps {

    private const val SETTINGS_PREFS = "settings"
    private const val KEY_ENABLED = "wearable_source"
    private const val KEY_BACKFILLED = "wearable_backfilled"
    private const val BACKFILL_DAYS = 30L
    private const val RECENT_DAYS = 1L
    private const val KEY_LAST_ATTEMPT = "wearable_last_attempt"
    private const val MIN_INTERVAL_MS = 5 * 60 * 1000L
    const val MAX_PAGES = 100

    val READ_STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)
    val PERMISSIONS: Set<String> = setOf(READ_STEPS, HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND) + WearableHealth.PERMISSIONS

    @Volatile
    private var lastLoggedToday: Long? = null
    private val running = Mutex()

    fun isAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_ENABLED, enabled)
            putBoolean(KEY_BACKFILLED, false)
            remove(KEY_LAST_ATTEMPT)
        }
        if (!enabled) StepRepository.get(context).clearBandDays()
    }

    suspend fun hasPermission(context: Context): Boolean =
        isAvailable(context) && READ_STEPS in HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    suspend fun sync(context: Context, source: ReadSource): Long? {
        val now = System.currentTimeMillis()
        val lastAttempt = prefs(context).getLong(KEY_LAST_ATTEMPT, 0L)
        if (now - lastAttempt in 0 until MIN_INTERVAL_MS) return null
        if (!running.tryLock()) return null
        return try {
            prefs(context).edit(commit = true) { putLong(KEY_LAST_ATTEMPT, now) }
            syncNow(context, source)
        } finally {
            running.unlock()
        }
    }

    private suspend fun syncNow(context: Context, source: ReadSource): Long? {
        val log = ReadingLog.get(context)
        if (!isAvailable(context)) {
            log.failure(source, R.string.log_wearable_missing)
            return null
        }
        val zone = ZoneId.systemDefault()
        val backfilled = prefs(context).getBoolean(KEY_BACKFILLED, false)
        val from = LocalDate.now().minusDays(if (backfilled) RECENT_DAYS else BACKFILL_DAYS).atStartOfDay(zone).toInstant()
        val days = try {
            dailyTotals(HealthConnectClient.getOrCreate(context), from, Instant.now(), zone)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            log.failure(source, R.string.log_wearable_denied)
            return null
        } catch (e: Exception) {
            log.note(source, context.getString(R.string.log_wearable_error, listOfNotNull(e.javaClass.simpleName, e.message?.take(120)).joinToString(": ")))
            return null
        }
        StepRepository.get(context).replaceBandDays(days)
        prefs(context).edit { putBoolean(KEY_BACKFILLED, true) }
        val today = days[LocalDate.now()] ?: 0L
        if (source != ReadSource.App || today != lastLoggedToday) {
            lastLoggedToday = today
            log.note(source, context.getString(R.string.log_wearable_synced, today, days.size))
        }
        return today
    }

    private suspend fun dailyTotals(client: HealthConnectClient, from: Instant, to: Instant, zone: ZoneId): Map<LocalDate, Long> {
        val byOrigin = HashMap<String, HashMap<LocalDate, Long>>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(from, to), pageToken = pageToken),
            )
            for (record in response.records) {
                val totals = byOrigin.getOrPut(record.metadata.dataOrigin.packageName) { HashMap() }
                val interval = StepInterval(record.count, record.startTime.toEpochMilli(), record.endTime.toEpochMilli())
                for ((day, count) in StepMath.splitByDay(interval, zone)) {
                    totals[day] = (totals[day] ?: 0L) + count
                }
            }
            pageToken = response.pageToken
            pages++
        } while (!pageToken.isNullOrEmpty() && pages < MAX_PAGES)
        return StepMath.bestSourcePerDay(byOrigin.values)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}
