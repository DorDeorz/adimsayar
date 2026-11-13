package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WalksTest {

    @Test
    fun walkWithinOneDayIsTheDifference() {
        assertEquals(1_200L, Walks.stepsDuring(startSteps = 3_000L, todaySteps = 4_200L, firstDayTotal = null))
    }

    @Test
    fun countThatWentBackGivesNoSteps() {
        assertEquals(0L, Walks.stepsDuring(startSteps = 3_000L, todaySteps = 2_900L, firstDayTotal = null))
    }

    @Test
    fun walkOverMidnightAddsBothDays() {
        // Started at 9,000 steps, the day ended at 9,400, and 700 steps came after midnight.
        assertEquals(1_100L, Walks.stepsDuring(startSteps = 9_000L, todaySteps = 700L, firstDayTotal = 9_400L))
        // More steps after midnight than the first day had at the start: still both parts.
        assertEquals(650L, Walks.stepsDuring(startSteps = 200L, todaySteps = 500L, firstDayTotal = 350L))
    }

    @Test
    fun walkOverMidnightWithoutTheFirstDayKeepsTodaysSteps() {
        assertEquals(700L, Walks.stepsDuring(startSteps = 9_000L, todaySteps = 700L, firstDayTotal = 9_000L))
    }
}
