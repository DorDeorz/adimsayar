package com.dordeorz.adimsayar.background

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.MainActivity
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.AchievementMath
import com.dordeorz.adimsayar.data.AppSettings
import com.dordeorz.adimsayar.data.BandPeriod
import com.dordeorz.adimsayar.data.HealthMath
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.data.StreakFreezes
import com.dordeorz.adimsayar.data.Water
import com.dordeorz.adimsayar.widget.Widgets
import com.dordeorz.adimsayar.data.WearableHealth
import com.dordeorz.adimsayar.data.weekStartOf
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

object Reminders {

    private const val CHANNEL_ID = "hedef"
    private const val WATER_CHANNEL_ID = "su"
    private const val WATER_NOTIFICATION_ID = 15
    private const val WATER_REQUEST = 3
    private const val WATER_ADD_REQUEST = 4
    private const val WATER_FIRST_HOUR = 9
    private const val WATER_LAST_HOUR = 21
    private const val GOAL_NOTIFICATION_ID = 10
    private const val STREAK_NOTIFICATION_ID = 11
    private const val NEAR_NOTIFICATION_ID = 12
    private const val WEEK_NOTIFICATION_ID = 13
    private const val WEIGHT_NOTIFICATION_ID = 14
    private const val NEAR_GOAL_PERCENT = 80
    private const val PREFS = "reminders"
    private const val KEY_GOAL_DATE = "goal_notified_date"
    private const val KEY_NEAR_DATE = "near_notified_date"
    private const val EVENING_REQUEST = 1
    private const val WEIGHT_REQUEST = 2
    private const val HEALTH_DAYS = 7L
    private val EVENING_TIME: LocalTime = LocalTime.of(20, 0)
    private val WEIGHT_TIME: LocalTime = LocalTime.of(9, 0)

    fun onTodaySteps(context: Context, steps: Long) {
        val settings = SettingsStore.get(context).settings.value
        val goal = settings.dailyGoal
        when {
            settings.goalNotification && steps >= goal -> notifyOncePerDay(context, KEY_GOAL_DATE, GOAL_NOTIFICATION_ID) { strings ->
                strings.getString(R.string.notify_goal_title) to strings.getString(R.string.notify_goal_text, format(steps))
            }
            settings.nearGoalNotification && steps < goal && steps * 100 >= goal * NEAR_GOAL_PERCENT ->
                notifyOncePerDay(context, KEY_NEAR_DATE, NEAR_NOTIFICATION_ID) { strings ->
                    strings.getString(R.string.notify_near_title) to strings.getString(R.string.notify_near_text, format(goal - steps))
                }
        }
    }

    suspend fun eveningCheck(context: Context) {
        val settings = SettingsStore.get(context).settings.value
        if (!settings.streakReminder && !settings.weeklySummary) return
        val today = LocalDate.now()
        val history = StepRepository.get(context).loadHistory()
        val strings = Locales.wrap(context)
        if (settings.streakReminder) streakCheck(strings, history, StreakFreezes.load(context), today, settings)
        if (settings.weeklySummary && today.dayOfWeek == settings.weekStart.minus(1)) {
            val health = if (WearableHealth.isEnabled(context)) healthLines(strings, WearableHealth.period(context, today, HEALTH_DAYS)) else emptyList()
            weeklySummary(strings, history, today, settings, health)
        }
    }

    fun weightCheck(context: Context) {
        val settings = SettingsStore.get(context).settings.value
        if (!settings.weightReminder) return
        val strings = Locales.wrap(context)
        notify(
            strings,
            WEIGHT_NOTIFICATION_ID,
            strings.getString(R.string.notify_weight_title),
            strings.getString(R.string.notify_weight_text),
            MainActivity.TAB_HEALTH,
        )
    }

    suspend fun waterCheck(context: Context) {
        val settings = SettingsStore.get(context).settings.value
        if (!settings.waterReminder) return
        val hour = LocalTime.now().hour
        if (hour < WATER_FIRST_HOUR || hour > WATER_LAST_HOUR) return
        val drunk = Water.today(context)
        if (drunk < settings.waterGoalMl) notifyWater(context, drunk, settings, alert = true)
    }

    suspend fun addWater(context: Context) {
        val settings = SettingsStore.get(context).settings.value
        val drunk = Water.add(context, LocalDate.now(), settings.glassMl)
        if (drunk >= settings.waterGoalMl) {
            NotificationManagerCompat.from(context).cancel(WATER_NOTIFICATION_ID)
        } else {
            notifyWater(context, drunk, settings, alert = false)
        }
        Widgets.updateWater(context)
    }

