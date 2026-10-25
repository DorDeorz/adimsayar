package com.dordeorz.adimsayar.band

import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.io.encoding.Base64

object XiaomiCloudCrypto {

    fun nonce(random: ByteArray, millis: Long): String {
        val bytes = ByteBuffer.allocate(12).put(random, 0, 8).putInt((millis / 60_000L).toInt()).array()
        return Base64.encode(bytes)
    }

    fun signedNonce(ssecurity: String, nonce: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Base64.decode(ssecurity))
        digest.update(Base64.decode(nonce))
        return Base64.encode(digest.digest())
    }

    fun signature(method: String, path: String, signedNonce: String, params: Map<String, String>): String {
        val parts = listOf(method.uppercase(), path) + params.map { "${it.key}=${it.value}" } + signedNonce
        val digest = MessageDigest.getInstance("SHA-1").digest(parts.joinToString("&").toByteArray())
        return Base64.encode(digest)
    }

    fun requestParams(path: String, signedNonce: String, nonce: String, ssecurity: String, data: String): Map<String, String> {
        val params = linkedMapOf("data" to data)
        params["rc4_hash__"] = signature("POST", path, signedNonce, params)
        for (key in params.keys.toList()) params[key] = encrypt(signedNonce, params.getValue(key))
        params["signature"] = signature("POST", path, signedNonce, params)
        params["ssecurity"] = ssecurity
        params["_nonce"] = nonce
        return params
    }

    fun encrypt(signedNonce: String, text: String): String =
        Base64.encode(rc4(Base64.decode(signedNonce), text.toByteArray()))

    fun decrypt(signedNonce: String, body: String): String =
        String(rc4(Base64.decode(signedNonce), Base64.decode(body.trim())))

    fun rc4(key: ByteArray, data: ByteArray): ByteArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xff)) and 0xff
            val t = s[i]; s[i] = s[j]; s[j] = t
        }
        var x = 0
        var y = 0
        fun next(): Int {
            x = (x + 1) and 0xff
            y = (y + s[x]) and 0xff
            val t = s[x]; s[x] = s[y]; s[y] = t
            return s[(s[x] + s[y]) and 0xff]
        }
        repeat(1024) { next() }
        return ByteArray(data.size) { (data[it].toInt() xor next()).toByte() }
    }
}
