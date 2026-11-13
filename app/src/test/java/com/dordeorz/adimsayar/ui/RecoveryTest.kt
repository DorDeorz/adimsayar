package com.dordeorz.adimsayar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecoveryTest {

    private val day = LocalDate.of(2026, 11, 10)

    private fun week(value: Long) = (1L..7L).associate { day.minusDays(it) to value }

    @Test
    fun dayIsComparedWithTheWeekBefore() {
        val nights = week(420L) + (day to 462L)
        val resting = week(65L) + (day to 63L)
        // 60 base, sleep 10% longer: +10, resting 2 bpm lower: +8.
        assertEquals(mapOf(day to 78), Recovery.days(listOf(day), nights, resting))
    }

    @Test
    fun dayWithoutSleepOrRestingHasNoScore() {
        assertTrue(Recovery.days(listOf(day), week(420L), week(65L)).isEmpty())
    }
}
