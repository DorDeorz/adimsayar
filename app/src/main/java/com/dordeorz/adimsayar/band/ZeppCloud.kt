package com.dordeorz.adimsayar.band

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ZeppCloudDevice(val address: String, val key: String, val active: Boolean)

/**
 * Reads the Bluetooth keys of bands paired in Zepp Life / Zepp from the Zepp (Huami) account.
 * The password goes only to Zepp's server and is not stored. Ported from huami-token (MIT).
 */
class ZeppCloud {

    private val random = SecureRandom()
    private var appToken: String? = null
    private var userId: String? = null

    /** True when the sign-in worked and [devices] can be called. */
    fun login(email: String, password: String): Boolean {
        val payload = form(
            "emailOrPhone" to email,
            "state" to "REDIRECTION",
            "client_id" to "HuaMi",
            "password" to password,
            "redirect_uri" to "https://s3-us-west-2.amazonaws.com/hm-registration/successsignin.html",
            "region" to "us-west-2",
            "token" to "access",
            "token" to "refresh",
            "country_code" to "US",
        )
        val tokens = open(TOKENS_URL)
        val location = try {
            tokens.requestMethod = "POST"
            tokens.doOutput = true
            APP_HEADERS.forEach { (k, v) -> tokens.setRequestProperty(k, v) }
            tokens.setRequestProperty("x-hm-ekv", "1")
            tokens.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            tokens.outputStream.use { it.write(encryptPayload(payload.toByteArray())) }
            if (tokens.responseCode !in 300..399) return false
            tokens.getHeaderField("Location") ?: return false
        } catch (e: Exception) {
            return false
        } finally {
            tokens.disconnect()
        }
        val query = query(location)
        val access = query["access"] ?: return false

        val login = open(LOGIN_URL)
        return try {
            login.requestMethod = "POST"
            login.doOutput = true
            WEB_HEADERS.forEach { (k, v) -> login.setRequestProperty(k, v) }
            login.outputStream.use {
                it.write(
                    form(
                        "code" to access,
                        "device_id" to UUID.randomUUID().toString(),
                        "device_model" to "android_phone",
                        "app_version" to APP_VERSION,
                        "dn" to "api-mifit.zepp.com,api-user.zepp.com,api-mifit.zepp.com,api-watch.zepp.com,app-analytics.zepp.com,auth.zepp.com,api-analytics.zepp.com",
                        "third_name" to "huami",
                        "source" to "com.huami.watch.hmwatchmanager:$APP_VERSION:$APP_BUILD",
                        "app_name" to "com.huami.midong",
                        "country_code" to "US",
                        "grant_type" to "access_token",
                        "allow_registration" to "false",
                        "lang" to "en",
                        "countryState" to "US-NY",
                    ).toByteArray(),
                )
            }
            if (login.responseCode != HttpURLConnection.HTTP_OK) return false
            val info = JSONObject(login.inputStream.bufferedReader().use { it.readText() }).optJSONObject("token_info") ?: return false
            appToken = info.optString("app_token").takeIf { it.isNotEmpty() }
            userId = info.opt("user_id")?.toString()?.takeIf { it.isNotEmpty() }
            appToken != null && userId != null
        } catch (e: Exception) {
            false
        } finally {
            login.disconnect()
        }
    }

