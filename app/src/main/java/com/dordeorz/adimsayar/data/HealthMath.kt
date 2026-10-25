package com.dordeorz.adimsayar.data

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

enum class SleepStage { Deep, Light, Rem, Awake }

class SleepSpan(val start: Instant, val end: Instant, val stages: List<Triple<SleepStage, Instant, Instant>>)

class SleepNight(
    val minutes: Long,
    val stages: Map<SleepStage, Long>,
    val bedtime: Instant,
    val wake: Instant,
)

enum class BmiCategory { Under, Normal, Over, Obese }

enum class HeartZone(val fromPercent: Int) { Rest(0), Light(50), FatBurn(60), Cardio(70), Peak(80), Max(90) }

enum class RestingCategory { Low, Normal, High }

object HealthMath {

    private const val MINUTES_PER_DAY = 24 * 60
    private const val NOON = 12 * 60

    fun night(spans: List<SleepSpan>): SleepNight? {
        if (spans.isEmpty()) return null
        val inBed = minutes(union(spans.map { it.start to it.end }))
        if (inBed <= 0L) return null
        val stages = spans.flatMap { it.stages }
            .groupBy({ it.first }, { it.second to it.third })
            .mapValues { (_, ranges) -> minutes(union(ranges)) }
            .filterValues { it > 0L }
        val asleep = (inBed - (stages[SleepStage.Awake] ?: 0L)).takeIf { it > 0L } ?: inBed
        return SleepNight(asleep, stages, spans.minOf { it.start }, spans.maxOf { it.end })
    }

    private fun union(ranges: List<Pair<Instant, Instant>>): List<Pair<Instant, Instant>> {
        val merged = ArrayList<Pair<Instant, Instant>>()
        for ((start, end) in ranges.filter { it.second > it.first }.sortedBy { it.first }) {
            val last = merged.lastOrNull()
            if (last != null && start <= last.second) {
                if (end > last.second) merged[merged.size - 1] = last.first to end
            } else {
                merged.add(start to end)
            }
        }
        return merged
    }

    private fun minutes(ranges: List<Pair<Instant, Instant>>): Long = ranges.sumOf { Duration.between(it.first, it.second).toMinutes() }

    fun averageBedtime(times: List<Instant>, zone: ZoneId): LocalTime? = averageClock(times, zone, NOON)

    fun averageWake(times: List<Instant>, zone: ZoneId): LocalTime? = averageClock(times, zone, 0)

    private fun averageClock(times: List<Instant>, zone: ZoneId, pivot: Int): LocalTime? {
        if (times.isEmpty()) return null
        val shifted = times.map {
            val clock = it.atZone(zone).toLocalTime()
            (clock.hour * 60 + clock.minute - pivot).mod(MINUTES_PER_DAY)
        }
        val average = ((shifted.sum().toDouble() / shifted.size).roundToInt() + pivot).mod(MINUTES_PER_DAY)
        return LocalTime.of(average / 60, average % 60)
    }

    fun bmi(weightKg: Double, heightCm: Int): Double? {
        if (heightCm <= 0 || weightKg <= 0.0) return null
        val meters = heightCm / 100.0
        return weightKg / (meters * meters)
    }

    fun bmiCategory(bmi: Double): BmiCategory = when {
        bmi < 18.5 -> BmiCategory.Under
        bmi < 25.0 -> BmiCategory.Normal
        bmi < 30.0 -> BmiCategory.Over
        else -> BmiCategory.Obese
    }

    fun average(values: List<Long>): Long? = if (values.isEmpty()) null else Math.round(values.sum().toDouble() / values.size)

    fun maxHeartRate(age: Int): Long = (220 - age).toLong()

    fun zoneOf(bpm: Long, maxRate: Long): HeartZone {
        val percent = bpm * 100 / maxRate.coerceAtLeast(1L)
        return HeartZone.entries.last { percent >= it.fromPercent }
    }

    fun zoneBpm(zone: HeartZone, maxRate: Long): Long = maxRate * zone.fromPercent / 100

    fun zoneMinutes(averages: List<Long>, maxRate: Long, slotMinutes: Long): Map<HeartZone, Long> =
        averages.groupingBy { zoneOf(it, maxRate) }.eachCount().mapValues { it.value * slotMinutes }

    fun restingEstimate(averages: List<Long>): Long? {
        if (averages.isEmpty()) return null
        val sorted = averages.sorted()
        return sorted[(sorted.size - 1) / 10]
    }

    fun restingCategory(bpm: Long): RestingCategory = when {
        bpm < 60L -> RestingCategory.Low
        bpm <= 100L -> RestingCategory.Normal
        else -> RestingCategory.High
    }
}
