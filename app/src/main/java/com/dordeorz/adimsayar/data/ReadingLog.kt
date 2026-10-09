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
    Xiaomi(R.string.source_xiaomi),
}

class ReadingLog private constructor(private val context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
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
            file.writeText(updated.joinToString("\n"))
        } catch (e: java.io.IOException) {
            return
        }
        _lines.value = updated
    }

    private fun load(): List<String> =
        try {
            if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()
        } catch (e: java.io.IOException) {
            emptyList()
        }

    companion object {
        private const val FILE_NAME = "okumalar.txt"
        private const val MAX_LINES = 60
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")

        @Volatile
        private var instance: ReadingLog? = null

        fun get(context: Context): ReadingLog = instance ?: synchronized(this) {
            instance ?: ReadingLog(context.applicationContext).also { instance = it }
        }
    }
}
