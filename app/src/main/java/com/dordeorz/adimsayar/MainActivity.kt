package com.dordeorz.adimsayar

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dordeorz.adimsayar.background.Schedules
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.sensor.LiveStepMonitor
import com.dordeorz.adimsayar.sensor.StepSensors
import com.dordeorz.adimsayar.ui.MainScreen
import com.dordeorz.adimsayar.ui.MainUiState
import com.dordeorz.adimsayar.ui.PermissionState
import com.dordeorz.adimsayar.ui.oem.BatteryOptimization
import com.dordeorz.adimsayar.ui.oem.OemProfile
import com.dordeorz.adimsayar.ui.theme.AdimSayarTheme
import com.dordeorz.adimsayar.widget.Widgets
import kotlinx.coroutines.launch
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

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = when {
            granted -> PermissionState.Granted
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION) -> PermissionState.NeedsRequest
            else -> PermissionState.Denied
        }
        if (granted) monitor.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        monitor = LiveStepMonitor(applicationContext)
        Schedules.ensure(applicationContext)
        setContent {
            val currentDay = today
            val week by remember(currentDay) { repository.observeWeek(currentDay) }.collectAsStateWithLifecycle(emptyList())
            val total by remember { repository.observeTotal() }.collectAsStateWithLifecycle(0L)
            val lastReading by repository.lastReadingWallMs.collectAsStateWithLifecycle()
            val frozen by monitor.frozen.collectAsStateWithLifecycle()
            AdimSayarTheme {
                MainScreen(
                    state = MainUiState(
                        week = week,
                        total = total,
                        lastReadingWallMs = lastReading,
                        permission = permission,
                        sensorAvailable = sensorAvailable,
                        frozen = frozen,
                        batteryProfile = profile.takeIf { batteryCardVisible && sensorAvailable },
                    ),
                    onRequestPermission = ::requestPermission,
                    onOpenAppSettings = { BatteryOptimization.openAppDetails(this) },
                    onDismissBatteryCard = {
                        settings.edit { putBoolean(KEY_BATTERY_CARD_DISMISSED, true) }
                        batteryCardVisible = false
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refreshPermission()
        if (permission == PermissionState.Granted) monitor.start()
    }

    override fun onResume() {
        super.onResume()
        today = LocalDate.now()
        refreshPermission()
        batteryCardVisible = !settings.getBoolean(KEY_BATTERY_CARD_DISMISSED, false) && !BatteryOptimization.isExempt(this)
    }

    override fun onStop() {
        monitor.stop()
        val appContext = applicationContext
        AppScope.launch { Widgets.updateAll(appContext) }
        super.onStop()
    }

    private fun refreshPermission() {
        permission = when {
            StepSensors.hasPermission(this) -> PermissionState.Granted
            permission == PermissionState.Denied -> PermissionState.Denied
            else -> PermissionState.NeedsRequest
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
    }
}
