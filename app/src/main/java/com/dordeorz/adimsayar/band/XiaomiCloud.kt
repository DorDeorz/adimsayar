package com.dordeorz.adimsayar.band

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom

class XiaomiCloudDevice(val name: String, val address: String, val key: String, val model: String)

class XiaomiCloudLogin(val qrUrl: String, val loginUrl: String, val pollUrl: String, val timeoutMs: Long)

class XiaomiCloud {

    private val random = SecureRandom()
    private val cookies = mutableMapOf<String, String>()
    private val agent = buildString {
        repeat(18) { append('a' + random.nextInt(26)) }
        append('-')
        repeat(13) { append('A' + random.nextInt(5)) }
        append(" APP/com.xiaomi.mihome APPV/10.5.201")
    }
    private var ssecurity: String? = null
    private var userId: String? = null
    private var serviceToken: String? = null

    fun startLogin(): XiaomiCloudLogin? {
        val query = form(
            "_qrsize" to "480",
            "qs" to "%3Fsid%3Dxiaomiio%26_json%3Dtrue",
            "callback" to STS_URL,
            "_hasLogo" to "false",
            "sid" to "xiaomiio",
            "serviceParam" to "",
            "_locale" to "en_GB",
            "_dc" to System.currentTimeMillis().toString(),
        )
        val response = request("$ACCOUNT_URL/longPolling/loginUrl?$query") ?: return null
        if (response.code != HttpURLConnection.HTTP_OK) return null
        val json = json(response.body) ?: return null
        val qr = json.optString("qr")
        val login = json.optString("loginUrl")
        val poll = json.optString("lp")
        if (qr.isEmpty() || login.isEmpty() || poll.isEmpty()) return null
        val timeout = json.optLong("timeout", DEFAULT_TIMEOUT_S).coerceIn(60L, 900L)
        return XiaomiCloudLogin(qr, login, poll, timeout * 1000L)
    }

