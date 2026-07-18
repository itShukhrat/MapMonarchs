package com.mapmonarchs.data.local

import android.content.Context

/**
 * Tracking cadence. This is a REAL setting with a real cost.
 *
 * Battery drain from continuous location is the single largest uninstall driver
 * in this genre, so the player is given the trade explicitly rather than having
 * it decided for them. BALANCED is the default because a territory game does not
 * need sub-second fixes to work.
 */
enum class TrackingMode(val label: String, val detail: String, val intervalMillis: Long) {
    PRECISE("PRECISE", "Fastest updates. Highest battery cost.", 2_000L),
    BALANCED("BALANCED", "Updates every few seconds. Recommended.", 5_000L),
    CONSERVATIVE("CONSERVATIVE", "Slower updates. Lowest battery cost.", 12_000L);

    companion object {
        /**
         * Resolves a stored value explicitly. No runCatching, nothing swallowed.
         *
         * Absent means "never chosen" and unknown means "written by a different
         * app version" -- both are legitimately the default, unlike a malformed
         * SERVER value, which is corruption and must throw (see
         * parseServerTimestamp). A default is only acceptable when absence is
         * itself a valid state.
         */
        fun resolve(stored: String?): TrackingMode =
            entries.firstOrNull { it.name == stored } ?: BALANCED
    }
}

class TrackingPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mapmonarchs.tracking", Context.MODE_PRIVATE)

    var mode: TrackingMode
        get() = TrackingMode.resolve(prefs.getString(KEY_MODE, null))
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    private companion object {
        const val KEY_MODE = "tracking_mode"
    }
}