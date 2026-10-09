package com.dordeorz.adimsayar

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.text.TextUtils
import android.view.View
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
        val system = ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]
        val locale = when {
            language != SYSTEM_LANGUAGE -> Locale.forLanguageTag(language)
            system != null && system.language in SUPPORTED_LANGUAGES -> system
            else -> FALLBACK
        }
        return Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()
    }

    fun isRtl(): Boolean = TextUtils.getLayoutDirectionFromLocale(current) == View.LAYOUT_DIRECTION_RTL

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
