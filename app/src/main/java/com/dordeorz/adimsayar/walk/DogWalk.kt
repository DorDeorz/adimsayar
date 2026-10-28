package com.dordeorz.adimsayar.walk

import android.content.Context
import androidx.core.content.edit
import com.dordeorz.adimsayar.data.Walk
import com.dordeorz.adimsayar.data.Walks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A dog walk: started and stopped by hand, its steps are the day's steps in between. */
object DogWalk {

    private const val PREFS = "dog_walk"
    private const val KEY_START = "start"
    private const val KEY_STEPS = "steps"

    private val _started = MutableStateFlow<Long?>(null)

    /** When the running dog walk started, or null. */
    val started: StateFlow<Long?> = _started.asStateFlow()

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _started.value = prefs.getLong(KEY_START, 0L).takeIf { it > 0L }
    }

    fun start(context: Context, todaySteps: Long) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_START, now)
            putLong(KEY_STEPS, todaySteps)
        }
        _started.value = now
    }

    suspend fun stop(context: Context, todaySteps: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val start = prefs.getLong(KEY_START, 0L)
        val startSteps = prefs.getLong(KEY_STEPS, 0L)
        prefs.edit { clear() }
        _started.value = null
        if (start <= 0L) return
        // A walk over midnight keeps today's steps only, since the day's count started again.
        val steps = if (todaySteps >= startSteps) todaySteps - startSteps else todaySteps
        Walks.save(context, Walk(kind = Walks.KIND_DOG, start = start, end = System.currentTimeMillis(), steps = steps, meters = 0.0, path = null))
    }
}
