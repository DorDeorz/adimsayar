package com.dordeorz.adimsayar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dordeorz.adimsayar.background.Reminders
import com.dordeorz.adimsayar.background.Schedules
import com.dordeorz.adimsayar.background.StepCounterService
import com.dordeorz.adimsayar.data.AchievementMath
import com.dordeorz.adimsayar.data.AppLanguage
import com.dordeorz.adimsayar.data.ReadSource
import com.dordeorz.adimsayar.data.ReadingLog
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.data.XiaomiStallDetector
import com.dordeorz.adimsayar.data.XiaomiSteps
import com.dordeorz.adimsayar.sensor.LiveStepMonitor
import com.dordeorz.adimsayar.sensor.StepSensors
import com.dordeorz.adimsayar.ui.AppScreen
import com.dordeorz.adimsayar.ui.MainActions
import com.dordeorz.adimsayar.ui.MainUiState
import com.dordeorz.adimsayar.ui.PermissionState
import com.dordeorz.adimsayar.ui.XiaomiProblem
import com.dordeorz.adimsayar.ui.oem.BatteryOptimization
import com.dordeorz.adimsayar.ui.oem.OemProfile
import com.dordeorz.adimsayar.ui.theme.AdimSayarTheme
import com.dordeorz.adimsayar.ui.theme.isDarkTheme
import com.dordeorz.adimsayar.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val repository by lazy { StepRepository.get(applicationContext) }
    private val settings by lazy { getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE) }
    private val sensorAvailable by lazy { StepSensors.hasCounter(this) }
    private val profile = OemProfile.current()
    private lateinit var monitor: LiveStepMonitor

    private var permission by mutableStateOf(PermissionState.NeedsRequest)
    private var batteryCardVisible by mutableStateOf(false)
    private var today by mutableStateOf(LocalDate.now())
    private var serviceEnabled by mutableStateOf(false)
    private var xiaomiAvailable by mutableStateOf(false)
    private var xiaomiEnabled by mutableStateOf(false)
    private var xiaomiSync: Job? = null
    private var xiaomiProblem by mutableStateOf<XiaomiProblem?>(null)

    private val settingsStore by lazy { SettingsStore.get(applicationContext) }

    private val actions = MainActions(
        onRequestPermission = ::requestPermission,
        onOpenAppSettings = { BatteryOptimization.openAppDetails(this) },
        onDismissBatteryCard = {
            settings.edit { putBoolean(KEY_BATTERY_CARD_DISMISSED, true) }
            batteryCardVisible = false
        },
        onServiceEnabledChange = ::changeServiceEnabled,
        onXiaomiEnabledChange = ::changeXiaomiEnabled,
        onThemeModeChange = { settingsStore.setThemeMode(it) },
        onDynamicColorChange = { settingsStore.setDynamicColor(it) },
        onDailyGoalChange = { goal ->
            settingsStore.setDailyGoal(goal)
            refreshWidgets()
        },
        onWeeklyGoalChange = { settingsStore.setWeeklyGoal(it) },
        onHeightChange = { settingsStore.setHeight(it) },
        onWeightChange = { settingsStore.setWeight(it) },
        onDistanceUnitChange = { settingsStore.setDistanceUnit(it) },
        onWeekStartChange = { settingsStore.setWeekStart(it) },
        onGoalNotificationChange = { enabled ->
            settingsStore.setGoalNotification(enabled)
            if (enabled) requestNotificationPermission()
        },
        onLanguageChange = ::changeLanguage,
        onStreakReminderChange = { enabled ->
            settingsStore.setStreakReminder(enabled)
            if (enabled) requestNotificationPermission()
            Reminders.scheduleEvening(applicationContext)
        },
        onExport = { exportLauncher.launch(EXPORT_FILE_NAME.format(LocalDate.now())) },
        onImport = { importLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
        loadDayDetail = { date -> XiaomiSteps.dayDetail(applicationContext, date) },
    )

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) transferCsv(R.string.export_done) { repository.exportCsv(requireNotNull(contentResolver.openOutputStream(uri))) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) transferCsv(R.string.import_done) { repository.importCsv(requireNotNull(contentResolver.openInputStream(uri))) }
    }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = when {
            granted -> PermissionState.Granted
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION) -> PermissionState.NeedsRequest
            else -> PermissionState.Denied
        }
        if (granted) {
            monitor.start()
            StepCounterService.startIfEnabled(applicationContext)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Locales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        monitor = LiveStepMonitor(applicationContext)
        Schedules.ensure(applicationContext)
        setContent {
            val currentDay = today
            val appSettings by settingsStore.settings.collectAsStateWithLifecycle()
            val week by remember(currentDay) { repository.observeWeek(currentDay) }.collectAsStateWithLifecycle(emptyList())
            val history by remember { repository.observeHistory() }.collectAsStateWithLifecycle(emptyMap())
            val earlyBirdSource = xiaomiEnabled
            val earlyBirdDays by produceState<Set<LocalDate>?>(null, earlyBirdSource, history[currentDay]) {
                value = if (earlyBirdSource) withContext(Dispatchers.IO) { XiaomiSteps.earlyBirdDays(applicationContext) } else null
            }
            val achievements = remember(history, currentDay, appSettings, earlyBirdDays) {
                AchievementMath.compute(
                    history,
                    currentDay,
                    appSettings.dailyGoal,
                    appSettings.weeklyGoal,
                    appSettings.weekStart,
                    earlyBirdDays,
                )
            }
            val lastReading by repository.lastReadingWallMs.collectAsStateWithLifecycle()
            val frozen by monitor.frozen.collectAsStateWithLifecycle()
            val logLines by ReadingLog.get(applicationContext).lines.collectAsStateWithLifecycle()
            val dark = isDarkTheme(appSettings.themeMode)
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AdimSayarTheme(darkTheme = dark, dynamicColor = appSettings.dynamicColor) {
                AppScreen(
                    state = MainUiState(
                        today = currentDay,
                        week = week,
                        history = history,
                        achievements = achievements,
                        settings = appSettings,
                        lastReadingWallMs = lastReading,
                        permission = permission,
                        sensorAvailable = sensorAvailable,
                        frozen = frozen,
                        batteryProfile = profile.takeIf { batteryCardVisible && sensorAvailable },
                        serviceEnabled = serviceEnabled,
                        xiaomiAvailable = xiaomiAvailable,
                        xiaomiEnabled = xiaomiEnabled,
                        xiaomiProblem = xiaomiProblem.takeIf { xiaomiEnabled },
                        logLines = logLines,
                        version = BuildConfig.VERSION_NAME,
                    ),
                    actions = actions,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refreshPermission()
        serviceEnabled = StepCounterService.isEnabled(this)
        if (permission == PermissionState.Granted) {
            monitor.start()
            StepCounterService.startIfEnabled(applicationContext)
        }
        xiaomiEnabled = XiaomiSteps.isEnabled(this)
        val appContext = applicationContext
        AppScope.launch(Dispatchers.IO) {
            val available = XiaomiSteps.isAvailable(appContext)
            withContext(Dispatchers.Main) { xiaomiAvailable = available }
        }
        startXiaomiSync()
    }

    override fun onResume() {
        super.onResume()
        today = LocalDate.now()
        refreshPermission()
        batteryCardVisible = !settings.getBoolean(KEY_BATTERY_CARD_DISMISSED, false) && !BatteryOptimization.isExempt(this)
    }

    override fun onStop() {
        xiaomiSync?.cancel()
        xiaomiSync = null
        monitor.stop()
        refreshWidgets()
        super.onStop()
    }

    private fun refreshWidgets() {
        val appContext = applicationContext
        AppScope.launch { Widgets.updateAll(appContext) }
    }

    private fun refreshPermission() {
        permission = when {
            StepSensors.hasPermission(this) -> PermissionState.Granted
            permission == PermissionState.Denied -> PermissionState.Denied
            else -> PermissionState.NeedsRequest
        }
    }

    private fun changeServiceEnabled(enabled: Boolean) {
        serviceEnabled = enabled
        if (enabled) requestNotificationPermission()
        StepCounterService.setEnabled(applicationContext, enabled)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun changeLanguage(language: AppLanguage) {
        if (language == settingsStore.settings.value.language) return
        settingsStore.setLanguage(language)
        refreshWidgets()
        recreate()
    }

    private fun transferCsv(@StringRes done: Int, block: suspend () -> Int?) {
        val appContext = applicationContext
        val strings = Locales.wrap(appContext)
        AppScope.launch(Dispatchers.IO) {
            val count = try {
                block()
            } catch (e: IOException) {
                null
            } catch (e: RuntimeException) {
                null
            }
            withContext(Dispatchers.Main) {
                val text = if (count == null) strings.getString(R.string.csv_failed) else strings.getString(done, count)
                Toast.makeText(appContext, text, Toast.LENGTH_LONG).show()
            }
            if (count != null) Widgets.updateAll(appContext)
        }
    }

    private fun changeXiaomiEnabled(enabled: Boolean) {
        xiaomiEnabled = enabled
        XiaomiSteps.setEnabled(applicationContext, enabled)
        if (enabled) {
            serviceEnabled = false
            StepCounterService.setEnabled(applicationContext, false)
            startXiaomiSync()
        } else {
            xiaomiSync?.cancel()
            xiaomiSync = null
            AppScope.launch { repository.resetReading() }
        }
    }

    private fun startXiaomiSync() {
        if (!xiaomiEnabled || xiaomiSync?.isActive == true) return
        val appContext = applicationContext
        val stallDetector = XiaomiStallDetector()
        xiaomiSync = AppScope.launch(Dispatchers.IO) {
            while (isActive) {
                val today = XiaomiSteps.sync(appContext, ReadSource.App)
                val problem = when {
                    today == null -> XiaomiProblem.Unreadable
                    stallDetector.onSync(today, monitor.detectorSteps, SystemClock.elapsedRealtime()) -> XiaomiProblem.Stalled
                    else -> null
                }
                withContext(Dispatchers.Main) { xiaomiProblem = problem }
                delay(XIAOMI_SYNC_INTERVAL_MS)
            }
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    private companion object {
        const val SETTINGS_PREFS = "settings"
        const val KEY_BATTERY_CARD_DISMISSED = "battery_card_dismissed"
        const val XIAOMI_SYNC_INTERVAL_MS = 60_000L
        const val EXPORT_FILE_NAME = "adimsayar-%s.csv"
    }
}
