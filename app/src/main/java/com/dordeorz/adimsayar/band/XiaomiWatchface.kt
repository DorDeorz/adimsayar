package com.dordeorz.adimsayar.band

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32

/** A watch face on the band. Built-in faces have [canDelete] false. */
data class BandWatchface(val id: String, val name: String, val active: Boolean, val canDelete: Boolean)

/** A decoded preview picture: ARGB pixels, row by row. */
class FacePreview(val width: Int, val height: Int, val pixels: IntArray)

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
    private const val PREVIEW_OFFSET = 0x20
    private const val MAX_PREVIEW = 1024
    private val RLE_MAGIC = byteArrayOf(0xe0.toByte(), 0x21, 0xa5.toByte(), 0x5a)

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

    /** The preview picture stored in a face file, or null when it has none or it cannot be read. */
    fun preview(bytes: ByteArray): FacePreview? = runCatching { decodePreview(bytes) }.getOrNull()

    private fun decodePreview(fw: ByteArray): FacePreview? {
        val bb = ByteBuffer.wrap(fw).order(ByteOrder.LITTLE_ENDIAN)
        val offset = bb.getInt(PREVIEW_OFFSET)
        if (offset <= 0 || offset + 12 > fw.size) return null
        bb.position(offset)
        val format = bb.get().toInt() and 0xff
        val compression = bb.get().toInt() and 0xff
        bb.short
        val width = bb.short.toInt() and 0xffff
        val height = bb.short.toInt() and 0xffff
        val size = bb.int
        if (width == 0 || height == 0 || width > MAX_PREVIEW || height > MAX_PREVIEW || size <= 0 || size > bb.remaining()) return null
        val raw = ByteArray(size).also { bb.get(it) }
        val data = when (compression) {
            0 -> raw
            4 -> rle(raw, v2 = true)
            8 -> rle(raw, v2 = false)
            else -> null
        } ?: return null
        return decode(data, format, width, height)
    }

    // LVGL run-length coding as Xiaomi faces use it; v1 and v2 swap the meaning of the top bit.
    private fun rle(data: ByteArray, v2: Boolean): ByteArray? {
        if (data.size < 8 || !RLE_MAGIC.indices.all { data[it] == RLE_MAGIC[it] }) return null
        val chunk = (data[4].toInt() and 0xf).coerceAtLeast(1)
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        bb.int
        val out = ByteArray((bb.int shr 4) and 0xfffffff)
        var at = 0
        while (bb.hasRemaining()) {
            val control = bb.get().toInt()
            val n = control and 0x7f
            val literal = (control and 0x80 != 0) == v2
            val count = if (v2) n else n + 1
            if (at + chunk * count > out.size) return null
            if (literal) {
                if (bb.remaining() < chunk * count) return null
                bb.get(out, at, chunk * count)
                at += chunk * count
            } else {
                if (bb.remaining() < chunk) return null
                val one = ByteArray(chunk).also { bb.get(it) }
                repeat(count) {
                    one.copyInto(out, at)
                    at += chunk
                }
            }
        }
        return out
    }

    private fun decode(data: ByteArray, format: Int, width: Int, height: Int): FacePreview? {
        val count = width * height
        val need = when (format) {
            0 -> count * 4
            1, 4, 7 -> count * 2
            16 -> 256 * 4 + count
            else -> return null
        }
        if (data.size < need) return null
        val bb = ByteBuffer.wrap(data).order(if (format == 7) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        val palette = if (format == 16) IntArray(256) { bb.int } else IntArray(0)
        val pixels = IntArray(count) {
            when (format) {
                0 -> bb.int
                16 -> palette[bb.get().toInt() and 0xff]
                else -> {
                    val c = bb.short.toInt() and 0xffff
                    (0xff000000.toInt()) or ((c and 0xf800) shl 8) or ((c and 0x07e0) shl 5) or ((c and 0x001f) shl 3)
                }
            }
        }
        return FacePreview(width, height, pixels)
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
