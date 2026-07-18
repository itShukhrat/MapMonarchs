package com.mapmonarchs.domain.model

/**
 * A single raw position reading. This is EVIDENCE, not a claim about territory.
 *
 * Note what is absent: there is no h3Index field. The client never names the hex
 * it thinks it is standing in -- the server derives that from lat/lng inside
 * attempt_capture(). If the client named the hex, spoofing would be trivial.
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val isMock: Boolean,
    /**
     * Monotonic device uptime, NOT wall-clock. Used only for local rate/interval
     * reasoning. Wall-clock time from a client is attacker-controlled and is
     * never trusted for scoring -- the server stamps everything with now().
     */
    val elapsedRealtimeMillis: Long
)