package com.mapmonarchs.telemetry

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * The single place Firebase data collection is switched on or off.
 *
 * Firebase is ADDITIVE here -- Supabase remains the backend of record. Two of the
 * three collection switches are pinned OFF and never exposed, because the app's
 * shipped promise ("no background tracking, and no advertising identifier")
 * forbids them: Analytics behavioural collection and the advertising id. The
 * manifest already defaults them off; this re-asserts the analytics one at
 * runtime so a future Firebase default-flip cannot start collecting silently.
 *
 * What the player can actually consent to is crash reporting alone: Crashlytics
 * uploads a stack trace when the app crashes -- diagnostic, not behavioural, and
 * nothing in the background. Its collection flag is persisted by Crashlytics
 * across launches, so setting it here on every startup keeps it in lockstep with
 * the stored consent (including a downgrade back to false).
 */
object FirebaseTelemetry {

    /** Applies the player's crash-reporting consent. Call at startup and on change. */
    fun setCrashReportingEnabled(context: Context, enabled: Boolean) {
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        // Behavioural analytics is never enabled: hold it explicitly off so the
        // "no background tracking" promise cannot be broken by a default change.
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(false)
    }
}