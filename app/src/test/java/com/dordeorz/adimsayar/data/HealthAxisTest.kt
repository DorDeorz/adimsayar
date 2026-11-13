package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class HealthAxisTest {

    private val zone = ZoneOffset.UTC

    private fun at(hour: Int, minute: Int = 0, day: Int = 10) = LocalDateTime.of(2026, 11, day, hour, minute).toInstant(zone)

    @Test
    fun shortNightIsLabelledEveryHour() {
        val (from, to) = HealthAxis.hoursAround(at(3, 1), at(5, 0), zone)
        assertEquals(at(3), from)
        assertEquals(at(5), to)
        assertEquals(listOf(at(3), at(4)), HealthAxis.ticks(from, to, zone))
    }

    @Test
    fun nightAndNapAreLabelledEveryTwoHours() {
        val (from, to) = HealthAxis.hoursAround(at(3, 1), at(13, 2), zone)
        assertEquals(at(3), from)
        assertEquals(at(14), to)
        assertEquals(listOf(at(4), at(6), at(8), at(10), at(12)), HealthAxis.ticks(from, to, zone))
    }

    @Test
    fun nightAcrossMidnightStartsOnAnEvenHour() {
        val (from, to) = HealthAxis.hoursAround(at(23, 30, day = 9), at(7, 15), zone)
        assertEquals(at(23, day = 9), from)
        assertEquals(at(8), to)
        assertEquals(listOf(at(0), at(2), at(4), at(6)), HealthAxis.ticks(from, to, zone))
    }

    @Test
    fun wholeDayIsLabelledEverySixHours() {
        assertEquals(listOf(at(0), at(6), at(12), at(18)), HealthAxis.ticks(at(0), at(0, day = 11), zone))
    }
}
