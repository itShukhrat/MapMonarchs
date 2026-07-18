package com.mapmonarchs.domain.model

/**
 * Server-owned territory state. The client only ever receives this -- it never
 * constructs one and pushes it up.
 */
data class HexOwnership(
    val h3Index: String,
    val ownerId: String,
    val strength: Float,
    val lastReinforcedAtEpochSeconds: Long
)

/** Result of a capture attempt, as decided by the server. */
sealed interface CaptureOutcome {
    data class Applied(val h3Index: String, val action: String, val ownerId: String) : CaptureOutcome
    data class Rejected(val reason: String) : CaptureOutcome
    data class Failed(val message: String) : CaptureOutcome
}