package com.mapmonarchs.feature.map

import androidx.compose.ui.graphics.Color

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
    val captureAvailability: CaptureAvailability
)