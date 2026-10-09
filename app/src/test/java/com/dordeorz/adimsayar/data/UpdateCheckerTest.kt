package com.dordeorz.adimsayar.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(UpdateChecker.isNewer("0.10.0", "0.9.0"))
        assertTrue(UpdateChecker.isNewer("0.6.2", "0.6.1"))
        assertTrue(UpdateChecker.isNewer("1.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewer("0.6.1", "0.6.1"))
        assertFalse(UpdateChecker.isNewer("0.6.0", "0.6.1"))
        assertFalse(UpdateChecker.isNewer("0.6", "0.6.0"))
        assertFalse(UpdateChecker.isNewer("0.6.1-beta", "0.6.1"))
    }
}
