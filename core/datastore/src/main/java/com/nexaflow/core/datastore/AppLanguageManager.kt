package com.nexaflow.core.datastore

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Owns NexaFlow's per-app language preference.
 *
 * AppCompat delegates to Android's LocaleManager on Android 13+ and keeps the
 * same preference persisted on Android 12 and lower. This means an in-app
 * change and Settings > App language always describe one shared preference.
 */
object AppLanguageManager {

    /** BCP-47 tags backed by app resources and declared in locales_config.xml. */
    val supportedLanguageTags: Set<String> = linkedSetOf(
        "en",
        "ar",
        "de",
        "es",
        "fr",
        "hi",
        "ja",
        "pt",
        "ru",
        "tr",
        "zh-CN"
    )

    /**
     * Returns the explicitly selected language or null when NexaFlow follows
     * the device language. The Context parameter keeps the API convenient for
     * Compose callers and reserves room for future platform-specific behavior.
     */
    @Suppress("UNUSED_PARAMETER")
    fun selectedLanguageTag(context: Context): String? {
        val locales = AppCompatDelegate.getApplicationLocales()
        return normalizeSupportedLanguageTag(locales[0]?.toLanguageTag())
    }

    /**
     * Normalizes region/script variants exposed by Android back to the one tag
     * NexaFlow ships for that language. For example, Android may report
     * `zh-Hans-CN` while the app resource configuration is `zh-CN`.
     */
    internal fun normalizeSupportedLanguageTag(tag: String?): String? {
        if (tag == null) return null
        if (tag in supportedLanguageTags) return tag
        return when (Locale.forLanguageTag(tag).language) {
            "en", "ar", "de", "es", "fr", "hi", "ja", "pt", "ru", "tr" ->
                Locale.forLanguageTag(tag).language
            "zh" -> "zh-CN"
            else -> null
        }
    }

    /**
     * Stores an explicit app language, or null to follow the device language.
     * AppCompat recreates the host Activity when the configuration changes.
     */
    @Suppress("UNUSED_PARAMETER")
    fun setLanguage(context: Context, languageTag: String?) {
        require(languageTag == null || languageTag in supportedLanguageTags) {
            "Unsupported app language tag: $languageTag"
        }
        val locales = languageTag?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
