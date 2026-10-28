package com.dordeorz.adimsayar

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.ui.theme.AdimSayarTheme
import com.dordeorz.adimsayar.ui.theme.isDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalDate

/**
 * Answers "How many steps did I take today?" from Google Assistant (App Actions) or the launcher
 * shortcut: shows the answer and reads it aloud.
 */
class StepAnswerActivity : ComponentActivity() {

    private var answer by mutableStateOf<String?>(null)
    private var speech: TextToSpeech? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Locales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsStore.get(applicationContext).settings.value
        setContent {
            AdimSayarTheme(darkTheme = isDarkTheme(settings.themeMode), dynamicColor = settings.dynamicColor) {
                answer?.let { text ->
                    AlertDialog(
                        onDismissRequest = ::finish,
                        title = { Text(getString(R.string.app_name)) },
                        text = { Text(text) },
                        confirmButton = { TextButton(onClick = ::finish) { Text(getString(R.string.close)) } },
                    )
                }
            }
        }
        AppScope.launch(Dispatchers.Main) {
            val steps = withContext(Dispatchers.IO) { StepRepository.get(applicationContext).today() }
            if (isFinishing) return@launch
            val goal = settings.goalOn(LocalDate.now())
            val format = NumberFormat.getIntegerInstance(Locales.current)
            val text = if (steps >= goal) {
                getString(R.string.answer_steps_done, format.format(steps))
            } else {
                getString(R.string.answer_steps, format.format(steps), format.format(goal - steps))
            }
            answer = text
            speech = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    speech?.language = Locales.current
                    speech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
                }
            }
        }
    }

    override fun onDestroy() {
        speech?.shutdown()
        speech = null
        super.onDestroy()
    }

    private companion object {
        const val UTTERANCE = "fitwren-steps"
    }
}
