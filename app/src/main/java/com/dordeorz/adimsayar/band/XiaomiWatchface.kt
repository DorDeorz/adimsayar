package com.dordeorz.adimsayar.band

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32

/** A watch face on the band. Built-in faces have [canDelete] false. */
data class BandWatchface(val id: String, val name: String, val active: Boolean, val canDelete: Boolean)

/** A watch face file (.bin) read from the phone. */
class WatchfaceFile(val id: String, val name: String, val bytes: ByteArray)

/**
 * Watch faces (command type 4) and the data upload (type 22) that carries the face file.
 * Ported from Gadgetbridge's XiaomiWatchfaceService, XiaomiDataUploadService and XiaomiFWHelper.
 */
object XiaomiWatchface {

    const val TYPE = 4
    const val LIST = 0
    const val SET = 1
    const val DELETE = 2
    const val INSTALL = 4

    const val UPLOAD_TYPE = 22
    const val UPLOAD_START = 0
    const val UPLOAD_WATCHFACE = 16

    /** Mi Band 8 does not say its chunk size; this is what it takes. */
    const val DEFAULT_CHUNK = 2048

    private const val MAX_FILE = 16 * 1024 * 1024
    private const val ID_OFFSET = 0x28
    private const val NAME_OFFSET = 0x68

    /** Reads the face id and name from a .bin file; null when it is not a Xiaomi watch face. */
    fun parse(bytes: ByteArray): WatchfaceFile? {
        if (bytes.size < NAME_OFFSET + 4 || bytes.size > MAX_FILE) return null
        if (bytes[0] != 0x5A.toByte() || bytes[1] != 0xA5.toByte()) return null
        val id = text(bytes, ID_OFFSET) ?: return null
        if (id.isEmpty() || !id.all(Char::isDigit)) return null
        val localized = (0 until 4).all { bytes[NAME_OFFSET + it] == 0xff.toByte() }
        val name = if (localized) "" else text(bytes, NAME_OFFSET).orEmpty()
        return WatchfaceFile(id, name.ifEmpty { id }, bytes)
    }

    fun list(cmd: ProtoMessage): List<BandWatchface> =
        cmd.message(6)?.message(1)?.messages(1).orEmpty().mapNotNull { f ->
            val id = f.string(1) ?: return@mapNotNull null
            BandWatchface(id, f.string(2).orEmpty().ifEmpty { id }, f.uint(3) == 1L, f.uint(4) == 1L)
        }

    fun byId(id: String): ProtoWriter = ProtoWriter().string(2, id)

    fun installStart(file: WatchfaceFile): ProtoWriter =
        ProtoWriter().message(6, ProtoWriter().string(1, file.id).uint(2, file.bytes.size.toLong()))

    fun uploadRequest(bytes: ByteArray): ProtoWriter =
        ProtoWriter().message(
            1,
            ProtoWriter().uint(1, UPLOAD_WATCHFACE.toLong()).bytes(2, md5(bytes)).uint(3, bytes.size.toLong()),
        )

    /**
     * The upload split into parts of at most [chunkSize] bytes: `[0, type, md5, size, file, crc32]`,
     * each part led by the part count and its own number (both 16-bit, from 1).
     */
    fun parts(type: Int, bytes: ByteArray, chunkSize: Int): List<ByteArray> {
        val head = ByteBuffer.allocate(2 + 16 + 4 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(0)
            .put(type.toByte())
            .put(md5(bytes))
            .putInt(bytes.size)
            .put(bytes)
            .array()
        val crc = CRC32().apply { update(head) }.value.toInt()
        val payload = ByteBuffer.allocate(head.size + 4).order(ByteOrder.LITTLE_ENDIAN).put(head).putInt(crc).array()
        val partSize = chunkSize - 4
        val total = (payload.size + partSize - 1) / partSize
        return (0 until total).map { i ->
            val start = i * partSize
            val end = minOf(start + partSize, payload.size)
            ByteBuffer.allocate(4 + end - start).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(total.toShort())
                .putShort((i + 1).toShort())
                .put(payload, start, end - start)
                .array()
        }
    }

    private fun md5(bytes: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(bytes)

    private fun text(bytes: ByteArray, offset: Int): String? {
        if (offset >= bytes.size) return null
        var end = offset
        while (end < bytes.size && bytes[end] != 0.toByte()) end++
        if (end == bytes.size) return null
        return String(bytes, offset, end - offset, Charsets.UTF_8)
    }
}
