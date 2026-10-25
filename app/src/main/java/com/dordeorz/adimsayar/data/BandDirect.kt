package com.dordeorz.adimsayar.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.band.BandFile
import com.dordeorz.adimsayar.band.BandResult
import com.dordeorz.adimsayar.band.XiaomiBand
import com.dordeorz.adimsayar.band.XiaomiCrypto
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class BandStatus(val enabled: Boolean, val address: String?, val name: String?, val hasKey: Boolean, val lastSyncMs: Long, val lastResult: BandResult?)

object BandDirect {

    private const val SETTINGS_PREFS = "settings"
    private const val KEY_ENABLED = "band_direct"
    private const val KEY_ADDRESS = "band_direct_address"
    private const val KEY_NAME = "band_direct_name"
    private const val KEY_SECRET = "band_direct_key"
    private const val KEY_LAST_SYNC = "band_direct_last_sync"
    private const val KEY_LAST_RESULT = "band_direct_last_result"
    private const val KEY_NEXT_APP = "band_direct_next_app"
    private const val KEY_NEXT_JOB = "band_direct_next_job"
    private const val APP_INTERVAL_MS = 5 * 60 * 1000L
    private const val JOB_INTERVAL_MS = 15 * 60 * 1000L - 60 * 1000L
    private const val MERGE_DAYS = 30L

    private val running = Mutex()

    val CONNECT_PERMISSIONS: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()

    val SCAN_PERMISSIONS: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun status(context: Context): BandStatus {
        val p = prefs(context)
        return BandStatus(
            enabled = p.getBoolean(KEY_ENABLED, false),
            address = p.getString(KEY_ADDRESS, null),
            name = p.getString(KEY_NAME, null),
            hasKey = p.getString(KEY_SECRET, null) != null,
            lastSyncMs = p.getLong(KEY_LAST_SYNC, 0L),
            lastResult = p.getString(KEY_LAST_RESULT, null)?.let { name -> BandResult.entries.firstOrNull { it.name == name } },
        )
    }

    fun granted(context: Context, permissions: Array<String>): Boolean =
        permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun setDevice(context: Context, address: String, name: String?) {
        prefs(context).edit {
            putString(KEY_ADDRESS, address)
            putString(KEY_NAME, name)
            remove(KEY_LAST_RESULT)
        }
    }

    fun setKey(context: Context, text: String): Boolean {
        val key = XiaomiCrypto.parseKey(text) ?: return false
        prefs(context).edit {
            putString(KEY_SECRET, key.joinToString("") { "%02x".format(it) })
            remove(KEY_LAST_RESULT)
        }
        return true
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_ENABLED, enabled)
            remove(KEY_NEXT_APP)
            remove(KEY_NEXT_JOB)
        }
        if (enabled) {
            if (WearableSteps.isEnabled(context)) WearableSteps.setEnabled(context, false)
            remerge(context)
        } else {
            StepRepository.get(context).clearBandDays()
        }
    }

    suspend fun sync(context: Context, source: ReadSource, force: Boolean = false): BandResult? {
        val p = prefs(context)
        val address = p.getString(KEY_ADDRESS, null) ?: return null
        val secret = p.getString(KEY_SECRET, null)?.let { XiaomiCrypto.parseKey(it) } ?: return null
        val foreground = source == ReadSource.App
        val now = System.currentTimeMillis()
        if (!force && now < p.getLong(if (foreground) KEY_NEXT_APP else KEY_NEXT_JOB, 0L)) return null
        if (!granted(context, CONNECT_PERMISSIONS)) return record(context, source, BandResult.NoPermission)
        if (!running.tryLock()) return null
        return try {
            p.edit(commit = true) {
                putLong(KEY_NEXT_APP, now + APP_INTERVAL_MS)
                putLong(KEY_NEXT_JOB, now + JOB_INTERVAL_MS)
            }
            val repository = StepRepository.get(context)
            var files = 0
            val result = XiaomiBand(context, address, secret).sync { file ->
                repository.saveBandHours(file.id.epochSeconds, hourly(file))
                files++
                true
            }
            if (isEnabled(context) && files > 0) remerge(context)
            if (result == BandResult.Ok) p.edit { putLong(KEY_LAST_SYNC, System.currentTimeMillis()) }
            record(context, source, result, files)
        } finally {
            running.unlock()
        }
    }

    private suspend fun remerge(context: Context) {
        val from = LocalDate.now().minusDays(MERGE_DAYS)
        val band = StepRepository.get(context).loadBandHours(from)
        if (band.isEmpty()) return
        StepRepository.get(context).replaceBandDays(WearableSteps.merged(context, band, from))
    }

    private fun hourly(file: BandFile): Map<LocalDateTime, Long> {
        val zone = ZoneId.systemDefault()
        val hours = HashMap<LocalDateTime, Long>()
        for (minute in file.minutes) {
            if (!minute.worn) continue
            val hour = Instant.ofEpochSecond(minute.epochSeconds).atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
            hours[hour] = (hours[hour] ?: 0L) + minute.steps
        }
        return hours
    }

    private fun record(context: Context, source: ReadSource, result: BandResult, files: Int = 0): BandResult {
        prefs(context).edit { putString(KEY_LAST_RESULT, result.name) }
        val text = if (result == BandResult.Ok) {
            context.getString(R.string.log_band_synced, files)
        } else {
            context.getString(R.string.log_band_failed, context.getString(resultText(result)))
        }
        ReadingLog.get(context).note(source, text)
        return result
    }

    fun resultText(result: BandResult): Int = when (result) {
        BandResult.Ok -> R.string.band_result_ok
        BandResult.BluetoothOff -> R.string.band_result_bluetooth_off
        BandResult.NoPermission -> R.string.band_result_permission
        BandResult.ConnectFailed -> R.string.band_result_connect
        BandResult.Unsupported -> R.string.band_result_unsupported
        BandResult.AuthFailed -> R.string.band_result_auth
        BandResult.Timeout -> R.string.band_result_timeout
    }

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}
