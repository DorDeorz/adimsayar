package com.dordeorz.adimsayar.background

import android.app.job.JobParameters
import android.app.job.JobService
import androidx.core.content.edit
import com.dordeorz.adimsayar.AppScope
import com.dordeorz.adimsayar.data.ReadSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StepJobService : JobService() {

    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val source = when (params.jobId) {
            Schedules.READ_NOW_JOB_ID -> ReadSource.Widget
            Schedules.READ_SYSTEM_JOB_ID -> ReadSource.System
            else -> ReadSource.Job
        }
        if (source == ReadSource.Job && ranJustNow()) return false
        if (ReadOnceService.start(applicationContext, source)) return false
        running = AppScope.launch {
            try {
                StepUpdater.refresh(applicationContext, READ_TIMEOUT_MS, source)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    // A periodic job that ran late can be started again right away; one read is enough.
    private fun ranJustNow(): Boolean {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_RUN, 0L)
        if (now - last in 0 until MIN_GAP_MS) return true
        prefs.edit { putLong(KEY_LAST_RUN, now) }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return false
    }

    private companion object {
        const val READ_TIMEOUT_MS = 10_000L
        const val MIN_GAP_MS = 2 * 60 * 1000L
        const val PREFS = "settings"
        const val KEY_LAST_RUN = "periodic_job_last_run"
    }
}
