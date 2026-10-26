package com.dordeorz.adimsayar.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {

    @Test
    fun overnightRange() {
        val from = 23 * 60
        val to = 7 * 60
        assertTrue(inQuietHours(23 * 60, from, to))
        assertTrue(inQuietHours(2 * 60, from, to))
        assertFalse(inQuietHours(7 * 60, from, to))
        assertFalse(inQuietHours(12 * 60, from, to))
    }

    @Test
    fun sameDayRangeAndEmptyRange() {
        assertTrue(inQuietHours(13 * 60, 12 * 60, 14 * 60))
        assertFalse(inQuietHours(14 * 60, 12 * 60, 14 * 60))
        assertFalse(inQuietHours(8 * 60, 8 * 60, 8 * 60))
    }

    @Test
    fun phoneNumbers() {
        assertTrue(looksLikeNumber("+90 555 111 22 33"))
        assertTrue(looksLikeNumber("(0212) 555-1122"))
        assertFalse(looksLikeNumber("Gelen arama"))
        assertFalse(looksLikeNumber("12"))
    }
}
