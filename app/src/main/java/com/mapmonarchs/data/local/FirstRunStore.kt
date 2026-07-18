package com.mapmonarchs.data.local

import android.content.Context

/**
 * Remembers whether first-run priming has been shown.
 *
 * SharedPreferences rather than DataStore: one boolean, read once at startup.
 * DataStore would add a dependency and a coroutine surface for a single flag.
 */
class FirstRunStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mapmonarchs.firstrun", Context.MODE_PRIVATE)

    fun hasCompletedOnboarding(): Boolean = prefs.getBoolean(KEY_ONBOARDED, false)

    fun markOnboardingComplete() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    private companion object {
        const val KEY_ONBOARDED = "onboarding_complete"
    }
}