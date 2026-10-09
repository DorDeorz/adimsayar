package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val dailyGoal: Long = DEFAULT_DAILY_GOAL,
    val weeklyGoal: Long = DEFAULT_WEEKLY_GOAL,
)

const val DEFAULT_DAILY_GOAL = 10_000L
const val DEFAULT_WEEKLY_GOAL = 70_000L
val DAILY_GOAL_RANGE = 1_000L..100_000L
val WEEKLY_GOAL_RANGE = 5_000L..700_000L

class SettingsStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())

    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun setDailyGoal(goal: Long) = update { it.copy(dailyGoal = goal.coerceIn(DAILY_GOAL_RANGE)) }

    fun setWeeklyGoal(goal: Long) = update { it.copy(weeklyGoal = goal.coerceIn(WEEKLY_GOAL_RANGE)) }

    private fun update(change: (AppSettings) -> AppSettings) {
        val next = change(_settings.value)
        prefs.edit {
            putString(KEY_THEME, next.themeMode.name)
            putBoolean(KEY_DYNAMIC_COLOR, next.dynamicColor)
            putLong(KEY_DAILY_GOAL, next.dailyGoal)
            putLong(KEY_WEEKLY_GOAL, next.weeklyGoal)
        }
        _settings.value = next
    }

    private fun load() = AppSettings(
        themeMode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.System,
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
        dailyGoal = prefs.getLong(KEY_DAILY_GOAL, DEFAULT_DAILY_GOAL),
        weeklyGoal = prefs.getLong(KEY_WEEKLY_GOAL, DEFAULT_WEEKLY_GOAL),
    )

    companion object {
        private const val PREFS = "settings"
        private const val KEY_THEME = "theme"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_DAILY_GOAL = "daily_goal"
        private const val KEY_WEEKLY_GOAL = "weekly_goal"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore = instance ?: synchronized(this) {
            instance ?: SettingsStore(context.applicationContext).also { instance = it }
        }
    }
}
