package com.dordeorz.adimsayar.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.dordeorz.adimsayar.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDate
import java.time.Period

object WearableSteps {

    private const val SETTINGS_PREFS = "settings"
    private const val KEY_ENABLED = "wearable_source"
    private const val KEY_BACKFILLED = "wearable_backfilled"
    private const val BACKFILL_DAYS = 30L
    private const val RECENT_DAYS = 1L
    private const val KEY_NEXT_ATTEMPT = "wearable_next_attempt"
    private const val MIN_INTERVAL_MS = 5 * 60 * 1000L
    private const val ERROR_BACKOFF_MS = 60 * 60 * 1000L
    const val MAX_PAGES = 20

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
            remove(KEY_NEXT_ATTEMPT)
        }
        if (!enabled) StepRepository.get(context).clearBandDays()
    }

    suspend fun hasPermission(context: Context): Boolean =
        isAvailable(context) && READ_STEPS in HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    suspend fun sync(context: Context, source: ReadSource): Long? {
        val now = System.currentTimeMillis()
        val next = prefs(context).getLong(KEY_NEXT_ATTEMPT, 0L)
        if (now < next && next - now <= ERROR_BACKOFF_MS) return null
        if (!running.tryLock()) return null
        return try {
            prefs(context).edit(commit = true) { putLong(KEY_NEXT_ATTEMPT, now + MIN_INTERVAL_MS) }
            syncNow(context, source).also { today ->
                if (today == null) prefs(context).edit(commit = true) { putLong(KEY_NEXT_ATTEMPT, now + ERROR_BACKOFF_MS) }
            }
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
        val backfilled = prefs(context).getBoolean(KEY_BACKFILLED, false)
        val today = LocalDate.now()
        val from = today.minusDays(if (backfilled) RECENT_DAYS else BACKFILL_DAYS)
        val days = try {
            dailyTotals(HealthConnectClient.getOrCreate(context), from, today)
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
        val todaySteps = days[today] ?: 0L
        if (source != ReadSource.App || todaySteps != lastLoggedToday) {
            lastLoggedToday = todaySteps
            log.note(source, context.getString(R.string.log_wearable_synced, todaySteps, days.size))
        }
        return todaySteps
    }

    private suspend fun dailyTotals(client: HealthConnectClient, from: LocalDate, to: LocalDate): Map<LocalDate, Long> {
        val range = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay())
        val origins = client.aggregateGroupByPeriod(dailySteps(range, emptySet())).flatMap { it.result.dataOrigins }.toSet()
        val perOrigin = origins.map { origin ->
            client.aggregateGroupByPeriod(dailySteps(range, setOf(origin))).mapNotNull { group ->
                group.result[StepsRecord.COUNT_TOTAL]?.takeIf { it > 0L }?.let { group.startTime.toLocalDate() to it }
            }.toMap()
        }
        return StepMath.bestSourcePerDay(perOrigin)
    }

    private fun dailySteps(range: TimeRangeFilter, origins: Set<DataOrigin>) =
        AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), range, Period.ofDays(1), origins)

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}
