package com.dordeorz.adimsayar.background

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.dordeorz.adimsayar.AppScope
import com.dordeorz.adimsayar.BuildConfig
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.ExtrasStore
import com.dordeorz.adimsayar.data.UpdateChecker
import com.dordeorz.adimsayar.data.UpdateResult
import com.dordeorz.adimsayar.data.UpdateWatch
import com.dordeorz.adimsayar.ui.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The automatic update check, only when it is turned on: once a day in a job that waits for the internet,
 * and on opening the app when the last answer is old. A new release is announced once in a notification
 * and stays as a card on Today until it is installed or put away.
 */
object UpdateChecks {

    private const val JOB_ID = 0x5A_D1_00_04
    private const val NOTIFICATION_ID = 26
    private const val CHANNEL_ID = "guncelleme"
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val FLEX_MS = 6 * 60 * 60 * 1000L
    const val JOB_GAP_MS = 12 * 60 * 60 * 1000L
    const val OPEN_GAP_MS = 6 * 60 * 60 * 1000L

    fun isOn(context: Context): Boolean = ExtrasStore.get(context).extras.value.updateWatch

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!isOn(context)) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.allPendingJobs.any { it.id == JOB_ID }) return
        val builder = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setPeriodic(DAY_MS, FLEX_MS) else builder.setPeriodic(DAY_MS)
        runCatching { scheduler.schedule(builder.build()) }
    }

    /**
     * Asks GitHub when the last answer is older than [gapMs]. With [notify] a new release gets a notification;
     * on opening the app the Today card is enough, and that release is not announced again later.
     */
    suspend fun run(context: Context, gapMs: Long, notify: Boolean): UpdateResult? {
        if (!isOn(context)) return null
        val watch = UpdateWatch.get(context)
        if (!watch.due(gapMs)) return null
        val result = UpdateChecker.check(BuildConfig.VERSION_NAME)
        watch.record(result)
        if (result is UpdateResult.Available && watch.firstNotice(result.version) && notify) announce(context, result)
        return result
    }

    private fun announce(context: Context, release: UpdateResult.Available) {
        val strings = Locales.wrap(context)
        Reminders.notify(
            strings,
            NOTIFICATION_ID,
            strings.getString(R.string.update_new_title, release.version),
            strings.getString(R.string.update_notify_text, BuildConfig.VERSION_NAME),
            Tab.Today.name,
            CHANNEL_ID,
            R.string.update_channel,
        )
    }
}

class UpdateJobService : JobService() {

    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = AppScope.launch(Dispatchers.IO) {
            try {
                UpdateChecks.run(applicationContext, UpdateChecks.JOB_GAP_MS, notify = true)
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
}
