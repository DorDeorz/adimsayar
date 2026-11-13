package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the update checks found (0.21): the newest release seen on GitHub, the version already announced
 * in a notification and the version whose card was put away with "Later". Kept out of the backup.
 */
class UpdateWatch private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _latest = MutableStateFlow(loadLatest())
    private val _dismissed = MutableStateFlow(prefs.getString(KEY_DISMISSED, null))

    /** The newest release found; it may be the installed one after an update, see [pending]. */
    val latest: StateFlow<UpdateResult.Available?> = _latest.asStateFlow()
    val dismissed: StateFlow<String?> = _dismissed.asStateFlow()

    /** Keeps what a check found. An up-to-date answer clears an older find; a failed check changes nothing. */
    fun record(result: UpdateResult, nowMs: Long = System.currentTimeMillis()) {
        when (result) {
            is UpdateResult.Available -> {
                prefs.edit {
                    putString(KEY_VERSION, result.version)
                    putString(KEY_URL, result.url)
                    putString(KEY_APK, result.apkUrl)
                    putLong(KEY_LAST_CHECK, nowMs)
                }
                _latest.value = result
            }
            UpdateResult.UpToDate, UpdateResult.NoRelease -> {
                prefs.edit {
                    remove(KEY_VERSION)
                    remove(KEY_URL)
                    remove(KEY_APK)
                    putLong(KEY_LAST_CHECK, nowMs)
                }
                _latest.value = null
            }
            UpdateResult.Failed -> Unit
        }
    }

    /** True when the last answer from GitHub is older than [gapMs] (or the clock went back). */
    fun due(gapMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs - prefs.getLong(KEY_LAST_CHECK, 0L) !in 0 until gapMs

    /** True the first time it is called for [version]: each version is announced once. */
    fun firstNotice(version: String): Boolean {
        if (prefs.getString(KEY_NOTIFIED, null) == version) return false
        prefs.edit { putString(KEY_NOTIFIED, version) }
        return true
    }

    /** Hides the Today card of [version]; a later version shows it again. */
    fun dismiss(version: String) {
        prefs.edit { putString(KEY_DISMISSED, version) }
        _dismissed.value = version
    }

    private fun loadLatest(): UpdateResult.Available? {
        val version = prefs.getString(KEY_VERSION, null) ?: return null
        val url = prefs.getString(KEY_URL, null) ?: UpdateChecker.RELEASES_URL
        return UpdateResult.Available(version, url, prefs.getString(KEY_APK, null))
    }

    companion object {
        private const val PREFS = "update_watch"
        private const val KEY_VERSION = "latest_version"
        private const val KEY_URL = "latest_url"
        private const val KEY_APK = "latest_apk"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_NOTIFIED = "notified_version"
        private const val KEY_DISMISSED = "dismissed_version"

        /** The release to show on Today: newer than the installed [current] one and not put away. */
        fun pending(latest: UpdateResult.Available?, dismissed: String?, current: String): UpdateResult.Available? =
            latest?.takeIf { UpdateChecker.isNewer(it.version, current) && it.version != dismissed }

        @Volatile
        private var instance: UpdateWatch? = null

        fun get(context: Context): UpdateWatch = instance ?: synchronized(this) {
            instance ?: UpdateWatch(context.applicationContext).also { instance = it }
        }
    }
}
