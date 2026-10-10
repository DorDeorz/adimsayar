package com.dordeorz.adimsayar.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dordeorz.adimsayar.AppScope
import com.dordeorz.adimsayar.data.ReadSource
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val appContext = context.applicationContext
        val pending = goAsync()
        AppScope.launch {
            try {
                if (intent.action == Intent.ACTION_SHUTDOWN) {
                    StepUpdater.refresh(appContext, SHUTDOWN_TIMEOUT_MS, ReadSource.System)
                    return@launch
                }
                Schedules.ensure(appContext)
                StepCounterService.startIfEnabled(appContext)
                StepUpdater.refresh(appContext, READ_TIMEOUT_MS, ReadSource.System)
                if (intent.action == ACTION_EVENING) Reminders.eveningCheck(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MIDNIGHT = "com.dordeorz.adimsayar.MIDNIGHT"
        const val ACTION_EVENING = "com.dordeorz.adimsayar.EVENING"
        private const val READ_TIMEOUT_MS = 5_000L
        private const val SHUTDOWN_TIMEOUT_MS = 3_000L
        private val HANDLED = setOf(
            ACTION_MIDNIGHT,
            ACTION_EVENING,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_SHUTDOWN,
        )
    }
}
