package com.dordeorz.adimsayar.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.dordeorz.adimsayar.band.BandFamily

/** Which phone features go to the band over the always-open connection. */
object BandNotify {

    private const val SETTINGS_PREFS = "settings"
    private const val KEY_APPS = "band_notify_apps"
    private const val KEY_CALLS = "band_notify_calls"
    private const val KEY_MUSIC = "band_notify_music"

    fun apps(context: Context): Boolean = prefs(context).getBoolean(KEY_APPS, false)
    fun calls(context: Context): Boolean = prefs(context).getBoolean(KEY_CALLS, false)
    fun music(context: Context): Boolean = prefs(context).getBoolean(KEY_MUSIC, false)

    fun setApps(context: Context, on: Boolean) = set(context, KEY_APPS, on)
    fun setCalls(context: Context, on: Boolean) = set(context, KEY_CALLS, on)
    fun setMusic(context: Context, on: Boolean) = set(context, KEY_MUSIC, on)

    fun anyOn(context: Context): Boolean = apps(context) || calls(context) || music(context)

    /** Notification access: Android keeps FitWren running while it has it, so no ongoing notification is needed. */
    fun hasAccess(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Rejecting a call from the band needs ANSWER_PHONE_CALLS (Android 9+). */
    val CALL_PERMISSIONS: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) arrayOf(Manifest.permission.ANSWER_PHONE_CALLS) else emptyArray()

    fun canRejectCalls(context: Context): Boolean =
        CALL_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Only the Xiaomi family supports notifications for now. */
    fun supported(context: Context): Boolean = (BandDirect.model(context)?.family ?: BandFamily.Xiaomi) == BandFamily.Xiaomi

    /** True when the connection should stay open. */
    fun wanted(context: Context): Boolean =
        BandDirect.isEnabled(context) && anyOn(context) && hasAccess(context) && supported(context) &&
            BandDirect.device(context) != null && BandDirect.granted(context, BandDirect.CONNECT_PERMISSIONS)

    private fun set(context: Context, key: String, on: Boolean) {
        prefs(context).edit { putBoolean(key, on) }
        BandLive.refresh(context)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}
