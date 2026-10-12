package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek

enum class ThemeMode { System, Light, Dark }

enum class DistanceUnit { Km, Mile }


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
    val nearGoalNotification: Boolean = false,
    val weeklySummary: Boolean = false,
    val weightReminder: Boolean = false,
    val language: String = SYSTEM_LANGUAGE,
    val age: Int = DEFAULT_AGE,
    val waterGoalMl: Int = DEFAULT_WATER_GOAL_ML,
    val glassMl: Int = DEFAULT_GLASS_ML,
    val waterReminder: Boolean = false,
    val waterIntervalHours: Int = DEFAULT_WATER_INTERVAL_HOURS,
    val routeId: String = NO_ROUTE,
    val routeStartDay: Long = NO_ROUTE_START,
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
const val DEFAULT_AGE = 30
val AGE_RANGE = 10..100
const val DEFAULT_WATER_GOAL_ML = 2_000
val WATER_GOAL_RANGE = 500..6_000
const val DEFAULT_GLASS_ML = 250
val GLASS_OPTIONS = listOf(200, 250, 330, 500)
const val DEFAULT_WATER_INTERVAL_HOURS = 2
val WATER_INTERVAL_OPTIONS = listOf(1, 2, 3)
const val NO_ROUTE = ""
const val NO_ROUTE_START = -1L
const val SYSTEM_LANGUAGE = ""
val SUPPORTED_LANGUAGES = listOf("tr", "en", "de", "es", "fr", "it", "pt", "ru", "ar", "ja", "zh", "ko")

private fun languageTag(stored: String?): String = when (stored) {
    "Turkish" -> "tr"
    "English" -> "en"
    in SUPPORTED_LANGUAGES -> stored.orEmpty()
    else -> SYSTEM_LANGUAGE
}

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

    fun setNearGoalNotification(enabled: Boolean) = update { it.copy(nearGoalNotification = enabled) }

    fun setWeeklySummary(enabled: Boolean) = update { it.copy(weeklySummary = enabled) }

    fun setWeightReminder(enabled: Boolean) = update { it.copy(weightReminder = enabled) }

    fun setLanguage(language: String) = update { it.copy(language = language) }

    fun setAge(age: Int) = update { it.copy(age = age.coerceIn(AGE_RANGE)) }

    fun setWaterGoal(ml: Int) = update { it.copy(waterGoalMl = ml.coerceIn(WATER_GOAL_RANGE)) }

    fun setGlass(ml: Int) = update { it.copy(glassMl = ml.takeIf { value -> value in GLASS_OPTIONS } ?: DEFAULT_GLASS_ML) }

    fun setWaterReminder(enabled: Boolean) = update { it.copy(waterReminder = enabled) }

    fun setWaterInterval(hours: Int) = update {
        it.copy(waterIntervalHours = hours.takeIf { value -> value in WATER_INTERVAL_OPTIONS } ?: DEFAULT_WATER_INTERVAL_HOURS)
    }

    fun setRoute(id: String, startDay: Long) = update { it.copy(routeId = id, routeStartDay = startDay) }

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
            putBoolean(KEY_NEAR_GOAL, next.nearGoalNotification)
            putBoolean(KEY_WEEKLY_SUMMARY, next.weeklySummary)
            putBoolean(KEY_WEIGHT_REMINDER, next.weightReminder)
            putString(KEY_LANGUAGE, next.language)
            putInt(KEY_AGE, next.age)
            putInt(KEY_WATER_GOAL, next.waterGoalMl)
            putInt(KEY_GLASS, next.glassMl)
            putBoolean(KEY_WATER_REMINDER, next.waterReminder)
            putInt(KEY_WATER_INTERVAL, next.waterIntervalHours)
            putString(KEY_ROUTE, next.routeId)
            putLong(KEY_ROUTE_START, next.routeStartDay)
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
        nearGoalNotification = prefs.getBoolean(KEY_NEAR_GOAL, false),
        weeklySummary = prefs.getBoolean(KEY_WEEKLY_SUMMARY, false),
        weightReminder = prefs.getBoolean(KEY_WEIGHT_REMINDER, false),
        language = languageTag(prefs.getString(KEY_LANGUAGE, null)),
        age = prefs.getInt(KEY_AGE, DEFAULT_AGE),
        waterGoalMl = prefs.getInt(KEY_WATER_GOAL, DEFAULT_WATER_GOAL_ML),
        glassMl = prefs.getInt(KEY_GLASS, DEFAULT_GLASS_ML),
        waterReminder = prefs.getBoolean(KEY_WATER_REMINDER, false),
        waterIntervalHours = prefs.getInt(KEY_WATER_INTERVAL, DEFAULT_WATER_INTERVAL_HOURS),
        routeId = prefs.getString(KEY_ROUTE, null) ?: NO_ROUTE,
        routeStartDay = prefs.getLong(KEY_ROUTE_START, NO_ROUTE_START),
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
        private const val KEY_NEAR_GOAL = "near_goal_notification"
        private const val KEY_WEEKLY_SUMMARY = "weekly_summary"
        private const val KEY_WEIGHT_REMINDER = "weight_reminder"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_AGE = "age"
        private const val KEY_WATER_GOAL = "water_goal_ml"
        private const val KEY_GLASS = "glass_ml"
        private const val KEY_WATER_REMINDER = "water_reminder"
        private const val KEY_WATER_INTERVAL = "water_interval_hours"
        private const val KEY_ROUTE = "route"
        private const val KEY_ROUTE_START = "route_start_day"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore = instance ?: synchronized(this) {
            instance ?: SettingsStore(context.applicationContext).also { instance = it }
        }
    }
}
