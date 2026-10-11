package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

enum class ExerciseKind { Walking, Running, Biking, Hiking, Swimming, Yoga, Strength, Other }

class Exercise(val kind: ExerciseKind, val title: String?, val start: Instant, val minutes: Long)

class HeartSummary(val resting: Long?, val min: Long, val average: Long, val max: Long)

class BandDay(
    val distanceKm: Double?,
    val activeKcal: Long?,
    val exercises: List<Exercise>,
    val heart: HeartSummary?,
    val sleepMinutes: Long?,
)

class BandPeriod(
    val days: List<LocalDate>,
    val sleep: Map<LocalDate, Long>,
    val resting: List<Pair<LocalDate, Long?>>,
    val exercises: List<Exercise>,
    val distanceKm: Double?,
    val sleepAllowed: Boolean,
    val exerciseAllowed: Boolean,
)

object WearableHealth {

    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    suspend fun day(context: Context, date: LocalDate): BandDay? {
        if (!WearableSteps.isAvailable(context)) return null
        return try {
            read(HealthConnectClient.getOrCreate(context), date, ZoneId.systemDefault())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun period(context: Context, to: LocalDate, days: Long): BandPeriod? {
        if (!WearableSteps.isAvailable(context)) return null
        return try {
            readPeriod(HealthConnectClient.getOrCreate(context), to.minusDays(days - 1), to, ZoneId.systemDefault())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun readPeriod(client: HealthConnectClient, from: LocalDate, to: LocalDate, zone: ZoneId): BandPeriod {
        val granted = client.permissionController.getGrantedPermissions()
        fun allowed(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted

        val start = from.atStartOfDay(zone).toInstant()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant()
        val localRange = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay())
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()

        val sleep = if (allowed(SleepSessionRecord::class)) {
            readAll(client, SleepSessionRecord::class, start.minus(Duration.ofHours(12)), end)
                .filter { it.endTime >= start && it.endTime < end }
                .groupBy { it.endTime.atZone(zone).toLocalDate() }
                .mapValues { (_, sessions) ->
                    sessions.groupBy { it.metadata.dataOrigin.packageName }.values
                        .maxOf { origin -> origin.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() } }
                }
                .filterValues { it > 0L }
        } else {
            emptyMap()
        }

        val exercises = if (allowed(ExerciseSessionRecord::class)) {
            readAll(client, ExerciseSessionRecord::class, start, end)
                .distinctBy { it.exerciseType to it.startTime.epochSecond / 60 }
                .map { Exercise(kindOf(it.exerciseType), it.title, it.startTime, Duration.between(it.startTime, it.endTime).toMinutes()) }
                .sortedBy { it.start }
        } else {
            emptyList()
        }

        val restingMetrics = buildSet<AggregateMetric<*>> {
            if (allowed(HeartRateRecord::class)) add(HeartRateRecord.BPM_MIN)
            if (allowed(RestingHeartRateRecord::class)) add(RestingHeartRateRecord.BPM_AVG)
        }
        val restingByDay = if (restingMetrics.isNotEmpty()) {
            client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(restingMetrics, localRange, Period.ofDays(1)))
                .associate { it.startTime.toLocalDate() to (it.result[RestingHeartRateRecord.BPM_AVG] ?: it.result[HeartRateRecord.BPM_MIN]) }
        } else {
            emptyMap()
        }

        val distance = if (allowed(DistanceRecord::class)) {
            client.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), localRange)).dataOrigins
                .mapNotNull { origin ->
                    client.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), localRange, setOf(origin)))[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
                }
                .maxOrNull()
                ?.takeIf { it > 0.0 }
        } else {
            null
        }

        return BandPeriod(days, sleep, days.map { it to restingByDay[it] }, exercises, distance, allowed(SleepSessionRecord::class), allowed(ExerciseSessionRecord::class))
    }

    private suspend fun read(client: HealthConnectClient, date: LocalDate, zone: ZoneId): BandDay {
        val granted = client.permissionController.getGrantedPermissions()
        fun allowed(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted

        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()
        val dayRange = TimeRangeFilter.between(date.atStartOfDay(), date.plusDays(1).atStartOfDay())

        val amountMetrics = buildSet<AggregateMetric<*>> {
            if (allowed(DistanceRecord::class)) add(DistanceRecord.DISTANCE_TOTAL)
            if (allowed(ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        }
        var distance: Double? = null
        var kcal: Long? = null
        if (amountMetrics.isNotEmpty()) {
            val origins = client.aggregate(AggregateRequest(amountMetrics, dayRange)).dataOrigins
            for (origin in origins) {
                val result = client.aggregate(AggregateRequest(amountMetrics, dayRange, setOf(origin)))
                result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers?.takeIf { it > 0.0 }?.let { distance = maxOf(distance ?: 0.0, it) }
                result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.takeIf { it > 0.0 }
                    ?.let { kcal = maxOf(kcal ?: 0L, Math.round(it)) }
            }
        }

        val exercises = if (allowed(ExerciseSessionRecord::class)) {
            readAll(client, ExerciseSessionRecord::class, from, to)
                .distinctBy { it.exerciseType to it.startTime.epochSecond / 60 }
                .map { Exercise(kindOf(it.exerciseType), it.title, it.startTime, Duration.between(it.startTime, it.endTime).toMinutes()) }
                .sortedBy { it.start }
        } else {
            emptyList()
        }
        val sleep = if (allowed(SleepSessionRecord::class)) {
            readAll(client, SleepSessionRecord::class, from.minus(Duration.ofHours(12)), to)
                .filter { it.endTime >= from && it.endTime < to }
                .groupBy { it.metadata.dataOrigin.packageName }
                .values
                .maxOfOrNull { sessions -> sessions.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() } }
        } else {
            null
        }

        val heartMetrics = buildSet<AggregateMetric<*>> {
            if (allowed(HeartRateRecord::class)) addAll(listOf(HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MAX))
            if (allowed(RestingHeartRateRecord::class)) add(RestingHeartRateRecord.BPM_AVG)
        }
        val heart = if (heartMetrics.isNotEmpty()) {
            val result = client.aggregate(AggregateRequest(heartMetrics, dayRange))
            val min = result[HeartRateRecord.BPM_MIN]
            val avg = result[HeartRateRecord.BPM_AVG]
            val max = result[HeartRateRecord.BPM_MAX]
            if (min != null && avg != null && max != null) HeartSummary(result[RestingHeartRateRecord.BPM_AVG] ?: min, min, avg, max) else null
        } else {
            null
        }

        return BandDay(distance, kcal, exercises, heart, sleep)
    }

    private suspend fun <T : Record> readAll(client: HealthConnectClient, type: KClass<T>, from: Instant, to: Instant): List<T> {
        val result = ArrayList<T>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageToken = pageToken))
            result += response.records
            pageToken = response.pageToken
            pages++
        } while (!pageToken.isNullOrEmpty() && pages < WearableSteps.MAX_PAGES)
        return result
    }

    private fun kindOf(type: Int): ExerciseKind = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> ExerciseKind.Walking
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> ExerciseKind.Running
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> ExerciseKind.Biking
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> ExerciseKind.Hiking
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> ExerciseKind.Swimming
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> ExerciseKind.Yoga
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> ExerciseKind.Strength
        else -> ExerciseKind.Other
    }
}
