package com.sh7411usa.shliachtzibbur.ui.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Supported in-app languages. English is the base resource set; the rest have
 * `values-<code>` translations. Yiddish, Hebrew and Arabic render right-to-left.
 *
 * Hebrew uses the legacy ISO code `iw` for resource folders (Android maps `he`
 * to `iw` on older devices); the BCP-47 tag stored in preferences is `he`.
 */
enum class AppLanguage(val tag: String, val endonym: String, val isRtl: Boolean) {
    SYSTEM(tag = "", endonym = "", isRtl = false),
    ENGLISH(tag = "en", endonym = "English", isRtl = false),
    YIDDISH(tag = "yi", endonym = "ייִדיש", isRtl = true),
    HEBREW(tag = "he", endonym = "עברית", isRtl = true),
    ARABIC(tag = "ar", endonym = "العربية", isRtl = true),
    SPANISH(tag = "es", endonym = "Español", isRtl = false);

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == (tag ?: "") } ?: SYSTEM
    }
}

object LocaleManager {
    /** Apply a BCP-47 [tag], or "" to follow the system language. */
    fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun current(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return AppLanguage.SYSTEM
        val language = locales[0]?.language ?: return AppLanguage.SYSTEM
        // Normalise the legacy Hebrew code.
        val normalised = if (language == "iw") "he" else language
        return AppLanguage.fromTag(normalised)
    }
}
