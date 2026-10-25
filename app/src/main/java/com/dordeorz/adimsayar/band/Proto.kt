package com.dordeorz.adimsayar.band

import java.io.ByteArrayOutputStream

class ProtoWriter {

    private val out = ByteArrayOutputStream()

    fun uint(field: Int, value: Long): ProtoWriter {
        key(field, WIRE_VARINT)
        varint(value)
        return this
    }

    fun sint(field: Int, value: Int): ProtoWriter = uint(field, ((value shl 1) xor (value shr 31)).toLong() and 0xffffffffL)

    fun bool(field: Int, value: Boolean): ProtoWriter = uint(field, if (value) 1L else 0L)

    fun float(field: Int, value: Float): ProtoWriter {
        key(field, WIRE_FIXED32)
        val bits = java.lang.Float.floatToIntBits(value)
        for (i in 0 until 4) out.write((bits ushr (8 * i)) and 0xff)
        return this
    }

    fun bytes(field: Int, value: ByteArray): ProtoWriter {
        key(field, WIRE_LENGTH)
        varint(value.size.toLong())
        out.write(value)
        return this
    }

    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, value: ProtoWriter): ProtoWriter = bytes(field, value.toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun key(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())

    private fun varint(value: Long) {
        var v = value
        while (v and 0x7fL.inv() != 0L) {
            out.write(((v and 0x7f) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }
}

class ProtoMessage private constructor(private val fields: Map<Int, List<Any>>) {

    fun uint(field: Int): Long? = fields[field]?.lastOrNull() as? Long

    fun bytes(field: Int): ByteArray? = fields[field]?.lastOrNull() as? ByteArray

    fun message(field: Int): ProtoMessage? = bytes(field)?.let { parse(it) }

    companion object {
        fun parse(data: ByteArray): ProtoMessage? {
            val fields = HashMap<Int, MutableList<Any>>()
            var pos = 0
            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    if (pos >= data.size || shift > 63) throw IllegalArgumentException()
                    val b = data[pos++].toInt() and 0xff
                    result = result or ((b and 0x7f).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }
            return try {
                while (pos < data.size) {
                    val key = varint()
                    val field = (key ushr 3).toInt()
                    val value: Any = when ((key and 7).toInt()) {
                        WIRE_VARINT -> varint()
                        WIRE_FIXED64 -> fixed(8)(data, pos).also { pos += 8 }
                        WIRE_LENGTH -> {
                            val length = varint().toInt()
                            if (length < 0 || pos + length > data.size) return null
                            data.copyOfRange(pos, pos + length).also { pos += length }
                        }
                        WIRE_FIXED32 -> fixed(4)(data, pos).also { pos += 4 }
                        else -> return null
                    }
                    fields.getOrPut(field) { ArrayList() }.add(value)
                }
                ProtoMessage(fields)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private fun fixed(size: Int): (ByteArray, Int) -> Long = { data, start ->
            if (start + size > data.size) throw IllegalArgumentException()
            var v = 0L
            for (i in 0 until size) v = v or ((data[start + i].toLong() and 0xff) shl (8 * i))
            v
        }
    }
}

private const val WIRE_VARINT = 0
private const val WIRE_FIXED64 = 1
private const val WIRE_LENGTH = 2
private const val WIRE_FIXED32 = 5
