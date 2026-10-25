package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
    val nights: Map<LocalDate, SleepNight>,
    val resting: List<Pair<LocalDate, Long?>>,
    val restingBefore: Long?,
    val exercises: List<Exercise>,
    val distanceKm: Double?,
    val activeKcal: Map<LocalDate, Long>,
    val oxygen: OxygenSummary?,
    val sleepAllowed: Boolean,
    val exerciseAllowed: Boolean,
    val oxygenAllowed: Boolean,
    val missingPermissions: Boolean,
)

class OxygenSummary(val average: Double, val min: Double, val count: Int)

class HeartSlot(val start: LocalTime, val min: Long, val avg: Long, val max: Long)

class HeartDay(val slots: List<HeartSlot>, val resting: Long?)

class HealthSnapshot(val sleepMinutes: Long?, val resting: Long?, val latest: Long?, val latestAt: Instant?)

enum class HealthType { Steps, Heart, Sleep, Oxygen, Exercise, Distance, Calories }

enum class DeviceKind { Watch, Band, Ring, Phone, Other }

data class SourceDevice(val kind: DeviceKind, val name: String?)

class SourceApp(val packageName: String, val types: Set<HealthType>, val devices: Set<SourceDevice>)

class SourceReport(val apps: List<SourceApp>, val missingPermissions: Boolean) {
    val hasWearable: Boolean get() = apps.any { app -> app.types.any { it in WEARABLE_TYPES } }

    private companion object {
        val WEARABLE_TYPES = setOf(HealthType.Heart, HealthType.Sleep, HealthType.Oxygen)
    }
}

object WearableHealth {

