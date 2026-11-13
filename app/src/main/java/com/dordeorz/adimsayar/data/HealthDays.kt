package com.dordeorz.adimsayar.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One stretch of sleep: the band writes one for each sleep of a day (a night and a nap are two parts). */
class SleepPart(val start: Instant, val end: Instant, val minutes: Long)

/**
 * One night for the sleep detail page: the total, the parts with their times, the stage timeline when the
 * source has one (Health Connect; the band sends only stage totals), and heart rate and SpO2 while asleep.
 */
class SleepDay(
    val night: SleepNight,
    val parts: List<SleepPart>,
    val stages: List<Triple<SleepStage, Instant, Instant>>,
    val heart: List<Pair<Instant, Long>>,
    val oxygen: List<Pair<Instant, Double>>,
)

/** SpO2 and stress readings of one day, oldest first. */
class DayReadings(val oxygen: List<Pair<Instant, Double>>, val stress: List<Pair<Instant, Long>>)

object HealthAxis {

    /** Whole hours to label on a time axis from [from] up to (not including) [to]: every 1, 2, 3 or 6 hours. */
    fun ticks(from: Instant, to: Instant, zone: ZoneId): List<Instant> {
        val hours = Duration.between(from, to).toHours()
        val step = when {
            hours <= 6L -> 1L
            hours <= 12L -> 2L
            hours <= 18L -> 3L
            else -> 6L
        }
        var tick = from.atZone(zone).truncatedTo(ChronoUnit.HOURS)
        if (tick.toInstant() < from) tick = tick.plusHours(1)
        while (tick.hour % step != 0L) tick = tick.plusHours(1)
        val result = ArrayList<Instant>()
        while (tick.toInstant() < to) {
            result += tick.toInstant()
            tick = tick.plusHours(step)
        }
        return result
    }

    /** The whole hours around a stretch of time, so a chart starts and ends on an hour. */
    fun hoursAround(start: Instant, end: Instant, zone: ZoneId): Pair<Instant, Instant> {
        val first = start.atZone(zone).truncatedTo(ChronoUnit.HOURS)
        val lastHour = end.atZone(zone).truncatedTo(ChronoUnit.HOURS)
        val last = if (lastHour.toInstant() == end) lastHour else lastHour.plusHours(1)
        return first.toInstant() to last.toInstant()
    }
}
