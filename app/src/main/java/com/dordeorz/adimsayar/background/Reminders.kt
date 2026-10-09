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
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.data.weekStartOf
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

object Reminders {

    private const val CHANNEL_ID = "hedef"
    private const val GOAL_NOTIFICATION_ID = 10
    private const val STREAK_NOTIFICATION_ID = 11
    private const val NEAR_NOTIFICATION_ID = 12
    private const val WEEK_NOTIFICATION_ID = 13
    private const val NEAR_GOAL_PERCENT = 80
    private const val PREFS = "reminders"
    private const val KEY_GOAL_DATE = "goal_notified_date"
    private const val KEY_NEAR_DATE = "near_notified_date"
    private const val EVENING_REQUEST = 1
    private val EVENING_TIME: LocalTime = LocalTime.of(20, 0)

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
        if (settings.streakReminder) streakCheck(strings, history, today, settings)
        if (settings.weeklySummary && today.dayOfWeek == settings.weekStart.minus(1)) weeklySummary(strings, history, today, settings)
    }

    private fun streakCheck(strings: Context, history: Map<LocalDate, Long>, today: LocalDate, settings: AppSettings) {
        val steps = history[today] ?: 0L
        if (steps >= settings.dailyGoal) return
        val streak = AchievementMath.compute(history, today, settings.dailyGoal, settings.weeklyGoal, settings.weekStart)
            .records.currentStreak
        if (streak == 0) return
        notify(
            strings,
            STREAK_NOTIFICATION_ID,
            strings.getString(R.string.notify_streak_title, streak),
            strings.getString(R.string.notify_streak_text, format(settings.dailyGoal - steps)),
        )
    }

    private fun weeklySummary(strings: Context, history: Map<LocalDate, Long>, today: LocalDate, settings: AppSettings) {
        val start = weekStartOf(today, settings.weekStart)
        val thisWeek = (0L..6L).sumOf { history[start.plusDays(it)] ?: 0L }
        val lastWeek = (1L..7L).sumOf { history[start.minusDays(it)] ?: 0L }
        val goal = format(settings.weeklyGoal)
        val text = when {
            lastWeek <= 0L -> strings.getString(R.string.notify_week_goal, goal)
            thisWeek >= lastWeek -> strings.getString(R.string.notify_week_more, percentChange(thisWeek, lastWeek), goal)
            else -> strings.getString(R.string.notify_week_less, percentChange(thisWeek, lastWeek), goal)
        }
        notify(strings, WEEK_NOTIFICATION_ID, strings.getString(R.string.notify_week_title, format(thisWeek)), text)
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
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atZone(zone).toInstant().toEpochMilli(), pending)
    }

    private fun notify(context: Context, id: Int, title: String, text: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notify_channel), NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
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
