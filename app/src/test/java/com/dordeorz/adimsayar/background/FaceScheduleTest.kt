package com.dordeorz.adimsayar.background

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceScheduleTest {

    @Test
    fun dayAndNight() {
        val plan = FaceSchedule.Plan(on = true, dayId = "day", nightId = "night", dayStart = 7 * 60, nightStart = 20 * 60)
        assertEquals("night", FaceSchedule.faceAt(plan, 6 * 60 + 59))
        assertEquals("day", FaceSchedule.faceAt(plan, 7 * 60))
        assertEquals("day", FaceSchedule.faceAt(plan, 19 * 60 + 59))
        assertEquals("night", FaceSchedule.faceAt(plan, 20 * 60))
    }

    @Test
    fun dayAcrossMidnight() {
        val plan = FaceSchedule.Plan(on = true, dayId = "day", nightId = "night", dayStart = 22 * 60, nightStart = 2 * 60)
        assertEquals("day", FaceSchedule.faceAt(plan, 23 * 60))
        assertEquals("day", FaceSchedule.faceAt(plan, 60))
        assertEquals("night", FaceSchedule.faceAt(plan, 12 * 60))
    }

    @Test
    fun alarmWaitsForTheNextChangeWhenNothingIsWaiting() {
        assertEquals(50_000_000L, FaceSchedule.alarmAt(next = 50_000_000L, retryAt = 0L, now = 1_000_000L))
    }

    @Test
    fun alarmKeepsTheWaitingTryWhenAlarmsAreSetAgain() {
        // A change did not reach the band: the try in 10 minutes stays, however often the alarms are set again.
        assertEquals(1_600_000L, FaceSchedule.alarmAt(next = 50_000_000L, retryAt = 1_600_000L, now = 1_000_000L))
        assertEquals(1_600_000L, FaceSchedule.alarmAt(next = 50_000_000L, retryAt = 1_600_000L, now = 1_300_000L))
        // A try that is overdue runs in a few seconds, not in the past.
        assertEquals(2_005_000L, FaceSchedule.alarmAt(next = 50_000_000L, retryAt = 1_600_000L, now = 2_000_000L))
        // The next change of face comes first when it is sooner.
        assertEquals(1_200_000L, FaceSchedule.alarmAt(next = 1_200_000L, retryAt = 1_600_000L, now = 1_000_000L))
    }
}
