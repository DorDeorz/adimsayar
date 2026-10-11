package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
    val restingWeek: List<Pair<LocalDate, Long?>>,
)

object WearableHealth {

    private const val WEEK_DAYS = 7L
    private const val SCALE = 1000L

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

    private suspend fun read(client: HealthConnectClient, date: LocalDate, zone: ZoneId): BandDay {
        val granted = client.permissionController.getGrantedPermissions()
        fun allowed(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted

        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()

        val distance = if (allowed(DistanceRecord::class)) {
            largestOrigin(readAll(client, DistanceRecord::class, from, to), date, zone) { record ->
                StepInterval(Math.round(record.distance.inMeters * SCALE), record.startTime.toEpochMilli(), record.endTime.toEpochMilli())
            }?.let { it.toDouble() / SCALE / 1000.0 }
        } else {
            null
        }
        val kcal = if (allowed(ActiveCaloriesBurnedRecord::class)) {
            largestOrigin(readAll(client, ActiveCaloriesBurnedRecord::class, from, to), date, zone) { record ->
                StepInterval(Math.round(record.energy.inKilocalories * SCALE), record.startTime.toEpochMilli(), record.endTime.toEpochMilli())
            }?.let { Math.round(it.toDouble() / SCALE) }
        } else {
            null
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

        val heartAllowed = allowed(HeartRateRecord::class)
        val restingAllowed = allowed(RestingHeartRateRecord::class)
        val weekFrom = date.minusDays(WEEK_DAYS - 1).atStartOfDay(zone).toInstant()
        val samples = if (heartAllowed) {
            readAll(client, HeartRateRecord::class, weekFrom, to).flatMap { it.samples }.filter { it.time >= weekFrom && it.time < to }
        } else {
            emptyList()
        }
        val resting = if (restingAllowed) readAll(client, RestingHeartRateRecord::class, weekFrom, to) else emptyList()
        val samplesByDay = samples.groupBy { it.time.atZone(zone).toLocalDate() }.mapValues { (_, list) -> list.map { it.beatsPerMinute } }
        val restingByDay = resting.groupBy { it.time.atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> Math.round(list.map { it.beatsPerMinute }.average()) }
        val restingWeek = (WEEK_DAYS - 1 downTo 0L).map { offset ->
            val day = date.minusDays(offset)
            day to (restingByDay[day] ?: samplesByDay[day]?.minOrNull())
        }
        val heart = samplesByDay[date]?.takeIf { it.isNotEmpty() }?.let { bpm ->
            HeartSummary(restingByDay[date], bpm.min(), Math.round(bpm.average()), bpm.max())
        }

        return BandDay(distance, kcal, exercises, heart, sleep, restingWeek.takeIf { week -> week.any { it.second != null } } ?: emptyList())
    }

    private fun <T : Record> largestOrigin(records: List<T>, date: LocalDate, zone: ZoneId, interval: (T) -> StepInterval): Long? {
        val byOrigin = records.groupBy { it.metadata.dataOrigin.packageName }.values.map { list ->
            list.sumOf { StepMath.splitByDay(interval(it), zone)[date] ?: 0L }
        }
        return byOrigin.maxOrNull()?.takeIf { it > 0L }
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
