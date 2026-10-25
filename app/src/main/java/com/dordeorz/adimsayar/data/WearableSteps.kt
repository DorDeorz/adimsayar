package com.dordeorz.adimsayar.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.dordeorz.adimsayar.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object WearableSteps {

    private const val SETTINGS_PREFS = "settings"
    private const val KEY_ENABLED = "wearable_source"
    private const val KEY_BACKFILLED = "wearable_backfilled_hourly"
    private const val BACKFILL_DAYS = 30L
    private const val RECENT_DAYS = 6L
    private const val KEY_NEXT_ATTEMPT = "wearable_next_attempt"
    private const val KEY_NEXT_APP_ATTEMPT = "wearable_next_app_attempt"
    private const val APP_INTERVAL_MS = 60 * 1000L
    private const val MIN_INTERVAL_MS = 5 * 60 * 1000L
    private const val ERROR_BACKOFF_MS = 60 * 60 * 1000L
    private const val RESYNC_ATTEMPTS = 10
    private const val RESYNC_WAIT_MS = 1_000L
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
            remove(KEY_NEXT_APP_ATTEMPT)
        }
        if (!enabled) StepRepository.get(context).clearBandDays()
    }

    suspend fun resync(context: Context): Long? {
        prefs(context).edit(commit = true) {
            putBoolean(KEY_BACKFILLED, false)
            remove(KEY_NEXT_ATTEMPT)
            remove(KEY_NEXT_APP_ATTEMPT)
        }
        repeat(RESYNC_ATTEMPTS) {
            if (!running.isLocked) return sync(context, ReadSource.App)
            delay(RESYNC_WAIT_MS)
        }
        return null
    }

    suspend fun hasPermission(context: Context): Boolean =
        isAvailable(context) && READ_STEPS in HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    suspend fun sync(context: Context, source: ReadSource): Long? {
        val foreground = source == ReadSource.App
        val key = if (foreground) KEY_NEXT_APP_ATTEMPT else KEY_NEXT_ATTEMPT
        val now = System.currentTimeMillis()
        val next = prefs(context).getLong(key, 0L)
        if (now < next && next - now <= ERROR_BACKOFF_MS) return null
        if (!running.tryLock()) return null
        return try {
            prefs(context).edit(commit = true) {
                putLong(KEY_NEXT_APP_ATTEMPT, now + APP_INTERVAL_MS)
                putLong(KEY_NEXT_ATTEMPT, now + MIN_INTERVAL_MS)
            }
            syncNow(context, source).also { today ->
                if (today == null) prefs(context).edit(commit = true) {
                    putLong(KEY_NEXT_APP_ATTEMPT, now + MIN_INTERVAL_MS)
                    putLong(KEY_NEXT_ATTEMPT, now + ERROR_BACKOFF_MS)
                }
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
            merged(context, bandHourly(HealthConnectClient.getOrCreate(context), from, today), from)
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

    suspend fun merged(context: Context, band: Map<LocalDateTime, Long>, from: LocalDate): Map<LocalDate, Long> {
        val phoneHours = if (XiaomiSteps.isEnabled(context)) XiaomiSteps.hourlyTotals(context, from) else null
        if (phoneHours != null) return StepMath.mergeHourly(band, phoneHours)
        val bandDays = HashMap<LocalDate, Long>()
        for ((hour, steps) in band) bandDays[hour.toLocalDate()] = (bandDays[hour.toLocalDate()] ?: 0L) + steps
        return StepMath.mergeDaily(bandDays, StepRepository.get(context).loadPhoneDays(from))
    }

    private suspend fun bandHourly(client: HealthConnectClient, from: LocalDate, to: LocalDate): Map<LocalDateTime, Long> {
        val zone = ZoneId.systemDefault()
        val days = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay())
        val origins = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), days, Period.ofDays(1)),
        ).flatMap { it.result.dataOrigins }.toSet()
        val hours = TimeRangeFilter.between(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
        val perOrigin = origins.map { origin ->
            client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(setOf(StepsRecord.COUNT_TOTAL), hours, Duration.ofHours(1), setOf(origin)),
            ).mapNotNull { group ->
                group.result[StepsRecord.COUNT_TOTAL]?.takeIf { it > 0L }?.let {
                    group.startTime.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS) to it
                }
            }.toMap()
        }
        val best = HashMap<LocalDateTime, Long>()
        for (source in perOrigin) {
            for ((hour, steps) in source) if (steps > (best[hour] ?: 0L)) best[hour] = steps
        }
        return best
    }

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}
