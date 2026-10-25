package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.core.content.edit
import androidx.glance.appwidget.updateAll
import com.dordeorz.adimsayar.background.BandAlerts
import com.dordeorz.adimsayar.widget.BandWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The band's last reported battery. [charging] is null when the band does not say. */
class BandBatteryState(val level: Int, val charging: Boolean?, val atMs: Long)

/** Keeps the band's battery level and warns once per discharge when it runs low. */
object BandBattery {

    private const val SETTINGS_PREFS = "settings"
    private const val KEY_LEVEL = "band_battery_level"
    private const val KEY_CHARGING = "band_battery_charging"
    private const val KEY_TIME = "band_battery_time"
    private const val KEY_ALERT = "band_battery_alert"
    private const val KEY_WARNED = "band_battery_warned"
    const val LOW_LEVEL = 20
    private const val REARM_LEVEL = 30

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow<BandBatteryState?>(null)

    fun observe(context: Context): StateFlow<BandBatteryState?> {
        if (state.value == null) state.value = read(context)
        return state.asStateFlow()
    }

    fun read(context: Context): BandBatteryState? {
        val p = prefs(context)
        val level = p.getInt(KEY_LEVEL, -1).takeIf { it >= 0 } ?: return null
        val charging = if (p.contains(KEY_CHARGING)) p.getBoolean(KEY_CHARGING, false) else null
        return BandBatteryState(level, charging, p.getLong(KEY_TIME, 0L))
    }

    fun alertOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ALERT, true)

    fun setAlert(context: Context, on: Boolean) = prefs(context).edit { putBoolean(KEY_ALERT, on) }

    fun save(context: Context, level: Int, charging: Boolean?) {
        val app = context.applicationContext
        val p = prefs(app)
        val warned = p.getBoolean(KEY_WARNED, false)
        val low = level <= LOW_LEVEL && charging != true
        val warnNow = low && !warned && alertOn(app)
        p.edit {
            putInt(KEY_LEVEL, level)
            if (charging == null) remove(KEY_CHARGING) else putBoolean(KEY_CHARGING, charging)
            putLong(KEY_TIME, System.currentTimeMillis())
            when {
                warnNow -> putBoolean(KEY_WARNED, true)
                level >= REARM_LEVEL || charging == true -> putBoolean(KEY_WARNED, false)
            }
        }
        val previous = state.value
        state.value = BandBatteryState(level, charging, System.currentTimeMillis())
        if (warnNow) BandAlerts.batteryLow(app, level)
        if (charging == true || level >= REARM_LEVEL) BandAlerts.cancelBatteryLow(app)
        if (previous?.level != level || previous.charging != charging) {
            scope.launch { runCatching { BandWidget().updateAll(app) } }
        }
    }

    /** Forgets the level, for a new band. */
    fun clear(context: Context) {
        prefs(context).edit {
            remove(KEY_LEVEL)
            remove(KEY_CHARGING)
            remove(KEY_TIME)
            remove(KEY_WARNED)
        }
        state.value = null
    }

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}
