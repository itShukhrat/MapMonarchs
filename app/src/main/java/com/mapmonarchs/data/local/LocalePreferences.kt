package com.mapmonarchs.data.local

import android.content.Context

/**
 * Persists the chosen in-app language for API levels below 33.
 *
 * Only needed there: from API 33 the framework LocaleManager stores and applies
 * the per-app locale itself. Below it there is no platform store, so the tag
 * lives here and [AppLocale] re-applies it in attachBaseContext on every launch.
 * "" means "follow the system".
 */
class LocalePreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mapmonarchs.locale", Context.MODE_PRIVATE)

    var languageTag: String
        get() = prefs.getString(KEY_TAG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TAG, value).apply()

    private companion object {
        const val KEY_TAG = "language_tag"
    }
}
