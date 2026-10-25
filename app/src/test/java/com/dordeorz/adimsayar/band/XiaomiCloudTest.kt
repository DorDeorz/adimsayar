package com.dordeorz.adimsayar.band

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiCloudTest {

    private val ssecurity = "AAECAwQFBgcICQoLDA0ODw=="
    private val nonce = "AQIDBAUGBwgBsFUV"
    private val signed = "5ep+rMHy/Bph+UDBR3Cb6aW2JDdKFCrVRtJBDO5BLUQ="

    @Test
    fun rc4MatchesReferenceAfterDrop() {
        val out = XiaomiCloudCrypto.rc4(byteArrayOf(1, 2, 3, 4, 5), ByteArray(7))
        assertEquals("30abbcc7c20b01", out.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun nonceAndSignedNonce() {
        assertEquals(nonce, XiaomiCloudCrypto.nonce(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), 1_700_000_000_000L))
        assertEquals(signed, XiaomiCloudCrypto.signedNonce(ssecurity, nonce))
    }

    @Test
    fun requestParamsMatchExtractor() {
        val params = XiaomiCloudCrypto.requestParams("/v2/homeroom/gethome", signed, nonce, ssecurity, """{"a":1}""")
        assertEquals(listOf("data", "rc4_hash__", "signature", "ssecurity", "_nonce"), params.keys.toList())
        assertEquals("J+nT+LPZJw==", params["data"])
        assertEquals("OqTiu8yFFaNbS0fytnHOU8xcvZnqf2S9K/y/JQ==", params["rc4_hash__"])
        assertEquals("bU7P0GfzdeQDzk4Tm57REscdJ7c=", params["signature"])
        assertEquals("""{"a":1}""", XiaomiCloudCrypto.decrypt(signed, params.getValue("data")))
    }

    @Test
    fun wearableModels() {
        assertTrue(XiaomiCloud.isWearable("miwear.watch.m66gl"))
        assertFalse(XiaomiCloud.isWearable("xiaomi.repeater.mc04"))
    }
}
