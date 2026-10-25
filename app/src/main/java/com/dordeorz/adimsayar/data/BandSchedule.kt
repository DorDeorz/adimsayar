package com.dordeorz.adimsayar.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.dordeorz.adimsayar.band.BandAlarm
import com.dordeorz.adimsayar.band.BandCalendarEvent
import com.dordeorz.adimsayar.band.XiaomiBand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The band's alarms (kept on the band, edited from FitWren) and the phone calendar sent to it. */
object BandSchedule {

    private const val SETTINGS_PREFS = "settings"
    private const val KEY_CALENDAR = "band_calendar"
    private const val CALENDAR_DAYS = 7L
    private const val MAX_EVENTS = 50

    class Alarms(val list: List<BandAlarm>, val max: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val alarms = MutableStateFlow<Alarms?>(null)

    @Volatile
    private var lastCalendar: Int? = null

    fun alarms(): StateFlow<Alarms?> = alarms.asStateFlow()

    fun onAlarms(list: List<BandAlarm>, max: Int) {
        Log.i(XiaomiBand.TAG, "alarms ${list.size}/$max")
        alarms.value = Alarms(list, max)
    }

    /** False when the band is not connected. */
    fun save(alarm: BandAlarm): Boolean = BandLive.saveAlarm(alarm)

    fun delete(alarm: BandAlarm): Boolean = BandLive.deleteAlarm(alarm.id)

    fun calendarOn(context: Context): Boolean = prefs(context).getBoolean(KEY_CALENDAR, false)

    fun setCalendar(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean(KEY_CALENDAR, on) }
        lastCalendar = null
        syncCalendar(context)
    }

    fun hasCalendarPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Sends the next week's events after each band sync, only when they changed. */
    fun syncCalendar(context: Context) {
        val app = context.applicationContext
        if (!BandLive.connected) return
        scope.launch {
            val events = if (calendarOn(app) && hasCalendarPermission(app)) runCatching { events(app) }.getOrNull() ?: return@launch else null
            val hash = events?.map { listOf(it.title, it.location, it.start, it.end, it.allDay, it.remindMinutes) }.hashCode()
            if (hash == lastCalendar) return@launch
            // Turning off is sent once; after that an unchanged "off" needs no message.
            if (events == null && lastCalendar == null && !calendarOn(app)) {
                lastCalendar = hash
                return@launch
            }
            if (BandLive.calendar(events)) {
                lastCalendar = hash
                Log.i(XiaomiBand.TAG, "calendar ${events?.size ?: "off"}")
            }
        }
    }

    fun onDisconnected() {
        lastCalendar = null
        alarms.value = null
    }

    private fun events(context: Context): List<BandCalendarEvent> {
        val now = System.currentTimeMillis()
        val until = now + CALENDAR_DAYS * 24 * 60 * 60 * 1000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, until)
        }.build()
        val columns = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_ID,
        )
        val list = ArrayList<Pair<BandCalendarEvent, Long>>()
        context.contentResolver.query(uri, columns, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && list.size < MAX_EVENTS) {
                val event = BandCalendarEvent(
                    title = c.getString(0).orEmpty(),
                    location = c.getString(1).orEmpty(),
                    start = c.getLong(2) / 1000,
                    end = c.getLong(3) / 1000,
                    allDay = c.getInt(4) == 1,
                    remindMinutes = 0,
                )
                list += event to c.getLong(5)
            }
        }
        return list.map { (event, id) -> withReminder(context, event, id) }
    }

    private fun withReminder(context: Context, event: BandCalendarEvent, eventId: Long): BandCalendarEvent {
        val minutes = runCatching {
            CalendarContract.Reminders.query(context.contentResolver, eventId, arrayOf(CalendarContract.Reminders.MINUTES))?.use { c ->
                if (c.moveToFirst()) c.getInt(0) else null
            }
        }.getOrNull() ?: return event
        return BandCalendarEvent(event.title, event.location, event.start, event.end, event.allDay, minutes.coerceAtLeast(0))
    }

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}
