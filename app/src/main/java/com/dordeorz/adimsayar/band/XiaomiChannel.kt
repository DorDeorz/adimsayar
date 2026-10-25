package com.dordeorz.adimsayar.band

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class XiaomiCipher {

    var keys: XiaomiKeys? = null
    var sending = false

    fun encrypt(plain: ByteArray, counter: Int): ByteArray {
        val k = checkNotNull(keys)
        return XiaomiCrypto.encrypt(k.encryptionKey, XiaomiCrypto.packetNonce(k.encryptionNonce, counter), plain)
    }

    fun decrypt(data: ByteArray): ByteArray? {
        val k = keys ?: return null
        return XiaomiCrypto.decrypt(k.decryptionKey, XiaomiCrypto.packetNonce(k.decryptionNonce, 0), data)
    }
}

class XiaomiChannel(
    private val cipher: XiaomiCipher,
    private val write: (ByteArray) -> Unit,
    private val onMessage: (ByteArray) -> Unit,
    private val encrypted: Boolean = true,
) {

    private class Outgoing(val bytes: ByteArray, val done: (Boolean) -> Unit)

    var maxWrite = DEFAULT_MAX_WRITE
    private var counter = 1
    private val queue = ArrayDeque<Outgoing>()
    private var current: Outgoing? = null
    private var currentBytes = ByteArray(0)
    private var currentChunkSize = 0
    private var expectedChunks = 0
    private val chunks = HashMap<Int, ByteArray>()

    val idle: Boolean get() = current == null && queue.isEmpty()

    fun reset() {
        counter = 1
        queue.clear()
        current = null
        expectedChunks = 0
        chunks.clear()
    }

    fun send(payload: ByteArray, done: (Boolean) -> Unit = {}) {
        queue.addLast(Outgoing(payload, done))
        sendNext()
    }

    fun onNotification(value: ByteArray) {
        if (value.size < 3) return
        val buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
        val chunk = buf.short.toInt() and 0xffff
        if (chunk != 0) {
            onChunk(chunk, value.copyOfRange(2, value.size))
            return
        }
        when (buf.get().toInt()) {
            TYPE_CHUNK_START -> {
                if (buf.remaining() < 3) return
                buf.get()
                expectedChunks = buf.short.toInt() and 0xffff
                chunks.clear()
                write(byteArrayOf(0, 0, TYPE_CHUNK_ACK.toByte(), ACK_START.toByte()))
            }
            TYPE_CHUNK_ACK -> onChunkAck(if (buf.hasRemaining()) buf.get().toInt() else -1, buf)
            TYPE_SINGLE -> {
                write(PAYLOAD_ACK)
                if (!buf.hasRemaining()) return
                val sealed = buf.get().toInt() == 1
                val rest = ByteArray(buf.remaining()).also { buf.get(it) }
                val plain = if (sealed) cipher.decrypt(rest) else rest
                if (plain != null) onMessage(plain)
            }
            TYPE_ACK -> finish(buf.hasRemaining() && buf.get().toInt() == 0)
        }
    }

    private fun onChunk(index: Int, data: ByteArray) {
        if (index > expectedChunks) return
        chunks[index] = data
        if (chunks.size < expectedChunks) return
        write(byteArrayOf(0, 0, TYPE_CHUNK_ACK.toByte(), ACK_END.toByte()))
        val out = ByteArrayOutputStream()
        for (i in 1..expectedChunks) out.write(chunks[i] ?: return)
        expectedChunks = 0
        chunks.clear()
        val payload = out.toByteArray()
        (if (encrypted) cipher.decrypt(payload) else payload)?.let(onMessage)
    }

    private fun onChunkAck(subtype: Int, buf: ByteBuffer) {
        when (subtype) {
            ACK_END -> finish(true)
            ACK_START -> {
                var i = 0
                while (i * currentChunkSize < currentBytes.size) sendChunk(i++)
            }
            ACK_NACK -> finish(false)
            ACK_RESEND -> {
                if (!buf.hasRemaining()) {
                    finish(false)
                    return
                }
                while (buf.remaining() >= 2) sendChunk((buf.short.toInt() and 0xffff) - 1)
            }
        }
    }

    private fun sendChunk(index: Int) {
        val start = index * currentChunkSize
        if (index < 0 || start >= currentBytes.size) return
        val end = minOf(start + currentChunkSize, currentBytes.size)
        val packet = ByteBuffer.allocate(2 + end - start).order(ByteOrder.LITTLE_ENDIAN)
            .putShort((index + 1).toShort())
            .put(currentBytes, start, end - start)
            .array()
        write(packet)
    }

    private fun finish(success: Boolean) {
        val done = current ?: return
        current = null
        done.done(success)
        sendNext()
    }

    private fun sendNext() {
        if (current != null) return
        val next = queue.removeFirstOrNull() ?: return
        current = next
        val encrypt = cipher.sending
        val index = counter
        val body = if (encrypt) cipher.encrypt(next.bytes, index) else next.bytes
        if (!encrypted || body.size + 6 > maxWrite) {
            if (encrypt) counter++
            currentBytes = if (encrypt) {
                ByteBuffer.allocate(2 + body.size).order(ByteOrder.LITTLE_ENDIAN).putShort(index.toShort()).put(body).array()
            } else {
                body
            }
            currentChunkSize = maxWrite - 2
            val count = (currentBytes.size + currentChunkSize - 1) / currentChunkSize
            write(
                ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(0)
                    .put(TYPE_CHUNK_START.toByte())
                    .put((if (encrypt) 1 else 0).toByte())
                    .putShort(count.toShort())
                    .array(),
            )
        } else {
            val packet = ByteBuffer.allocate((if (encrypt) 6 else 4) + body.size).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(0)
                .put(TYPE_SINGLE.toByte())
                .put((if (encrypt) 1 else 2).toByte())
            if (encrypt) packet.putShort(counter++.toShort())
            packet.put(body)
            write(packet.array())
        }
    }

    companion object {
        const val DEFAULT_MAX_WRITE = 244
        private const val TYPE_CHUNK_START = 0
        private const val TYPE_CHUNK_ACK = 1
        private const val TYPE_SINGLE = 2
        private const val TYPE_ACK = 3
        private const val ACK_END = 0
        private const val ACK_START = 1
        private const val ACK_NACK = 2
        private const val ACK_RESEND = 5
        private val PAYLOAD_ACK = byteArrayOf(0, 0, 3, 0)
    }
}
