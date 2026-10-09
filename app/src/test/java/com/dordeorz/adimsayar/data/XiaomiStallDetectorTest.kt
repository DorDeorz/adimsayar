package com.dordeorz.adimsayar.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiStallDetectorTest {

    private val minute = 60_000L

    @Test
    fun walkingWhileRecordAdvancesIsFine() {
        val detector = XiaomiStallDetector()
        assertFalse(detector.onSync(1_000L, 0L, 0L))
        assertFalse(detector.onSync(1_100L, 100L, 5 * minute))
        assertFalse(detector.onSync(1_250L, 250L, 12 * minute))
    }

    @Test
    fun walkingWhileRecordStandsStillIsStall() {
        val detector = XiaomiStallDetector()
        assertFalse(detector.onSync(1_000L, 0L, 0L))
        assertFalse(detector.onSync(1_000L, 150L, 5 * minute))
        assertTrue(detector.onSync(1_000L, 200L, 11 * minute))
    }

    @Test
    fun sittingStillIsNotStall() {
        val detector = XiaomiStallDetector()
        assertFalse(detector.onSync(1_000L, 0L, 0L))
        assertFalse(detector.onSync(1_000L, 20L, 30 * minute))
    }

    @Test
    fun recordCatchingUpClearsStall() {
        val detector = XiaomiStallDetector()
        detector.onSync(1_000L, 0L, 0L)
        assertTrue(detector.onSync(1_000L, 200L, 11 * minute))
        assertFalse(detector.onSync(1_180L, 210L, 12 * minute))
    }
}
