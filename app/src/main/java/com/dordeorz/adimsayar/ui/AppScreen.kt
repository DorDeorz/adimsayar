package com.dordeorz.adimsayar.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.ExtraController
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.Achievements
import com.dordeorz.adimsayar.data.AppSettings
import com.dordeorz.adimsayar.data.BandDay
import com.dordeorz.adimsayar.data.BandPeriod
import com.dordeorz.adimsayar.data.ChallengeSummary
import com.dordeorz.adimsayar.data.DayDetail
import com.dordeorz.adimsayar.data.DaySteps
import com.dordeorz.adimsayar.data.DistanceUnit
import com.dordeorz.adimsayar.data.HeartDay
import com.dordeorz.adimsayar.data.SourceReport
import com.dordeorz.adimsayar.data.ThemeMode
import com.dordeorz.adimsayar.data.Weight
import com.dordeorz.adimsayar.ui.oem.OemProfile
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

enum class PermissionState { Granted, NeedsRequest, Denied }

enum class XiaomiProblem { Unreadable, Stalled }

enum class Tab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    History(R.string.tab_history, R.drawable.ic_calendar),
    Health(R.string.tab_health, R.drawable.ic_heart),
    Today(R.string.tab_today, R.drawable.ic_walk),
    Medals(R.string.tab_medals, R.drawable.ic_trophy),
    Settings(R.string.settings, R.drawable.ic_settings),
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
    val exactAlarmsAllowed: Boolean?,
    val xiaomiAvailable: Boolean,
    val xiaomiEnabled: Boolean,
    val xiaomiProblem: XiaomiProblem?,
    val wearableAvailable: Boolean,
    val wearableEnabled: Boolean,
    val bandDirectEnabled: Boolean,
    val bandOnly: Boolean,
    val bandSyncing: Boolean,
    val logLines: List<String>,
    val version: String,
    val weights: List<Weight>,
    val healthRefresh: Int,
    val requestedTab: Tab?,
    val water: Map<LocalDate, Int>,
    val freezes: Set<LocalDate>,
    val challenges: ChallengeSummary,
    val extra: ExtraState = ExtraState(),
) {
    val healthEnabled: Boolean get() = wearableEnabled || bandDirectEnabled
}

class MainActions(
    val onRequestPermission: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onDismissBatteryCard: () -> Unit,
    val onServiceEnabledChange: (Boolean) -> Unit,
    val onOpenExactAlarms: () -> Unit,
    val onXiaomiEnabledChange: (Boolean) -> Unit,
    val onWearableEnabledChange: (Boolean) -> Unit,
    val onWearableResync: () -> Unit,
    val onBandDirectChange: suspend (Boolean) -> Unit,
    val onSyncBand: () -> Unit,
    val onBandOnlyChange: (Boolean) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onDynamicColorChange: (Boolean) -> Unit,
    val onWidgetOpacityChange: (Int) -> Unit,
    val onDailyGoalChange: (Long) -> Unit,
    val onSmartGoalChange: (Boolean) -> Unit,
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
    val onMonthReport: (YearMonth) -> Unit,
    val loadDayDetail: suspend (LocalDate) -> DayDetail?,
    val loadBandDay: suspend (LocalDate) -> BandDay?,
    val loadBandPeriod: suspend (LocalDate, Long) -> BandPeriod?,
    val loadHeartDay: suspend (LocalDate) -> HeartDay?,
    val onGrantHealthPermissions: () -> Unit,
    val onAddWeight: (LocalDate, Double) -> Unit,
    val onDeleteWeight: (LocalDate) -> Unit,
    val onWeightReminderChange: (Boolean) -> Unit,
    val onTabOpened: () -> Unit,
    val loadSources: suspend () -> SourceReport?,
    val onOpenHealthConnect: () -> Unit,
    val onInstallHealthConnect: () -> Unit,
    val onAddWater: (Int) -> Unit,
    val onFreezeChange: (LocalDate, Boolean) -> Unit,
    val onAgeChange: (Int) -> Unit,
    val onWaterGoalChange: (Int) -> Unit,
    val onGlassChange: (Int) -> Unit,
    val onWaterReminderChange: (Boolean) -> Unit,
    val onWaterIntervalChange: (Int) -> Unit,
    val onRouteChange: (String?) -> Unit,
    val extra: ExtraController,
)

@Composable
fun AppScreen(state: MainUiState, actions: MainActions) {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    if (tab != Tab.Today) BackHandler { tab = Tab.Today }
    LaunchedEffect(state.requestedTab) {
        state.requestedTab?.let {
            tab = it
            actions.onTabOpened()
        }
    }

    Scaffold(
        topBar = {
            Text(
                stringResource(tab.label),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    val main = item == Tab.Today
                    val label: (@Composable () -> Unit)? = if (main) null else { { Text(stringResource(item.label), maxLines = 1) } }
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tab = item },
                        icon = {
                            if (main) {
                                Box(
                                    modifier = Modifier
                                        .size(TODAY_BUTTON_SIZE)
                                        .clip(CircleShape)
                                        .background(if (item == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painterResource(item.icon),
                                        contentDescription = stringResource(item.label),
                                        tint = if (item == tab) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(TODAY_ICON_SIZE),
                                    )
                                }
                            } else {
                                Icon(painterResource(item.icon), contentDescription = null)
                            }
                        },
                        label = label,
                        colors = if (main) NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent) else NavigationBarItemDefaults.colors(),
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = { fadeIn(tween(TAB_FADE_IN_MS, delayMillis = TAB_FADE_OUT_MS)) togetherWith fadeOut(tween(TAB_FADE_OUT_MS)) },
            label = "tab",
        ) { shown ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (shown) {
                    Tab.History -> HistoryScreen(state, actions)
                    Tab.Health -> HealthScreen(state, actions, onOpenSettings = { tab = Tab.Settings })
                    Tab.Today -> if (state.extra.extras.simpleView) SimpleTodayScreen(state, actions) else TodayScreen(state, actions)
                    Tab.Medals -> MedalsScreen(state, actions.onRouteChange)
                    Tab.Settings -> SettingsScreen(state, actions)
                }
            }
        }
    }
}

private val TODAY_BUTTON_SIZE = 56.dp

private val TODAY_ICON_SIZE = 30.dp

private const val TAB_FADE_IN_MS = 180

private const val TAB_FADE_OUT_MS = 90
