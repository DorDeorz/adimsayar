package com.dordeorz.adimsayar.background

import android.app.job.JobParameters
import android.app.job.JobService
import com.dordeorz.adimsayar.AppScope
import com.dordeorz.adimsayar.data.ReadSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StepJobService : JobService() {

    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = AppScope.launch {
            try {
                StepUpdater.refresh(applicationContext, READ_TIMEOUT_MS, if (params.jobId == Schedules.READ_NOW_JOB_ID) ReadSource.Widget else ReadSource.Job)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return false
    }

    private companion object {
        const val READ_TIMEOUT_MS = 10_000L
    }
}
