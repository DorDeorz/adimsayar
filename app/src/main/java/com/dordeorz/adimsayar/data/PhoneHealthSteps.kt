package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.dordeorz.adimsayar.R
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// Android 14+ records the phone's own steps in Health Connect. Past days use it to fill steps our sparse
// reads lost, such as the minutes before a reboot. Today is skipped: our next read will still add those steps.
object PhoneHealthSteps {

    private const val DAYS = 3L
    private const val PLATFORM_PACKAGE = "android"

    suspend fun fillGaps(context: Context): Long {
        if (XiaomiSteps.isEnabled(context) || !WearableSteps.hasPermission(context)) return 0L
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.minusDays(DAYS)
        val range = TimeRangeFilter.between(from.atStartOfDay(zone).toInstant(), today.atStartOfDay(zone).toInstant())
        val added = try {
            val client = HealthConnectClient.getOrCreate(context)
            val origins = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))
                .dataOrigins.filter { it.packageName == PLATFORM_PACKAGE }.toSet()
            if (origins.isEmpty()) return 0L
            val platform = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(setOf(StepsRecord.COUNT_TOTAL), range, Duration.ofHours(1), origins),
            ).mapNotNull { group ->
                group.result[StepsRecord.COUNT_TOTAL]?.takeIf { it > 0L }?.let {
                    group.startTime.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS) to it
                }
            }.toMap()
            val repository = StepRepository.get(context)
            val own = repository.loadPhoneHourMap(from).filterKeys { it < today.atStartOfDay() }
            repository.addPhoneHours(StepMath.phoneGaps(platform, own))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return 0L
        }
        if (added > 0L) ReadingLog.get(context).note(ReadSource.App, context.getString(R.string.log_phone_gaps, added))
        return added
    }
}