    fun devices(): List<ZeppCloudDevice> {
        val token = appToken ?: return emptyList()
        val user = userId ?: return emptyList()
        val r = UUID.randomUUID().toString()
        val params = form(
            "r" to r,
            "r" to r,
            "enableMultiDeviceOnMultiType" to "true",
            "enableMultiDeviceOnMultiType" to "true",
            "userid" to user,
            "appid" to java.lang.Long.toUnsignedString(random.nextLong()),
            "channel" to CHANNEL,
            "country" to "US",
            "cv" to "${APP_BUILD}_$APP_VERSION",
            "device" to "android_32",
            "device_type" to "android_phone",
            "enableMultiDevice" to "true",
            "lang" to "en_US",
            "timezone" to "Europe/London",
            "v" to "2.0",
        )
        val connection = open("https://api-mifit.zepp.com/users/${URLEncoder.encode(user, "UTF-8")}/devices?$params")
        return try {
            APP_HEADERS.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.setRequestProperty("apptoken", token)
            connection.setRequestProperty("x-request-id", UUID.randomUUID().toString())
            connection.setRequestProperty("channel", CHANNEL)
            connection.setRequestProperty("country", "US")
            connection.setRequestProperty("lang", "en_US")
            connection.setRequestProperty("timezone", "Europe/London")
            connection.setRequestProperty("hm-privacy-diagnostics", "false")
            connection.setRequestProperty("hm-privacy-ceip", "true")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return emptyList()
            val items = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("items") ?: return emptyList()
            (0 until items.length()).mapNotNull { i -> items.optJSONObject(i)?.let(::parseDevice) }
        } catch (e: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = false
        }

    companion object {
        private const val TOKENS_URL = "https://api-user-us2.zepp.com/v2/registrations/tokens"
        private const val LOGIN_URL = "https://api-mifit-us2.zepp.com/v2/client/login"
        private const val APP_VERSION = "9.12.5"
        private const val APP_BUILD = "151689"
        private const val CHANNEL = "a100900101016"
        private const val TIMEOUT_MS = 15_000
        private val KEY = Regex("[0-9a-fA-F]{32}")
        private val ADDRESS = Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}")
        private val PAYLOAD_KEY = "xeNtBVqzDc6tuNTh".toByteArray()
        private val PAYLOAD_IV = "MAAAYAAAAAAAAABg".toByteArray()

        private val APP_HEADERS = mapOf(
            "app_name" to "com.huami.midong",
            "appname" to "com.huami.midong",
            "cv" to "${APP_BUILD}_$APP_VERSION",
            "v" to "2.0",
            "appplatform" to "android_phone",
            "vb" to "202509151347",
            "vn" to APP_VERSION,
            "User-Agent" to "Zepp/$APP_VERSION (Pixel 4; Android 12; Density/2.75)",
            "Accept-Encoding" to "identity",
        )

        private val WEB_HEADERS = mapOf(
            "app_name" to "com.huami.webapp",
            "appname" to "com.huami.webapp",
            "Origin" to "https://user.zepp.com",
            "Referer" to "https://user.zepp.com/",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:133.0) Gecko/20100101 Firefox/133.0",
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to "en-US,en;q=0.5",
            "Accept-Encoding" to "identity",
        )

        /** AES-128-CBC with PKCS#7 padding, as the Zepp app sends the sign-in form. */
        fun encryptPayload(data: ByteArray): ByteArray =
            Cipher.getInstance("AES/CBC/PKCS5Padding")
                .apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(PAYLOAD_KEY, "AES"), IvParameterSpec(PAYLOAD_IV)) }
                .doFinal(data)

        fun parseDevice(item: JSONObject): ZeppCloudDevice? {
            val address = item.optString("macAddress").uppercase()
            val info = item.optString("additionalInfo").takeIf { it.isNotEmpty() }?.let {
                try {
                    JSONObject(it)
                } catch (e: Exception) {
                    null
                }
            } ?: return null
            val key = info.optString("auth_key").removePrefix("0x")
            if (!ADDRESS.matches(address) || !KEY.matches(key)) return null
            return ZeppCloudDevice(address, key.lowercase(), item.optInt("activeStatus", 0) != 0)
        }

        fun query(url: String): Map<String, String> =
            url.substringAfter('?', "").substringBefore('#').split('&').filter { '=' in it }.associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }

        private fun form(vararg pairs: Pair<String, String>): String =
            pairs.joinToString("&") { "${URLEncoder.encode(it.first, "UTF-8")}=${URLEncoder.encode(it.second, "UTF-8")}" }
    }
}
