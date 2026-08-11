package com.mapmonarchs.data.local

import androidx.annotation.StringRes
import android.content.Context
import com.mapmonarchs.R

/**
 * Tracking cadence. This is a REAL setting with a real cost.
 *
 * Battery drain from continuous location is the single largest uninstall driver
 * in this genre, so the player is given the trade explicitly rather than having
 * it decided for them. BALANCED is the default because a territory game does not
 * need sub-second fixes to work.
 */
enum class TrackingMode(
    @StringRes val labelRes: Int,
    @StringRes val detailRes: Int,
    val intervalMillis: Long
) {
    PRECISE(R.string.cadence_precise, R.string.cadence_precise_detail, 2_000L),
    BALANCED(R.string.cadence_balanced, R.string.cadence_balanced_detail, 5_000L),
    CONSERVATIVE(R.string.cadence_conservative, R.string.cadence_conservative_detail, 12_000L);

    companion object {
        /**
         * Resolves a stored value explicitly. No runCatching, nothing swallowed.
         *
         * Absent means "never chosen" and unknown means "written by a different
         * app version" -- both are legitimately the default. A default is only
         * acceptable when absence is itself a valid state, unlike corrupt server
         * data, which must surface rather than silently become a plausible value.
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