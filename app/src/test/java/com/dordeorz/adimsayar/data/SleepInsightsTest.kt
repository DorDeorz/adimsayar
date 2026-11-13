package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class SleepInsightsTest {

    private val today = LocalDate.of(2026, 10, 28)

    private fun night(minutes: Long) = SleepNight(minutes, emptyMap(), Instant.EPOCH, Instant.EPOCH)

    @Test
    fun comparesStepsAfterLongAndShortNights() {
        val nights = (1..6L).associate { today.minusDays(it) to night(if (it <= 3) 480L else 360L) }
        val history = (1..6L).associate { today.minusDays(it) to if (it <= 3) 9_000L else 6_000L }
        val split = SleepInsights.compute(nights, history, today, 10_000L).steps
        assertNotNull(split)
        assertEquals(9_000L, split!!.goodSteps)
        assertEquals(6_000L, split.shortSteps)
        assertEquals(3, split.goodNights)
        assertEquals(3, split.shortNights)
    }

    @Test
    fun needsThreeNightsInEachGroup() {
        val nights = (1..5L).associate { today.minusDays(it) to night(if (it <= 3) 480L else 360L) }
        val history = (1..5L).associate { today.minusDays(it) to 8_000L }
        assertNull(SleepInsights.compute(nights, history, today, 10_000L).steps)
    }

    @Test
    fun todayIsLeftOut() {
        val nights = (0..5L).associate { today.minusDays(it) to night(if (it <= 2) 480L else 360L) }
        val history = (0..5L).associate { today.minusDays(it) to 8_000L }
        assertNull(SleepInsights.compute(nights, history, today, 10_000L).steps)
    }

    @Test
    fun sleepAfterActiveDaysUsesNextNight() {
        val history = (2..7L).associate { today.minusDays(it) to if (it <= 4) 12_000L else 4_000L }
        val nights = (1..6L).associate { today.minusDays(it) to night(if (it <= 3) 500L else 400L) }
        val split = SleepInsights.compute(nights, history, today, 10_000L).sleep!!
        assertEquals(500L, split.activeSleep)
        assertEquals(400L, split.restSleep)
    }

    @Test
    fun activeDaysUseEachDaysOwnGoal() {
        // 28 October 2026 is a Wednesday, so 24 and 25 October are the weekend before it.
        val history = (2..9L).associate { today.minusDays(it) to 7_000L }
        val nights = (1..8L).associate { offset ->
            val date = today.minusDays(offset)
            // The night after a weekend day is the long one.
            date to night(if (date.minusDays(1).dayOfWeek >= DayOfWeek.SATURDAY) 500L else 400L)
        }
        val weekendGoal = { date: LocalDate -> if (date.dayOfWeek >= DayOfWeek.SATURDAY) 6_000L else 10_000L }
        assertNull(SleepInsights.compute(nights, history, today, 10_000L).sleep)
        val more = (2..16L).associate { today.minusDays(it) to 7_000L }
        val moreNights = (1..15L).associate { offset ->
            val date = today.minusDays(offset)
            date to night(if (date.minusDays(1).dayOfWeek >= DayOfWeek.SATURDAY) 500L else 400L)
        }
        val split = SleepInsights.compute(moreNights, more, today, 10_000L, weekendGoal).sleep!!
        assertEquals(4, split.activeDays)
        assertEquals(11, split.restDays)
        assertEquals(500L, split.activeSleep)
        assertEquals(400L, split.restSleep)
    }
}
