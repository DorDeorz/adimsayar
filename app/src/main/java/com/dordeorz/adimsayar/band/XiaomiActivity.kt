package com.dordeorz.adimsayar.band

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

data class XiaomiFileId(val epochSeconds: Long, val timezone: Int, val version: Int, val flags: Int) {

    val isDailyDetails: Boolean get() = flags == 0

    fun toBytes(): ByteArray = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(epochSeconds.toInt())
        .put(timezone.toByte())
        .put(version.toByte())
        .put(flags.toByte())
        .array()

    companion object {
        const val SIZE = 7

        fun from(bytes: ByteArray, offset: Int = 0): XiaomiFileId {
            val buf = ByteBuffer.wrap(bytes, offset, SIZE).order(ByteOrder.LITTLE_ENDIAN)
            return XiaomiFileId(
                epochSeconds = buf.int.toLong() and 0xffffffffL,
                timezone = buf.get().toInt(),
                version = buf.get().toInt() and 0xff,
                flags = buf.get().toInt() and 0xff,
            )
        }

        fun list(bytes: ByteArray): List<XiaomiFileId> {
            if (bytes.size % SIZE != 0) return emptyList()
            return (0 until bytes.size / SIZE).map { from(bytes, it * SIZE) }.filter { it.epochSeconds != 0L || it.version != 0 }
        }
    }
}

class XiaomiFileAssembler {

    private var buffer = ByteArrayOutputStream()

    fun add(chunk: ByteArray): ByteArray? {
        if (chunk.size < 4) return null
        val total = u16(chunk, 0)
        val num = u16(chunk, 2)
        if (num == 1) buffer = ByteArrayOutputStream()
        buffer.write(chunk, 4, chunk.size - 4)
        if (num != total) return null
        val data = buffer.toByteArray()
        buffer = ByteArrayOutputStream()
        if (data.size < 13) return ByteArray(0)
        val crc = CRC32().apply { update(data, 0, data.size - 4) }.value
        val expected = ByteBuffer.wrap(data, data.size - 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        return if (crc == expected) data else ByteArray(0)
    }

    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8)
}

object XiaomiDailyDetails {

    fun minuteSteps(fileId: XiaomiFileId, data: ByteArray): List<Pair<Long, Int>>? {
        val headerSize = when (fileId.version) {
            1, 2 -> 4
            3 -> 5
            4 -> 6
            else -> return null
        }
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buf.limit(buf.limit() - 4)
        if (buf.remaining() < XiaomiFileId.SIZE + 1 + headerSize) return null
        buf.position(XiaomiFileId.SIZE + 1)
        val header = ByteArray(headerSize).also { buf.get(it) }
        val parser = ComplexParser(header, buf)
        val result = ArrayList<Pair<Long, Int>>()
        var time = fileId.epochSeconds
        try {
            while (buf.hasRemaining()) {
                parser.reset()
                var steps = 0
                var extra = 0
                if (parser.next(16)) {
                    if (parser.has(1)) extra = parser.get(1, 1)
                    if (parser.has(2)) steps = parser.get(2, 14)
                }
                parser.next(8)
                parser.next(8)
                parser.next(16)
                parser.next(8)
                parser.next(8)
                parser.next(16)
                if (fileId.version >= 3) {
                    parser.next(8)
                    parser.next(8)
                }
                if (extra == 1) parser.next(8)
                if (fileId.version >= 4) {
                    parser.next(16)
                    parser.next(16)
                }
                result.add(time to steps)
                time += 60
            }
        } catch (e: java.nio.BufferUnderflowException) {
            return result
        }
        return result
    }

    private class ComplexParser(private val header: ByteArray, private val buf: ByteBuffer) {

        private var group = -1
        private var bits = 0
        private var value = 0

        fun reset() {
            group = -1
            bits = 0
            value = 0
        }

        fun next(n: Int): Boolean {
            group++
            if (group >= header.size * 2) {
                consume(n)
                return false
            }
            if (nibble() and 8 == 0) return false
            bits = n
            value = consume(n)
            return true
        }

        fun has(idx: Int): Boolean = nibble() and (1 shl (2 - idx)) != 0

        fun get(idx: Int, n: Int): Int {
            val shift = bits - idx - n
            return (value and (((1 shl n) - 1) shl shift)) ushr shift
        }

        private fun consume(n: Int): Int = when (n) {
            8 -> buf.get().toInt() and 0xff
            16 -> buf.short.toInt() and 0xffff
            else -> throw IllegalArgumentException()
        }

        private fun nibble(): Int {
            val b = header[group / 2].toInt()
            return if (group % 2 == 0) (b and 0xf0) shr 4 else b and 0x0f
        }
    }
}