    fun qrImage(login: XiaomiCloudLogin): ByteArray? {
        val connection = open(login.qrUrl)
        return try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.use { it.readBytes() } else null
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    suspend fun waitForLogin(login: XiaomiCloudLogin): Boolean {
        val end = System.currentTimeMillis() + login.timeoutMs
        while (System.currentTimeMillis() < end) {
            currentCoroutineContext().ensureActive()
            val response = request(login.pollUrl, readTimeoutMs = POLL_TIMEOUT_MS)
            if (response == null) continue
            if (response.code != HttpURLConnection.HTTP_OK) {
                delay(RETRY_MS)
                continue
            }
            val json = json(response.body) ?: return false
            ssecurity = json.optString("ssecurity").takeIf { it.isNotEmpty() } ?: return false
            userId = json.opt("userId")?.toString()
            val location = json.optString("location").takeIf { it.isNotEmpty() } ?: return false
            return fetchServiceToken(location)
        }
        return false
    }

    private fun fetchServiceToken(location: String): Boolean {
        var url = location
        repeat(MAX_REDIRECTS) {
            val response = request(url) ?: return false
            cookies["serviceToken"]?.let { serviceToken = it }
            val next = response.location
            if (response.code in 300..399 && next != null) url = next else return serviceToken != null
        }
        return serviceToken != null
    }

    fun devices(): List<XiaomiCloudDevice> {
        val found = linkedMapOf<String, XiaomiCloudDevice>()
        for (server in SERVERS) {
            for ((homeId, owner) in homes(server)) {
                val data = """{"home_owner":$owner,"home_id":$homeId,"limit":200,"get_split_device":true,"support_smart_home":true}"""
                val list = call(server, "/v2/home/home_device_list", data)?.optJSONObject("result")?.optJSONArray("device_info") ?: continue
                for (i in 0 until list.length()) {
                    val device = list.optJSONObject(i) ?: continue
                    val address = device.optString("mac").uppercase()
                    val key = device.optString("token").lowercase()
                    val model = device.optString("model")
                    if (!ADDRESS.matches(address) || !KEY.matches(key) || !isWearable(model)) continue
                    found[address] = XiaomiCloudDevice(device.optString("name").ifEmpty { model }, address, key, model)
                }
            }
        }
        return found.values.toList()
    }

    private fun homes(server: String): List<Pair<String, String>> {
        val owner = userId ?: return emptyList()
        val homes = mutableListOf<Pair<String, String>>()
        call(server, "/v2/homeroom/gethome", """{"fg":true,"fetch_share":true,"fetch_share_dev":true,"limit":300,"app_ver":7}""")
            ?.optJSONObject("result")?.optJSONArray("homelist")?.let { list ->
                for (i in 0 until list.length()) list.optJSONObject(i)?.opt("id")?.let { homes.add(it.toString() to owner) }
            }
        call(server, "/v2/user/get_device_cnt", """{"fetch_own":true,"fetch_share":true}""")
            ?.optJSONObject("result")?.optJSONObject("share")?.optJSONArray("share_family")?.let { list ->
                for (i in 0 until list.length()) {
                    val home = list.optJSONObject(i) ?: continue
                    homes.add(home.opt("home_id").toString() to home.opt("home_owner").toString())
                }
            }
        return homes.distinct()
    }

    private fun call(server: String, path: String, data: String): JSONObject? {
        val security = ssecurity ?: return null
        val token = serviceToken ?: return null
        val randomBytes = ByteArray(8).also { random.nextBytes(it) }
        val nonce = XiaomiCloudCrypto.nonce(randomBytes, System.currentTimeMillis())
        val signed = XiaomiCloudCrypto.signedNonce(security, nonce)
        val params = XiaomiCloudCrypto.requestParams(path, signed, nonce, security, data)
        val host = if (server == "cn") "api.io.mi.com" else "$server.api.io.mi.com"
        val cookie = listOf(
            "userId" to userId.orEmpty(),
            "yetAnotherServiceToken" to token,
            "serviceToken" to token,
            "locale" to "en_GB",
            "channel" to "MI_APP_STORE",
        ).joinToString("; ") { "${it.first}=${it.second}" }
        val connection = open("https://$host/app$path?${form(*params.toList().toTypedArray())}")
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("x-xiaomi-protocal-flag-cli", "PROTOCAL-HTTP2")
            connection.setRequestProperty("MIOT-ENCRYPT-ALGORITHM", "ENCRYPT-RC4")
            connection.setRequestProperty("Cookie", cookie)
            connection.outputStream.use { }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(XiaomiCloudCrypto.decrypt(signed, body))
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private class Response(val code: Int, val body: String, val location: String?)

    private fun request(url: String, readTimeoutMs: Int = TIMEOUT_MS): Response? {
        val connection = open(url, readTimeoutMs)
        return try {
            if (cookies.isNotEmpty()) connection.setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            val code = connection.responseCode
            connection.headerFields["Set-Cookie"].orEmpty().forEach { header ->
                val pair = header.substringBefore(';')
                val name = pair.substringBefore('=').trim()
                if (name.isNotEmpty()) cookies[name] = pair.substringAfter('=', "").trim()
            }
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val location = connection.getHeaderField("Location")?.let { URL(URL(url), it).toString() }
            Response(code, body, location)
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, readTimeoutMs: Int = TIMEOUT_MS): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", agent)
        }

    private fun json(body: String): JSONObject? = try {
        JSONObject(body.removePrefix("&&&START&&&"))
    } catch (e: Exception) {
        null
    }

    private fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { "${URLEncoder.encode(it.first, "UTF-8")}=${URLEncoder.encode(it.second, "UTF-8")}" }

    companion object {
        private const val ACCOUNT_URL = "https://account.xiaomi.com"
        private const val STS_URL = "https://sts.api.io.mi.com/sts"
        private const val TIMEOUT_MS = 15_000
        private const val POLL_TIMEOUT_MS = 10_000
        private const val DEFAULT_TIMEOUT_S = 300L
        private const val MAX_REDIRECTS = 5
        private const val RETRY_MS = 1_000L
        private val SERVERS = listOf("de", "sg", "us", "ru", "tw", "in", "i2", "cn")
        private val ADDRESS = Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}")
        private val KEY = Regex("[0-9a-f]{32}")

        fun isWearable(model: String): Boolean =
            model.startsWith("miwear.") || model.contains(".watch.") || model.contains(".band.")
    }
}
