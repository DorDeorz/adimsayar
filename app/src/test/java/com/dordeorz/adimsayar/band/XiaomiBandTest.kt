package com.dordeorz.adimsayar.band

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class XiaomiBandTest {

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private val key = ByteArray(16) { it.toByte() }
    private val nonce = XiaomiCrypto.packetNonce(hex("01020304"), 5)

    @Test
    fun packetNonceLayout() {
        assertArrayEquals(hex("010203040000000005000000"), nonce)
    }

    @Test
    fun ccmMatchesReference() {
        assertArrayEquals(hex("cdbc6b13"), XiaomiCrypto.encrypt(key, nonce, ByteArray(0)))
        assertArrayEquals(hex("6e345f6d9921542218"), XiaomiCrypto.encrypt(key, nonce, "hello".toByteArray()))
        val long = ByteArray(40) { it.toByte() }
        val expected = hex("06503102f2906d42265c4596dbefefa6d1017b057c4dc7a3d0294617655ce20a7e21be2f69cf9feed584ffe0")
        assertArrayEquals(expected, XiaomiCrypto.encrypt(key, nonce, long))
        assertArrayEquals(long, XiaomiCrypto.decrypt(key, nonce, expected))
    }

    @Test
    fun ccmRejectsBadTag() {
        val data = hex("6e345f6d9921542219")
        assertNull(XiaomiCrypto.decrypt(key, nonce, data))
    }

    @Test
    fun keyDerivation() {
        val keys = XiaomiCrypto.deriveKeys(hex("00112233445566778899aabbccddeeff"), ByteArray(16) { 1 }, ByteArray(16) { 2 })
        val out = hex("405ba24b582fc07b84e714f691c73adab2c987db5f2250514e5939602c838ffad4470d7e81925f1283da505a2db6c801fe6784b5d3d0ab3511b7806b98dd37b6")
        assertArrayEquals(out.copyOfRange(0, 16), keys.decryptionKey)
        assertArrayEquals(out.copyOfRange(16, 32), keys.encryptionKey)
        assertArrayEquals(out.copyOfRange(32, 36), keys.decryptionNonce)
        assertArrayEquals(out.copyOfRange(36, 40), keys.encryptionNonce)
    }

    @Test
    fun parseKey() {
        assertArrayEquals(hex("00112233445566778899aabbccddeeff"), XiaomiCrypto.parseKey(" 0x00112233445566778899AABBCCDDEEFF "))
        assertNull(XiaomiCrypto.parseKey("0011"))
        assertNull(XiaomiCrypto.parseKey("zz112233445566778899aabbccddeeff"))
    }

    @Test
    fun protoRoundTrip() {
        val bytes = ProtoWriter()
            .uint(1, 8)
            .uint(2, 1)
            .message(10, ProtoWriter().message(5, ProtoWriter().uint(1, 0)).bytes(2, byteArrayOf(1, 2, 3)))
            .toByteArray()
        assertArrayEquals(hex("080810015209" + "2a02080012030102" + "03"), bytes)
        val parsed = ProtoMessage.parse(bytes)!!
        assertEquals(8L, parsed.uint(1))
        assertEquals(1L, parsed.uint(2))
        assertArrayEquals(byteArrayOf(1, 2, 3), parsed.message(10)?.bytes(2))
        assertEquals(0L, parsed.message(10)?.message(5)?.uint(1))
    }

    @Test
    fun protoSignedAndLargeValues() {
        val bytes = ProtoWriter().sint(1, -4).uint(2, 300).toByteArray()
        assertArrayEquals(hex("0807 10ac02".replace(" ", "")), bytes)
        assertNull(ProtoMessage.parse(byteArrayOf(0x0a, 0x05, 1)))
    }

    @Test
    fun channelSendsPlainThenEncrypted() {
        val cipher = XiaomiCipher()
        val written = ArrayList<ByteArray>()
        val channel = XiaomiChannel(cipher, { written += it }, {})
        var done = false
        channel.send(byteArrayOf(9, 9)) { done = it }
        assertArrayEquals(byteArrayOf(0, 0, 2, 2, 9, 9), written.last())
        channel.send(byteArrayOf(7))
        assertEquals(1, written.size)
        channel.onNotification(byteArrayOf(0, 0, 3, 0))
        assertTrue(done)
        assertArrayEquals(byteArrayOf(0, 0, 2, 2, 7), written.last())
        channel.onNotification(byteArrayOf(0, 0, 3, 0))

        cipher.keys = XiaomiKeys(key, key, hex("01020304"), hex("01020304"))
        cipher.sending = true
        channel.reset()
        channel.send("hello".toByteArray())
        val packet = written.last()
        assertArrayEquals(byteArrayOf(0, 0, 2, 1, 1, 0), packet.copyOfRange(0, 6))
        assertArrayEquals(XiaomiCrypto.encrypt(key, XiaomiCrypto.packetNonce(hex("01020304"), 1), "hello".toByteArray()), packet.copyOfRange(6, packet.size))
    }

    @Test
    fun channelReceivesSingleAndChunked() {
        val cipher = XiaomiCipher()
        cipher.keys = XiaomiKeys(key, key, hex("01020304"), hex("01020304"))
        val written = ArrayList<ByteArray>()
        val received = ArrayList<ByteArray>()
        val channel = XiaomiChannel(cipher, { written += it }, { received += it })

        channel.onNotification(byteArrayOf(0, 0, 2, 2, 5, 6))
        assertArrayEquals(byteArrayOf(0, 0, 3, 0), written.last())
        assertArrayEquals(byteArrayOf(5, 6), received.last())

        val plain = ByteArray(30) { it.toByte() }
        val encrypted = XiaomiCrypto.encrypt(key, XiaomiCrypto.packetNonce(hex("01020304"), 0), plain)
        channel.onNotification(byteArrayOf(0, 0, 0, 1, 2, 0))
        assertArrayEquals(byteArrayOf(0, 0, 1, 1), written.last())
        channel.onNotification(byteArrayOf(2, 0) + encrypted.copyOfRange(20, encrypted.size))
        channel.onNotification(byteArrayOf(1, 0) + encrypted.copyOfRange(0, 20))
        assertArrayEquals(byteArrayOf(0, 0, 1, 0), written.last())
        assertArrayEquals(plain, received.last())
    }

    @Test
    fun channelSendsChunkedWhenLarge() {
        val cipher = XiaomiCipher()
        val written = ArrayList<ByteArray>()
        val channel = XiaomiChannel(cipher, { written += it }, {})
        channel.maxWrite = 10
        var done = false
        channel.send(ByteArray(12) { it.toByte() }) { done = it }
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 2, 0), written.last())
        channel.onNotification(byteArrayOf(0, 0, 1, 1))
        assertArrayEquals(byteArrayOf(1, 0, 0, 1, 2, 3, 4, 5, 6, 7), written[1])
        assertArrayEquals(byteArrayOf(2, 0, 8, 9, 10, 11), written[2])
        channel.onNotification(byteArrayOf(0, 0, 1, 0))
        assertTrue(done)
    }

    @Test
    fun fileIdEncoding() {
        val id = XiaomiFileId.from(hex("21F3286504008C"))
        assertEquals(1697182497L, id.epochSeconds)
        assertEquals(4, id.timezone)
        assertEquals(0, id.version)
        assertArrayEquals(hex("21F3286504008C"), id.toBytes())
        assertEquals(false, id.isDailyDetails)
        assertEquals(2, XiaomiFileId.list(hex("21F328650403A021F3286504008C") + ByteArray(7)).size)
    }

    private fun dailyFile(version: Int, header: ByteArray, samples: ByteArray): ByteArray {
        val body = hex("00E1286504") + byteArrayOf(version.toByte(), 0, 0) + header + samples
        val crc = CRC32().apply { update(body) }.value.toInt()
        return body + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc).array()
    }

    @Test
    fun dailyDetailsSteps() {
        val header = byteArrayOf(0x98.toByte(), 0, 0xC0.toByte(), 0, 0, 0)
        val samples = byteArrayOf(10, 0, 1, 70, 0x2C, 0x01, 1, 80, 0, 0, 1, 90)
        val data = dailyFile(4, header, samples)
        val assembler = XiaomiFileAssembler()
        val half = data.size / 2
        assertNull(assembler.add(byteArrayOf(2, 0, 1, 0) + data.copyOfRange(0, half)))
        val file = assembler.add(byteArrayOf(2, 0, 2, 0) + data.copyOfRange(half, data.size))
        assertNotNull(file)
        val id = XiaomiFileId.from(file!!)
        assertEquals(true, id.isDailyDetails)
        val minutes = XiaomiDailyDetails.minutes(id, file)!!
        assertEquals(listOf(10, 300, 0), minutes.map { it.steps })
        assertEquals(listOf(70, 80, 90), minutes.map { it.heartRate })
        assertEquals(listOf(true, true, true), minutes.map { it.worn })
        assertEquals(listOf(id.epochSeconds, id.epochSeconds + 60, id.epochSeconds + 120), minutes.map { it.epochSeconds })
    }

    @Test
    fun sleepSummary() {
        val bed = 1_761_350_400L
        val wake = bed + 7 * 3600
        val id = XiaomiFileId(bed, 12, 2, 8 shl 2)
        val head = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).put(0).put(0).putInt(bed.toInt()).putInt(wake.toInt()).array()
        val data = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN)
            .put(0x21).putShort(400).putShort(20).putShort(250).putShort(70).putShort(80).put(0x10).put(0).array()
        val packet = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0xfffcfafb.toInt()).put(17).putLong(bed).put(1).put(16).put(0).put(13).array()
        val body = id.toBytes() + byteArrayOf(0) + head + packet + data
        val sleep = XiaomiSleepDetails.parse(id, body + ByteArray(4))!!
        assertEquals(true, id.isSleep)
        assertEquals(false, id.isDailyDetails)
        assertEquals(bed, sleep.bedtime)
        assertEquals(wake, sleep.wake)
        assertEquals(listOf(400, 80, 250, 70, 20), listOf(sleep.minutes, sleep.deep, sleep.light, sleep.rem, sleep.awake))
    }

    @Test
    fun sleepOxygenSamples() {
        val bed = 1_761_350_400L
        val wake = bed + 7 * 3600
        val id = XiaomiFileId(bed, 12, 2, 8 shl 2)
        val head = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).put(0x10).put(0).putInt(bed.toInt()).putInt(wake.toInt()).array()
        val oxygen = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(60).putShort(2).putInt((bed + 600).toInt()).put(96).put(94).array()
        val body = id.toBytes() + byteArrayOf(0) + head + oxygen
        val sleep = XiaomiSleepDetails.parse(id, body + ByteArray(4))!!
        assertEquals(listOf(bed + 600, bed + 660), sleep.samples.map { it.epochSeconds })
        assertEquals(listOf(96, 94), sleep.samples.map { it.spo2 })
    }

    @Test
    fun manualSamples() {
        val id = XiaomiFileId(1_761_350_400L, 12, 2, 6 shl 2)
        val body = ByteBuffer.allocate(23).order(ByteOrder.LITTLE_ENDIAN)
            .put(id.toBytes()).put(0)
            .putInt(1_761_350_500).put(0x12).put(97)
            .putInt(1_761_350_560).put(0x13).put(28)
            .array()
        assertEquals(true, id.isManualSamples)
        assertEquals(true, id.isWanted)
        val samples = XiaomiManualSamples.parse(body + ByteArray(4))
        assertEquals(listOf(97, 0), samples.map { it.spo2 })
        assertEquals(listOf(0, 28), samples.map { it.stress })
    }

    @Test
    fun assemblerRejectsBadCrc() {
        val data = dailyFile(4, ByteArray(6), ByteArray(0))
        data[data.size - 1] = (data[data.size - 1] + 1).toByte()
        assertEquals(0, XiaomiFileAssembler().add(byteArrayOf(1, 0, 1, 0) + data)?.size)
    }
}
