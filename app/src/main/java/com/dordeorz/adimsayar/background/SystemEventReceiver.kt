package com.dordeorz.adimsayar.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dordeorz.adimsayar.AppScope
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val appContext = context.applicationContext
        val pending = goAsync()
        AppScope.launch {
            try {
                Schedules.ensure(appContext)
                StepUpdater.refresh(appContext, READ_TIMEOUT_MS)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MIDNIGHT = "com.dordeorz.adimsayar.MIDNIGHT"
        private const val READ_TIMEOUT_MS = 5_000L
        private val HANDLED = setOf(
            ACTION_MIDNIGHT,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
