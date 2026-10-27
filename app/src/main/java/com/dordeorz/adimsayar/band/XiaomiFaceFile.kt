package com.dordeorz.adimsayar.band

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A Smart Band 8 face file split into its pictures, so they can be swapped and the file written
 * again without Mi Create's Windows compiler. The file is a head (header, section tables, widgets)
 * followed by the pictures: plain images (section 2, RGB565, no transparency), picture lists
 * (section 3, BGRA with transparency) and the preview. Each section entry holds its picture's
 * offset and length; the preview's offset is kept at 0x20 and 0xAC. Checked against compiled
 * faces with watchface/facebin.py.
 */
class XiaomiFaceFile private constructor(
    private val head: ByteArray,
    private val pieces: List<Piece>,
    private val tail: ByteArray,
) {

    /** One picture: where its offset and length are written ([entry], -1 for the preview) and its bytes. */
    private class Piece(val section: Int, val index: Int, val entry: Int, val offset: Int, var bytes: ByteArray)

    /** Widgets (section 0) in the order of the project; each has a 16-byte record in the head. */
    val widgets: Int get() = section(WIDGETS).first

    /** Moves widget [index] down by [dy] pixels (up when negative); its record holds x and y at +4 and +6. */
    fun moveWidget(index: Int, dy: Int) {
        val bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        val (count, table) = section(WIDGETS)
        require(index in 0 until count)
        val record = bb.getInt(table + 16 * index + 8)
        val y = bb.getShort(record + 6).toInt() + dy
        require(y in 0..0x7fff)
        bb.putShort(record + 6, y.toShort())
    }

    private fun section(section: Int): Pair<Int, Int> {
        val bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        return bb.getInt(SECTIONS_AT + 8 * section) to bb.getInt(SECTIONS_AT + 8 * section + 4)
    }

    val images: Int get() = pieces.count { it.section == IMAGES }
    val lists: Int get() = pieces.count { it.section == LISTS }

    fun image(index: Int): FacePicture = decodeImage(piece(IMAGES, index).bytes)

    fun setImage(index: Int, picture: FacePicture) {
        piece(IMAGES, index).bytes = encodeImage(picture)
    }

    fun list(index: Int): List<FacePicture> = decodeList(piece(LISTS, index).bytes)

    fun setList(index: Int, pictures: List<FacePicture>) {
        piece(LISTS, index).bytes = encodeList(pictures)
    }

    /** The preview is a plain image like the others; the Smart Band 8 wants it at 122 x 310. */
    fun setPreview(picture: FacePicture) {
        piece(PREVIEW, 0).bytes = encodeImage(picture)
    }

    /** The file with the new pictures; ids and names are left as they were. */
    fun bytes(): ByteArray {
        val out = ByteArrayOutputStream(head.size + pieces.sumOf { it.bytes.size + 3 } + tail.size)
        out.write(head)
        val places = HashMap<Piece, Int>()
        for (piece in pieces) {
            while (out.size() % 4 != 0) out.write(0)
            places[piece] = out.size()
            out.write(piece.bytes)
        }
        out.write(tail)
        val bytes = out.toByteArray()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (piece in pieces) {
            val at = places.getValue(piece)
            if (piece.entry >= 0) {
                bb.putInt(piece.entry + 8, at)
                bb.putInt(piece.entry + 12, piece.bytes.size)
            } else {
                bb.putInt(PREVIEW_AT, at)
                bb.putInt(PREVIEW_AT_TOO, at)
            }
        }
        return bytes
    }

    private fun piece(section: Int, index: Int): Piece =
        pieces.firstOrNull { it.section == section && it.index == index } ?: throw IndexOutOfBoundsException("$section/$index")

    companion object {

        private const val WIDGETS = 0
        private const val IMAGES = 2
        private const val LISTS = 3
        private const val PREVIEW = -1
        private const val SECTIONS_AT = 0xb0
        private const val PREVIEW_AT = 0x20
        private const val PREVIEW_AT_TOO = 0xac
        private const val FORMAT_IMAGE = 3
        private const val FORMAT_LIST = 0
        private const val RLE = 4
        private val RLE_MAGIC = byteArrayOf(0xe0.toByte(), 0x21, 0xa5.toByte(), 0x5a)

        /** Null when the file is not laid out as expected, so nothing gets written over a face we do not understand. */
        fun parse(bytes: ByteArray): XiaomiFaceFile? = runCatching { read(bytes) }.getOrNull()

        private fun read(bytes: ByteArray): XiaomiFaceFile? {
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val found = ArrayList<Piece>()
            for (section in intArrayOf(IMAGES, LISTS)) {
                val count = bb.getInt(SECTIONS_AT + 8 * section)
                val table = bb.getInt(SECTIONS_AT + 8 * section + 4)
                if (count !in 0..256) return null
                for (i in 0 until count) {
                    val entry = table + 16 * i
                    val offset = bb.getInt(entry + 8)
                    val length = bb.getInt(entry + 12)
                    if (offset <= 0 || length <= 0 || offset + length > bytes.size) return null
                    found += Piece(section, i, entry, offset, bytes.copyOfRange(offset, offset + length))
                }
            }
            val preview = bb.getInt(PREVIEW_AT)
            if (preview <= 0 || preview + 12 > bytes.size) return null
            val previewEnd = preview + 12 + bb.getInt(preview + 8)
            if (previewEnd > bytes.size) return null
            found += Piece(PREVIEW, 0, -1, preview, bytes.copyOfRange(preview, previewEnd))
            found.sortBy { it.offset }
            // the pictures follow each other, only padded to 4 bytes
            for (i in 1 until found.size) {
                val gap = found[i].offset - (found[i - 1].offset + found[i - 1].bytes.size)
                if (gap !in 0..3) return null
            }
            val last = found.last()
            return XiaomiFaceFile(
                bytes.copyOfRange(0, found.first().offset),
                found,
                bytes.copyOfRange(last.offset + last.bytes.size, bytes.size),
            )
        }

        private fun decodeImage(record: ByteArray): FacePicture {
            val bb = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN)
            require(record[0].toInt() == FORMAT_IMAGE && record[1].toInt() == RLE)
            val width = bb.getShort(4).toInt() and 0xffff
            val height = bb.getShort(6).toInt() and 0xffff
            val raw = rleDecode(record, 12, bb.getInt(8))
            val pixels = IntArray(width * height) { i ->
                val c = (raw[2 * i].toInt() and 0xff) or ((raw[2 * i + 1].toInt() and 0xff) shl 8)
                (0xff shl 24) or ((c and 0xf800) shl 8) or ((c and 0x07e0) shl 5) or ((c and 0x001f) shl 3)
            }
            return FacePicture(width, height, pixels)
        }

        private fun encodeImage(picture: FacePicture): ByteArray {
            val raw = ByteArray(picture.pixels.size * 2)
            picture.pixels.forEachIndexed { i, argb ->
                val c = ((argb shr 8) and 0xf800) or ((argb shr 5) and 0x07e0) or ((argb shr 3) and 0x001f)
                raw[2 * i] = c.toByte()
                raw[2 * i + 1] = (c shr 8).toByte()
            }
            val data = rleEncode(raw, 2)
            return ByteBuffer.allocate(12 + data.size).order(ByteOrder.LITTLE_ENDIAN)
                .put(FORMAT_IMAGE.toByte()).put(RLE.toByte()).putShort(0)
                .putShort(picture.width.toShort()).putShort(picture.height.toShort())
                .putInt(data.size).put(data).array()
        }

        private fun decodeList(record: ByteArray): List<FacePicture> {
            val bb = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN)
            require(record[0].toInt() == FORMAT_LIST && record[2].toInt() == RLE)
            val count = record[1].toInt() and 0xff
            val width = bb.getShort(4).toInt() and 0xffff
            val height = bb.getShort(6).toInt() and 0xffff
            var at = 8 + 4 * (count + 1)
            return (0 until count).map { i ->
                val size = bb.getInt(12 + 4 * i)
                val raw = rleDecode(record, at, size)
                at += size
                FacePicture(width, height, IntArray(width * height) { p ->
                    val b = raw[4 * p].toInt() and 0xff
                    val g = raw[4 * p + 1].toInt() and 0xff
                    val r = raw[4 * p + 2].toInt() and 0xff
                    val a = raw[4 * p + 3].toInt() and 0xff
                    (a shl 24) or (r shl 16) or (g shl 8) or b
                })
            }
        }

        private fun encodeList(pictures: List<FacePicture>): ByteArray {
            val first = pictures.first()
            val frames = pictures.map { picture ->
                require(picture.width == first.width && picture.height == first.height)
                val raw = ByteArray(picture.pixels.size * 4)
                picture.pixels.forEachIndexed { i, argb ->
                    raw[4 * i] = argb.toByte()
                    raw[4 * i + 1] = (argb shr 8).toByte()
                    raw[4 * i + 2] = (argb shr 16).toByte()
                    raw[4 * i + 3] = (argb ushr 24).toByte()
                }
                rleEncode(raw, 4)
            }
            val total = frames.sumOf { it.size }
            val bb = ByteBuffer.allocate(8 + 4 * (frames.size + 1) + total).order(ByteOrder.LITTLE_ENDIAN)
                .put(FORMAT_LIST.toByte()).put(frames.size.toByte()).put(RLE.toByte()).put(0)
                .putShort(first.width.toShort()).putShort(first.height.toShort())
                .putInt(total)
            frames.forEach { bb.putInt(it.size) }
            frames.forEach { bb.put(it) }
            return bb.array()
        }

        /** LVGL run-length coding: a control byte, top bit set for [n] literal chunks, clear for one chunk repeated [n] times. */
        internal fun rleDecode(data: ByteArray, start: Int, length: Int): ByteArray {
            require((0 until 4).all { data[start + it] == RLE_MAGIC[it] })
            val word = ByteBuffer.wrap(data, start + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val chunk = word and 0xf
            val out = ByteArray(word ushr 4)
            var at = 0
            var p = start + 8
            val end = start + length
            while (p < end) {
                val control = data[p++].toInt() and 0xff
                val n = control and 0x7f
                if (control and 0x80 != 0) {
                    System.arraycopy(data, p, out, at, chunk * n)
                    p += chunk * n
                } else {
                    repeat(n) { System.arraycopy(data, p, out, at + it * chunk, chunk) }
                    p += chunk
                }
                at += chunk * n
            }
            require(at == out.size)
            return out
        }

        internal fun rleEncode(raw: ByteArray, chunk: Int): ByteArray {
            val out = ByteArrayOutputStream(raw.size / 2 + 16)
            out.write(RLE_MAGIC)
            val word = (raw.size shl 4) or chunk
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(word).array())
            val count = raw.size / chunk
            fun same(a: Int, b: Int): Boolean = (0 until chunk).all { raw[a * chunk + it] == raw[b * chunk + it] }
            var i = 0
            while (i < count) {
                var run = 1
                while (i + run < count && run < MAX_RUN && same(i + run, i)) run++
                if (run >= 2) {
                    out.write(run)
                    out.write(raw, i * chunk, chunk)
                    i += run
                    continue
                }
                var j = i
                while (j < count && j - i < MAX_RUN && !(j + 1 < count && same(j + 1, j))) j++
                if (j == i) j = i + 1
                out.write(0x80 or (j - i))
                out.write(raw, i * chunk, (j - i) * chunk)
                i = j
            }
            return out.toByteArray()
        }

        private const val MAX_RUN = 127
    }
}

/** A picture of a face file: ARGB pixels, row by row. */
class FacePicture(val width: Int, val height: Int, val pixels: IntArray)
