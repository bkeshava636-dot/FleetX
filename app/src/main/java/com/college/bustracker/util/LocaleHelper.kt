package com.college.bustracker.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Manages in-app language selection (English & Kannada).
 * Persists the user's preference in SharedPreferences and provides dynamic Compose updates
 * without requiring app restart or altering system device language.
 */
object LocaleHelper {

    const val PREFS_NAME = "fleetx_language_prefs"
    const val KEY_LANGUAGE = "selected_language"

    const val LANGUAGE_EN = "en"
    const val LANGUAGE_KN = "kn"

    private val _currentLanguage = MutableStateFlow(LANGUAGE_EN)
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    fun init(context: Context) {
        val savedLang = getSavedLanguage(context)
        _currentLanguage.value = savedLang
        applyLocale(context, savedLang)
    }

    fun getSavedLanguage(context: Context): String {
        val prefs = getPrefs(context)
        val saved = prefs.getString(KEY_LANGUAGE, null)
        if (saved != null) return saved

        // Default: If device language is Kannada, use Kannada; otherwise English
        val deviceLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locale
        }
        return if (deviceLocale.language.equals(LANGUAGE_KN, ignoreCase = true)) {
            LANGUAGE_KN
        } else {
            LANGUAGE_EN
        }
    }

    fun setLanguage(context: Context, langCode: String) {
        val cleanLang = if (langCode == LANGUAGE_KN) LANGUAGE_KN else LANGUAGE_EN
        getPrefs(context).edit().putString(KEY_LANGUAGE, cleanLang).apply()
        _currentLanguage.value = cleanLang
        applyLocale(context, cleanLang)
    }

    fun isKannada(context: Context? = null): Boolean {
        return _currentLanguage.value == LANGUAGE_KN ||
                (context != null && getSavedLanguage(context) == LANGUAGE_KN)
    }

    /**
     * Updates the JVM default locale and configuration for the given context.
     */
    fun applyLocale(context: Context, langCode: String): Context {
        val locale = Locale(langCode)
        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        config.setLayoutDirection(locale)

        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)

        return context.createConfigurationContext(config)
    }

    /**
     * Creates a Configuration-wrapped context for a specific language code.
     */
    fun getLocalizedContext(context: Context, langCode: String): Context {
        val locale = Locale(langCode)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
