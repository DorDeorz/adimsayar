package com.dordeorz.adimsayar.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CounterReading(val counter: Long, val elapsedMs: Long, val wallMs: Long)

data class StepInterval(val steps: Long, val fromWallMs: Long, val toWallMs: Long)

object StepMath {

    fun interval(previous: CounterReading?, current: CounterReading): StepInterval {
        if (previous == null) return StepInterval(0L, current.wallMs, current.wallMs)
        val rebooted = current.elapsedMs < previous.elapsedMs || current.counter < previous.counter
        if (rebooted) {
            val bootWallMs = current.wallMs - current.elapsedMs
            return StepInterval(current.counter, maxOf(previous.wallMs, bootWallMs), current.wallMs)
        }
        return StepInterval(current.counter - previous.counter, previous.wallMs, current.wallMs)
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
