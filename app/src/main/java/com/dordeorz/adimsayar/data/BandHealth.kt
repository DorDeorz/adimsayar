package com.dordeorz.adimsayar.data

import android.content.Context
import com.dordeorz.adimsayar.band.BandMinute
import com.dordeorz.adimsayar.band.BandSleepRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object BandHealth {

    private const val SLOT_SECONDS = WearableHealth.HEART_SLOT_MINUTES * 60L
    private val HEART = 30..220
    private val OXYGEN = 70..100
    private val STRESS = 1..100
    private const val MAX_BEFORE_DAYS = 30L

    suspend fun saveMinutes(context: Context, minutes: List<BandMinute>) {
        val rows = mergeSamples(minutes.mapNotNull { minute ->
            val heart = minute.heartRate.takeIf { it in HEART } ?: 0
            val spo2 = minute.spo2.takeIf { it in OXYGEN } ?: 0
            val stress = minute.stress.takeIf { it in STRESS } ?: 0
            if (heart == 0 && spo2 == 0 && stress == 0) null else BandSample(minute.epochSeconds - minute.epochSeconds % 60L, heart, spo2, stress)
        })
        if (rows.isEmpty()) return
        val dao = dao(context)
        val existing = dao.samples(rows.first().minute, rows.last().minute + 60L)
        dao.upsertSamples(mergeSamples(existing + rows))
    }

    fun mergeSamples(rows: List<BandSample>): List<BandSample> =
        rows.groupBy { it.minute }.map { (minute, list) ->
            BandSample(
                minute,
                list.lastOrNull { it.heart > 0 }?.heart ?: 0,
                list.lastOrNull { it.spo2 > 0 }?.spo2 ?: 0,
                list.lastOrNull { it.stress > 0 }?.stress ?: 0,
            )
        }.sortedBy { it.minute }

    suspend fun saveSleep(context: Context, sleep: BandSleepRecord) {
        val dao = dao(context)
        // The band can send a night more than once. A copy made before the night ended has an earlier
        // wake time or fewer minutes, and must not replace the whole night.
        val kept = dao.sleepAt(sleep.bedtime)
        if (kept != null && (kept.wake > sleep.wake || (kept.wake == sleep.wake && kept.minutes > sleep.minutes))) return
        dao.upsertSleep(BandSleep(sleep.bedtime, sleep.wake, sleep.minutes, sleep.deep, sleep.light, sleep.rem, sleep.awake))
    }

    suspend fun day(context: Context, date: LocalDate): BandDay {
        val zone = ZoneId.systemDefault()
        val (from, to) = bounds(date, date, zone)
        val samples = dao(context).samples(from, to)
        val hearts = samples.map { it.heart.toLong() }.filter { it > 0L }
        val heart = if (hearts.isEmpty()) {
            null
        } else {
            val rest = if (restingDay(context, date, zone)) resting(samples) ?: hearts.min() else null
            HeartSummary(rest, hearts.min(), HealthMath.average(hearts) ?: 0L, hearts.max())
        }
        return BandDay(null, null, emptyList(), heart, nights(context, date, date, zone)[date]?.minutes)
    }

    suspend fun heartDay(context: Context, date: LocalDate): HeartDay {
        val zone = ZoneId.systemDefault()
        val (from, to) = bounds(date, date, zone)
        val slots = slots(dao(context).samples(from, to)).map { (start, values) ->
            HeartSlot(Instant.ofEpochSecond(start).atZone(zone).toLocalTime(), values.min(), HealthMath.average(values) ?: 0L, values.max())
        }
        return HeartDay(slots, if (restingDay(context, date, zone)) HealthMath.restingEstimate(slots.map { it.avg }) else null)
    }

    suspend fun sleepDay(context: Context, date: LocalDate): SleepDay? {
        val (from, to) = bounds(date, date, ZoneId.systemDefault())
        val rows = dao(context).sleep(from, to).sortedBy { it.bedtime }
        val night = nightOf(rows) ?: return null
        val samples = dao(context).samples(rows.first().bedtime, rows.maxOf { it.wake } + 60L)
            .filter { sample -> rows.any { sample.minute >= it.bedtime && sample.minute <= it.wake } }
        return SleepDay(
            night = night,
            parts = rows.map { SleepPart(Instant.ofEpochSecond(it.bedtime), Instant.ofEpochSecond(it.wake), sleptMinutes(it)) },
            stages = emptyList(),
            heart = slots(samples).mapNotNull { (start, values) -> HealthMath.average(values)?.let { Instant.ofEpochSecond(start) to it } },
            oxygen = samples.filter { it.spo2 > 0 }.map { Instant.ofEpochSecond(it.minute) to it.spo2.toDouble() },
        )
    }

    suspend fun readings(context: Context, date: LocalDate): DayReadings {
        val (from, to) = bounds(date, date, ZoneId.systemDefault())
        val samples = dao(context).samples(from, to)
        return DayReadings(
            oxygen = samples.filter { it.spo2 > 0 }.map { Instant.ofEpochSecond(it.minute) to it.spo2.toDouble() },
            stress = samples.filter { it.stress > 0 }.map { Instant.ofEpochSecond(it.minute) to it.stress.toLong() },
        )
    }

    suspend fun period(context: Context, to: LocalDate, days: Long): BandPeriod {
        val zone = ZoneId.systemDefault()
        val from = to.minusDays(days - 1)
        val before = from.minusDays(minOf(days, MAX_BEFORE_DAYS))
        val dates = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        val (start, end) = bounds(before, to, zone)
        val byDay = dao(context).samples(start, end).groupBy { Instant.ofEpochSecond(it.minute).atZone(zone).toLocalDate() }
        val allNights = nights(context, before, to, zone)
        val restingDays = HealthMath.restingDays(byDay.keys, allNights.keys)
        val restingByDay = byDay.filterKeys { it in restingDays }.mapValues { resting(it.value) }
        val restingBefore = HealthMath.average(restingByDay.filterKeys { it < from }.values.filterNotNull())
        val current = byDay.filterKeys { it >= from }
        val oxygen = current.values.flatten().map { it.spo2.toDouble() }.filter { it > 0.0 }
            .takeIf { it.isNotEmpty() }
            ?.let { OxygenSummary(it.average(), it.min(), it.size) }
        val oxygenDaily = current.mapValues { (_, rows) -> rows.map { it.spo2.toDouble() }.filter { it > 0.0 } }
            .filterValues { it.isNotEmpty() }
            .mapValues { it.value.average() }
        val stressDaily = current.mapValues { (_, rows) -> rows.map { it.stress.toLong() }.filter { it > 0L } }
            .filterValues { it.isNotEmpty() }
            .mapValues { HealthMath.average(it.value) ?: 0L }
        val heartDaily = current.mapNotNull { (date, rows) ->
            val hearts = rows.map { it.heart.toLong() }.filter { it > 0L }
            if (hearts.isEmpty()) null else date to HeartSummary(restingByDay[date], hearts.min(), HealthMath.average(hearts) ?: 0L, hearts.max())
        }.toMap()
        return BandPeriod(
            days = dates,
            nights = allNights.filterKeys { it >= from },
            resting = dates.map { it to restingByDay[it] },
            restingBefore = restingBefore,
            exercises = emptyList(),
            distanceKm = null,
            activeKcal = emptyMap(),
            oxygen = oxygen,
            oxygenDaily = oxygenDaily,
            stressDaily = stressDaily,
            sleepAllowed = true,
            exerciseAllowed = true,
            oxygenAllowed = true,
            missingPermissions = false,
            heartDaily = heartDaily,
        )
    }

    suspend fun snapshot(context: Context): HealthSnapshot {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val (from, to) = bounds(today, today, zone)
        val latest = dao(context).latestHeart()
        return HealthSnapshot(
            nights(context, today, today, zone)[today]?.minutes,
            if (restingDay(context, today, zone)) resting(dao(context).samples(from, to)) else null,
            latest?.heart?.toLong(),
            latest?.let { Instant.ofEpochSecond(it.minute) },
        )
    }

    /** Whether [date]'s heart rate gives a resting value, by the rule of [HealthMath.restingDays] the trend uses. */
    private suspend fun restingDay(context: Context, date: LocalDate, zone: ZoneId): Boolean {
        val nights = nights(context, date.minusDays(MAX_BEFORE_DAYS), date, zone).keys
        return date in HealthMath.restingDays(setOf(date), nights)
    }

    private suspend fun nights(context: Context, from: LocalDate, to: LocalDate, zone: ZoneId): Map<LocalDate, SleepNight> {
        val (start, end) = bounds(from, to, zone)
        return dao(context).sleep(start, end)
            .groupBy { Instant.ofEpochSecond(it.wake).atZone(zone).toLocalDate() }
            .mapNotNull { (date, rows) -> nightOf(rows)?.let { date to it } }
            .toMap()
    }

    /** The sleeps that ended on one day, added up; null when they hold no sleep. */
    private fun nightOf(rows: List<BandSleep>): SleepNight? {
        if (rows.isEmpty()) return null
        val stages = mapOf(
            SleepStage.Deep to rows.sumOf { it.deep }.toLong(),
            SleepStage.Light to rows.sumOf { it.light }.toLong(),
            SleepStage.Rem to rows.sumOf { it.rem }.toLong(),
            SleepStage.Awake to rows.sumOf { it.awake }.toLong(),
        ).filterValues { it > 0L }
        val total = rows.sumOf { sleptMinutes(it) }
        if (total <= 0L) return null
        return SleepNight(total, stages, Instant.ofEpochSecond(rows.minOf { it.bedtime }), Instant.ofEpochSecond(rows.maxOf { it.wake }))
    }

    private fun sleptMinutes(row: BandSleep): Long = row.minutes.takeIf { it > 0 }?.toLong() ?: ((row.wake - row.bedtime) / 60L)

    private fun slots(samples: List<BandSample>): List<Pair<Long, List<Long>>> =
        samples.filter { it.heart > 0 }
            .groupBy { it.minute - it.minute % SLOT_SECONDS }
            .map { (start, rows) -> start to rows.map { it.heart.toLong() } }
            .sortedBy { it.first }

    private fun resting(samples: List<BandSample>): Long? =
        HealthMath.restingEstimate(slots(samples).mapNotNull { HealthMath.average(it.second) })

    private fun bounds(from: LocalDate, to: LocalDate, zone: ZoneId): Pair<Long, Long> =
        from.atStartOfDay(zone).toEpochSecond() to to.plusDays(1).atStartOfDay(zone).toEpochSecond()

    private fun dao(context: Context) = StepDatabase.get(context).bandHealth()
}
