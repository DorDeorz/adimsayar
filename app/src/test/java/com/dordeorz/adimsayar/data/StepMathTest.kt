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
        val interval = StepMath.interval(null, CounterReading(5_000L, 1_000L, at("2026-10-09T10:00"), 7))
        assertEquals(0L, interval.steps)
        assertEquals(ReadingKind.Baseline, interval.kind)
    }

    @Test
    fun normalReadingIsDifference() {
        val previous = CounterReading(5_000L, 1_000L, at("2026-10-09T10:00"), 7)
        val current = CounterReading(5_500L, 901_000L, at("2026-10-09T10:15"), 7)
        assertEquals(StepInterval(500L, previous.wallMs, current.wallMs), StepMath.interval(previous, current))
    }

    @Test
    fun rebootCountsStepsSinceBoot() {
        val previous = CounterReading(9_000L, 50_000_000L, at("2026-10-09T08:00"), 7)
        val current = CounterReading(300L, 600_000L, at("2026-10-09T10:00"), 8)
        val interval = StepMath.interval(previous, current)
        assertEquals(300L, interval.steps)
        assertEquals(ReadingKind.Reboot, interval.kind)
        assertEquals(at("2026-10-09T09:50"), interval.fromWallMs)
    }

    @Test
    fun rebootDetectedByBootCountEvenIfCounterHigher() {
        val previous = CounterReading(100L, 600_000L, at("2026-10-09T08:00"), 7)
        val current = CounterReading(400L, 900_000L, at("2026-10-09T10:00"), 8)
        assertEquals(400L, StepMath.interval(previous, current).steps)
    }

    @Test
    fun olderEventFromBatchIsIgnored() {
        val previous = CounterReading(121L, 1_000_000L, at("2026-10-09T10:01"), 7)
        val late = CounterReading(90L, 970_000L, at("2026-10-09T10:00:30"), 7)
        val interval = StepMath.interval(previous, late)
        assertEquals(0L, interval.steps)
        assertEquals(ReadingKind.Stale, interval.kind)
    }

    @Test
    fun interleavedListenersCountEachStepOnce() {
        val events = listOf(
            CounterReading(63L, 1_000_000L, at("2026-10-09T10:00:00"), 7),
            CounterReading(80L, 1_020_000L, at("2026-10-09T10:00:20"), 7),
            CounterReading(70L, 1_010_000L, at("2026-10-09T10:00:10"), 7),
            CounterReading(100L, 1_040_000L, at("2026-10-09T10:00:40"), 7),
            CounterReading(90L, 1_030_000L, at("2026-10-09T10:00:30"), 7),
            CounterReading(121L, 1_060_000L, at("2026-10-09T10:01:00"), 7),
        )
        var stored: CounterReading? = null
        var total = 0L
        for (event in events) {
            val interval = StepMath.interval(stored, event)
            if (interval.kind != ReadingKind.Stale) stored = event
            total += interval.steps
        }
        assertEquals(58L, total)
    }

    @Test
    fun counterDropWithinSameBootIsReset() {
        val previous = CounterReading(5_000L, 1_000_000L, at("2026-10-09T10:00"), 7)
        val current = CounterReading(40L, 1_900_000L, at("2026-10-09T10:15"), 7)
        val interval = StepMath.interval(previous, current)
        assertEquals(40L, interval.steps)
        assertEquals(ReadingKind.Reset, interval.kind)
    }

    @Test
    fun withoutBootCountSmallStepBackIsStale() {
        val previous = CounterReading(121L, 1_000_000L, at("2026-10-09T10:01"), null)
        val late = CounterReading(90L, 970_000L, at("2026-10-09T10:00:30"), null)
        assertEquals(ReadingKind.Stale, StepMath.interval(previous, late).kind)
    }

    @Test
    fun withoutBootCountLargeStepBackIsReboot() {
        val previous = CounterReading(9_000L, 50_000_000L, at("2026-10-09T08:00"), null)
        val current = CounterReading(300L, 600_000L, at("2026-10-09T10:00"), null)
        assertEquals(ReadingKind.Reboot, StepMath.interval(previous, current).kind)
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
