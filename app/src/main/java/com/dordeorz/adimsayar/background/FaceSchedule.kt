package com.dordeorz.adimsayar.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.dordeorz.adimsayar.band.XiaomiBand
import com.dordeorz.adimsayar.data.BandLive
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Changes the band's face by the time of day: the day face from [Plan.dayStart], the night face
 * from [Plan.nightStart] (minutes after midnight). Needs the band connected at that moment; when
 * it is not, it tries again a little later.
 */
object FaceSchedule {

    data class Plan(
        val on: Boolean = false,
        val dayId: String? = null,
        val nightId: String? = null,
        val dayStart: Int = 7 * 60,
        val nightStart: Int = 20 * 60,
    )

    private const val PREFS = "settings"
    private const val KEY_ON = "face_schedule_on"
    private const val KEY_DAY = "face_schedule_day"
    private const val KEY_NIGHT = "face_schedule_night"
    private const val KEY_DAY_START = "face_schedule_day_start"
    private const val KEY_NIGHT_START = "face_schedule_night_start"
    private const val REQUEST = 0x5A_D1_0F_01
    private const val RETRY_MS = 10 * 60 * 1000L

    fun plan(context: Context): Plan {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val default = Plan()
        return Plan(
            on = p.getBoolean(KEY_ON, false),
            dayId = p.getString(KEY_DAY, null),
            nightId = p.getString(KEY_NIGHT, null),
            dayStart = p.getInt(KEY_DAY_START, default.dayStart),
            nightStart = p.getInt(KEY_NIGHT_START, default.nightStart),
        )
    }

    /** Saves [plan], sets the face it wants now and waits for the next change. */
    fun save(context: Context, plan: Plan) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ON, plan.on)
            putString(KEY_DAY, plan.dayId)
            putString(KEY_NIGHT, plan.nightId)
            putInt(KEY_DAY_START, plan.dayStart)
            putInt(KEY_NIGHT_START, plan.nightStart)
        }
        if (plan.on) apply(context) else schedule(context)
    }

    /** The face [plan] wants at [minute] after midnight. */
    fun faceAt(plan: Plan, minute: Int): String? {
        val day = if (plan.dayStart <= plan.nightStart) {
            minute >= plan.dayStart && minute < plan.nightStart
        } else {
            minute >= plan.dayStart || minute < plan.nightStart
        }
        return if (day) plan.dayId else plan.nightId
    }

    /** Sets the face for now; when the band is not connected, tries again later. */
    fun apply(context: Context) {
        val plan = plan(context)
        if (!plan.on) return schedule(context)
        val now = LocalTime.now()
        val id = faceAt(plan, now.hour * 60 + now.minute)
        val done = id == null || BandLive.setWatchface(id)
        Log.i(XiaomiBand.TAG, "face schedule: $id ${if (done) "set" else "band not connected"}")
        schedule(context, retry = !done)
    }

    /** Waits for the next change of face, or [RETRY_MS] when the last one did not reach the band. */
    fun schedule(context: Context, retry: Boolean = false) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, SystemEventReceiver::class.java).setAction(SystemEventReceiver.ACTION_FACE)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val plan = plan(context)
        if (!plan.on || (plan.dayId == null && plan.nightId == null)) {
            alarms.cancel(pending)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val next = listOf(today, today.plusDays(1)).flatMap { date ->
            listOf(plan.dayStart, plan.nightStart).map { date.atTime(it / 60, it % 60).atZone(zone).toInstant().toEpochMilli() }
        }.filter { it > now }.minOrNull() ?: return
        val at = if (retry) minOf(next, now + RETRY_MS) else next
        Schedules.setAlarm(alarms, at, pending)
    }
}