    private const val SOURCE_PAGE_SIZE = 200
    const val HEART_SLOT_MINUTES = 5L

    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
    )

    fun isEnabled(context: Context): Boolean = WearableSteps.isEnabled(context) || BandDirect.isEnabled(context)

    suspend fun day(context: Context, date: LocalDate): BandDay? {
        if (BandDirect.isEnabled(context)) return BandHealth.day(context, date)
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
        if (BandDirect.isEnabled(context)) return BandHealth.period(context, to, days)
        if (!WearableSteps.isAvailable(context)) return null
        return try {
            readPeriod(HealthConnectClient.getOrCreate(context), to.minusDays(days - 1), to, ZoneId.systemDefault())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun sources(context: Context, days: Long): SourceReport? {
        if (BandDirect.isEnabled(context)) return null
        if (!WearableSteps.isAvailable(context)) return null
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            fun allowed(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val range = TimeRangeFilter.between(today.minusDays(days - 1).atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant())
            val found = LinkedHashMap<String, MutableSet<HealthType>>()
            fun add(type: HealthType, packages: Collection<String>) {
                packages.filter { it != context.packageName }.forEach { found.getOrPut(it) { LinkedHashSet() } += type }
            }
            val metrics = listOf<Triple<HealthType, KClass<out Record>, AggregateMetric<*>>>(
                Triple(HealthType.Steps, StepsRecord::class, StepsRecord.COUNT_TOTAL),
                Triple(HealthType.Heart, HeartRateRecord::class, HeartRateRecord.BPM_AVG),
                Triple(HealthType.Sleep, SleepSessionRecord::class, SleepSessionRecord.SLEEP_DURATION_TOTAL),
                Triple(HealthType.Exercise, ExerciseSessionRecord::class, ExerciseSessionRecord.EXERCISE_DURATION_TOTAL),
                Triple(HealthType.Distance, DistanceRecord::class, DistanceRecord.DISTANCE_TOTAL),
                Triple(HealthType.Calories, ActiveCaloriesBurnedRecord::class, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
            )
            for ((type, record, metric) in metrics) {
                if (allowed(record)) add(type, client.aggregate(AggregateRequest(setOf(metric), range)).dataOrigins.map { it.packageName })
            }
            val devices = HashMap<String, MutableSet<SourceDevice>>()
            fun collect(records: List<Record>) {
                for (record in records) {
                    val device = record.metadata.device ?: continue
                    val name = listOfNotNull(device.manufacturer, device.model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
                    devices.getOrPut(record.metadata.dataOrigin.packageName) { LinkedHashSet() } += SourceDevice(deviceKind(device), name)
                }
            }
            if (allowed(OxygenSaturationRecord::class)) {
                val records = client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, range, pageSize = SOURCE_PAGE_SIZE)).records
                add(HealthType.Oxygen, records.map { it.metadata.dataOrigin.packageName }.toSet())
                collect(records)
            }
            if (allowed(HeartRateRecord::class)) {
                collect(client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range, ascendingOrder = false, pageSize = SOURCE_PAGE_SIZE)).records)
            }
            if (allowed(StepsRecord::class)) {
                collect(client.readRecords(ReadRecordsRequest(StepsRecord::class, range, ascendingOrder = false, pageSize = SOURCE_PAGE_SIZE)).records)
            }
            SourceReport(
                found.map { (pkg, types) ->
                    val known = devices[pkg].orEmpty()
                    SourceApp(pkg, types.sortedBy { it.ordinal }.toSet(), known.filter { it.kind != DeviceKind.Other || it.name != null }.toSet())
                },
                !granted.containsAll(PERMISSIONS) || WearableSteps.READ_STEPS !in granted,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun snapshot(context: Context): HealthSnapshot? {
        if (BandDirect.isEnabled(context)) return BandHealth.snapshot(context)
        if (!WearableSteps.isAvailable(context)) return null
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            fun allowed(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val from = today.atStartOfDay(zone).toInstant()
            val to = today.plusDays(1).atStartOfDay(zone).toInstant()
            val sleep = if (allowed(SleepSessionRecord::class)) {
                readAll(client, SleepSessionRecord::class, from.minus(Duration.ofHours(12)), to)
                    .filter { it.endTime >= from && it.endTime < to }
                    .groupBy { it.metadata.dataOrigin.packageName }
                    .values
                    .maxOfOrNull { sessions -> sessions.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() } }
            } else {
                null
            }
            val dayRange = TimeRangeFilter.between(today.atStartOfDay(), today.plusDays(1).atStartOfDay())
            val restingMetrics = buildSet<AggregateMetric<*>> {
                if (allowed(HeartRateRecord::class)) add(HeartRateRecord.BPM_MIN)
                if (allowed(RestingHeartRateRecord::class)) add(RestingHeartRateRecord.BPM_AVG)
            }
            val resting = if (restingMetrics.isNotEmpty()) {
                val result = client.aggregate(AggregateRequest(restingMetrics, dayRange))
                result[RestingHeartRateRecord.BPM_AVG] ?: result[HeartRateRecord.BPM_MIN]
            } else {
                null
            }
            val latest = if (allowed(HeartRateRecord::class)) {
                client.readRecords(
                    ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(from, Instant.now()), ascendingOrder = false, pageSize = 1),
                ).records.firstOrNull()?.samples?.maxByOrNull { it.time }
            } else {
                null
            }
            HealthSnapshot(sleep, resting, latest?.beatsPerMinute, latest?.time)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun heartDay(context: Context, date: LocalDate): HeartDay? {
        if (BandDirect.isEnabled(context)) return BandHealth.heartDay(context, date)
        if (!WearableSteps.isAvailable(context)) return null
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (HealthPermission.getReadPermission(HeartRateRecord::class) !in granted) return null
            val zone = ZoneId.systemDefault()
            val range = TimeRangeFilter.between(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant())
            val slots = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    setOf(HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MAX),
                    range,
                    Duration.ofMinutes(HEART_SLOT_MINUTES),
                ),
            ).mapNotNull { group ->
                val min = group.result[HeartRateRecord.BPM_MIN]
                val avg = group.result[HeartRateRecord.BPM_AVG]
                val max = group.result[HeartRateRecord.BPM_MAX]
                if (min != null && avg != null && max != null) HeartSlot(group.startTime.atZone(zone).toLocalTime(), min, avg, max) else null
            }
            val resting = if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in granted) {
                val dayRange = TimeRangeFilter.between(date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                client.aggregate(AggregateRequest(setOf(RestingHeartRateRecord.BPM_AVG), dayRange))[RestingHeartRateRecord.BPM_AVG]
            } else {
                null
            }
            HeartDay(slots, resting ?: HealthMath.restingEstimate(slots.map { it.avg }))
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

        val nights = if (allowed(SleepSessionRecord::class)) {
            readAll(client, SleepSessionRecord::class, start.minus(Duration.ofHours(12)), end)
                .filter { it.endTime >= start && it.endTime < end }
                .groupBy { it.endTime.atZone(zone).toLocalDate() }
                .mapNotNull { (date, sessions) ->
                    sessions.groupBy { it.metadata.dataOrigin.packageName }.values
                        .mapNotNull { origin -> HealthMath.night(origin.map(::span)) }
                        .maxByOrNull { it.minutes }
                        ?.let { date to it }
                }
                .toMap()
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
        val before = from.minusDays(days.size.toLong())
        val restingByDay = if (restingMetrics.isNotEmpty()) {
            val range = TimeRangeFilter.between(before.atStartOfDay(), to.plusDays(1).atStartOfDay())
            client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(restingMetrics, range, Period.ofDays(1)))
                .associate { it.startTime.toLocalDate() to (it.result[RestingHeartRateRecord.BPM_AVG] ?: it.result[HeartRateRecord.BPM_MIN]) }
        } else {
            emptyMap()
        }
        val restingBefore = HealthMath.average(restingByDay.filterKeys { it < from && !it.isBefore(before) }.values.filterNotNull())

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

        val activeKcal = HashMap<LocalDate, Long>()
        if (allowed(ActiveCaloriesBurnedRecord::class)) {
            val metric = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            for (origin in client.aggregate(AggregateRequest(metric, localRange)).dataOrigins) {
                client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(metric, localRange, Period.ofDays(1), setOf(origin))).forEach { group ->
                    val kcal = group.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.let { Math.round(it) } ?: 0L
                    val date = group.startTime.toLocalDate()
                    if (kcal > (activeKcal[date] ?: 0L)) activeKcal[date] = kcal
                }
            }
        }

        val oxygen = if (allowed(OxygenSaturationRecord::class)) {
            readAll(client, OxygenSaturationRecord::class, start, end)
                .map { it.percentage.value }
                .filter { it > 0.0 }
                .takeIf { it.isNotEmpty() }
                ?.let { OxygenSummary(it.average(), it.min(), it.size) }
        } else {
            null
        }

        return BandPeriod(
            days = days,
            nights = nights,
            resting = days.map { it to restingByDay[it] },
            restingBefore = restingBefore,
            exercises = exercises,
            distanceKm = distance,
            activeKcal = activeKcal,
            oxygen = oxygen,
            sleepAllowed = allowed(SleepSessionRecord::class),
            exerciseAllowed = allowed(ExerciseSessionRecord::class),
            oxygenAllowed = allowed(OxygenSaturationRecord::class),
            missingPermissions = !granted.containsAll(PERMISSIONS),
        )
    }

    private fun deviceKind(device: Device): DeviceKind = when (device.type) {
        Device.TYPE_WATCH -> DeviceKind.Watch
        Device.TYPE_FITNESS_BAND -> DeviceKind.Band
        Device.TYPE_RING -> DeviceKind.Ring
        Device.TYPE_PHONE -> DeviceKind.Phone
        else -> DeviceKind.Other
    }

    private fun span(record: SleepSessionRecord) = SleepSpan(
        record.startTime,
        record.endTime,
        record.stages.mapNotNull { stage -> stageOf(stage.stage)?.let { Triple(it, stage.startTime, stage.endTime) } },
    )

    private fun stageOf(type: Int): SleepStage? = when (type) {
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.Deep
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.Light
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.Rem
        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStage.Awake
        else -> null
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
