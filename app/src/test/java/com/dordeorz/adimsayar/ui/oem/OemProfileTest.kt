package com.dordeorz.adimsayar.ui.oem

import org.junit.Assert.assertEquals
import org.junit.Test

class OemProfileTest {

    @Test
    fun redmiIsXiaomi() {
        assertEquals(OemProfile.Xiaomi, OemProfile.detect("Xiaomi", "Redmi", "OS1.0.5.0.TMGEUXM", ""))
    }

    @Test
    fun samsungIsSamsung() {
        assertEquals(OemProfile.Samsung, OemProfile.detect("samsung", "samsung", "", ""))
    }

    @Test
    fun hyperOsMarkerFallsBackToXiaomi() {
        assertEquals(OemProfile.Xiaomi, OemProfile.detect("", "", "hyperos", ""))
    }

    @Test
    fun pixelIsUnknown() {
        assertEquals(OemProfile.Unknown, OemProfile.detect("Google", "google", "", ""))
    }
}
