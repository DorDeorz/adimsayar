package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.time.DayOfWeek

/** Settings of the extras added in 0.17: reminders, the bird's looks, app lock, simple view. */
data class ExtraSettings(
    val morningSummary: Boolean = false,
    val morningHour: Int = DEFAULT_MORNING_HOUR,
    val recordNotify: Boolean = false,
    val sedentary: Boolean = false,
    val walkPlan: Map<DayOfWeek, Int> = emptyMap(),
    val monthReportReminder: Boolean = false,
    val bandCelebrate: Boolean = false,
    val healthWrite: Boolean = false,
    val appLock: Boolean = false,
    val lockBiometric: Boolean = false,
    val pinHash: String = "",
    val simpleView: Boolean = false,
    val showQuote: Boolean = true,
    val birdSpecies: String = DEFAULT_SPECIES,
    val birdColor: String = DEFAULT_BIRD_COLOR,
    val birdSound: String = DEFAULT_BIRD_SOUND,
    val hiddenItems: Set<String> = emptySet(),
    val metronomeBpm: Int = DEFAULT_BPM,
    val onboardingDone: Boolean = false,
    val unlockedLooks: Set<String> = emptySet(),
    val seedsSpent: Long = 0L,
    val fedOn: Long = -1L,
)

const val DEFAULT_MORNING_HOUR = 8
val MORNING_HOURS = listOf(6, 7, 8, 9, 10)
const val DEFAULT_SPECIES = "Sparrow"
const val DEFAULT_BIRD_COLOR = "Default"
const val DEFAULT_BIRD_SOUND = "Chirp"
const val DEFAULT_BPM = 110
val BPM_RANGE = 60..180
const val DEFAULT_WALK_MINUTE = 18 * 60

class ExtrasStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _extras = MutableStateFlow(load())

    val extras: StateFlow<ExtraSettings> = _extras.asStateFlow()

    fun update(change: (ExtraSettings) -> ExtraSettings) {
        val next = change(_extras.value)
        prefs.edit {
            putBoolean(KEY_MORNING, next.morningSummary)
            putInt(KEY_MORNING_HOUR, next.morningHour)
            putBoolean(KEY_RECORD, next.recordNotify)
            putBoolean(KEY_SEDENTARY, next.sedentary)
            putString(KEY_WALK_PLAN, next.walkPlan.entries.joinToString(",") { "${it.key.value}:${it.value}" })
            putBoolean(KEY_MONTH_REPORT, next.monthReportReminder)
            putBoolean(KEY_BAND_CELEBRATE, next.bandCelebrate)
            putBoolean(KEY_HEALTH_WRITE, next.healthWrite)
            putBoolean(KEY_APP_LOCK, next.appLock)
            putBoolean(KEY_LOCK_BIOMETRIC, next.lockBiometric)
            putString(KEY_PIN, next.pinHash)
            putBoolean(KEY_SIMPLE, next.simpleView)
            putBoolean(KEY_QUOTE, next.showQuote)
            putString(KEY_SPECIES, next.birdSpecies)
            putString(KEY_BIRD_COLOR, next.birdColor)
            putString(KEY_BIRD_SOUND, next.birdSound)
            putStringSet(KEY_HIDDEN_ITEMS, HashSet(next.hiddenItems))
            putInt(KEY_BPM, next.metronomeBpm)
            putBoolean(KEY_ONBOARDING, next.onboardingDone)
            putStringSet(KEY_UNLOCKED, HashSet(next.unlockedLooks))
            putLong(KEY_SEEDS_SPENT, next.seedsSpent)
            putLong(KEY_FED_ON, next.fedOn)
        }
        _extras.value = next
    }

    fun reload() {
        _extras.value = load()
    }

    /** True when the app was used before 0.17, so the welcome flow is not shown to existing users. */
    fun hasStoredData(context: Context): Boolean =
        context.getSharedPreferences(SettingsStore.PREFS, Context.MODE_PRIVATE).all.isNotEmpty()

    private fun load() = ExtraSettings(
        morningSummary = prefs.getBoolean(KEY_MORNING, false),
        morningHour = prefs.getInt(KEY_MORNING_HOUR, DEFAULT_MORNING_HOUR),
        recordNotify = prefs.getBoolean(KEY_RECORD, false),
        sedentary = prefs.getBoolean(KEY_SEDENTARY, false),
        walkPlan = parsePlan(prefs.getString(KEY_WALK_PLAN, null)),
        monthReportReminder = prefs.getBoolean(KEY_MONTH_REPORT, false),
        bandCelebrate = prefs.getBoolean(KEY_BAND_CELEBRATE, false),
        healthWrite = prefs.getBoolean(KEY_HEALTH_WRITE, false),
        appLock = prefs.getBoolean(KEY_APP_LOCK, false),
        lockBiometric = prefs.getBoolean(KEY_LOCK_BIOMETRIC, false),
        pinHash = prefs.getString(KEY_PIN, null).orEmpty(),
        simpleView = prefs.getBoolean(KEY_SIMPLE, false),
        showQuote = prefs.getBoolean(KEY_QUOTE, true),
        birdSpecies = prefs.getString(KEY_SPECIES, null) ?: DEFAULT_SPECIES,
        birdColor = prefs.getString(KEY_BIRD_COLOR, null) ?: DEFAULT_BIRD_COLOR,
        birdSound = prefs.getString(KEY_BIRD_SOUND, null) ?: DEFAULT_BIRD_SOUND,
        hiddenItems = prefs.getStringSet(KEY_HIDDEN_ITEMS, null).orEmpty().toSet(),
        metronomeBpm = prefs.getInt(KEY_BPM, DEFAULT_BPM).coerceIn(BPM_RANGE),
        onboardingDone = prefs.getBoolean(KEY_ONBOARDING, false),
        unlockedLooks = prefs.getStringSet(KEY_UNLOCKED, null).orEmpty().toSet(),
        seedsSpent = prefs.getLong(KEY_SEEDS_SPENT, 0L),
        fedOn = prefs.getLong(KEY_FED_ON, -1L),
    )

    companion object {
        const val PREFS = "extras"
        private const val KEY_MORNING = "morning_summary"
        private const val KEY_MORNING_HOUR = "morning_hour"
        private const val KEY_RECORD = "record_notify"
        private const val KEY_SEDENTARY = "sedentary"
        private const val KEY_WALK_PLAN = "walk_plan"
        private const val KEY_MONTH_REPORT = "month_report_reminder"
        private const val KEY_BAND_CELEBRATE = "band_celebrate"
        private const val KEY_HEALTH_WRITE = "health_write"
        private const val KEY_APP_LOCK = "app_lock"
        private const val KEY_LOCK_BIOMETRIC = "lock_biometric"
        private const val KEY_PIN = "pin_hash"
        private const val KEY_SIMPLE = "simple_view"
        private const val KEY_QUOTE = "show_quote"
        private const val KEY_SPECIES = "bird_species"
        private const val KEY_BIRD_COLOR = "bird_color"
        private const val KEY_BIRD_SOUND = "bird_sound"
        private const val KEY_HIDDEN_ITEMS = "bird_hidden_items"
        private const val KEY_BPM = "metronome_bpm"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_UNLOCKED = "bird_unlocked"
        private const val KEY_SEEDS_SPENT = "seeds_spent"
        private const val KEY_FED_ON = "bird_fed_on"

        fun parsePlan(text: String?): Map<DayOfWeek, Int> = text.orEmpty().split(',').mapNotNull { part ->
            val day = part.substringBefore(':').toIntOrNull()?.takeIf { it in 1..7 } ?: return@mapNotNull null
            val minute = part.substringAfter(':').toIntOrNull()?.takeIf { it in 0 until 24 * 60 } ?: return@mapNotNull null
            DayOfWeek.of(day) to minute
        }.toMap()

        fun hashPin(pin: String): String =
            MessageDigest.getInstance("SHA-256").digest("fitwren:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

        @Volatile
        private var instance: ExtrasStore? = null

        fun get(context: Context): ExtrasStore = instance ?: synchronized(this) {
            instance ?: ExtrasStore(context.applicationContext).also { instance = it }
        }
    }
}
