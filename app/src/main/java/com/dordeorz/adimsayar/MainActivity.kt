package com.dordeorz.adimsayar

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dordeorz.adimsayar.background.Reminders
import com.dordeorz.adimsayar.background.Schedules
import com.dordeorz.adimsayar.background.StepCounterService
import com.dordeorz.adimsayar.data.ChallengeMath
import com.dordeorz.adimsayar.data.DayDetail
import com.dordeorz.adimsayar.data.KeptMedals
import com.dordeorz.adimsayar.data.NO_ROUTE
import com.dordeorz.adimsayar.data.NO_ROUTE_START
import com.dordeorz.adimsayar.data.StreakFreezes
import com.dordeorz.adimsayar.data.Water
import com.dordeorz.adimsayar.data.BandDirect
import com.dordeorz.adimsayar.data.BandLive
import com.dordeorz.adimsayar.data.ReadSource
import com.dordeorz.adimsayar.data.ReadingLog
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.HealthMath
import com.dordeorz.adimsayar.data.MonthReport
import com.dordeorz.adimsayar.data.PdfReport
import com.dordeorz.adimsayar.data.ReportHealth
import com.dordeorz.adimsayar.data.SmartGoal
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.data.WearableHealth
import com.dordeorz.adimsayar.data.WearableSteps
import com.dordeorz.adimsayar.data.Weights
import com.dordeorz.adimsayar.data.XiaomiStallDetector
import com.dordeorz.adimsayar.data.XiaomiSteps
import com.dordeorz.adimsayar.sensor.LiveStepMonitor
import com.dordeorz.adimsayar.sensor.StepSensors
import com.dordeorz.adimsayar.ui.AppScreen
import com.dordeorz.adimsayar.ui.ExtraState
import com.dordeorz.adimsayar.data.DayNotes
import com.dordeorz.adimsayar.data.DayOverrides
import com.dordeorz.adimsayar.data.ExtrasStore
import com.dordeorz.adimsayar.data.Floors
import com.dordeorz.adimsayar.data.Walks
import com.dordeorz.adimsayar.ui.MainActions
import com.dordeorz.adimsayar.ui.MainUiState
import com.dordeorz.adimsayar.ui.PermissionState
import com.dordeorz.adimsayar.ui.Tab
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
import java.time.YearMonth
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private val repository by lazy { StepRepository.get(applicationContext) }
    private val settings by lazy { getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE) }
    private val sensorAvailable by lazy { StepSensors.hasCounter(this) }
    private val profile by lazy { OemProfile.current(this) }
    private lateinit var monitor: LiveStepMonitor

    private var permission by mutableStateOf(PermissionState.NeedsRequest)
    private var batteryCardVisible by mutableStateOf(false)
    private var today by mutableStateOf(LocalDate.now())
    private var serviceEnabled by mutableStateOf(false)
    private var exactAlarmsAllowed by mutableStateOf<Boolean?>(null)
    private var xiaomiAvailable by mutableStateOf(false)
    private var xiaomiEnabled by mutableStateOf(false)
    private var xiaomiSync: Job? = null
    private var xiaomiProblem by mutableStateOf<XiaomiProblem?>(null)
    private var wearableAvailable by mutableStateOf(false)
    private var wearableEnabled by mutableStateOf(false)
    private var bandDirectEnabled by mutableStateOf(false)
    private var bandSyncing by mutableStateOf(false)
    private var bandOnly by mutableStateOf(false)
    private var wearableSync: Job? = null
    private var healthRefresh by mutableIntStateOf(0)
    private var requestedTab by mutableStateOf<Tab?>(null)

    private val settingsStore by lazy { SettingsStore.get(applicationContext) }

    private val extra = ExtraController(this)

    private val actions = MainActions(
        onRequestPermission = ::requestPermission,
        onOpenAppSettings = { BatteryOptimization.openAppDetails(this) },
        onDismissBatteryCard = {
            settings.edit { putBoolean(KEY_BATTERY_CARD_DISMISSED, true) }
            batteryCardVisible = false
        },
        onServiceEnabledChange = ::changeServiceEnabled,
        onOpenExactAlarms = ::openExactAlarmSettings,
        onXiaomiEnabledChange = ::changeXiaomiEnabled,
        onWearableEnabledChange = ::changeWearableEnabled,
        onWearableResync = ::resyncWearable,
        onBandDirectChange = ::changeBandDirect,
        onSyncBand = ::syncBandNow,
        onBandOnlyChange = ::changeBandOnly,
        onThemeModeChange = { settingsStore.setThemeMode(it) },
        onDynamicColorChange = {
            settingsStore.setDynamicColor(it)
            refreshWidgets()
        },
        onWidgetOpacityChange = {
            settingsStore.setWidgetOpacity(it)
            refreshWidgets()
        },
        onDailyGoalChange = { goal ->
            settingsStore.setDailyGoal(goal)
            refreshWidgets()
        },
        onSmartGoalChange = { enabled ->
            val week = SmartGoal.weekOf(LocalDate.now(), settingsStore.settings.value.weekStart)
            settingsStore.setSmartGoal(enabled, week.toEpochDay())
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
        onNearGoalNotificationChange = { enabled ->
            settingsStore.setNearGoalNotification(enabled)
            if (enabled) requestNotificationPermission()
        },
        onWeeklySummaryChange = { enabled ->
            settingsStore.setWeeklySummary(enabled)
            if (enabled) requestNotificationPermission()
            Reminders.scheduleEvening(applicationContext)
        },
        onLanguageChange = ::changeLanguage,
        onStreakReminderChange = { enabled ->
            settingsStore.setStreakReminder(enabled)
            if (enabled) requestNotificationPermission()
            Reminders.scheduleEvening(applicationContext)
        },
        onExport = { exportLauncher.launch(EXPORT_FILE_NAME.format(LocalDate.now())) },
        onMonthReport = { month ->
            reportMonth = month
            reportLauncher.launch(REPORT_FILE_NAME.format(month))
        },
        onImport = { importLauncher.launch(arrayOf("text/*", "application/zip", "application/octet-stream")) },
        loadDayDetail = { date ->
            if (XiaomiSteps.isEnabled(applicationContext)) {
                XiaomiSteps.dayDetail(applicationContext, date)
            } else {
                repository.loadPhoneHours(date)?.let { DayDetail(it, 0L, 0L) }
            }
        },
        loadBandDay = { date -> WearableHealth.day(applicationContext, date) },
        loadBandPeriod = { to, days -> WearableHealth.period(applicationContext, to, days) },
        loadHeartDay = { date -> WearableHealth.heartDay(applicationContext, date) },
        onGrantHealthPermissions = { runCatching { healthPermissionLauncher.launch(WearableSteps.PERMISSIONS) } },
        onAddWeight = { date, kg ->
            val appContext = applicationContext
            AppScope.launch(Dispatchers.IO) {
                Weights.add(appContext, date, kg)
                Widgets.updateWeight(appContext)
            }
        },
        onDeleteWeight = { date ->
            val appContext = applicationContext
            AppScope.launch(Dispatchers.IO) {
                Weights.delete(appContext, date)
                Widgets.updateWeight(appContext)
            }
        },
        onWeightReminderChange = { enabled ->
            settingsStore.setWeightReminder(enabled)
            if (enabled) requestNotificationPermission()
            Reminders.scheduleWeight(applicationContext)
        },
        onTabOpened = { requestedTab = null },
        loadSources = { WearableHealth.sources(applicationContext, SOURCE_DAYS) },
        onOpenHealthConnect = {
            val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HEALTH_CONNECT_SETTINGS_14 else HEALTH_CONNECT_SETTINGS
            openOrToast(Intent(action))
        },
        onInstallHealthConnect = {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))
            if (!openOrToast(market, silent = true)) {
                openOrToast(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE")))
            }
        },
        onAddWater = { ml ->
            val appContext = applicationContext
            AppScope.launch(Dispatchers.IO) {
                val drunk = Water.add(appContext, LocalDate.now(), ml)
                Reminders.onWaterChanged(appContext, drunk)
                Widgets.updateWater(appContext)
            }
        },
        onFreezeChange = { date, frozen ->
            val appContext = applicationContext
            AppScope.launch(Dispatchers.IO) {
                StreakFreezes.set(appContext, date, frozen)
                Widgets.updateAll(appContext)
            }
        },
        onAgeChange = { settingsStore.setAge(it) },
        onWaterGoalChange = { settingsStore.setWaterGoal(it) },
        onGlassChange = { settingsStore.setGlass(it) },
        onWaterReminderChange = { enabled ->
            settingsStore.setWaterReminder(enabled)
            if (enabled) requestNotificationPermission()
            Reminders.scheduleWater(applicationContext)
        },
        onWaterIntervalChange = { hours ->
            settingsStore.setWaterInterval(hours)
            Reminders.scheduleWater(applicationContext)
        },
        onRouteChange = { id ->
            if (id == null) settingsStore.setRoute(NO_ROUTE, NO_ROUTE_START) else settingsStore.setRoute(id, LocalDate.now().toEpochDay())
            refreshWidgets()
        },
        extra = extra,
    )

    private fun openOrToast(intent: Intent, silent: Boolean = false): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        if (!silent) Toast.makeText(this, R.string.wearable_unavailable, Toast.LENGTH_LONG).show()
        false
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) transferCsv(R.string.export_done) { repository.exportCsv(requireNotNull(contentResolver.openOutputStream(uri))) }
    }

    private var reportMonth: YearMonth? = null

    private val reportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val month = reportMonth ?: return@registerForActivityResult
        if (uri != null) writeReport(uri, month)
    }

    private fun writeReport(uri: Uri, month: YearMonth) {
        val appContext = applicationContext
        val strings = Locales.wrap(appContext)
        AppScope.launch(Dispatchers.IO) {
            val done = try {
                val settings = settingsStore.settings.value
                val report = MonthReport.build(
                    month = month,
                    today = LocalDate.now(),
                    history = repository.loadHistory(),
                    water = Water.observe(appContext).first(),
                    weights = Weights.observe(appContext).first(),
                    goal = settings.dailyGoal,
                )
                val health = if (WearableHealth.isEnabled(appContext)) reportHealth(appContext, report) else null
                PdfReport.write(appContext, requireNotNull(contentResolver.openOutputStream(uri)), report, settings, health)
                true
            } catch (e: IOException) {
                false
            } catch (e: RuntimeException) {
                false
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, strings.getString(if (done) R.string.report_done else R.string.report_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun reportHealth(context: Context, report: MonthReport): ReportHealth? {
        val last = report.days.lastOrNull()?.date ?: return null
        val period = WearableHealth.period(context, last, report.days.size.toLong()) ?: return null
        val nights = period.nights.filterKeys { YearMonth.from(it) == report.month }
        val resting = period.resting.mapNotNull { it.second }
        val health = ReportHealth(
            sleepMinutes = HealthMath.average(nights.values.map { it.minutes }),
            nights = nights.size,
            resting = HealthMath.average(resting),
        )
        return health.takeIf { it.sleepMinutes != null || it.resting != null }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) transferCsv(R.string.import_done) { repository.importCsv(requireNotNull(contentResolver.openInputStream(uri))) }
    }

    private val wearablePermissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (WearableSteps.READ_STEPS in granted) {
                enableWearable()
            } else {
                Toast.makeText(this, R.string.wearable_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    private val healthPermissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { healthRefresh++ }

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
        if (savedInstanceState == null) openRequestedTab(intent)
        setContent {
            val currentDay = today
            val appSettings by settingsStore.settings.collectAsStateWithLifecycle()
            val week by remember(currentDay) { repository.observeWeek(currentDay) }.collectAsStateWithLifecycle(emptyList())
            val history by remember { repository.observeHistory() }.collectAsStateWithLifecycle(emptyMap())
            val weights by remember { Weights.observe(applicationContext) }.collectAsStateWithLifecycle(emptyList())
            val earlyBirdSource = xiaomiEnabled
            val earlyBirdDays by produceState<Set<LocalDate>?>(null, earlyBirdSource, history[currentDay]) {
                value = if (earlyBirdSource) withContext(Dispatchers.IO) { XiaomiSteps.earlyBirdDays(applicationContext) } else null
            }
            val water by remember { Water.observe(applicationContext) }.collectAsStateWithLifecycle(emptyMap())
            val freezes by remember { StreakFreezes.observe(applicationContext) }.collectAsStateWithLifecycle(emptySet())
            val kept by remember { KeptMedals.observe(applicationContext) }.collectAsStateWithLifecycle(emptyMap())
            val computed = remember(history, currentDay, appSettings, earlyBirdDays, freezes) {
                appSettings.achievements(history, currentDay, earlyBirdDays, freezes)
            }
            LaunchedEffect(computed) {
                withContext(Dispatchers.IO) { KeptMedals.keep(applicationContext, computed.medals) }
            }
            val achievements = remember(computed, kept) { computed.copy(medals = KeptMedals.apply(computed.medals, kept)) }
            val challenges = remember(history, currentDay, appSettings.dailyGoal, appSettings.weekStart) {
                ChallengeMath.summary(history, currentDay, appSettings.dailyGoal, appSettings.weekStart)
            }
            val extras by remember { ExtrasStore.get(applicationContext).extras }.collectAsStateWithLifecycle()
            val notes by remember { DayNotes.observe(applicationContext) }.collectAsStateWithLifecycle(emptyMap())
            val overrides by remember { DayOverrides.observe(applicationContext) }.collectAsStateWithLifecycle(emptyMap())
            val walks by remember { Walks.observe(applicationContext) }.collectAsStateWithLifecycle(emptyList())
            val floors by remember { Floors.observe(applicationContext) }.collectAsStateWithLifecycle(emptyMap())
            val extraState = remember(extras, notes, overrides, walks, floors) {
                ExtraState(extras, notes, overrides, walks, floors, Floors.hasSensor(applicationContext))
            }
            val lastReading by repository.lastReadingWallMs.collectAsStateWithLifecycle()
            val frozen by monitor.frozen.collectAsStateWithLifecycle()
            val logLines by ReadingLog.get(applicationContext).lines.collectAsStateWithLifecycle()
            val dark = isDarkTheme(appSettings.themeMode)
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val direction = if (Locales.isRtl()) LayoutDirection.Rtl else LayoutDirection.Ltr
            AdimSayarTheme(darkTheme = dark, dynamicColor = appSettings.dynamicColor) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
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
                            exactAlarmsAllowed = exactAlarmsAllowed,
                            xiaomiAvailable = xiaomiAvailable,
                            xiaomiEnabled = xiaomiEnabled,
                            xiaomiProblem = xiaomiProblem.takeIf { xiaomiEnabled },
                            wearableAvailable = wearableAvailable,
                            wearableEnabled = wearableEnabled,
                            bandDirectEnabled = bandDirectEnabled,
                            bandSyncing = bandSyncing,
                            bandOnly = bandOnly,
                            logLines = logLines,
                            version = BuildConfig.VERSION_NAME,
                            weights = weights,
                            healthRefresh = healthRefresh,
                            requestedTab = requestedTab,
                            water = water,
                            freezes = freezes,
                            challenges = challenges,
                            extra = extraState,
                        ),
                        actions = actions,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequestedTab(intent)
    }

    private fun openRequestedTab(intent: Intent?) {
        val name = intent?.getStringExtra(EXTRA_TAB) ?: return
        Tab.entries.firstOrNull { it.name.lowercase() == name }?.let { requestedTab = it }
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
        AppScope.launch(Dispatchers.IO) {
            if (SmartGoal.applyIfDue(appContext)) Widgets.updateAll(appContext)
        }
        wearableAvailable = WearableSteps.isAvailable(this)
        wearableEnabled = WearableSteps.isEnabled(this)
        startWearableSync()
        bandDirectEnabled = BandDirect.isEnabled(this)
        bandOnly = WearableSteps.isBandOnly(this)
        if (bandDirectEnabled) {
            BandLive.refresh(appContext)
            AppScope.launch(Dispatchers.IO) {
                val result = BandDirect.sync(appContext, ReadSource.App)
                Widgets.updateAll(appContext)
                if (result != null) withContext(Dispatchers.Main) { healthRefresh++ }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        today = LocalDate.now()
        refreshPermission()
        batteryCardVisible = !settings.getBoolean(KEY_BATTERY_CARD_DISMISSED, false) && !BatteryOptimization.isExempt(this)
        refreshExactAlarms()
    }

    private fun refreshExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val allowed = Schedules.exactAlarmsAllowed(this)
        if (allowed && exactAlarmsAllowed == false) Schedules.ensure(applicationContext)
        exactAlarmsAllowed = allowed
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        } catch (e: ActivityNotFoundException) {
            BatteryOptimization.openAppDetails(this)
        }
    }

    override fun onStop() {
        xiaomiSync?.cancel()
        xiaomiSync = null
        wearableSync?.cancel()
        wearableSync = null
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

    private fun changeLanguage(language: String) {
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

    private fun changeBandOnly(enabled: Boolean) {
        bandOnly = enabled
        val appContext = applicationContext
        AppScope.launch(Dispatchers.IO) {
            WearableSteps.setBandOnly(appContext, enabled)
            when {
                BandDirect.isEnabled(appContext) -> BandDirect.remerge(appContext)
                WearableSteps.isEnabled(appContext) -> WearableSteps.resync(appContext)
            }
            Widgets.updateAll(appContext)
        }
    }

    private fun changeWearableEnabled(enabled: Boolean) {
        if (!enabled) {
            wearableEnabled = false
            wearableSync?.cancel()
            wearableSync = null
            val appContext = applicationContext
            AppScope.launch(Dispatchers.IO) {
                WearableSteps.setEnabled(appContext, false)
                Widgets.updateAll(appContext)
            }
            return
        }
        if (!WearableSteps.isAvailable(this)) {
            Toast.makeText(this, R.string.wearable_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val appContext = applicationContext
        AppScope.launch(Dispatchers.Main) {
            if (WearableSteps.hasPermission(appContext)) {
                enableWearable()
            } else {
                runCatching { wearablePermissionLauncher.launch(WearableSteps.PERMISSIONS) }
            }
        }
    }

    private suspend fun changeBandDirect(enabled: Boolean) {
        if (enabled) {
            wearableEnabled = false
            wearableSync?.cancel()
            wearableSync = null
        }
        val appContext = applicationContext
        withContext(Dispatchers.IO) {
            BandDirect.setEnabled(appContext, enabled)
            Widgets.updateAll(appContext)
        }
        bandDirectEnabled = enabled
        healthRefresh++
    }

    private fun syncBandNow() {
        if (!bandDirectEnabled) {
            if (wearableEnabled) resyncWearable()
            return
        }
        if (bandSyncing) return
        val appContext = applicationContext
        val strings = Locales.wrap(appContext)
        if (!BandDirect.granted(appContext, BandDirect.CONNECT_PERMISSIONS)) {
            Toast.makeText(appContext, strings.getString(R.string.band_result_permission), Toast.LENGTH_LONG).show()
            return
        }
        bandSyncing = true
        AppScope.launch(Dispatchers.IO) {
            val result = BandDirect.sync(appContext, ReadSource.App, force = true)
            Widgets.updateAll(appContext)
            withContext(Dispatchers.Main) {
                bandSyncing = false
                healthRefresh++
                val text = result?.let { BandDirect.resultText(it) } ?: R.string.band_result_connect
                Toast.makeText(appContext, strings.getString(text), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resyncWearable() {
        val appContext = applicationContext
        val strings = Locales.wrap(appContext)
        Toast.makeText(appContext, strings.getString(R.string.wearable_resync_started), Toast.LENGTH_SHORT).show()
        AppScope.launch(Dispatchers.IO) {
            val today = WearableSteps.resync(appContext)
            Widgets.updateAll(appContext)
            withContext(Dispatchers.Main) {
                val text = if (today == null) strings.getString(R.string.wearable_resync_failed) else strings.getString(R.string.wearable_resync_done)
                Toast.makeText(appContext, text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enableWearable() {
        wearableEnabled = true
        val appContext = applicationContext
        AppScope.launch(Dispatchers.IO) {
            if (BandDirect.isEnabled(appContext)) BandDirect.setEnabled(appContext, false)
            withContext(Dispatchers.Main) { bandDirectEnabled = false }
            WearableSteps.setEnabled(appContext, true)
            withContext(Dispatchers.Main) { startWearableSync() }
        }
    }

    private fun startWearableSync() {
        if (!wearableEnabled || wearableSync?.isActive == true) return
        val appContext = applicationContext
        wearableSync = AppScope.launch(Dispatchers.IO) {
            while (isActive) {
                WearableSteps.sync(appContext, ReadSource.App)
                Widgets.updateAll(appContext)
                delay(XIAOMI_SYNC_INTERVAL_MS)
            }
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_HEALTH = "health"
        private const val SETTINGS_PREFS = "settings"
        private const val KEY_BATTERY_CARD_DISMISSED = "battery_card_dismissed"
        private const val XIAOMI_SYNC_INTERVAL_MS = 60_000L
        private const val EXPORT_FILE_NAME = "fitwren-%s.csv"
        private const val REPORT_FILE_NAME = "fitwren-rapor-%s.pdf"
        private const val SOURCE_DAYS = 7L
        private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
        private const val HEALTH_CONNECT_SETTINGS = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
        private const val HEALTH_CONNECT_SETTINGS_14 = "android.health.connect.action.HEALTH_HOME_SETTINGS"
    }
}
