package com.dordeorz.adimsayar.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.MainActivity
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.band.BandNotice
import com.dordeorz.adimsayar.data.BandLive
import com.dordeorz.adimsayar.data.DayHours
import com.dordeorz.adimsayar.data.ExtraSettings
import com.dordeorz.adimsayar.data.ExtrasStore
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.data.WearableHealth
import com.dordeorz.adimsayar.data.weekStartOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** Reminders added in 0.17: morning summary, records, sitting still, walk plan, monthly report, band celebration. */
object ExtraReminders {

    private const val MORNING_ID = 20
    private const val RECORD_DAY_ID = 21
    private const val RECORD_WEEK_ID = 22
    private const val SEDENTARY_ID = 23
    private const val WALK_PLAN_ID = 24
    private const val MONTH_REPORT_ID = 25
    private const val MORNING_REQUEST = 10
    private const val SEDENTARY_REQUEST = 11
    private const val WALK_PLAN_REQUEST = 12
    private const val MONTH_REPORT_REQUEST = 13
    private const val PREFS = "extra_reminders"
    private const val KEY_RECORD_DAY = "record_day"
    private const val KEY_RECORD_WEEK = "record_week"
    private const val KEY_CELEBRATED = "band_celebrated"
    private const val SEDENTARY_FIRST_HOUR = 10
    private const val SEDENTARY_LAST_HOUR = 20
    const val SEDENTARY_STEPS = 250L
    const val WALK_DONE_STEPS = 1_500L
    private const val MIN_RECORD_DAYS = 7
    private const val MONTH_REPORT_HOUR = 10
    private const val BAND_NOTICE_ID = 0x7F17

    fun scheduleAll(context: Context) {
        val extras = ExtrasStore.get(context).extras.value
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        schedule(context, SystemEventReceiver.ACTION_MORNING, MORNING_REQUEST, extras.morningSummary) {
            val today = now.toLocalDate().atTime(extras.morningHour, 0)
            if (today.isAfter(now)) today else today.plusDays(1)
        }
        schedule(context, SystemEventReceiver.ACTION_SEDENTARY, SEDENTARY_REQUEST, extras.sedentary) {
            val next = now.withMinute(0).withSecond(0).withNano(0).plusHours(1)
            when {
                next.hour < SEDENTARY_FIRST_HOUR -> next.withHour(SEDENTARY_FIRST_HOUR)
                next.hour > SEDENTARY_LAST_HOUR -> next.toLocalDate().plusDays(1).atTime(SEDENTARY_FIRST_HOUR, 0)
                else -> next
            }
        }
        schedule(context, SystemEventReceiver.ACTION_WALK_PLAN, WALK_PLAN_REQUEST, extras.walkPlan.isNotEmpty()) {
            nextPlanned(extras, now)
        }
        schedule(context, SystemEventReceiver.ACTION_MONTH_REPORT, MONTH_REPORT_REQUEST, extras.monthReportReminder) {
            val thisMonth = now.toLocalDate().withDayOfMonth(1).atTime(MONTH_REPORT_HOUR, 0)
            if (thisMonth.isAfter(now)) thisMonth else thisMonth.plusMonths(1)
        }
    }

    fun nextPlanned(extras: ExtraSettings, now: LocalDateTime): LocalDateTime? =
        (0L..7L).asSequence().mapNotNull { offset ->
            val date = now.toLocalDate().plusDays(offset)
            extras.walkPlan[date.dayOfWeek]?.let { minute -> date.atTime(minute / 60, minute % 60) }
        }.firstOrNull { it.isAfter(now) }

