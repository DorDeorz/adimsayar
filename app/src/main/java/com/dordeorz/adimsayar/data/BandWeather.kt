package com.dordeorz.adimsayar.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.band.BandWeatherReport
import com.dordeorz.adimsayar.band.XiaomiBand
import com.dordeorz.adimsayar.band.XiaomiWeather
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class WeatherPlace(val name: String, val detail: String, val latitude: Double, val longitude: Double)

/**
 * Fills the band's weather screen from Open-Meteo (no account or key). Only the chosen place's
 * coordinates leave the phone, and only while the band is connected and weather is turned on.
 */
object BandWeather {

    private const val SETTINGS_PREFS = "settings"
    private const val KEY_ON = "band_weather"
    private const val KEY_PLACE = "band_weather_place"
    private const val KEY_LAT = "band_weather_lat"
    private const val KEY_LON = "band_weather_lon"
    private const val KEY_JSON = "band_weather_json"
    private const val KEY_FETCHED = "band_weather_fetched"
    private const val REFRESH_MS = 60 * 60 * 1000L
    private const val TIMEOUT_MS = 15_000
    private const val SEARCH_RESULTS = 8
    private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
    private const val SEARCH_URL = "https://geocoding-api.open-meteo.com/v1/search"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    fun place(context: Context): WeatherPlace? {
        val p = prefs(context)
        val name = p.getString(KEY_PLACE, null) ?: return null
        return WeatherPlace(name, "", p.getFloat(KEY_LAT, 0f).toDouble(), p.getFloat(KEY_LON, 0f).toDouble())
    }

    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean(KEY_ON, on) }
        if (on) refresh(context, force = true)
    }

    fun setPlace(context: Context, place: WeatherPlace) {
        prefs(context).edit {
            putString(KEY_PLACE, place.name)
            putFloat(KEY_LAT, place.latitude.toFloat())
            putFloat(KEY_LON, place.longitude.toFloat())
            remove(KEY_JSON)
            remove(KEY_FETCHED)
        }
        refresh(context, force = true)
    }

    /** After each band sync: fetches at most once an hour and sends the weather to the band. */
    fun refresh(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (!isOn(app) || !BandLive.connected) return
        scope.launch {
            val p = prefs(app)
            val place = place(app) ?: return@launch
            val stale = System.currentTimeMillis() - p.getLong(KEY_FETCHED, 0L) > REFRESH_MS
            val json = if (force || stale || p.getString(KEY_JSON, null) == null) {
                fetch(place)?.also { text ->
                    p.edit {
                        putString(KEY_JSON, text)
                        putLong(KEY_FETCHED, System.currentTimeMillis())
                    }
                }
            } else {
                null
            } ?: p.getString(KEY_JSON, null) ?: return@launch
            val report = runCatching { parse(place.name, JSONObject(json)) }
                .onFailure { Log.w(XiaomiBand.TAG, "weather parse: $it") }
                .getOrNull() ?: return@launch
            Log.i(XiaomiBand.TAG, "weather ${report.place} ${report.temperature}° code ${report.condition}")
            BandLive.weather(report)
        }
    }

    suspend fun search(query: String): List<WeatherPlace> = withContext(Dispatchers.IO) {
        val language = Locales.current.language
        val url = "$SEARCH_URL?name=${URLEncoder.encode(query.trim(), "UTF-8")}&count=$SEARCH_RESULTS&language=$language&format=json"
        val text = get(url) ?: return@withContext emptyList()
        runCatching {
            val results = JSONObject(text).optJSONArray("results") ?: return@runCatching emptyList()
            (0 until results.length()).mapNotNull { i ->
                val r = results.optJSONObject(i) ?: return@mapNotNull null
                WeatherPlace(
                    name = r.optString("name"),
                    detail = listOf(r.optString("admin1"), r.optString("country")).filter { it.isNotBlank() }.joinToString(", "),
                    latitude = r.optDouble("latitude"),
                    longitude = r.optDouble("longitude"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The phone's last known position as a place; asked while the app is open. */
    @SuppressLint("MissingPermission")
    suspend fun currentPlace(context: Context, fallbackName: String): WeatherPlace? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        val location = manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return@withContext null
        val name = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locales.current).getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull() ?: fallbackName
        WeatherPlace(name, "", location.latitude, location.longitude)
    }

    private fun fetch(place: WeatherPlace): String? {
        val url = FORECAST_URL +
            "?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,surface_pressure" +
            "&hourly=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max" +
            "&timezone=auto&forecast_days=7&forecast_hours=24"
        return get(url)
    }

    private fun get(url: String): String? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        Log.w(XiaomiBand.TAG, "weather fetch: $e")
        null
    }

    private fun parse(place: String, json: JSONObject): BandWeatherReport {
        val offset = ZoneOffset.ofTotalSeconds(json.optInt("utc_offset_seconds"))
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val hourly = json.getJSONObject("hourly")
        fun iso(local: String): String = LocalDateTime.parse(local).atOffset(offset).format(ISO)
        val days = (0 until daily.getJSONArray("time").length()).map { i ->
            BandWeatherReport.Day(
                min = daily.getJSONArray("temperature_2m_min").optDouble(i).roundToInt(),
                max = daily.getJSONArray("temperature_2m_max").optDouble(i).roundToInt(),
                condition = XiaomiWeather.fromWmo(daily.getJSONArray("weather_code").optInt(i)),
                sunriseIso = iso(daily.getJSONArray("sunrise").getString(i)),
                sunsetIso = iso(daily.getJSONArray("sunset").getString(i)),
            )
        }
        val hours = (0 until hourly.getJSONArray("time").length()).map { i ->
            BandWeatherReport.Hour(
                temperature = hourly.getJSONArray("temperature_2m").optDouble(i).roundToInt(),
                condition = XiaomiWeather.fromWmo(hourly.getJSONArray("weather_code").optInt(i)),
                windBeaufort = XiaomiWeather.beaufort(hourly.getJSONArray("wind_speed_10m").optDouble(i)),
                windDirection = hourly.getJSONArray("wind_direction_10m").optDouble(i).roundToInt(),
            )
        }
        return BandWeatherReport(
            place = place,
            publishedIso = Instant.now().atOffset(offset).format(ISO),
            condition = XiaomiWeather.fromWmo(current.optInt("weather_code")),
            temperature = current.optDouble("temperature_2m").roundToInt(),
            humidity = current.optDouble("relative_humidity_2m").roundToInt(),
            windBeaufort = XiaomiWeather.beaufort(current.optDouble("wind_speed_10m")),
            windDirection = current.optDouble("wind_direction_10m").roundToInt(),
            uv = daily.optJSONArray("uv_index_max")?.optDouble(0)?.takeIf { !it.isNaN() }?.roundToInt() ?: 0,
            pressureHpa = current.optDouble("surface_pressure").toFloat(),
            days = days,
            hours = hours,
        )
    }

    private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}
