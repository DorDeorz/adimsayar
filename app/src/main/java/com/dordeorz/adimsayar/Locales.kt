package com.dordeorz.adimsayar

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import com.dordeorz.adimsayar.data.AppLanguage
import com.dordeorz.adimsayar.data.SettingsStore
import java.util.Locale

object Locales {

    private val TURKISH: Locale = Locale.forLanguageTag("tr-TR")
    private val ENGLISH: Locale = Locale.forLanguageTag("en-US")

    @Volatile
    var current: Locale = TURKISH
        private set

    fun resolve(language: AppLanguage): Locale = when (language) {
        AppLanguage.Turkish -> TURKISH
        AppLanguage.English -> ENGLISH
        AppLanguage.System -> {
            val system = ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]
            if (system?.language == ENGLISH.language) system else TURKISH
        }
    }

    fun wrap(context: Context): Context {
        val locale = resolve(SettingsStore.get(context).settings.value.language)
        current = locale
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
