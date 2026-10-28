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

    @Test
    fun mergeHourlyUsesBandHoursAndPhoneForTheRest() {
        val morning = LocalDateTime.parse("2026-10-11T09:00")
        val evening = LocalDateTime.parse("2026-10-11T21:00")
        val otherDay = LocalDateTime.parse("2026-10-10T12:00")
        val band = mapOf(evening to 200L)
        val phone = mapOf(morning to 1_500L, evening to 260L, otherDay to 3_000L)
        assertEquals(mapOf(LocalDate.parse("2026-10-11") to 1_700L), StepMath.mergeHourly(band, phone))
    }

    @Test
    fun mergeHourlyKeepsWornBandHoursWithoutSteps() {
        val morning = LocalDateTime.parse("2026-10-11T09:00")
        val evening = LocalDateTime.parse("2026-10-11T21:00")
        val band = mapOf(morning to 0L, evening to 200L)
        val phone = mapOf(morning to 120L, evening to 260L)
        assertEquals(mapOf(LocalDate.parse("2026-10-11") to 200L), StepMath.mergeHourly(band, phone))
    }

    @Test
    fun mergeMixedUsesHoursWherePhoneHoursExist() {
        val today = LocalDate.parse("2026-10-28")
        val older = LocalDate.parse("2026-10-20")
        val band = mapOf(
            LocalDateTime.parse("2026-10-28T10:00") to 1_000L,
            LocalDateTime.parse("2026-10-20T10:00") to 2_000L,
        )
        val phoneHours = mapOf(
            LocalDateTime.parse("2026-10-28T10:00") to 1_400L,
            LocalDateTime.parse("2026-10-28T18:00") to 300L,
        )
        val phoneDays = mapOf(today to 1_700L, older to 2_500L)
        assertEquals(mapOf(today to 1_300L, older to 2_500L), StepMath.mergeMixed(band, phoneHours, phoneDays))
    }

    @Test
    fun phoneGapsAddOnlyTheDailyShortfall() {
        val nine = LocalDateTime.parse("2026-10-27T09:00")
        val ten = LocalDateTime.parse("2026-10-27T10:00")
        val eleven = LocalDateTime.parse("2026-10-27T11:00")
        // Our reads spread steps differently over 9 and 10, and missed 200 steps at 11 (reboot).
        val own = mapOf(nine to 700L, ten to 300L)
        val other = mapOf(nine to 500L, ten to 500L, eleven to 200L)
        val gaps = StepMath.phoneGaps(other, own)
        assertEquals(200L, gaps.values.sum())
        assertEquals(null, gaps[nine])
    }

    @Test
    fun phoneGapsSkipDaysWhereWeCountedMore() {
        val hour = LocalDateTime.parse("2026-10-27T09:00")
        assertEquals(emptyMap<LocalDateTime, Long>(), StepMath.phoneGaps(mapOf(hour to 500L), mapOf(hour to 600L)))
    }

    @Test
    fun earlyBirdUsesBandHoursFirstAndOnlyMornings() {
        val band = mapOf(LocalDateTime.parse("2026-10-28T07:00") to 600L)
        val phone = mapOf(
            LocalDateTime.parse("2026-10-28T06:00") to 500L,
            LocalDateTime.parse("2026-10-28T07:00") to 900L,
            LocalDateTime.parse("2026-10-27T07:00") to 800L,
            LocalDateTime.parse("2026-10-27T08:00") to 5_000L,
        )
        assertEquals(setOf(LocalDate.parse("2026-10-28")), StepMath.earlyBirdDays(band, phone))
    }

    @Test
    fun bandDaysSkipDaysWithoutBandSteps() {
        val band = mapOf(
            LocalDateTime.parse("2026-10-28T10:00") to 1_000L,
            LocalDateTime.parse("2026-10-28T11:00") to 500L,
            LocalDateTime.parse("2026-10-27T10:00") to 0L,
        )
        assertEquals(mapOf(LocalDate.parse("2026-10-28") to 1_500L), StepMath.bandDays(band))
    }

    @Test
    fun mergeDailyKeepsPhoneTotalWhenBandWasWornBriefly() {
        val day = LocalDate.parse("2026-10-11")
        assertEquals(mapOf(day to 1_561L), StepMath.mergeDaily(mapOf(day to 17L), mapOf(day to 1_561L)))
    }

    @Test
    fun hourSplitKeepsTotalAndFollowsTime() {
        val hours = StepMath.splitByHour(StepInterval(900L, at("2026-10-01T13:30"), at("2026-10-01T15:00")), zone)
        assertEquals(mapOf(LocalDateTime.parse("2026-10-01T13:00") to 300L, LocalDateTime.parse("2026-10-01T14:00") to 600L), hours)
    }

    @Test
    fun hourSplitOfInstantReadingGoesToItsHour() {
        val hours = StepMath.splitByHour(StepInterval(5L, at("2026-10-01T09:10"), at("2026-10-01T09:10")), zone)
        assertEquals(mapOf(LocalDateTime.parse("2026-10-01T09:00") to 5L), hours)
    }
}
