package com.mapmonarchs.data.dto

import com.mapmonarchs.domain.model.Entitlement
import com.mapmonarchs.domain.model.PlayerRecords
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/**
 * Wire shapes for the profile surfaces, plus their mappings to domain.
 *
 * The mappers are pure and total so they can be unit-tested without a network:
 * timestamp parsing, entitlement expiry, and null-preserving record aggregation
 * are exactly the places a subtle bug would quietly turn server truth into a
 * client lie, so they are kept out of the repository's IO body and pinned by
 * tests instead.
 */

/** A row of public.user_badges. */
@Serializable
data class UserBadgeDto(
    @SerialName("badge_key") val badgeKey: String,
    @SerialName("earned_at") val earnedAt: String,
)

/**
 * Just the key of a public.badge_definitions row. Used only to COUNT the
 * catalogue for the deployment probe, so it deliberately pulls one column rather
 * than the whole definition -- the probe cares that the table answers and how
 * many keys it serves, nothing more.
 */
@Serializable
data class BadgeKeyDto(
    val key: String,
)

/** A row of public.entitlements. Absence of a row means Free -- not modelled here. */
@Serializable
data class EntitlementDto(
    val tier: String,
    @SerialName("expires_at") val expiresAt: String? = null,
)

/** The single row returned by public.get_player_records(uid). */
@Serializable
data class PlayerRecordsDto(
    @SerialName("first_capture") val firstCapture: String? = null,
    @SerialName("total_captures") val totalCaptures: Long? = null,
    @SerialName("current_streak_days") val currentStreakDays: Int? = null,
    @SerialName("longest_streak_days") val longestStreakDays: Int? = null,
    @SerialName("total_hexes_held") val totalHexesHeld: Long? = null,
    @SerialName("peak_hexes_held") val peakHexesHeld: Long? = null,
)

/** ISO-8601 timestamptz (with offset or Z) -> epoch millis. */
internal fun parseTimestampMillis(iso: String): Long =
    OffsetDateTime.parse(iso).toInstant().toEpochMilli()

/**
 * Resolve an entitlement row to a tier.
 *
 * Pro only if the row says 'pro' AND it has not lapsed. A row with a past
 * expires_at is a subscription that ended; honouring it would be granting a
 * capability the player no longer holds. [now] is injected so the expiry
 * boundary is testable.
 */
fun EntitlementDto.toEntitlement(now: Long = System.currentTimeMillis()): Entitlement {
    val active = tier == "pro" && (expiresAt == null || parseTimestampMillis(expiresAt) > now)
    return if (active) Entitlement.Pro else Entitlement.Free
}

/** Aggregate row -> domain, preserving null as unknown (never coercing to zero). */
fun PlayerRecordsDto.toDomain(): PlayerRecords = PlayerRecords(
    firstCaptureEpochMillis = firstCapture?.let { parseTimestampMillis(it) },
    currentStreakDays = currentStreakDays,
    longestStreakDays = longestStreakDays,
    totalHexesEverHeld = totalHexesHeld?.toInt(),
    peakHexesHeld = peakHexesHeld?.toInt(),
    totalCaptures = totalCaptures?.toInt(),
)

/** Rows of earned badges -> key -> earned-at millis. */
fun List<UserBadgeDto>.toEarnedMap(): Map<String, Long> =
    associate { it.badgeKey to parseTimestampMillis(it.earnedAt) }
