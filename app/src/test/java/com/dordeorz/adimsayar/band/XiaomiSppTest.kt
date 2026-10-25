package com.dordeorz.adimsayar.band

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiSppTest {

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private val key = ByteArray(16) { it.toByte() }
    private val keys = XiaomiKeys(key, key, hex("01020304"), hex("01020304"))

    /** Gadgetbridge's bit-by-bit version, kept here to check ours against it. */
    private fun gadgetbridgeCrc(payload: ByteArray): Int {
        var crc = 0
        for (b in payload) {
            for (j in 0 until 8) {
                crc = crc shl 1
                if (((crc shr 16) and 1) xor ((b.toInt() shr j) and 1) == 1) crc = crc xor 0x8005
            }
        }
        return Integer.reverse(crc) ushr 16
    }

    @Test
    fun crcMatchesArcAndGadgetbridge() {
        assertEquals(0xbb3d, XiaomiSppProtocol.crc16("123456789".toByteArray()))
        val random = java.util.Random(7)
        repeat(20) {
            val data = ByteArray(random.nextInt(200)).also(random::nextBytes)
            assertEquals(gadgetbridgeCrc(data), XiaomiSppProtocol.crc16(data))
        }
    }

    @Test
    fun versionOneHandshakeAndCommands() {
        val cipher = XiaomiCipher()
        val written = ArrayList<ByteArray>()
        val commands = ArrayList<ByteArray>()
        var ready = 0
        val protocol = XiaomiSppProtocol(cipher, { written += it }, { commands += it }, {}, { ready++ })

        protocol.requestVersion()
        assertArrayEquals(hex("badcfe00c00300000000ef"), written.last())

        val reply = XiaomiSppProtocol.encodeV1(0, 0x80, 2, 0, 0, byteArrayOf(1, 0))
        protocol.onBytes(reply.copyOfRange(0, 5))
        assertFalse(protocol.ready)
        protocol.onBytes(reply.copyOfRange(5, reply.size))
        assertTrue(protocol.ready)
        assertEquals(1, protocol.version)
        assertEquals(1, ready)

        protocol.send(byteArrayOf(8, 1), auth = true)
        assertArrayEquals(hex("badcfe02800500020002" + "0801" + "ef"), written.last())

        cipher.keys = keys
        protocol.send(byteArrayOf(8, 2), auth = false)
        val sealed = XiaomiCrypto.encrypt(key, XiaomiCrypto.packetNonce(hex("01020304"), 1), byteArrayOf(8, 2))
        assertArrayEquals(XiaomiSppProtocol.encodeV1(2, 0x80, 2, 1, 1, byteArrayOf(1, 0) + sealed), written.last())

        val incoming = XiaomiCrypto.encrypt(key, XiaomiCrypto.packetNonce(hex("01020304"), 0), byteArrayOf(8, 3))
        protocol.onBytes(byteArrayOf(0x55) + XiaomiSppProtocol.encodeV1(1, 0x80, 2, 0, 1, incoming))
        assertArrayEquals(byteArrayOf(8, 3), commands.last())
    }

    @Test
    fun versionTwoSessionDataAndAck() {
        val cipher = XiaomiCipher()
        val written = ArrayList<ByteArray>()
        val commands = ArrayList<ByteArray>()
        val activity = ArrayList<ByteArray>()
        val protocol = XiaomiSppProtocol(cipher, { written += it }, { commands += it }, { activity += it }, {})

        protocol.requestVersion()
        protocol.onBytes(XiaomiSppProtocol.encodeV1(0, 0x80, 2, 0, 0, byteArrayOf(2)))
        assertEquals(2, protocol.version)
        assertFalse(protocol.ready)
        assertEquals(0xa5.toByte(), written.last()[0])
        assertEquals(2, written.last()[2].toInt())

        protocol.onBytes(XiaomiSppProtocol.encodeV2(2, 0, byteArrayOf(2)))
        assertTrue(protocol.ready)

        protocol.send(byteArrayOf(8, 1), auth = true)
        assertArrayEquals(XiaomiSppProtocol.encodeV2(3, 0, byteArrayOf(1, 1, 8, 1)), written.last())
        cipher.keys = keys
        protocol.send(byteArrayOf(8, 2), auth = false)
        assertArrayEquals(XiaomiSppProtocol.encodeV2(3, 1, byteArrayOf(1, 2) + XiaomiCrypto.ctr(key, byteArrayOf(8, 2))), written.last())

        val sealed = XiaomiCrypto.ctr(key, byteArrayOf(9, 9, 9))
        protocol.onBytes(XiaomiSppProtocol.encodeV2(3, 7, byteArrayOf(5, 2) + sealed))
        assertArrayEquals(byteArrayOf(9, 9, 9), activity.last())
        assertArrayEquals(XiaomiSppProtocol.encodeV2(1, 7, ByteArray(0)), written.last())

        val bad = XiaomiSppProtocol.encodeV2(3, 8, byteArrayOf(1, 1, 4))
        bad[6] = (bad[6] + 1).toByte()
        protocol.onBytes(bad)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun ctrRoundTrips() {
        val data = ByteArray(40) { it.toByte() }
        assertArrayEquals(data, XiaomiCrypto.ctr(key, XiaomiCrypto.ctr(key, data)))
    }

    @Test
    fun plainChannelAlwaysChunks() {
        val written = ArrayList<ByteArray>()
        val received = ArrayList<ByteArray>()
        val channel = XiaomiChannel(XiaomiCipher(), { written += it }, { received += it }, encrypted = false)
        channel.send(byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 1, 0), written.last())
        channel.onNotification(byteArrayOf(0, 0, 1, 1))
        assertArrayEquals(byteArrayOf(1, 0, 1, 2, 3), written.last())

        channel.onNotification(byteArrayOf(0, 0, 0, 0, 1, 0))
        channel.onNotification(byteArrayOf(1, 0, 7, 8))
        assertArrayEquals(byteArrayOf(7, 8), received.last())
    }

    @Test
    fun plainSessionAuthenticatesWithUserId() {
        val sent = ArrayList<Pair<ByteArray, Boolean>>()
        val session = XiaomiSession(null, "abc", XiaomiPhoneInfo(33, "Phone", "TR", false))
        session.send = { bytes, auth -> sent += bytes to auth }
        session.startPlain()
        val first = ProtoMessage.parse(sent.last().first)!!
        assertEquals(1L, first.uint(1))
        assertEquals(5L, first.uint(2))
        assertEquals("abc", String(first.message(3)!!.bytes(7)!!))
        assertTrue(sent.last().second)

        session.onCommand(ProtoWriter().uint(1, 1).uint(2, 5).message(3, ProtoWriter().uint(8, 0)).toByteArray())
        assertEquals("0000000000", String(ProtoMessage.parse(sent.last().first)!!.message(3)!!.bytes(7)!!))
        assertFalse(session.authenticated)

        session.onCommand(ProtoWriter().uint(1, 1).uint(2, 5).message(3, ProtoWriter().uint(8, 1)).toByteArray())
        assertTrue(session.authenticated)
        assertEquals(8L, ProtoMessage.parse(sent.last().first)!!.uint(1))
        assertNull(session.result)
    }

    @Test
    fun modelsMatchAdvertisedNames() {
        assertEquals("Xiaomi Smart Band 8", BandModels.find("Xiaomi Smart Band 8 3A4F")?.name)
        assertEquals("Xiaomi Smart Band 8", BandModels.find("Xiaomi Smart Band 8")?.name)
        assertEquals("Xiaomi Smart Band 8 Pro", BandModels.find("Xiaomi Smart Band 8 Pro 1B2C")?.name)
        assertEquals("Xiaomi Smart Band 8 Active", BandModels.find("Xiaomi Band 8 Active ABCD")?.name)
        assertEquals(BandLink.Classic, BandModels.find("Xiaomi Smart Band 9 0A1B")?.link)
        assertEquals("Redmi Watch 2 Lite", BandModels.find("Redmi Watch 2 Lite A1B2")?.name)
        assertEquals("Redmi Watch 3 Active", BandModels.find("Redmi Watch 3 Active A1B2")?.name)
        assertEquals("Mi Watch Lite", BandModels.find("Mi Watch Lite_A1B2")?.name)
        assertEquals(BandShape.RoundWatch, BandModels.find("Xiaomi Watch S3 eSIM 12AB")?.shape)
        assertEquals(BandFamily.WearFit, BandModels.find("HK10 Pro Max")?.family)
        assertEquals("HK10 Pro+", BandModels.displayName("HK10 Pro+"))
        assertEquals(BandShape.SquareWatch, BandModels.shapeOf("Some Watch"))
    }
}
