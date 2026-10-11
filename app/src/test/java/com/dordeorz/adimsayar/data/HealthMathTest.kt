package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class HealthMathTest {

    private val zone = ZoneOffset.UTC

    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute).toInstant(zone)

    @Test
    fun bedtimeAveragesAcrossMidnight() {
        val times = listOf(at(1, 23, 30), at(2, 0, 30))
        assertEquals(LocalTime.of(0, 0), HealthMath.averageBedtime(times, zone))
    }

    @Test
    fun wakeAveragesMorningTimes() {
        val times = listOf(at(2, 7, 0), at(3, 8, 0))
        assertEquals(LocalTime.of(7, 30), HealthMath.averageWake(times, zone))
    }

    @Test
    fun nightSumsSpansAndStages() {
        val first = SleepSpan(
            at(1, 23),
            at(2, 3),
            listOf(Triple(SleepStage.Deep, at(1, 23), at(2, 0)), Triple(SleepStage.Light, at(2, 0), at(2, 3))),
        )
        val second = SleepSpan(at(2, 4), at(2, 6), listOf(Triple(SleepStage.Rem, at(2, 4), at(2, 5))))
        val night = requireNotNull(HealthMath.night(listOf(first, second)))
        assertEquals(360L, night.minutes)
        assertEquals(mapOf(SleepStage.Deep to 60L, SleepStage.Light to 180L, SleepStage.Rem to 60L), night.stages)
        assertEquals(at(1, 23), night.bedtime)
        assertEquals(at(2, 6), night.wake)
    }

    @Test
    fun emptyNightIsNull() {
        assertNull(HealthMath.night(emptyList()))
    }

    @Test
    fun bmiAndCategories() {
        assertEquals(24.22, requireNotNull(HealthMath.bmi(70.0, 170)), 0.01)
        assertEquals(BmiCategory.Under, HealthMath.bmiCategory(18.4))
        assertEquals(BmiCategory.Normal, HealthMath.bmiCategory(18.5))
        assertEquals(BmiCategory.Over, HealthMath.bmiCategory(25.0))
        assertEquals(BmiCategory.Obese, HealthMath.bmiCategory(30.0))
        assertNull(HealthMath.bmi(70.0, 0))
    }

    @Test
    fun averageRounds() {
        assertEquals(61L, HealthMath.average(listOf(60L, 61L, 62L)))
        assertNull(HealthMath.average(emptyList()))
    }
}
