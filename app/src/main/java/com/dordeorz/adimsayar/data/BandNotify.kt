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
    private const val KEY_REMINDERS = "band_notify_reminders"
    private const val KEY_LOST = "band_notify_lost"
    private const val KEY_BLOCKED = "band_notify_blocked"
    private const val KEY_QUIET = "band_notify_quiet"
    private const val KEY_QUIET_FROM = "band_notify_quiet_from"
    private const val KEY_QUIET_TO = "band_notify_quiet_to"
    const val DEFAULT_QUIET_FROM = 23 * 60
    const val DEFAULT_QUIET_TO = 7 * 60

    /** FitWren's own reminder channels (goal, streak, weekly summary, weight, water). */
    val REMINDER_CHANNELS = setOf("hedef", "su")

    fun apps(context: Context): Boolean = prefs(context).getBoolean(KEY_APPS, false)
    fun calls(context: Context): Boolean = prefs(context).getBoolean(KEY_CALLS, false)
    fun music(context: Context): Boolean = prefs(context).getBoolean(KEY_MUSIC, false)

    fun setApps(context: Context, on: Boolean) = set(context, KEY_APPS, on)
    fun setCalls(context: Context, on: Boolean) = set(context, KEY_CALLS, on)
    fun setMusic(context: Context, on: Boolean) = set(context, KEY_MUSIC, on)

    fun reminders(context: Context): Boolean = prefs(context).getBoolean(KEY_REMINDERS, false)
    fun setReminders(context: Context, on: Boolean) = set(context, KEY_REMINDERS, on)

    /** Phone warning when the band is out of reach for a minute. */
    fun lostAlert(context: Context): Boolean = prefs(context).getBoolean(KEY_LOST, false)
    fun setLostAlert(context: Context, on: Boolean) = set(context, KEY_LOST, on)

    /** Apps whose notifications stay on the phone. Every other app goes to the band. */
    fun blocked(context: Context): Set<String> = prefs(context).getStringSet(KEY_BLOCKED, emptySet()).orEmpty()

    fun setBlocked(context: Context, packages: Set<String>) = prefs(context).edit { putStringSet(KEY_BLOCKED, HashSet(packages)) }

    fun allowed(context: Context, packageName: String): Boolean = packageName !in blocked(context)

    fun quiet(context: Context): Boolean = prefs(context).getBoolean(KEY_QUIET, false)
    fun setQuiet(context: Context, on: Boolean) = prefs(context).edit { putBoolean(KEY_QUIET, on) }

    /** Quiet hours as minutes after midnight; [to] may be earlier than [from] (overnight). */
    fun quietFrom(context: Context): Int = prefs(context).getInt(KEY_QUIET_FROM, DEFAULT_QUIET_FROM)
    fun quietTo(context: Context): Int = prefs(context).getInt(KEY_QUIET_TO, DEFAULT_QUIET_TO)

    fun setQuietHours(context: Context, from: Int, to: Int) = prefs(context).edit {
        putInt(KEY_QUIET_FROM, from)
        putInt(KEY_QUIET_TO, to)
    }

    /** True inside quiet hours: app notifications and reminders stay on the phone, calls still come. */
    fun quietNow(context: Context, minuteOfDay: Int = java.time.LocalTime.now().let { it.hour * 60 + it.minute }): Boolean {
        if (!quiet(context)) return false
        return inQuietHours(minuteOfDay, quietFrom(context), quietTo(context))
    }

    fun anyOn(context: Context): Boolean = apps(context) || calls(context) || music(context) || reminders(context) || lostAlert(context)

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

/** [minute], [from] and [to] are minutes after midnight; [to] before [from] spans midnight. */
fun inQuietHours(minute: Int, from: Int, to: Int): Boolean = when {
    from == to -> false
    from < to -> minute in from until to
    else -> minute >= from || minute < to
}
