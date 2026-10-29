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
}
