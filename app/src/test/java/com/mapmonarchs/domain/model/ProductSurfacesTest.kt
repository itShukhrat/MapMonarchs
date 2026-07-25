package com.mapmonarchs.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These pin the honesty rules the profile surfaces are built on. Each one guards
 * a specific way the code could start lying to the player.
 */
class ProductSurfacesTest {

    // ---- Badges ------------------------------------------------------------

    @Test
    fun withNoServerDataEveryBadgeIsLocked() {
        // The universal state today. If this ever returns an Earned badge from an
        // empty map, the cabinet has begun fabricating progress.
        val states = BadgeCatalog.states(emptyMap())
        assertEquals(BadgeCatalog.total, states.size)
        assertTrue("a badge was earned from no data", states.none { it.earned })
    }

    @Test
    fun aBadgeIsEarnedOnlyWhenTheServerSaysSo() {
        val states = BadgeCatalog.states(mapOf("first_capture" to 1_700_000_000_000L))
        val earned = states.filter { it.earned }
        assertEquals(1, earned.size)
        assertEquals("first_capture", earned.single().badge.key)
    }

    @Test
    fun everyBadgeKeyIsUniqueSoServerAwardsMapCleanly() {
        val keys = BadgeCatalog.all.map { it.key }
        assertEquals("duplicate badge key", keys.size, keys.toSet().size)
    }

    @Test
    fun rankBadgesAreMarkedBlockedUpstreamBecauseRankingIsLocked() {
        // "cannot be earned by anyone yet" is a different claim from "you have
        // not earned it", and the COMMAND badges must make the first one.
        val command = BadgeCatalog.all.filter { it.category == BadgeCategory.COMMAND }
        assertTrue("no COMMAND badges to check", command.isNotEmpty())
        assertTrue("a rank badge is not marked blocked", command.all { it.blockedUpstream })
        // ...and the non-rank badges are earnable now (not blocked).
        val field = BadgeCatalog.all.filter { it.category == BadgeCategory.FIELD }
        assertTrue(field.none { it.blockedUpstream })
    }

    // ---- Records -----------------------------------------------------------

    @Test
    fun defaultRecordsAreEmptyAndUnknownNotZero() {
        val r = PlayerRecords.NONE
        assertTrue(r.isEmpty)
        // Null is the wire for "unknown". A zero here would assert the player has
        // a zero-day streak / has held nothing, which the app cannot know.
        assertEquals(null, r.currentStreakDays)
        assertEquals(null, r.totalCaptures)
        assertEquals(null, r.peakHexesHeld)
    }

    @Test
    fun recordsWithAnySingleFactAreNotEmpty() {
        assertFalse(PlayerRecords(totalCaptures = 1).isEmpty)
        assertFalse(PlayerRecords(currentStreakDays = 0).isEmpty)  // a real zero streak IS data
    }

    // ---- Pro benefits ------------------------------------------------------

    @Test
    fun noProBenefitIsDeliverableInThisBuild() {
        // Every benefit needs backend/storage that does not exist, so all are
        // marked not-live. A live benefit here would promise a capability the
        // app cannot honour.
        assertTrue("a Pro benefit claims to be live with no backend", ProBenefits.all.none { it.live })
        assertFalse(ProBenefits.anyLive)
        assertTrue(ProBenefits.all.isNotEmpty())
    }
}
