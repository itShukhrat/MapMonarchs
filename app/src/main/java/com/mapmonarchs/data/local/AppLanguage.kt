package com.mapmonarchs.data.local

import androidx.annotation.StringRes
import com.mapmonarchs.R

/**
 * The languages the app ships translated resources for.
 *
 * SYSTEM is first and is the default: an empty tag means "no per-app override",
 * so the app follows the device language and English stays the base fallback for
 * anything a locale has not translated. Each real language is labelled by its
 * endonym -- a Russian speaker scanning the list finds "Русский", not a
 * translation of the word, which is the one label that never needs translating.
 */
enum class AppLanguage(
    /** BCP-47 tag, or "" for "follow the system". */
    val tag: String,
    @StringRes val labelRes: Int,
    @StringRes val detailRes: Int
) {
    SYSTEM("", R.string.settings_language_system, R.string.settings_language_system_detail),
    ENGLISH("en", R.string.lang_autonym_english, R.string.settings_language_name_english),
    RUSSIAN("ru", R.string.lang_autonym_russian, R.string.settings_language_name_russian),
    UZBEK("uz", R.string.lang_autonym_uzbek, R.string.settings_language_name_uzbek);

    companion object {
        /**
         * Which row a stored/active tag corresponds to. Matches on the language
         * subtag so a region-qualified value ("ru-RU" from LocaleManager) still
         * resolves. Absent, empty, or unknown all mean SYSTEM -- absence is a
         * legitimate state here, exactly as it is for [TrackingMode].
         */
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.isNotEmpty() && tag != null && tag.startsWith(it.tag) }
                ?: SYSTEM
    }
}