    private fun schedule(context: Context, action: String, request: Int, on: Boolean, next: () -> LocalDateTime?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            request,
            Intent(context, SystemEventReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = if (on) next() else null
        if (at == null) {
            alarms.cancel(pending)
            return
        }
        Schedules.setAlarm(alarms, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pending)
    }

    private fun quiet(context: Context): Boolean = SettingsStore.get(context).settings.value.inVacation(LocalDate.now())

    suspend fun morning(context: Context) {
        val extras = ExtrasStore.get(context).extras.value
        if (!extras.morningSummary || quiet(context)) return
        val today = LocalDate.now()
        val settings = SettingsStore.get(context).settings.value
        val history = StepRepository.get(context).loadHistory()
        val strings = Locales.wrap(context)
        val lines = ArrayList<String>()
        lines += strings.getString(R.string.morning_yesterday, Reminders.format(history[today.minusDays(1)] ?: 0L))
        if (WearableHealth.isEnabled(context)) {
            WearableHealth.snapshot(context)?.sleepMinutes?.takeIf { it > 0L }?.let { sleep ->
                lines += strings.getString(R.string.morning_sleep, sleep / 60, sleep % 60)
            }
        }
        lines += strings.getString(R.string.morning_goal, Reminders.format(settings.goalOn(today)))
        Reminders.notify(strings, MORNING_ID, strings.getString(R.string.morning_title), lines.joinToString("\n"))
    }

    /** Checks for a new best day or week; each is announced once. */
    suspend fun recordCheck(context: Context) {
        if (!ExtrasStore.get(context).extras.value.recordNotify) return
        val today = LocalDate.now()
        val settings = SettingsStore.get(context).settings.value
        val history = StepRepository.get(context).loadHistory()
        if (history.count { it.key < today && it.value > 0L } < MIN_RECORD_DAYS) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val steps = history[today] ?: 0L
        val bestBefore = history.filterKeys { it < today }.values.maxOrNull() ?: 0L
        val strings = Locales.wrap(context)
        if (steps > bestBefore && bestBefore > 0L && prefs.getString(KEY_RECORD_DAY, null) != today.toString()) {
            prefs.edit { putString(KEY_RECORD_DAY, today.toString()) }
            Reminders.notify(
                strings,
                RECORD_DAY_ID,
                strings.getString(R.string.record_day_title),
                strings.getString(R.string.record_day_text, Reminders.format(steps), Reminders.format(bestBefore)),
            )
        }
        val weekStart = weekStartOf(today, settings.weekStart)
        val week = history.filterKeys { it >= weekStart && it <= today }.values.sum()
        val bestWeek = history.filterKeys { it < weekStart }.entries
            .groupBy({ weekStartOf(it.key, settings.weekStart) }, { it.value })
            .values.maxOfOrNull { it.sum() } ?: 0L
        if (week > bestWeek && bestWeek > 0L && prefs.getString(KEY_RECORD_WEEK, null) != weekStart.toString()) {
            prefs.edit { putString(KEY_RECORD_WEEK, weekStart.toString()) }
            Reminders.notify(
                strings,
                RECORD_WEEK_ID,
                strings.getString(R.string.record_week_title),
                strings.getString(R.string.record_week_text, Reminders.format(week), Reminders.format(bestWeek)),
            )
        }
    }

    suspend fun sedentary(context: Context) {
        if (!ExtrasStore.get(context).extras.value.sedentary || quiet(context)) return
        val now = LocalDateTime.now()
        val last = now.minusHours(1)
        if (last.hour < SEDENTARY_FIRST_HOUR - 1 || now.hour > SEDENTARY_LAST_HOUR) return
        val steps = DayHours.day(context, last.toLocalDate())[last.hour]
        if (steps >= SEDENTARY_STEPS) return
        val strings = Locales.wrap(context)
        Reminders.notify(strings, SEDENTARY_ID, strings.getString(R.string.sedentary_title), strings.getString(R.string.sedentary_text, steps))
    }

    fun walkPlan(context: Context) {
        val extras = ExtrasStore.get(context).extras.value
        val minute = extras.walkPlan[LocalDate.now().dayOfWeek] ?: return
        if (quiet(context)) return
        val strings = Locales.wrap(context)
        val time = LocalTime.of(minute / 60, minute % 60)
        Reminders.notify(strings, WALK_PLAN_ID, strings.getString(R.string.walk_plan_title), strings.getString(R.string.walk_plan_text, time.toString()))
    }

    fun monthReport(context: Context) {
        if (!ExtrasStore.get(context).extras.value.monthReportReminder) return
        if (LocalDate.now().dayOfMonth != 1) return
        val strings = Locales.wrap(context)
        val month = YearMonth.now().minusMonths(1)
        val name = month.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, Locales.current)
        Reminders.notify(
            strings,
            MONTH_REPORT_ID,
            strings.getString(R.string.month_report_title, name),
            strings.getString(R.string.month_report_text),
            MainActivity.TAB_HISTORY,
        )
    }

    /** True when the plan time of [date] was followed by a walk: enough steps in that hour or the next. */
    fun planDone(hours: LongArray, minute: Int): Boolean {
        val hour = minute / 60
        return hours[hour] >= WALK_DONE_STEPS || (hour + 1 < hours.size && hours[hour + 1] >= WALK_DONE_STEPS)
    }

    /** The band vibrates with a bird message once a day when the goal is reached. */
    fun onTodaySteps(context: Context, steps: Long, goal: Long) {
        if (steps < goal || !ExtrasStore.get(context).extras.value.bandCelebrate || !BandLive.connected) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_CELEBRATED, null) == today) return
        prefs.edit { putString(KEY_CELEBRATED, today) }
        val strings = Locales.wrap(context)
        BandLive.notify(
            BandNotice(
                BAND_NOTICE_ID,
                context.packageName,
                strings.getString(R.string.app_name),
                strings.getString(R.string.band_celebrate_title),
                strings.getString(R.string.band_celebrate_text, Reminders.format(steps)),
                System.currentTimeMillis(),
                null,
            ),
        )
    }
}
