package com.mapmonarchs.data.dto

import com.mapmonarchs.domain.model.Entitlement
import com.mapmonarchs.domain.model.isPro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire-to-domain mappings, where a subtle bug would turn server truth into a
 * client lie. Pure functions, pinned without a network.
 */
class ProfileDtosTest {

    private val now = 1_700_000_000_000L // fixed "now" for expiry boundaries

    // ---- Entitlement expiry ------------------------------------------------

    @Test
    fun proWithNoExpiryIsActive() {
        assertTrue(EntitlementDto(tier = "pro", expiresAt = null).toEntitlement(now).isPro())
    }

    @Test
    fun proThatHasLapsedIsFreeAgain() {
        // A subscription whose expires_at is in the past is over. Honouring it
        // would grant a capability the player no longer holds.
        val pastIso = "2000-01-01T00:00:00Z"
        assertEquals(Entitlement.Free, EntitlementDto(tier = "pro", expiresAt = pastIso).toEntitlement(now))
    }

    @Test
    fun proStillWithinItsWindowIsActive() {
        val futureIso = "2999-01-01T00:00:00+00:00" // note: offset form, not just Z
        assertTrue(EntitlementDto(tier = "pro", expiresAt = futureIso).toEntitlement(now).isPro())
    }

    @Test
    fun anUnknownTierIsTreatedAsFreeNotProByAccident() {
        // Defensive: the check column only allows 'pro', but if the server ever
        // returned something else, it must NOT read as Pro.
        assertEquals(Entitlement.Free, EntitlementDto(tier = "free", expiresAt = null).toEntitlement(now))
    }

    // ---- Records: null stays null (unknown), never coerced to zero ---------

    @Test
    fun emptyRecordsDtoMapsToUnknownNotZero() {
        val domain = PlayerRecordsDto().toDomain()
        assertTrue(domain.isEmpty)
        assertNull(domain.totalCaptures)
        assertNull(domain.currentStreakDays)
        assertNull(domain.peakHexesHeld)
    }

    @Test
    fun populatedRecordsDtoMapsEveryField() {
        val domain = PlayerRecordsDto(
            firstCapture = "2026-01-02T03:04:05Z",
            totalCaptures = 12,
            currentStreakDays = 3,
            longestStreakDays = 9,
            totalHexesHeld = 40,
            peakHexesHeld = 7,
        ).toDomain()
        assertFalse(domain.isEmpty)
        assertEquals(12, domain.totalCaptures)
        assertEquals(3, domain.currentStreakDays)
        assertEquals(9, domain.longestStreakDays)
        assertEquals(40, domain.totalHexesEverHeld)
        assertEquals(7, domain.peakHexesHeld)
        assertEquals(parseTimestampMillis("2026-01-02T03:04:05Z"), domain.firstCaptureEpochMillis)
    }

    // ---- Badges: rows -> key/earned-at map ---------------------------------

    @Test
    fun earnedBadgeRowsMapByKey() {
        val map = listOf(
            UserBadgeDto("first_capture", "2026-01-01T00:00:00Z"),
            UserBadgeDto("streak_7", "2026-02-01T00:00:00Z"),
        ).toEarnedMap()
        assertEquals(2, map.size)
        assertTrue(map.containsKey("first_capture"))
        assertEquals(parseTimestampMillis("2026-02-01T00:00:00Z"), map["streak_7"])
    }

    @Test
    fun timestampParsingAcceptsBothZuluAndOffsetForms() {
        assertEquals(
            parseTimestampMillis("2026-01-01T00:00:00Z"),
            parseTimestampMillis("2026-01-01T00:00:00+00:00"),
        )
    }
}
