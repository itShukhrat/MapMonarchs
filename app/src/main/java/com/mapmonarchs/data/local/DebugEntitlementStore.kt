package com.mapmonarchs.data.local

import android.content.Context

/**
 * A QA-only override that lets a tester see the Pro-gated UI on a device without
 * a real purchase.
 *
 * WHY THIS IS SAFE, AND WHY IT IS NOT DISHONEST. Real Pro is server-driven: the
 * entitlements table, written only by verified purchase or an admin grant, is
 * the source of truth (see SupabaseProfileRepository). This flag never touches
 * that table and never reaches the server -- it only flips what the CLIENT
 * displays, and only in a debug build. Every place that honours it is guarded by
 * BuildConfig.DEBUG, so a release build ignores the stored value entirely; there
 * is no code path by which a shipped app can grant itself Pro from here.
 *
 * It therefore cannot forge a real benefit either: avatar upload is gated by a
 * Storage RLS policy that checks the entitlements TABLE and the user's session,
 * so a debug-Pro client with no server entitlement still cannot upload. What it
 * unlocks is exactly the display states -- tier PRO, the avatar row reading
 * AVAILABLE, the subscription screen's active-plan copy -- which is precisely
 * what a tester needs to verify and nothing more.
 */
class DebugEntitlementStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mapmonarchs.debug", Context.MODE_PRIVATE)

    var simulatePro: Boolean
        get() = prefs.getBoolean(KEY_SIMULATE_PRO, false)
        set(value) = prefs.edit().putBoolean(KEY_SIMULATE_PRO, value).apply()

    private companion object {
        const val KEY_SIMULATE_PRO = "simulate_pro"
    }
}
