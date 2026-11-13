package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BirthdayTest {

    private val birth = LocalDate.of(1996, 5, 14)

    @Test
    fun ageGrowsOnTheBirthday() {
        assertEquals(29, Birthday.age(birth, LocalDate.of(2026, 5, 13)))
        assertEquals(30, Birthday.age(birth, LocalDate.of(2026, 5, 14)))
    }

    @Test
    fun leapDayBirthdayIsKeptOnTheLastDayOfFebruary() {
        val leap = LocalDate.of(2000, 2, 29)
        assertTrue(Birthday.isBirthday(leap, LocalDate.of(2027, 2, 28)))
        assertFalse(Birthday.isBirthday(leap, LocalDate.of(2028, 2, 28)))
        assertTrue(Birthday.isBirthday(leap, LocalDate.of(2028, 2, 29)))
    }

    @Test
    fun nextGreetingIsAtNineOnTheNextBirthday() {
        assertEquals(LocalDateTime.of(2027, 5, 14, 9, 0), Birthday.nextNotify(birth, LocalDateTime.of(2026, 11, 13, 18, 0), 2026))
        assertEquals(LocalDateTime.of(2026, 5, 14, 9, 0), Birthday.nextNotify(birth, LocalDateTime.of(2026, 1, 2, 8, 0), 0))
    }

    @Test
    fun missedGreetingOnTheBirthdayGoesOutAtOnce() {
        val now = LocalDateTime.of(2026, 5, 14, 15, 30)
        assertEquals(now, Birthday.nextNotify(birth, now, 2025))
        assertEquals(LocalDateTime.of(2026, 5, 14, 9, 0), Birthday.nextNotify(birth, LocalDateTime.of(2026, 5, 14, 7, 0), 2025))
    }

    @Test
    fun greetingSentThisYearWaitsForNextYear() {
        assertEquals(LocalDateTime.of(2027, 5, 14, 9, 0), Birthday.nextNotify(birth, LocalDateTime.of(2026, 5, 14, 15, 30), 2026))
    }

    @Test
    fun birthDateIsKeptInsideTheAgeRange() {
        val today = LocalDate.of(2026, 11, 13)
        assertEquals(AGE_RANGE.first, Birthday.age(Birthday.clamp(LocalDate.of(2025, 1, 1), today), today))
        assertEquals(AGE_RANGE.last, Birthday.age(Birthday.clamp(LocalDate.of(1900, 1, 1), today), today))
        assertTrue(Birthday.allowed(birth, today))
    }

    @Test
    fun typedAgeIsUsedUntilABirthDateIsSet() {
        val today = LocalDate.of(2026, 11, 13)
        assertEquals(42, AppSettings(age = 42).ageOn(today))
        assertEquals(30, AppSettings(age = 42, birthDate = birth).ageOn(today))
    }
}
