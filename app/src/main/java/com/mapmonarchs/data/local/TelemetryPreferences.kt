package com.mapmonarchs.data.local

import android.content.Context

/**
 * Crash-reporting consent. Default OFF, and it stays a real choice.
 *
 * The app's own promise -- Settings: "no background tracking, and no advertising
 * identifier" -- sets the bar. This governs Crashlytics ONLY: a stack trace sent
 * when the app crashes, never behavioural analytics and never an ad id. Absent
 * means never chosen, which is legitimately OFF -- crash reporting begins only
 * when the player turns it on, mirroring TrackingPreferences' explicit-choice
 * stance.
 */
class TelemetryPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mapmonarchs.telemetry", Context.MODE_PRIVATE)

    var crashReportingEnabled: Boolean
        get() = prefs.getBoolean(KEY_CRASH, false)
        set(value) = prefs.edit().putBoolean(KEY_CRASH, value).apply()

    private companion object {
        const val KEY_CRASH = "crash_reporting_enabled"
    }
}