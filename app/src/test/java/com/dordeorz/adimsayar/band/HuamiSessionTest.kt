package com.dordeorz.adimsayar.band

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class HuamiSessionTest {

    private val key = ByteArray(16) { (it + 1).toByte() }
    private val zone = ZoneId.of("Europe/Istanbul")
    private val since = LocalDateTime.of(2026, 10, 25, 9, 0).atZone(zone).toEpochSecond()

    private fun session(crypt: Int = 0x80, sampleSize: Int = 4, now: Long = since + 86_400) =
        HuamiSession(key, crypt, sampleSize, since, zone) { now }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun authenticate(s: HuamiSession, auth: MutableList<ByteArray>) {
        s.start()
        val challenge = ByteArray(16) { (it * 7).toByte() }
        s.onAuth(bytes(0x10, 0x82, 0x01) + challenge)
        val expected = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }.doFinal(challenge)
        assertArrayEquals(bytes(0x83, 0x08) + expected, auth.last())
        s.onAuth(bytes(0x10, 0x83, 0x01))
    }

    @Test
    fun authenticatesAndFetchesMinutes() {
        val auth = ArrayList<ByteArray>()
        val fetch = ArrayList<ByteArray>()
        val s = session()
        s.writeAuth = { auth += it }
        s.writeFetch = { fetch += it }
        authenticate(s, auth)
        assertArrayEquals(bytes(0x82, 0x08, 0x02, 0x01, 0x00), auth.first())
        assertTrue(s.authenticated)
        // 2026-10-25 09:00, UTC+3 = 12 quarter hours
        assertArrayEquals(bytes(0x01, 0x01, 0xea, 0x07, 10, 25, 9, 0, 0, 12), fetch.last())

        s.onFetch(bytes(0x10, 0x01, 0x01, 3, 0, 0, 0, 0xea, 0x07, 10, 25, 9, 0, 0, 12))
        assertArrayEquals(bytes(0x02), fetch.last())
        s.onData(bytes(0, 1, 50, 12, 80, 1, 50, 0, 0xff))
        s.onData(bytes(1, 1, 50, 7, 0))
        s.onFetch(bytes(0x10, 0x02, 0x01))

        assertEquals(3, s.minutes.size)
        assertEquals(since, s.minutes[0].epochSeconds)
        assertEquals(12, s.minutes[0].steps)
        assertEquals(80, s.minutes[0].heartRate)
        assertEquals(0, s.minutes[1].heartRate)
        assertEquals(since + 120, s.minutes[2].epochSeconds)
        // next fetch starts right after the last minute
        assertArrayEquals(bytes(0x01, 0x01, 0xea, 0x07, 10, 25, 9, 3, 0, 12), fetch.last())
        s.onFetch(bytes(0x10, 0x01, 0x01, 0, 0, 0, 0, 0xea, 0x07, 10, 25, 9, 3, 0, 12))
        assertEquals(BandResult.Ok, s.result)
    }

    @Test
    fun extendedSamplesUseEightBytes() {
        val s = session(sampleSize = 8, now = since + 60)
        val auth = ArrayList<ByteArray>()
        s.writeAuth = { auth += it }
        authenticate(s, auth)
        s.onFetch(bytes(0x10, 0x01, 0x01, 1, 0, 0, 0, 0xea, 0x07, 10, 25, 6, 0, 0, 0))
        s.onData(bytes(0, 1, 50, 30, 90, 0, 0, 0, 0))
        s.onFetch(bytes(0x10, 0x02, 0x01))
        assertEquals(1, s.minutes.size)
        assertEquals(LocalDateTime.of(2026, 10, 25, 6, 0).toEpochSecond(ZoneOffset.UTC), s.minutes[0].epochSeconds)
        assertEquals(30, s.minutes[0].steps)
        assertEquals(BandResult.Ok, s.result)
    }

    @Test
    fun outOfOrderPacketsDropTheBatch() {
        val s = session()
        s.writeAuth = {}
        s.onAuth(bytes(0x10, 0x83, 0x01))
        s.onFetch(bytes(0x10, 0x01, 0x01, 2, 0, 0, 0, 0xea, 0x07, 10, 25, 9, 0, 0, 12))
        s.onData(bytes(1, 1, 50, 12, 80))
        s.onFetch(bytes(0x10, 0x02, 0x01))
        assertTrue(s.minutes.isEmpty())
        assertEquals(BandResult.Ok, s.result)
    }

    @Test
    fun oldBandSendsKeyWhenChallengeRefused() {
        val auth = ArrayList<ByteArray>()
        val s = session(crypt = 0)
        s.writeAuth = { auth += it }
        s.start()
        assertArrayEquals(bytes(0x02, 0x08), auth.last())
        s.onAuth(bytes(0x10, 0x02, 0x04))
        assertArrayEquals(bytes(0x01, 0x08) + key, auth.last())
        assertNull(s.result)
        s.onAuth(bytes(0x10, 0x01, 0x01))
        assertArrayEquals(bytes(0x02, 0x08), auth.last())
        s.onAuth(bytes(0x10, 0x02, 0x04))
        assertEquals(BandResult.AuthFailed, s.result)
    }

    @Test
    fun huamiModelsAreRecognised() {
        assertEquals(BandFamily.Huami, BandModels.find("Mi Smart Band 6")?.family)
        assertEquals("Mi Band 3", BandModels.find("Mi Band 3")?.name)
        assertEquals(0, BandModels.find("MI Band 2")?.huamiCrypt)
        assertEquals("Amazfit GTS 2 mini", BandModels.find("Amazfit GTS2 mini")?.name)
        assertEquals(8, BandModels.find("Amazfit GTR 2")?.huamiSampleSize)
        assertEquals("Amazfit Bip S", BandModels.find("Amazfit Bip S")?.name)
        assertEquals(BandFamily.Xiaomi, BandModels.find("Xiaomi Smart Band 8 ABCD")?.family)
    }
}
