package com.dordeorz.adimsayar.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

    fun dayOf(wallMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(wallMs).atZone(zone).toLocalDate()
}
