package com.dordeorz.adimsayar.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.Achievements
import com.dordeorz.adimsayar.data.AppSettings
import com.dordeorz.adimsayar.data.DayDetail
import com.dordeorz.adimsayar.data.DaySteps
import com.dordeorz.adimsayar.data.DistanceUnit
import com.dordeorz.adimsayar.data.ThemeMode
import com.dordeorz.adimsayar.ui.oem.OemProfile
import java.time.DayOfWeek
import java.time.LocalDate

enum class PermissionState { Granted, NeedsRequest, Denied }

enum class XiaomiProblem { Unreadable, Stalled }

enum class Tab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    Today(R.string.tab_today, R.drawable.ic_walk),
    History(R.string.tab_history, R.drawable.ic_calendar),
    Medals(R.string.tab_medals, R.drawable.ic_trophy),
}

data class MainUiState(
    val today: LocalDate,
    val week: List<DaySteps>,
    val history: Map<LocalDate, Long>,
    val achievements: Achievements,
    val settings: AppSettings,
    val lastReadingWallMs: Long?,
    val permission: PermissionState,
    val sensorAvailable: Boolean,
    val frozen: Boolean,
    val batteryProfile: OemProfile?,
    val serviceEnabled: Boolean,
    val xiaomiAvailable: Boolean,
    val xiaomiEnabled: Boolean,
    val xiaomiProblem: XiaomiProblem?,
    val logLines: List<String>,
    val version: String,
)

class MainActions(
    val onRequestPermission: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onDismissBatteryCard: () -> Unit,
    val onServiceEnabledChange: (Boolean) -> Unit,
    val onXiaomiEnabledChange: (Boolean) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onDynamicColorChange: (Boolean) -> Unit,
    val onDailyGoalChange: (Long) -> Unit,
    val onWeeklyGoalChange: (Long) -> Unit,
    val onHeightChange: (Int) -> Unit,
    val onWeightChange: (Int) -> Unit,
    val onDistanceUnitChange: (DistanceUnit) -> Unit,
    val onWeekStartChange: (DayOfWeek) -> Unit,
    val onGoalNotificationChange: (Boolean) -> Unit,
    val onStreakReminderChange: (Boolean) -> Unit,
    val onNearGoalNotificationChange: (Boolean) -> Unit,
    val onWeeklySummaryChange: (Boolean) -> Unit,
    val onLanguageChange: (String) -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val loadDayDetail: suspend (LocalDate) -> DayDetail?,
)

@Composable
fun AppScreen(state: MainUiState, actions: MainActions) {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    if (settingsOpen) {
        BackHandler { settingsOpen = false }
        SettingsScreen(state, actions, onBack = { settingsOpen = false })
        return
    }
    if (tab != Tab.Today) BackHandler { tab = Tab.Today }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(tab.label),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { settingsOpen = true }) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                }
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tab = item },
                        icon = { Icon(painterResource(item.icon), contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Today -> TodayScreen(state, actions)
                Tab.History -> HistoryScreen(state, actions.loadDayDetail)
                Tab.Medals -> MedalsScreen(state)
            }
        }
    }
}
