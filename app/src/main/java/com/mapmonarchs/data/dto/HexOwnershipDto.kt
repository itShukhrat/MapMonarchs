package com.mapmonarchs.data.dto

import com.mapmonarchs.domain.model.HexOwnership
import com.mapmonarchs.domain.model.MalformedTimestampException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeParseException

@Serializable
data class HexOwnershipDto(
    @SerialName("h3_index") val h3Index: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("strength") val strength: Float,
    @SerialName("last_reinforced_at") val lastReinforcedAt: String
)

/**
 * Maps to the domain, THROWING on a bad timestamp.
 *
 * The previous version used getOrDefault(0L), which turned an unparseable
 * timestamp into epoch 0 (1970). Fed into
 *   effective_strength = strength - hoursSince(lastReinforced) * decayRate
 * that reads as infinitely decayed, i.e. neutral -- so a hex somebody owns would
 * silently render as unclaimed. In a game whose whole premise is "I own this and
 * can lose it", that is data corruption wearing a helpful face.
 */
fun HexOwnershipDto.toDomain(): HexOwnership = HexOwnership(
    h3Index = h3Index,
    ownerId = ownerId,
    strength = strength,
    lastReinforcedAtEpochSeconds = parseServerTimestamp(h3Index, lastReinforcedAt)
)

/**
 * Postgres timestamptz arrives in a few shapes depending on client settings
 * ("2026-07-18T10:00:00Z", "2026-07-18 10:00:00+00", "...:00.123456").
 * Anything we cannot read is an error, never a guess.
 */
internal fun parseServerTimestamp(h3Index: String, raw: String): Long {
    val normalised = raw.trim().let { s ->
        val withT = if (s.contains('T')) s else s.replaceFirst(' ', 'T')
        when {
            withT.endsWith("Z") -> withT
            Regex("[+-]\\d{2}$").containsMatchIn(withT) -> withT + ":00"
            Regex("[+-]\\d{2}:\\d{2}$").containsMatchIn(withT) -> withT
            else -> withT + "Z"
        }
    }
    return try {
        Instant.parse(normalised).epochSecond
    } catch (e: DateTimeParseException) {
        throw MalformedTimestampException(h3Index, raw)
    }
}