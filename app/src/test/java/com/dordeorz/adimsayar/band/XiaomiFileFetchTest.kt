package com.dordeorz.adimsayar.band

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * The band can answer a file request with a different (earlier) day file, and it lists today's
 * file under a new id at every sync. Hours must be stored under the id inside the data, so the
 * same day is written over itself instead of being added again; the ack still names the request.
 */
class XiaomiFileFetchTest {

    private val phone = XiaomiPhoneInfo(33, "Test", "TR", false)
    private val dayStart = 1_793_653_200L

    private fun session(sent: MutableList<ByteArray>): XiaomiSession {
        val s = XiaomiSession(null, "user", phone)
        s.send = { bytes, _ -> sent += bytes }
        s.startPlain()
        s.onCommand(ProtoWriter().uint(1, 1).uint(2, 5).message(3, ProtoWriter().uint(8, 1)).toByteArray())
        assertTrue(s.authenticated)
        return s
    }

    private fun list(subtype: Int, ids: List<XiaomiFileId>): ByteArray =
        ProtoWriter().uint(1, 8).uint(2, subtype.toLong())
            .message(10, ProtoWriter().bytes(2, ids.fold(ByteArray(0)) { acc, id -> acc + id.toBytes() }))
            .toByteArray()

    private fun dailyChunk(inner: XiaomiFileId): ByteArray {
        val header = byteArrayOf(0x98.toByte(), 0, 0xC0.toByte(), 0, 0, 0)
        val samples = byteArrayOf(10, 0, 1, 70, 0x2C, 0x01, 1, 80)
        val body = inner.toBytes() + byteArrayOf(0) + header + samples
        val crc = CRC32().apply { update(body) }.value.toInt()
        val data = body + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc).array()
        return byteArrayOf(1, 0, 1, 0) + data
    }

    private fun acks(sent: List<ByteArray>): List<XiaomiFileId> =
        sent.mapNotNull { ProtoMessage.parse(it) }
            .filter { it.uint(1) == 8L && it.uint(2) == 5L }
            .mapNotNull { it.message(10)?.bytes(3)?.let { b -> XiaomiFileId.from(b) } }

    @Test
    fun sameDayFileUnderNewIdsKeepsItsOwnId() = runBlocking {
        val sent = ArrayList<ByteArray>()
        val s = session(sent)
        val day = XiaomiFileId(dayStart, 12, 4, 0)
        val past = XiaomiFileId(dayStart + 4_380, 12, 4, 0)
        val today = XiaomiFileId(dayStart + 8_400, 12, 4, 0)
        s.onCommand(list(1, listOf(today)))
        s.onCommand(list(2, listOf(past)))

        val incoming = ArrayDeque(listOf(dailyChunk(day), dailyChunk(day)))
        val stored = ArrayList<BandFile>()
        val result = s.loop(
            pump = { incoming.removeFirstOrNull()?.let { s.onActivity(it); true } ?: false },
            store = { stored += it; true },
        )

        assertEquals(BandResult.Ok, result)
        assertEquals(listOf(past, today), stored.map { it.id })
        assertEquals(listOf(day, day), stored.map { it.source })
        assertEquals(listOf(dayStart, dayStart), stored.map { it.minutes.first().epochSeconds })
        assertEquals(listOf(past), acks(sent))
    }

    @Test
    fun ownFileKeepsRequestedId() = runBlocking {
        val sent = ArrayList<ByteArray>()
        val s = session(sent)
        val day = XiaomiFileId(dayStart, 12, 4, 0)
        s.onCommand(list(1, emptyList()))
        s.onCommand(list(2, listOf(day)))

        val incoming = ArrayDeque(listOf(dailyChunk(day)))
        val stored = ArrayList<BandFile>()
        s.loop(
            pump = { incoming.removeFirstOrNull()?.let { s.onActivity(it); true } ?: false },
            store = { stored += it; true },
        )

        assertEquals(listOf(day), stored.map { it.source })
        assertEquals(listOf(day), acks(sent))
    }
}
