package com.dordeorz.adimsayar.sensor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeDetectorTest {

    @Test
    fun detectorStepsWithoutCounterAdvanceIsFrozen() {
        val detector = FreezeDetector()
        detector.reset(0L)
        detector.onCounter(1_000L, 0L)
        for (i in 1..60) detector.onDetectorStep(60_000L + i * 1_000L)
        assertTrue(detector.isFrozen(121_000L))
    }

    @Test
    fun advancingCounterIsNotFrozen() {
        val detector = FreezeDetector()
        detector.reset(0L)
        for (i in 1..120) {
            val now = i * 1_000L
            detector.onDetectorStep(now)
            detector.onCounter(1_000L + i, now)
        }
        assertFalse(detector.isFrozen(120_000L))
    }

    @Test
    fun fewDetectorStepsIsNotFrozen() {
        val detector = FreezeDetector()
        detector.reset(0L)
        for (i in 1..20) detector.onDetectorStep(100_000L + i * 1_000L)
        assertFalse(detector.isFrozen(130_000L))
    }

    @Test
    fun shortWindowIsNotFrozen() {
        val detector = FreezeDetector()
        detector.reset(0L)
        for (i in 1..60) detector.onDetectorStep(i * 1_000L)
        assertFalse(detector.isFrozen(60_000L))
    }
}
