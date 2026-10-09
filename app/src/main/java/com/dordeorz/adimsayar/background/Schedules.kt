package com.dordeorz.adimsayar.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId

object Schedules {

    private const val PERIODIC_JOB_ID = 1
    private const val READ_NOW_JOB_ID = 2
    private const val PERIODIC_MS = 15 * 60 * 1000L
    private const val MIDNIGHT_DELAY_MS = 5_000L

    fun ensure(context: Context) {
        schedulePeriodic(context)
        scheduleMidnight(context)
    }

    fun requestRead(context: Context) {
        val job = JobInfo.Builder(READ_NOW_JOB_ID, ComponentName(context, StepJobService::class.java))
            .setOverrideDeadline(0L)
            .build()
        context.getSystemService(JobScheduler::class.java)?.schedule(job)
    }

    private fun schedulePeriodic(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.allPendingJobs.any { it.id == PERIODIC_JOB_ID }) return
        val job = JobInfo.Builder(PERIODIC_JOB_ID, ComponentName(context, StepJobService::class.java))
            .setPeriodic(PERIODIC_MS)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }

    private fun scheduleMidnight(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val at = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + MIDNIGHT_DELAY_MS
        val intent = Intent(context, SystemEventReceiver::class.java).setAction(SystemEventReceiver.ACTION_MIDNIGHT)
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val exactAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarms.canScheduleExactAlarms() else true
        try {
            if (exactAllowed) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }
}
