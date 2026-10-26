package com.dordeorz.adimsayar.band

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class XiaomiWatchfaceTest {

    private val phone = XiaomiPhoneInfo(33, "Test", "TR", false)

    private fun face(id: String, name: String, size: Int = 5000): ByteArray {
        val bytes = ByteArray(size) { (it % 251).toByte() }
        bytes[0] = 0x5A
        bytes[1] = 0xA5.toByte()
        id.toByteArray().copyInto(bytes, 0x28)
        bytes[0x28 + id.length] = 0
        name.toByteArray().copyInto(bytes, 0x68)
        bytes[0x68 + name.length] = 0
        return bytes
    }

    @Test
    fun readsIdAndName() {
        val file = XiaomiWatchface.parse(face("366001", "FitWren"))!!
        assertEquals("366001", file.id)
        assertEquals("FitWren", file.name)
        assertNull(XiaomiWatchface.parse(ByteArray(500)))
        assertNull(XiaomiWatchface.parse(face("abc", "x")))
    }

    @Test
    fun partsCarryHeaderAndCrc() {
        val bytes = face("1", "a", 3000)
        val parts = XiaomiWatchface.parts(16, bytes, 1000)
        val payloadSize = 2 + 16 + 4 + bytes.size + 4
        assertEquals((payloadSize + 995) / 996, parts.size)
        val joined = parts.flatMap { p ->
            val b = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(parts.size, b.short.toInt())
            p.copyOfRange(4, p.size).toList()
        }.toByteArray()
        assertEquals(payloadSize, joined.size)
        assertEquals(0, joined[0].toInt())
        assertEquals(16, joined[1].toInt())
        val size = ByteBuffer.wrap(joined, 18, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(bytes.size, size)
        val crc = CRC32().apply { update(joined, 0, joined.size - 4) }.value.toInt()
        assertEquals(crc, ByteBuffer.wrap(joined, joined.size - 4, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        assertEquals(2, ByteBuffer.wrap(parts[1]).order(ByteOrder.LITTLE_ENDIAN).getShort(2).toInt())
    }

    @Test
    fun installUploadsAndSelects() {
        val sent = ArrayList<ByteArray>()
        val parts = ArrayList<ByteArray>()
        val progress = ArrayList<WatchfaceUpload>()
        var faces: List<BandWatchface>? = null
        val s = XiaomiSession(null, "user", phone)
        s.send = { bytes, _ -> sent += bytes }
        s.sendData = { bytes, done ->
            parts += bytes
            done(true)
        }
        s.onUpload = { progress += it }
        s.onWatchfaces = { faces = it }
        s.startPlain()
        s.onCommand(ProtoWriter().uint(1, 1).uint(2, 5).message(3, ProtoWriter().uint(8, 1)).toByteArray())
        sent.clear()

        val file = XiaomiWatchface.parse(face("366001", "FitWren", 10_000))!!
        assertTrue(s.installWatchface(file))
        val install = ProtoMessage.parse(sent.last())!!
        assertEquals(4L, install.uint(1))
        assertEquals(4L, install.uint(2))
        assertEquals("366001", install.message(6)!!.message(6)!!.string(1))
        assertEquals(10_000L, install.message(6)!!.message(6)!!.uint(2))

        s.onCommand(ProtoWriter().uint(1, 4).uint(2, 4).message(6, ProtoWriter().uint(5, 0)).toByteArray())
        val request = ProtoMessage.parse(sent.last())!!
        assertEquals(22L, request.uint(1))
        assertEquals(16L, request.message(24)!!.message(1)!!.uint(1))

        s.onCommand(ProtoWriter().uint(1, 22).uint(2, 0).message(24, ProtoWriter().message(2, ProtoWriter().uint(2, 0))).toByteArray())
        assertEquals(XiaomiWatchface.parts(16, file.bytes, XiaomiWatchface.DEFAULT_CHUNK).size, parts.size)
        assertTrue((progress.last() as WatchfaceUpload.Done).success)
        val select = ProtoMessage.parse(sent.last())!!
        assertEquals(1L, select.uint(2))
        assertEquals("366001", select.message(6)!!.string(2))

        val list = ProtoWriter().message(
            1,
            ProtoWriter()
                .message(1, ProtoWriter().string(1, "1").string(2, "Saat").uint(3, 0).uint(4, 0))
                .message(1, ProtoWriter().string(1, "366001").string(2, "FitWren").uint(3, 1).uint(4, 1)),
        )
        s.onCommand(ProtoWriter().uint(1, 4).uint(2, 0).message(6, list).toByteArray())
        assertEquals(listOf("1", "366001"), faces!!.map { it.id })
        val second = faces!![1]
        assertTrue(second.active && second.canDelete)
    }

    @Test
    fun refusedPartFailsUpload() {
        val progress = ArrayList<WatchfaceUpload>()
        val s = XiaomiSession(null, "user", phone)
        s.send = { _, _ -> }
        s.sendData = { _, done -> done(false) }
        s.onUpload = { progress += it }
        s.startPlain()
        s.onCommand(ProtoWriter().uint(1, 1).uint(2, 5).message(3, ProtoWriter().uint(8, 1)).toByteArray())
        s.installWatchface(XiaomiWatchface.parse(face("5", "x"))!!)
        s.onCommand(ProtoWriter().uint(1, 4).uint(2, 4).message(6, ProtoWriter().uint(5, 0)).toByteArray())
        s.onCommand(ProtoWriter().uint(1, 22).uint(2, 0).message(24, ProtoWriter().message(2, ProtoWriter().uint(2, 0))).toByteArray())
        assertEquals(false, (progress.last() as WatchfaceUpload.Done).success)
    }
}
