package com.mapmonarchs.data.dto

import com.mapmonarchs.domain.model.HexOwnership
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HexOwnershipDto(
    @SerialName("h3_index") val h3Index: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("strength") val strength: Float,
    @SerialName("last_reinforced_at") val lastReinforcedAt: String
)

fun HexOwnershipDto.toDomain(): HexOwnership = HexOwnership(
    h3Index = h3Index,
    ownerId = ownerId,
    strength = strength,
    // Parsed lazily elsewhere; kept as epoch seconds 0 when unparseable so a bad
    // timestamp degrades the row rather than crashing the whole map fetch.
    lastReinforcedAtEpochSeconds = runCatching {
        java.time.Instant.parse(
            if (lastReinforcedAt.endsWith("Z")) lastReinforcedAt
            else lastReinforcedAt.replace(" ", "T") + "Z"
        ).epochSecond
    }.getOrDefault(0L)
)
