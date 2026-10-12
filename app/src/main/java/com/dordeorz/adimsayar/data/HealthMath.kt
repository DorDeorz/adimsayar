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

object HealthMath {

    private const val MINUTES_PER_DAY = 24 * 60
    private const val NOON = 12 * 60

    fun night(spans: List<SleepSpan>): SleepNight? {
        if (spans.isEmpty()) return null
        val minutes = spans.sumOf { Duration.between(it.start, it.end).toMinutes() }
        if (minutes <= 0L) return null
        val stages = HashMap<SleepStage, Long>()
        for (span in spans) {
            for ((stage, start, end) in span.stages) {
                stages[stage] = (stages[stage] ?: 0L) + Duration.between(start, end).toMinutes()
            }
        }
        return SleepNight(minutes, stages.filterValues { it > 0L }, spans.minOf { it.start }, spans.maxOf { it.end })
    }

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
}
