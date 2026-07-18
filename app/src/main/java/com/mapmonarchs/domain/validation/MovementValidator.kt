package com.mapmonarchs.domain.validation

import com.mapmonarchs.domain.model.LocationFix
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class RejectionReason { MOCK_LOCATION, LOW_ACCURACY, TOO_FAST, TELEPORT }

sealed interface MovementVerdict {
    data object Plausible : MovementVerdict
    data class Implausible(val reason: RejectionReason) : MovementVerdict
}

/**
 * Thresholds mirrored from attempt_capture() in supabase/schema.sql.
 *
 * If you change a value here, you MUST change it there. These constants exist to
 * avoid pointless round-trips, not to enforce anything.
 */
object MovementRules {
    const val MAX_ACCURACY_METERS = 30f
    const val MAX_SPEED_MPS = 7f          // ~25 km/h: above walking/jogging
    const val MAX_IMPLIED_SPEED_MPS = 15f // between two fixes => teleport
    const val EARTH_RADIUS_METERS = 6_371_000.0
}

/**
 * Client-side plausibility filter.
 *
 * !! THIS IS NOT SECURITY !!
 *
 * It is a UX affordance: it greys out the capture button and avoids firing
 * requests that the server would certainly reject. Every rule here is re-checked
 * server-side inside attempt_capture(), which is the only authority. A modified
 * client can bypass every line of this file and still gain nothing, because it
 * cannot write territory -- RLS grants it no write path.
 *
 * Deliberately pure: no Android types, no clock, no I/O. Fully unit-testable.
 */
class MovementValidator {

    fun validate(current: LocationFix, previousAccepted: LocationFix?): MovementVerdict {
        if (current.isMock) return MovementVerdict.Implausible(RejectionReason.MOCK_LOCATION)

        current.accuracyMeters?.let {
            if (it > MovementRules.MAX_ACCURACY_METERS) {
                return MovementVerdict.Implausible(RejectionReason.LOW_ACCURACY)
            }
        }

        current.speedMetersPerSecond?.let {
            if (it > MovementRules.MAX_SPEED_MPS) {
                return MovementVerdict.Implausible(RejectionReason.TOO_FAST)
            }
        }

        if (previousAccepted != null) {
            val elapsedSeconds =
                (current.elapsedRealtimeMillis - previousAccepted.elapsedRealtimeMillis) / 1000.0
            // Guard against divide-by-zero and non-monotonic input. Two fixes at
            // the same instant carry no speed information, so we cannot judge them.
            if (elapsedSeconds > 0.0) {
                val impliedSpeed = distanceMeters(previousAccepted, current) / elapsedSeconds
                if (impliedSpeed > MovementRules.MAX_IMPLIED_SPEED_MPS) {
                    return MovementVerdict.Implausible(RejectionReason.TELEPORT)
                }
            }
        }

        return MovementVerdict.Plausible
    }

    /** Great-circle distance in metres. Mirrors the Haversine used server-side. */
    fun distanceMeters(from: LocationFix, to: LocationFix): Double {
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(from.latitude)) *
            cos(Math.toRadians(to.latitude)) *
            sin(dLng / 2).pow(2)
        return 2 * MovementRules.EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }
}