package com.mapmonarchs.domain.model

/**
 * Result of a capture attempt, as decided by the server.
 *
 * (The per-hex ownership row that used to be modelled here is gone: the client no
 * longer reads raw hex_ownership to draw territory. It receives finished geometry
 * from the server -- see [TerritoryGeometry] -- so there is nothing for a client
 * HexOwnership type to hold.)
 */
sealed interface CaptureOutcome {
    data class Applied(val h3Index: String, val action: String, val ownerId: String) : CaptureOutcome
    data class Rejected(val reason: String) : CaptureOutcome
    data class Failed(val message: String) : CaptureOutcome
}