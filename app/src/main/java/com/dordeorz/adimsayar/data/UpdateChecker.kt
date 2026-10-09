package com.dordeorz.adimsayar.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateResult {
    data class Available(val version: String, val url: String) : UpdateResult
    data object UpToDate : UpdateResult
    data object NoRelease : UpdateResult
    data object Failed : UpdateResult
}

object UpdateChecker {

    const val GITHUB_URL = "https://github.com/DorDeorz/adimsayar"
    const val RELEASES_URL = "$GITHUB_URL/releases"
    private const val LATEST_API = "https://api.github.com/repos/DorDeorz/adimsayar/releases/latest"
    private const val TIMEOUT_MS = 10_000

    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(LATEST_API).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> Unit
                    HttpURLConnection.HTTP_NOT_FOUND -> return@runCatching UpdateResult.NoRelease
                    else -> return@runCatching UpdateResult.Failed
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val latest = json.getString("tag_name").removePrefix("v")
                if (isNewer(latest, currentVersion)) {
                    UpdateResult.Available(latest, json.optString("html_url", RELEASES_URL))
                } else {
                    UpdateResult.UpToDate
                }
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(UpdateResult.Failed)
    }

    fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.substringBefore('-').split('.').map { it.trim().toIntOrNull() ?: 0 }
}
