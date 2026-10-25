package com.dordeorz.adimsayar.band

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WearFitSessionTest {

    private val zone = ZoneId.of("Europe/Istanbul")
    private val now = LocalDateTime.of(2026, 10, 25, 14, 20)

    private fun session() = WearFitSession(zone) { now }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun steps(day: Int, hour: Int, steps: Int) =
        bytes(0xab, 0, 17, 0xff, 0x51, 0x20, 26, 10, day, hour, 0, steps shr 8, steps and 0xff, 0, 0, 3, 0, 0, 0, 0)

    private fun total(steps: Int) = bytes(0xab, 0, 14, 0xff, 0x51, 0x08, 0, steps shr 8, steps and 0xff, 0, 0, 1, 0, 0, 0, 0, 0)

    @Test
    fun buildsHistoryRequest() {
        val request = session().request(LocalDateTime.of(2026, 10, 18, 0, 0))
        assertArrayEquals(bytes(0xab, 0, 14, 0xff, 0x51, 0x80, 0, 26, 10, 18, 0, 0, 26, 10, 18, 0, 0), request)
    }

    @Test
    fun runningDayTotalsBecomeHourlySteps() {
        val s = session()
        s.onReport(steps(24, 9, 500))
        s.onReport(steps(24, 10, 1500))
        s.onReport(steps(24, 18, 4000))
        val hours = s.hours()
        assertEquals(500L, hours[LocalDateTime.of(2026, 10, 24, 9, 0)])
        assertEquals(1000L, hours[LocalDateTime.of(2026, 10, 24, 10, 0)])
        assertEquals(2500L, hours[LocalDateTime.of(2026, 10, 24, 18, 0)])
    }

    @Test
    fun fallingValuesAreReadAsPerHourCounts() {
        val s = session()
        s.onReport(steps(24, 9, 800))
        s.onReport(steps(24, 10, 200))
        val hours = s.hours()
        assertEquals(800L, hours[LocalDateTime.of(2026, 10, 24, 9, 0)])
        assertEquals(200L, hours[LocalDateTime.of(2026, 10, 24, 10, 0)])
    }

    @Test
    fun todayTotalTopsUpCurrentHour() {
        val s = session()
        s.onReport(steps(25, 9, 300))
        s.onReport(steps(25, 11, 1300))
        s.onReport(total(2000))
        val hours = s.hours()
        assertEquals(300L, hours[LocalDateTime.of(2026, 10, 25, 9, 0)])
        assertEquals(1000L, hours[LocalDateTime.of(2026, 10, 25, 11, 0)])
        assertEquals(700L, hours[LocalDateTime.of(2026, 10, 25, 14, 0)])
    }

    @Test
    fun todayTotalSpotsPerHourCountsThatRise() {
        val s = session()
        s.onReport(steps(25, 9, 300))
        s.onReport(steps(25, 10, 400))
        s.onReport(total(700))
        val hours = s.hours()
        assertEquals(300L, hours[LocalDateTime.of(2026, 10, 25, 9, 0)])
        assertEquals(400L, hours[LocalDateTime.of(2026, 10, 25, 10, 0)])
        assertNull(hours[LocalDateTime.of(2026, 10, 25, 14, 0)])
    }

    @Test
    fun heartSamplesBecomeMinutes() {
        val s = session()
        s.onReport(bytes(0xab, 0, 9, 0xff, 0x51, 0x11, 26, 10, 25, 8, 30, 72))
        s.onReport(bytes(0xab, 0, 9, 0xff, 0x51, 0x11, 26, 10, 25, 8, 40, 0xff))
        val minutes = s.minutes()
        assertEquals(1, minutes.size)
        assertEquals(72, minutes[0].heartRate)
        assertEquals(LocalDateTime.of(2026, 10, 25, 8, 30).atZone(zone).toEpochSecond(), minutes[0].epochSeconds)
        assertTrue(s.hasData)
    }

    @Test
    fun ignoresOtherReports() {
        val s = session()
        s.onReport(bytes(0xab, 0, 5, 0xff, 0x91, 0x80, 0, 80))
        s.onReport(bytes(0x01, 0x02))
        assertTrue(s.hours().isEmpty())
        assertEquals(1, s.reports)
    }
}
