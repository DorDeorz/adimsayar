package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.annotation.StringRes
import com.dordeorz.adimsayar.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class ReadSource(@param:StringRes val label: Int) {
    App(R.string.source_app),
    Job(R.string.source_job),
    System(R.string.source_system),
    Widget(R.string.source_widget),
    Service(R.string.source_service),
}

class ReadingLog private constructor(private val context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    // Lines added to the file since it was last cut back to its newest lines.
    private var added = 0
    private val _lines = MutableStateFlow(load())

    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun reading(source: ReadSource, reading: CounterReading, interval: StepInterval) {
        val text = when (interval.kind) {
            ReadingKind.Reboot -> R.string.log_reboot
            ReadingKind.Reset -> R.string.log_reset
            ReadingKind.Stale -> R.string.log_stale
            ReadingKind.Baseline, ReadingKind.Steps -> R.string.log_reading
        }
        add(source, context.getString(text, reading.counter, interval.steps))
    }

    fun failure(source: ReadSource, @StringRes reason: Int) = add(source, context.getString(reason))

    fun note(source: ReadSource, text: String) = add(source, text)

    @Synchronized
    private fun add(source: ReadSource, text: String) {
        val line = "${LocalDateTime.now().format(TIME_FORMAT)} · ${context.getString(source.label)} · $text"
        val updated = (listOf(line) + _lines.value).take(MAX_LINES)
        try {
            // Adding a line is cheap; the whole file is written again only now and then, to drop old lines.
            if (added >= TRIM_AFTER) {
                store(updated)
            } else {
                file.appendText(line + "\n")
                added++
            }
        } catch (e: java.io.IOException) {
            return
        }
        _lines.value = updated
    }

    /** The newest line first, as the log is shown. */
    private fun load(): List<String> =
        try {
            val old = File(context.filesDir, OLD_FILE_NAME)
            when {
                // Up to 0.21 the file held 60 lines, newest first, and was written again for every line.
                old.exists() -> old.readLines().filter { it.isNotBlank() }.also {
                    store(it)
                    old.delete()
                }
                file.exists() -> {
                    val all = file.readLines().filter { it.isNotBlank() }
                    added = (all.size - MAX_LINES).coerceAtLeast(0)
                    all.takeLast(MAX_LINES).asReversed()
                }
                else -> emptyList()
            }
        } catch (e: java.io.IOException) {
            emptyList()
        }

    /** Writes the file again with [lines] (newest first) in the order lines are added: oldest first. */
    private fun store(lines: List<String>) {
        file.writeText(lines.asReversed().joinToString("") { it + "\n" })
        added = 0
    }

    companion object {
        private const val FILE_NAME = "okuma-kaydi.txt"
        private const val OLD_FILE_NAME = "okumalar.txt"

        // About a day and a half of background readings, so the morning's band sync can still be read in the evening.
        private const val MAX_LINES = 400
        private const val TRIM_AFTER = 200
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")

        @Volatile
        private var instance: ReadingLog? = null

        fun get(context: Context): ReadingLog = instance ?: synchronized(this) {
            instance ?: ReadingLog(context.applicationContext).also { instance = it }
        }
    }
}
