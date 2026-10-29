package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Writes FitWren's hourly steps to Health Connect, so Google Fit and other apps see them. Each
 * hour is one record with a fixed id, so writing it again replaces it instead of adding to it.
 * It does not run while steps are read from Health Connect, which would count them twice.
 */
object HealthWrite {

    val WRITE_STEPS: String = HealthPermission.getWritePermission(StepsRecord::class)
    private const val PREFS = "health_write"
    private const val KEY_LAST = "last_write"
    private const val INTERVAL_MS = 30 * 60 * 1000L
    private const val RECENT_DAYS = 2L
    private const val FIRST_DAYS = 7L

    fun canRun(context: Context): Boolean = WearableSteps.isAvailable(context) && !WearableSteps.isEnabled(context)

    suspend fun hasPermission(context: Context): Boolean = try {
        WRITE_STEPS in HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** Writes the recent hours when the switch is on and the last write is older than half an hour. */
    suspend fun syncIfDue(context: Context, force: Boolean = false): Boolean {
        if (!ExtrasStore.get(context).extras.value.healthWrite || !canRun(context)) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST, 0L)
        val now = System.currentTimeMillis()
        if (!force && now - last < INTERVAL_MS) return false
        if (!hasPermission(context)) return false
        val days = if (last == 0L) FIRST_DAYS else RECENT_DAYS
        val written = write(context, LocalDate.now().minusDays(days - 1))
        if (written) prefs.edit { putLong(KEY_LAST, now) }
        return written
    }

    private suspend fun write(context: Context, from: LocalDate): Boolean {
        val repository = StepRepository.get(context)
        val hours = HashMap(repository.loadPhoneHourMap(from))
        if (BandDirect.isEnabled(context)) hours.putAll(repository.loadBandHours(from))
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val records = hours.filter { (hour, steps) -> steps > 0L && hour <= now }.map { (hour, steps) ->
            val start = hour.atZone(zone)
            val end = minOf(start.plusHours(1).toInstant(), java.time.Instant.now()).coerceAtLeast(start.toInstant().plusSeconds(1))
            StepsRecord(
                startTime = start.toInstant(),
                startZoneOffset = start.offset,
                endTime = end,
                endZoneOffset = start.offset,
                count = steps,
                metadata = Metadata.autoRecorded(
                    device = Device(type = Device.TYPE_PHONE),
                    clientRecordId = "fitwren-steps-$hour",
                    clientRecordVersion = System.currentTimeMillis(),
                ),
            )
        }
        if (records.isEmpty()) return true
        return try {
            HealthConnectClient.getOrCreate(context).insertRecords(records)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}
