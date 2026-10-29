package com.dordeorz.adimsayar.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dordeorz.adimsayar.AppScope
import com.dordeorz.adimsayar.data.ReadSource
import com.dordeorz.adimsayar.data.SmartGoal
import com.dordeorz.adimsayar.widget.Widgets
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val appContext = context.applicationContext
        val pending = goAsync()
        AppScope.launch {
            try {
                if (intent.action == ACTION_WATER_ADD) {
                    Reminders.addWater(appContext)
                    return@launch
                }
                Schedules.ensure(appContext)
                StepCounterService.startIfEnabled(appContext)
                StepUpdater.refresh(appContext, READ_TIMEOUT_MS, ReadSource.System)
                if (intent.action == ACTION_MIDNIGHT && SmartGoal.applyIfDue(appContext)) Widgets.updateAll(appContext)
                if (intent.action == ACTION_EVENING) Reminders.eveningCheck(appContext)
                if (intent.action == ACTION_WEIGHT) Reminders.weightCheck(appContext)
                if (intent.action == ACTION_WATER) Reminders.waterCheck(appContext)
                if (intent.action == ACTION_FACE) FaceSchedule.apply(appContext)
                if (intent.action == ACTION_MORNING) ExtraReminders.morning(appContext)
                if (intent.action == ACTION_SEDENTARY) ExtraReminders.sedentary(appContext)
                if (intent.action == ACTION_WALK_PLAN) ExtraReminders.walkPlan(appContext)
                if (intent.action == ACTION_MONTH_REPORT) ExtraReminders.monthReport(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MIDNIGHT = "com.dordeorz.adimsayar.MIDNIGHT"
        const val ACTION_EVENING = "com.dordeorz.adimsayar.EVENING"
        const val ACTION_WEIGHT = "com.dordeorz.adimsayar.WEIGHT"
        const val ACTION_WATER = "com.dordeorz.adimsayar.WATER"
        const val ACTION_WATER_ADD = "com.dordeorz.adimsayar.WATER_ADD"
        const val ACTION_FACE = "com.dordeorz.adimsayar.FACE"
        const val ACTION_MORNING = "com.dordeorz.adimsayar.MORNING"
        const val ACTION_SEDENTARY = "com.dordeorz.adimsayar.SEDENTARY"
        const val ACTION_WALK_PLAN = "com.dordeorz.adimsayar.WALK_PLAN"
        const val ACTION_MONTH_REPORT = "com.dordeorz.adimsayar.MONTH_REPORT"
        private const val ACTION_EXACT_ALARMS = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        private const val READ_TIMEOUT_MS = 5_000L
        private val HANDLED = setOf(
            ACTION_MIDNIGHT,
            ACTION_EVENING,
            ACTION_WEIGHT,
            ACTION_WATER,
            ACTION_WATER_ADD,
            ACTION_FACE,
            ACTION_MORNING,
            ACTION_SEDENTARY,
            ACTION_WALK_PLAN,
            ACTION_MONTH_REPORT,
            ACTION_EXACT_ALARMS,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
