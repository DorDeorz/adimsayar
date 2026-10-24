package com.dordeorz.adimsayar.band

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class XiaomiKeys(
    val decryptionKey: ByteArray,
    val encryptionKey: ByteArray,
    val decryptionNonce: ByteArray,
    val encryptionNonce: ByteArray,
)

object XiaomiCrypto {

    private const val BLOCK = 16
    private const val TAG = 4
    private const val NONCE = 12

    fun parseKey(text: String): ByteArray? {
        val hex = text.trim().removePrefix("0x").removePrefix("0X")
        if (hex.length != 32 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun deriveKeys(secret: ByteArray, phoneNonce: ByteArray, watchNonce: ByteArray): XiaomiKeys {
        val first = hmac(phoneNonce + watchNonce, secret)
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(first, "HmacSHA256")) }
        val output = ByteArray(64)
        var previous = ByteArray(0)
        var counter = 1
        var i = 0
        while (i < output.size) {
            mac.update(previous)
            mac.update("miwear-auth".toByteArray(Charsets.US_ASCII))
            mac.update(counter.toByte())
            previous = mac.doFinal()
            for (b in previous) {
                if (i >= output.size) break
                output[i++] = b
            }
            counter++
        }
        return XiaomiKeys(
            decryptionKey = output.copyOfRange(0, 16),
            encryptionKey = output.copyOfRange(16, 32),
            decryptionNonce = output.copyOfRange(32, 36),
            encryptionNonce = output.copyOfRange(36, 40),
        )
    }

    fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    fun packetNonce(base: ByteArray, counter: Int): ByteArray =
        ByteBuffer.allocate(NONCE).order(ByteOrder.LITTLE_ENDIAN).put(base).putInt(0).putInt(counter).array()

    fun encrypt(key: ByteArray, nonce: ByteArray, plain: ByteArray): ByteArray {
        val aes = aes(key)
        val tag = cbcMac(aes, nonce, plain)
        val out = ByteArray(plain.size + TAG)
        ctr(aes, nonce, plain, out)
        val s0 = aes.doFinal(counterBlock(nonce, 0))
        for (i in 0 until TAG) out[plain.size + i] = (tag[i].toInt() xor s0[i].toInt()).toByte()
        return out
    }

    fun decrypt(key: ByteArray, nonce: ByteArray, data: ByteArray): ByteArray? {
        if (data.size < TAG) return null
        val aes = aes(key)
        val plain = ByteArray(data.size - TAG)
        ctr(aes, nonce, data.copyOfRange(0, plain.size), plain)
        val tag = cbcMac(aes, nonce, plain)
        val s0 = aes.doFinal(counterBlock(nonce, 0))
        var diff = 0
        for (i in 0 until TAG) diff = diff or ((tag[i].toInt() xor s0[i].toInt()) xor data[plain.size + i].toInt())
        return if (diff and 0xff == 0) plain else null
    }

    private fun aes(key: ByteArray): Cipher =
        Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }

    private fun counterBlock(nonce: ByteArray, counter: Int): ByteArray {
        val block = ByteArray(BLOCK)
        block[0] = (BLOCK - 1 - NONCE - 1).toByte()
        nonce.copyInto(block, 1)
        block[13] = (counter ushr 16).toByte()
        block[14] = (counter ushr 8).toByte()
        block[15] = counter.toByte()
        return block
    }

    private fun ctr(aes: Cipher, nonce: ByteArray, input: ByteArray, output: ByteArray) {
        var offset = 0
        var counter = 1
        while (offset < input.size) {
            val stream = aes.doFinal(counterBlock(nonce, counter++))
            val n = minOf(BLOCK, input.size - offset)
            for (i in 0 until n) output[offset + i] = (input[offset + i].toInt() xor stream[i].toInt()).toByte()
            offset += n
        }
    }

    private fun cbcMac(aes: Cipher, nonce: ByteArray, plain: ByteArray): ByteArray {
        val b0 = ByteArray(BLOCK)
        b0[0] = ((((TAG - 2) / 2) shl 3) or (BLOCK - 1 - NONCE - 1)).toByte()
        nonce.copyInto(b0, 1)
        b0[13] = (plain.size ushr 16).toByte()
        b0[14] = (plain.size ushr 8).toByte()
        b0[15] = plain.size.toByte()
        var x = aes.doFinal(b0)
        var offset = 0
        while (offset < plain.size) {
            val block = x.copyOf()
            val n = minOf(BLOCK, plain.size - offset)
            for (i in 0 until n) block[i] = (block[i].toInt() xor plain[offset + i].toInt()).toByte()
            x = aes.doFinal(block)
            offset += n
        }
        return x
    }
}