    fun onWaterChanged(context: Context, drunk: Int) {
        val settings = SettingsStore.get(context).settings.value
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.activeNotifications.none { it.id == WATER_NOTIFICATION_ID }) return
        if (drunk >= settings.waterGoalMl) {
            manager.cancel(WATER_NOTIFICATION_ID)
        } else {
            notifyWater(context, drunk, settings, alert = false)
        }
    }

    private fun notifyWater(context: Context, drunk: Int, settings: AppSettings, alert: Boolean) {
        val strings = Locales.wrap(context)
        val left = settings.waterGoalMl - drunk
        val glasses = (left + settings.glassMl - 1) / settings.glassMl
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(WATER_CHANNEL_ID, strings.getString(R.string.water_channel), NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            context,
            WATER_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val add = PendingIntent.getBroadcast(
            context,
            WATER_ADD_REQUEST,
            Intent(context, SystemEventReceiver::class.java).setAction(SystemEventReceiver.ACTION_WATER_ADD),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = strings.getString(R.string.notify_water_text, format(drunk.toLong()), format(settings.waterGoalMl.toLong()), format(left.toLong()), glasses)
        val notification = NotificationCompat.Builder(context, WATER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.getString(R.string.notify_water_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, strings.getString(R.string.water_add_glass, settings.glassMl), add)
            .setOnlyAlertOnce(!alert)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(WATER_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            return
        }
    }

    fun scheduleWater(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, SystemEventReceiver::class.java).setAction(SystemEventReceiver.ACTION_WATER)
        val pending = PendingIntent.getBroadcast(
            context,
            WATER_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val settings = SettingsStore.get(context).settings.value
        if (!settings.waterReminder) {
            alarms.cancel(pending)
            NotificationManagerCompat.from(context).cancel(WATER_NOTIFICATION_ID)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val next = (listOf(today, today.plusDays(1))).flatMap { date ->
            (WATER_FIRST_HOUR..WATER_LAST_HOUR step settings.waterIntervalHours).map { hour ->
                date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
            }
        }.first { it > now }
        Schedules.setAlarm(alarms, next, pending)
    }

    private fun healthLines(strings: Context, period: BandPeriod?): List<String> {
        if (period == null) return emptyList()
        val lines = ArrayList<String>()
        HealthMath.average(period.resting.mapNotNull { it.second })?.let { resting ->
            val before = period.restingBefore
            lines += if (before == null || before == resting) {
                strings.getString(R.string.notify_health_resting, resting)
            } else {
                val res = if (resting < before) R.string.notify_health_resting_down else R.string.notify_health_resting_up
                strings.getString(res, resting, abs(resting - before))
            }
        }
        HealthMath.average(period.nights.values.map { it.minutes })?.let { sleep ->
            lines += strings.getString(R.string.notify_health_sleep, sleep / 60, sleep % 60)
        }
        return lines
    }

    private fun streakCheck(strings: Context, history: Map<LocalDate, Long>, freezes: Set<LocalDate>, today: LocalDate, settings: AppSettings) {
        val steps = history[today] ?: 0L
        if (steps >= settings.dailyGoal) return
        val streak = AchievementMath.compute(history, today, settings.dailyGoal, settings.weeklyGoal, settings.weekStart, freezes = freezes)
            .records.currentStreak
        if (streak == 0) return
        notify(
            strings,
            STREAK_NOTIFICATION_ID,
            strings.getString(R.string.notify_streak_title, streak),
            strings.getString(R.string.notify_streak_text, format(settings.dailyGoal - steps)),
        )
    }

    private fun weeklySummary(strings: Context, history: Map<LocalDate, Long>, today: LocalDate, settings: AppSettings, health: List<String>) {
        val start = weekStartOf(today, settings.weekStart)
        val thisWeek = (0L..6L).sumOf { history[start.plusDays(it)] ?: 0L }
        val lastWeek = (1L..7L).sumOf { history[start.minusDays(it)] ?: 0L }
        val goal = format(settings.weeklyGoal)
        val text = when {
            lastWeek <= 0L -> strings.getString(R.string.notify_week_goal, goal)
            thisWeek >= lastWeek -> strings.getString(R.string.notify_week_more, percentChange(thisWeek, lastWeek), goal)
            else -> strings.getString(R.string.notify_week_less, percentChange(thisWeek, lastWeek), goal)
        }
        notify(strings, WEEK_NOTIFICATION_ID, strings.getString(R.string.notify_week_title, format(thisWeek)), (listOf(text) + health).joinToString("\n"))
    }

    private fun percentChange(now: Long, before: Long): Int = (abs(now - before) * 100 / before).toInt()

    private fun notifyOncePerDay(context: Context, key: String, id: Int, content: (Context) -> Pair<String, String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString(key, null) == today) return
        prefs.edit { putString(key, today) }
        val strings = Locales.wrap(context)
        val (title, text) = content(strings)
        notify(strings, id, title, text)
    }

    fun scheduleEvening(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, SystemEventReceiver::class.java).setAction(SystemEventReceiver.ACTION_EVENING)
        val pending = PendingIntent.getBroadcast(
            context,
            EVENING_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val settings = SettingsStore.get(context).settings.value
        if (!settings.streakReminder && !settings.weeklySummary) {
            alarms.cancel(pending)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now(zone).atTime(EVENING_TIME)
        val next = if (now.atZone(zone).toInstant().toEpochMilli() > System.currentTimeMillis()) now else now.plusDays(1)
        Schedules.setAlarm(alarms, next.atZone(zone).toInstant().toEpochMilli(), pending)
    }

    fun scheduleWeight(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, SystemEventReceiver::class.java).setAction(SystemEventReceiver.ACTION_WEIGHT)
        val pending = PendingIntent.getBroadcast(
            context,
            WEIGHT_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val settings = SettingsStore.get(context).settings.value
        if (!settings.weightReminder) {
            alarms.cancel(pending)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val next = generateSequence(LocalDate.now(zone)) { it.plusDays(1) }
            .map { it.atTime(WEIGHT_TIME).atZone(zone).toInstant().toEpochMilli() to it }
            .first { (at, date) -> at > now && date.dayOfWeek == settings.weekStart }
            .first
        Schedules.setAlarm(alarms, next, pending)
    }

    private fun notify(context: Context, id: Int, title: String, text: String, tab: String? = null) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notify_channel), NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply { if (tab != null) putExtra(MainActivity.EXTRA_TAB, tab) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            return
        }
    }

    private fun format(steps: Long): String = NumberFormat.getIntegerInstance(Locales.current).format(steps)
}
