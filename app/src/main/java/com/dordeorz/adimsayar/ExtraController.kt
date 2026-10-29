package com.dordeorz.adimsayar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.dordeorz.adimsayar.background.ExtraReminders
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.health.connect.client.PermissionController
import com.dordeorz.adimsayar.data.Backup
import com.dordeorz.adimsayar.data.DataEraser
import com.dordeorz.adimsayar.data.DayNotes
import com.dordeorz.adimsayar.data.DayOverrides
import com.dordeorz.adimsayar.data.ExtraSettings
import com.dordeorz.adimsayar.data.ExtrasStore
import com.dordeorz.adimsayar.data.HealthWrite
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.WearableSteps
import com.dordeorz.adimsayar.data.BPM_RANGE
import com.dordeorz.adimsayar.data.Walks
import com.dordeorz.adimsayar.walk.DogWalk
import com.dordeorz.adimsayar.walk.MetronomeService
import com.dordeorz.adimsayar.walk.WalkRecordService
import com.dordeorz.adimsayar.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/** The actions of the extras added in 0.17, so MainActivity stays about counting and sources. */
class ExtraController(private val activity: ComponentActivity) {

    private val context: Context get() = activity.applicationContext
    private val settings get() = SettingsStore.get(context)
    private val store get() = ExtrasStore.get(context)

    private val backupLauncher = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runFile(R.string.backup_done) { Backup.write(context, requireNotNull(context.contentResolver.openOutputStream(uri))) }
    }

    private val restoreLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreFrom(uri)
    }

    private val healthWriteLauncher =
        activity.registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            val on = HealthWrite.WRITE_STEPS in granted
            store.update { it.copy(healthWrite = on) }
            if (on) AppScope.launch(Dispatchers.IO) { HealthWrite.syncIfDue(context, force = true) } else toast(R.string.health_write_denied)
        }

    fun setNote(date: LocalDate, text: String) = io { DayNotes.set(context, date, text) }

    fun setOverride(date: LocalDate, steps: Long?) = io {
        DayOverrides.set(context, date, steps)
        Widgets.updateAll(context)
    }

    fun setWeekendGoal(goal: Long) = settingsChanged { settings.setWeekendGoal(goal) }

    fun setMonthlyGoal(goal: Long) = settingsChanged { settings.setMonthlyGoal(goal) }

    fun setYearlyGoal(goal: Long) = settingsChanged { settings.setYearlyGoal(goal) }

    fun setVacation(from: LocalDate?, to: LocalDate?) = settingsChanged { settings.setVacation(from, to) }

    fun update(change: (ExtraSettings) -> ExtraSettings) {
        store.update(change)
        AppScope.launch { Widgets.updateAll(context) }
    }

    /** Changes a reminder setting: asks for the notification permission and sets the alarms again. */
    fun updateReminder(change: (ExtraSettings) -> ExtraSettings) {
        store.update(change)
        requestNotifications()
        ExtraReminders.scheduleAll(context)
    }

    private val notificationLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var gpsStartSteps = 0L

    private val locationLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            requestNotifications()
            WalkRecordService.start(context, gpsStartSteps)
        } else {
            toast(R.string.walk_gps_denied)
        }
    }

    fun startMetronome(bpm: Int) {
        store.update { it.copy(metronomeBpm = bpm.coerceIn(BPM_RANGE)) }
        requestNotifications()
        MetronomeService.start(context, bpm.coerceIn(BPM_RANGE))
    }

    fun stopMetronome() = MetronomeService.stop(context)

    fun startDogWalk(todaySteps: Long) = DogWalk.start(context, todaySteps)

    fun stopDogWalk(todaySteps: Long) = io { DogWalk.stop(context, todaySteps) }

    fun startGpsWalk(todaySteps: Long) {
        if (WalkRecordService.hasPermission(context)) {
            requestNotifications()
            WalkRecordService.start(context, todaySteps)
        } else {
            gpsStartSteps = todaySteps
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun stopGpsWalk() = WalkRecordService.stop(context)

    fun deleteWalk(id: Long) = io { Walks.delete(context, id) }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun backup() = backupLauncher.launch(BACKUP_FILE_NAME.format(LocalDate.now()))

    fun restore() = restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*"))

    fun eraseAll() = io {
        DataEraser.eraseAll(context)
        Widgets.updateAll(context)
        withContext(Dispatchers.Main) { toast(R.string.erase_done) }
    }

    fun eraseRange(from: LocalDate, to: LocalDate) = io {
        DataEraser.eraseRange(context, from, to)
        Widgets.updateAll(context)
        withContext(Dispatchers.Main) { toast(R.string.erase_done) }
    }

    fun setHealthWrite(on: Boolean) {
        if (!on) {
            store.update { it.copy(healthWrite = false) }
            return
        }
        if (!HealthWrite.canRun(context)) {
            toast(if (WearableSteps.isEnabled(context)) R.string.health_write_reading else R.string.wearable_unavailable)
            return
        }
        runCatching { healthWriteLauncher.launch(setOf(HealthWrite.WRITE_STEPS)) }
    }

    private fun restoreFrom(uri: Uri) = io {
        val rows = try {
            Backup.restore(context, requireNotNull(context.contentResolver.openInputStream(uri)))
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
        withContext(Dispatchers.Main) {
            if (rows == null) toast(R.string.restore_failed) else toast(Locales.wrap(context).getString(R.string.restore_done, rows))
        }
        if (rows != null) Widgets.updateAll(context)
    }

    private fun runFile(@StringRes done: Int, block: () -> Int) = io {
        val rows = try {
            block()
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
        withContext(Dispatchers.Main) {
            if (rows == null) toast(R.string.backup_failed) else toast(Locales.wrap(context).getString(done, rows))
        }
    }

    private fun settingsChanged(change: () -> Unit) {
        change()
        AppScope.launch { Widgets.updateAll(context) }
    }

    private fun io(block: suspend () -> Unit) {
        AppScope.launch(Dispatchers.IO) { block() }
    }

    private fun toast(@StringRes text: Int) = toast(Locales.wrap(context).getString(text))

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    companion object {
        private const val BACKUP_FILE_NAME = "fitwren-yedek-%s.json"
    }
}
