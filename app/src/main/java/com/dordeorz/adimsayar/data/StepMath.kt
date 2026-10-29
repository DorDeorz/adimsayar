package com.dordeorz.adimsayar.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class CounterReading(val counter: Long, val elapsedMs: Long, val wallMs: Long, val bootCount: Int?)

enum class ReadingKind { Baseline, Steps, Reboot, Reset, Stale }

fun ReadingKind.isNotable() = this == ReadingKind.Reboot || this == ReadingKind.Reset

data class StepInterval(
    val steps: Long,
    val fromWallMs: Long,
    val toWallMs: Long,
    val kind: ReadingKind = ReadingKind.Steps,
)

object StepMath {

    const val STALE_TOLERANCE_MS = 10 * 60 * 1000L

    fun interval(previous: CounterReading?, current: CounterReading): StepInterval {
        if (previous == null) return StepInterval(0L, current.wallMs, current.wallMs, ReadingKind.Baseline)
        if (!sameBoot(previous, current)) {
            val bootWallMs = current.wallMs - current.elapsedMs
            return StepInterval(current.counter, maxOf(previous.wallMs, bootWallMs), current.wallMs, ReadingKind.Reboot)
        }
        if (current.elapsedMs < previous.elapsedMs) {
            return StepInterval(0L, previous.wallMs, previous.wallMs, ReadingKind.Stale)
        }
        if (current.counter < previous.counter) {
            return StepInterval(current.counter, previous.wallMs, current.wallMs, ReadingKind.Reset)
        }
        return StepInterval(current.counter - previous.counter, previous.wallMs, current.wallMs)
    }

    private fun sameBoot(previous: CounterReading, current: CounterReading): Boolean {
        if (previous.bootCount != null && current.bootCount != null) return previous.bootCount == current.bootCount
        return current.elapsedMs >= previous.elapsedMs - STALE_TOLERANCE_MS
    }

    fun splitByDay(interval: StepInterval, zone: ZoneId): Map<LocalDate, Long> {
        val (steps, from, to) = interval
        if (steps <= 0L) return emptyMap()
        val lastDay = dayOf(to, zone)
        if (to <= from) return mapOf(lastDay to steps)
        val result = LinkedHashMap<LocalDate, Long>()
        val total = (to - from).toDouble()
        var assigned = 0L
        var day = dayOf(from, zone)
        var start = from
        while (day < lastDay) {
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val share = minOf(Math.round(steps * ((end - start) / total)), steps - assigned)
            if (share > 0L) result[day] = share
            assigned += share
            start = end
            day = day.plusDays(1)
        }
        val rest = steps - assigned
        if (rest > 0L) result[lastDay] = rest
        return result
    }

    fun splitByHour(interval: StepInterval, zone: ZoneId): Map<LocalDateTime, Long> {
        val (steps, from, to) = interval
        if (steps <= 0L) return emptyMap()
        val lastHour = hourOf(to, zone)
        if (to <= from) return mapOf(lastHour to steps)
        val result = LinkedHashMap<LocalDateTime, Long>()
        val total = (to - from).toDouble()
        var assigned = 0L
        var hour = hourOf(from, zone)
        var start = from
        while (hour < lastHour) {
            val end = hour.plusHours(1).atZone(zone).toInstant().toEpochMilli()
            val share = minOf(Math.round(steps * ((end - start).coerceAtLeast(0L) / total)), steps - assigned)
            if (share > 0L) result[hour] = share
            assigned += share
            start = maxOf(start, end)
            hour = hour.plusHours(1)
        }
        val rest = steps - assigned
        if (rest > 0L) result[lastHour] = rest
        return result
    }

    fun mergeHourly(band: Map<LocalDateTime, Long>, phone: Map<LocalDateTime, Long>): Map<LocalDate, Long> {
        val bandDays = band.filterValues { it > 0L }.keys.map { it.toLocalDate() }.toSet()
        val result = HashMap<LocalDate, Long>()
        for (hour in band.keys + phone.keys) {
            val day = hour.toLocalDate()
            if (day !in bandDays) continue
            val steps = band[hour] ?: phone[hour] ?: 0L
            result[day] = (result[day] ?: 0L) + steps
        }
        return result
    }

    fun bandDays(band: Map<LocalDateTime, Long>): Map<LocalDate, Long> {
        val days = HashMap<LocalDate, Long>()
        for ((hour, steps) in band) days[hour.toLocalDate()] = (days[hour.toLocalDate()] ?: 0L) + steps
        return days.filterValues { it > 0L }
    }

    // Days with phone hours merge hour by hour; older days (before phone hours were kept, or imported) by day.
    fun mergeMixed(band: Map<LocalDateTime, Long>, phoneHours: Map<LocalDateTime, Long>, phoneDays: Map<LocalDate, Long>): Map<LocalDate, Long> {
        val hourlyDays = phoneHours.keys.map { it.toLocalDate() }.toSet()
        val hourlyBand = band.filterKeys { it.toLocalDate() in hourlyDays }
        return mergeDaily(bandDays(band).filterKeys { it !in hourlyDays }, phoneDays) + mergeHourly(hourlyBand, phoneHours)
    }

    fun mergeDaily(band: Map<LocalDate, Long>, phone: Map<LocalDate, Long>): Map<LocalDate, Long> =
        band.filterValues { it > 0L }.mapValues { (day, steps) -> maxOf(steps, phone[day] ?: 0L) }

    fun hourOf(wallMs: Long, zone: ZoneId): LocalDateTime =
        Instant.ofEpochMilli(wallMs).atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)

    fun dayOf(wallMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(wallMs).atZone(zone).toLocalDate()
}
