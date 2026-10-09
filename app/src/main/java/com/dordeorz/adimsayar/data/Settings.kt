package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek

enum class ThemeMode { System, Light, Dark }

enum class DistanceUnit { Km, Mile }

enum class AppLanguage { System, Turkish, English }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val dailyGoal: Long = DEFAULT_DAILY_GOAL,
    val weeklyGoal: Long = DEFAULT_WEEKLY_GOAL,
    val heightCm: Int = DEFAULT_HEIGHT_CM,
    val weightKg: Int = DEFAULT_WEIGHT_KG,
    val distanceUnit: DistanceUnit = DistanceUnit.Km,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val goalNotification: Boolean = false,
    val streakReminder: Boolean = false,
    val language: AppLanguage = AppLanguage.System,
)

const val DEFAULT_DAILY_GOAL = 10_000L
const val DEFAULT_WEEKLY_GOAL = 70_000L
val DAILY_GOAL_RANGE = 1_000L..100_000L
val WEEKLY_GOAL_RANGE = 5_000L..700_000L
const val DEFAULT_HEIGHT_CM = 170
const val DEFAULT_WEIGHT_KG = 70
val HEIGHT_RANGE = 100..230
val WEIGHT_RANGE = 30..250
val WEEK_START_OPTIONS = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)

class SettingsStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())

    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun setDailyGoal(goal: Long) = update { it.copy(dailyGoal = goal.coerceIn(DAILY_GOAL_RANGE)) }

    fun setWeeklyGoal(goal: Long) = update { it.copy(weeklyGoal = goal.coerceIn(WEEKLY_GOAL_RANGE)) }

    fun setHeight(cm: Int) = update { it.copy(heightCm = cm.coerceIn(HEIGHT_RANGE)) }

    fun setWeight(kg: Int) = update { it.copy(weightKg = kg.coerceIn(WEIGHT_RANGE)) }

    fun setDistanceUnit(unit: DistanceUnit) = update { it.copy(distanceUnit = unit) }

    fun setWeekStart(day: DayOfWeek) = update { it.copy(weekStart = day) }

    fun setGoalNotification(enabled: Boolean) = update { it.copy(goalNotification = enabled) }

    fun setStreakReminder(enabled: Boolean) = update { it.copy(streakReminder = enabled) }

    fun setLanguage(language: AppLanguage) = update { it.copy(language = language) }

    private fun update(change: (AppSettings) -> AppSettings) {
        val next = change(_settings.value)
        prefs.edit {
            putString(KEY_THEME, next.themeMode.name)
            putBoolean(KEY_DYNAMIC_COLOR, next.dynamicColor)
            putLong(KEY_DAILY_GOAL, next.dailyGoal)
            putLong(KEY_WEEKLY_GOAL, next.weeklyGoal)
            putInt(KEY_HEIGHT, next.heightCm)
            putInt(KEY_WEIGHT, next.weightKg)
            putString(KEY_DISTANCE_UNIT, next.distanceUnit.name)
            putString(KEY_WEEK_START, next.weekStart.name)
            putBoolean(KEY_GOAL_NOTIFICATION, next.goalNotification)
            putBoolean(KEY_STREAK_REMINDER, next.streakReminder)
            putString(KEY_LANGUAGE, next.language.name)
        }
        _settings.value = next
    }

    private fun load() = AppSettings(
        themeMode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.System,
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
        dailyGoal = prefs.getLong(KEY_DAILY_GOAL, DEFAULT_DAILY_GOAL),
        weeklyGoal = prefs.getLong(KEY_WEEKLY_GOAL, DEFAULT_WEEKLY_GOAL),
        heightCm = prefs.getInt(KEY_HEIGHT, DEFAULT_HEIGHT_CM),
        weightKg = prefs.getInt(KEY_WEIGHT, DEFAULT_WEIGHT_KG),
        distanceUnit = DistanceUnit.entries.firstOrNull { it.name == prefs.getString(KEY_DISTANCE_UNIT, null) } ?: DistanceUnit.Km,
        weekStart = WEEK_START_OPTIONS.firstOrNull { it.name == prefs.getString(KEY_WEEK_START, null) } ?: DayOfWeek.MONDAY,
        goalNotification = prefs.getBoolean(KEY_GOAL_NOTIFICATION, false),
        streakReminder = prefs.getBoolean(KEY_STREAK_REMINDER, false),
        language = AppLanguage.entries.firstOrNull { it.name == prefs.getString(KEY_LANGUAGE, null) } ?: AppLanguage.System,
    )

    companion object {
        private const val PREFS = "settings"
        private const val KEY_THEME = "theme"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_DAILY_GOAL = "daily_goal"
        private const val KEY_WEEKLY_GOAL = "weekly_goal"
        private const val KEY_HEIGHT = "height_cm"
        private const val KEY_WEIGHT = "weight_kg"
        private const val KEY_DISTANCE_UNIT = "distance_unit"
        private const val KEY_WEEK_START = "week_start"
        private const val KEY_GOAL_NOTIFICATION = "goal_notification"
        private const val KEY_STREAK_REMINDER = "streak_reminder"
        private const val KEY_LANGUAGE = "language"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore = instance ?: synchronized(this) {
            instance ?: SettingsStore(context.applicationContext).also { instance = it }
        }
    }
}
