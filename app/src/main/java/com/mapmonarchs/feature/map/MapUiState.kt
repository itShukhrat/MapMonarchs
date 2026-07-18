package com.mapmonarchs.feature.map

import androidx.compose.ui.graphics.Color
import com.mapmonarchs.domain.validation.RejectionReason

/**
 * Whether the player may attempt a capture right now.
 *
 * NOTE: this is a UX affordance only. It exists to avoid pointless round-trips
 * and to give instant feedback. It is NEVER the authority on whether a capture
 * is legal -- attempt_capture() on the server decides that, and re-checks every
 * rule. Nothing here may become load-bearing for correctness or anti-cheat.
 */
enum class CaptureAvailability { AVAILABLE, UNAVAILABLE, IN_FLIGHT }

/**
 * All state the map screen renders. Deliberately a plain data class with no
 * Android or network types so it can be constructed freely in tests.
 */
data class MapUiState(
    val handle: String,
    val territoryColor: Color,
    val hexesHeld: Int,
    val hexesHeldDelta: Int,
    val captureAvailability: CaptureAvailability,
    val locationPermissionGranted: Boolean = false,
    val hasFix: Boolean = false,
    /** Why the last fix was refused locally, if it was. Drives an honest label. */
    val lastRejection: RejectionReason? = null
)

/**
 * The capture button says WHY it is unavailable rather than sitting greyed out.
 *
 * This is a trust decision, not a copy decision. In a game where movement earns
 * territory, a silent dead button reads as "the app is broken" or worse, "the
 * game is cheating me". Naming the reason -- including telling a spoofer plainly
 * that mock location was detected -- makes the rules feel enforced rather than
 * arbitrary.
 */
fun MapUiState.captureLabel(): String = when {
    !locationPermissionGranted -> "LOCATION OFF"
    captureAvailability == CaptureAvailability.IN_FLIGHT -> "CLAIMING"
    captureAvailability == CaptureAvailability.AVAILABLE -> "CLAIM HEX"
    !hasFix -> "ACQUIRING GPS"
    else -> when (lastRejection) {
        RejectionReason.MOCK_LOCATION -> "MOCK LOCATION"
        RejectionReason.LOW_ACCURACY -> "GPS TOO WEAK"
        RejectionReason.TOO_FAST -> "MOVING TOO FAST"
        RejectionReason.TELEPORT -> "SIGNAL JUMPED"
        null -> "OUT OF RANGE"
    }
}