package com.dordeorz.adimsayar

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import com.dordeorz.adimsayar.data.SUPPORTED_LANGUAGES
import com.dordeorz.adimsayar.data.SYSTEM_LANGUAGE
import com.dordeorz.adimsayar.data.SettingsStore
import java.util.Locale

object Locales {

    private val FALLBACK: Locale = Locale.forLanguageTag("en")

    @Volatile
    var current: Locale = resolve(SYSTEM_LANGUAGE)
        private set

    fun resolve(language: String): Locale {
        if (language != SYSTEM_LANGUAGE) return Locale.forLanguageTag(language)
        val system = ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]
        return if (system != null && system.language in SUPPORTED_LANGUAGES) system else FALLBACK
    }

    fun displayName(language: String): String {
        val locale = Locale.forLanguageTag(language)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    fun wrap(context: Context): Context {
        val locale = resolve(SettingsStore.get(context).settings.value.language)
        current = locale
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
