package com.dordeorz.adimsayar.band

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

data class XiaomiFileId(val epochSeconds: Long, val timezone: Int, val version: Int, val flags: Int) {

    val isDailyDetails: Boolean get() = flags == 0

    val isSleep: Boolean get() = flags shr 7 == 0 && (flags shr 2) and 0x1f == SUBTYPE_SLEEP && version <= 4

    val isManualSamples: Boolean get() = flags == SUBTYPE_MANUAL shl 2

    val isWanted: Boolean get() = isDailyDetails || isSleep || isManualSamples

    fun toBytes(): ByteArray = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(epochSeconds.toInt())
        .put(timezone.toByte())
        .put(version.toByte())
        .put(flags.toByte())
        .array()

    companion object {
        const val SIZE = 7
        private const val SUBTYPE_SLEEP = 8
        private const val SUBTYPE_MANUAL = 6

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

class BandMinute(val epochSeconds: Long, val steps: Int, val heartRate: Int, val spo2: Int = 0, val stress: Int = 0) {
    val worn: Boolean get() = steps > 0 || heartRate in 30..220
}

object XiaomiDailyDetails {

    fun minutes(fileId: XiaomiFileId, data: ByteArray): List<BandMinute>? {
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
        val result = ArrayList<BandMinute>()
        var time = fileId.epochSeconds
        try {
            while (buf.hasRemaining()) {
                parser.reset()
                var steps = 0
                var extra = 0
                var heartRate = 0
                var spo2 = 0
                var stress = 0
                if (parser.next(16)) {
                    if (parser.has(1)) extra = parser.get(1, 1)
                    if (parser.has(2)) steps = parser.get(2, 14)
                }
                parser.next(8)
                parser.next(8)
                parser.next(16)
                if (parser.next(8) && parser.has(0)) heartRate = parser.get(0, 8)
                parser.next(8)
                parser.next(16)
                if (fileId.version >= 3) {
                    if (parser.next(8) && parser.has(0)) spo2 = parser.get(0, 8)
                    if (parser.next(8) && parser.has(0)) stress = parser.get(0, 8).takeIf { it != 255 } ?: 0
                }
                if (extra == 1) parser.next(8)
                if (fileId.version >= 4) {
                    parser.next(16)
                    parser.next(16)
                }
                result.add(BandMinute(time, steps, heartRate, spo2, stress))
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

class BandSleepRecord(
    val bedtime: Long,
    val wake: Long,
    val minutes: Int,
    val deep: Int,
    val light: Int,
    val rem: Int,
    val awake: Int,
    val samples: List<BandMinute> = emptyList(),
)

object XiaomiManualSamples {

    private const val KIND_HEART = 1
    private const val KIND_OXYGEN = 2
    private const val KIND_STRESS = 3

    fun parse(fileId: XiaomiFileId, data: ByteArray): List<BandMinute> {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val start = XiaomiFileId.SIZE + 1 + if (fileId.version == 1) 1 else 0
        if (data.size < start + 4) return emptyList()
        buf.limit(buf.limit() - 4)
        buf.position(start)
        val result = ArrayList<BandMinute>()
        try {
            while (buf.remaining() >= 5) {
                val time = buf.int.toLong() and 0xffffffffL
                val type = buf.get().toInt() and 0xff
                val size = (type shr 4).takeIf { it > 0 } ?: 1
                if (size > buf.remaining()) break
                val value = when (size) {
                    1 -> buf.get().toInt() and 0xff
                    2 -> buf.short.toInt() and 0xffff
                    4 -> buf.int
                    else -> break
                }
                when (type and 0x0f) {
                    KIND_HEART -> result.add(BandMinute(time, 0, value))
                    KIND_OXYGEN -> result.add(BandMinute(time, 0, 0, spo2 = value))
                    KIND_STRESS -> result.add(BandMinute(time, 0, 0, stress = value))
                }
            }
        } catch (e: java.nio.BufferUnderflowException) {
            return result
        }
        return result
    }
}

object XiaomiSleepDetails {

    private const val STAGE_MAGIC = 0xfffcfafb.toInt()
    private const val TYPE_SUMMARY = 16
    private val EMPTY_TYPES = setOf(0x2, 0x3, 0x9, 0xc, 0xd, 0xe, 0xf)

    fun parse(fileId: XiaomiFileId, data: ByteArray): BandSleepRecord? {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buf.limit(buf.limit() - 4)
        try {
            buf.position(XiaomiFileId.SIZE + 1)
            val header = buf.get().toInt()
            buf.get()
            val bed = buf.int.toLong() and 0xffffffffL
            val wake = buf.int.toLong() and 0xffffffffL
            if (wake <= bed) return null
            var shift = 0
            if (fileId.version >= 4) {
                shift = 1
                buf.get()
            }
            val samples = ArrayList<BandMinute>()
            if (header and (1 shl (5 - shift)) != 0) {
                readSamples(buf, fileId.version, bed).forEach { (time, value) -> samples.add(BandMinute(time, 0, value)) }
            }
            if (header and (1 shl (4 - shift)) != 0) {
                readSamples(buf, fileId.version, bed).forEach { (time, value) -> samples.add(BandMinute(time, 0, 0, spo2 = value)) }
            }
            if (fileId.version >= 3 && header and (1 shl (3 - shift)) != 0) skipSamples(buf, fileId.version, 4)
            var summary: BandSleepRecord? = null
            while (buf.remaining() >= 17) {
                if (!seekStage(buf)) break
                buf.get()
                buf.long
                buf.get()
                val type = buf.get().toInt() and 0xff
                val length = ((buf.get().toInt() and 0xff) shl 8) or (buf.get().toInt() and 0xff)
                if (type in EMPTY_TYPES) continue
                if (length > buf.remaining()) break
                val block = ByteArray(length).also { buf.get(it) }
                if (type == TYPE_SUMMARY && length >= 11) {
                    val b = ByteBuffer.wrap(block).order(ByteOrder.BIG_ENDIAN)
                    b.get()
                    val total = b.short.toInt() and 0xffff
                    val awake = b.short.toInt() and 0xffff
                    val light = b.short.toInt() and 0xffff
                    val rem = b.short.toInt() and 0xffff
                    val deep = b.short.toInt() and 0xffff
                    summary = BandSleepRecord(bed, wake, total, deep, light, rem, awake, samples)
                }
            }
            return summary ?: BandSleepRecord(bed, wake, ((wake - bed) / 60).toInt(), 0, 0, 0, 0, samples)
        } catch (e: java.nio.BufferUnderflowException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
    }

    private fun readSamples(buf: ByteBuffer, version: Int, bed: Long): List<Pair<Long, Int>> {
        val unit = (buf.short.toInt() and 0xffff).takeIf { it > 0 } ?: 60
        val count = buf.short.toInt() and 0xffff
        if (count == 0) return emptyList()
        val first = if (version >= 2) buf.int.toLong() and 0xffffffffL else bed
        return (0 until count).map { index -> first + unit.toLong() * index to (buf.get().toInt() and 0xff) }
    }

    private fun skipSamples(buf: ByteBuffer, version: Int, size: Int) {
        buf.short
        val count = buf.short.toInt() and 0xffff
        if (count > 0) {
            if (version >= 2) buf.int
            buf.position(buf.position() + count * size)
        }
    }

    private fun seekStage(buf: ByteBuffer): Boolean {
        while (buf.remaining() >= 17) {
            if (buf.int == STAGE_MAGIC) return true
            buf.position(buf.position() - 3)
        }
        return false
    }
}
