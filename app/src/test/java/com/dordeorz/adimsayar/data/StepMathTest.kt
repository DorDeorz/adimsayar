package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class StepMathTest {

    private val zone = ZoneId.of("Europe/Istanbul")

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun firstReadingOnlySetsBaseline() {
        val interval = StepMath.interval(null, CounterReading(5_000L, 1_000L, at("2026-10-09T10:00")))
        assertEquals(0L, interval.steps)
    }

    @Test
    fun normalReadingIsDifference() {
        val previous = CounterReading(5_000L, 1_000L, at("2026-10-09T10:00"))
        val current = CounterReading(5_500L, 901_000L, at("2026-10-09T10:15"))
        assertEquals(StepInterval(500L, previous.wallMs, current.wallMs), StepMath.interval(previous, current))
    }

    @Test
    fun rebootCountsStepsSinceBoot() {
        val previous = CounterReading(9_000L, 50_000_000L, at("2026-10-09T08:00"))
        val current = CounterReading(300L, 600_000L, at("2026-10-09T10:00"))
        val interval = StepMath.interval(previous, current)
        assertEquals(300L, interval.steps)
        assertEquals(at("2026-10-09T09:50"), interval.fromWallMs)
    }

    @Test
    fun rebootDetectedByElapsedEvenIfCounterHigher() {
        val previous = CounterReading(100L, 50_000_000L, at("2026-10-09T08:00"))
        val current = CounterReading(400L, 600_000L, at("2026-10-09T10:00"))
        assertEquals(400L, StepMath.interval(previous, current).steps)
    }

    @Test
    fun sameDayStaysOnOneDay() {
        val split = StepMath.splitByDay(StepInterval(800L, at("2026-10-09T10:00"), at("2026-10-09T10:15")), zone)
        assertEquals(mapOf(LocalDate.parse("2026-10-09") to 800L), split)
    }

    @Test
    fun midnightIntervalIsSplitByTime() {
        val split = StepMath.splitByDay(StepInterval(1_000L, at("2026-10-09T23:45"), at("2026-10-10T00:15")), zone)
        assertEquals(mapOf(LocalDate.parse("2026-10-09") to 500L, LocalDate.parse("2026-10-10") to 500L), split)
    }

    @Test
    fun splitAlwaysKeepsTotal() {
        val split = StepMath.splitByDay(StepInterval(1_001L, at("2026-10-07T20:00"), at("2026-10-10T00:00")), zone)
        assertEquals(1_001L, split.values.sum())
        assertEquals(LocalDate.parse("2026-10-07"), split.keys.first())
    }

    @Test
    fun clockMovedBackGoesToCurrentDay() {
        val split = StepMath.splitByDay(StepInterval(200L, at("2026-10-10T10:00"), at("2026-10-09T10:00")), zone)
        assertEquals(mapOf(LocalDate.parse("2026-10-09") to 200L), split)
    }

    @Test
    fun noStepsNoRows() {
        assertEquals(emptyMap<LocalDate, Long>(), StepMath.splitByDay(StepInterval(0L, 0L, 1L), zone))
    }
}
