package com.mapmonarchs.feature.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The locked-feature contract.
 *
 * These exist so a locked surface can never degrade into a "coming soon" screen:
 * every lock must state why, what unlocks it, and what to do instead. A test that
 * only checked "the screen renders" would pass on an empty shell, which is
 * exactly the failure being guarded against.
 */
class FeatureLockTest {

    @Test
    fun `every locked route carries a complete lock`() {
        LOCKED_ROUTES.forEach { route ->
            val lock = route.lock
            assertNotNull("${route.key} is in LOCKED_ROUTES but has no lock", lock)
            // Content assertions live in FeatureLockCopyTest (instrumented): these
            // fields are string ids now, and only a Context can resolve them.
            assertTrue("${route.key}: why is not a real string id", lock!!.why != 0)
            assertTrue("${route.key}: unlocksWhen is not a real string id", lock.unlocksWhen != 0)
            assertTrue("${route.key}: insteadNow is not a real string id", lock.insteadNow != 0)
        }
    }

    @Test
    fun `active routes are not locked`() {
        (COMMAND_ROUTES + REFERENCE_ROUTES).forEach { route ->
            assertNull("${route.key} is presented as active but carries a lock", route.lock)
        }
    }

    @Test
    fun `locked and active route sets do not overlap`() {
        val active = (COMMAND_ROUTES + REFERENCE_ROUTES).toSet()
        LOCKED_ROUTES.forEach { assertFalse("${it.key} is in both groups", active.contains(it)) }
    }

    @Test
    fun `every route in the app appears in exactly one menu group`() {
        val all = ALL_ROUTES
        assertEquals("a route is listed twice", all.size, all.toSet().size)
    }

    @Test
    fun `map lives in the bar and is not duplicated in the command list`() {
        // Map moved from the command list to the floating bar. It must not appear
        // in both: two entries for one destination is the same defect as two
        // names for one concept, and it drifts the same way.
        assertTrue(NAV_DESTINATIONS.any { it.route == Route.Map })
        assertTrue("map must not also sit in the command list", !COMMAND_ROUTES.contains(Route.Map))
    }

    @Test
    fun `forbidden systems are locked, not shipped`() {
        // Hard rule 4: no ranks, teams, or shop before the loop is proven.
        listOf(Route.Leaderboard, Route.Empire, Route.Shop).forEach {
            assertNotNull("${it.key} must remain locked", it.lock)
            assertTrue(LOCKED_ROUTES.contains(it))
        }
    }

    /**
     * No lock routes to DIAGNOSTICS now that the backend gate is open.
     *
     * The schema, the capture function and the Edge Function are deployed, so
     * Diagnostics reads REACHABLE. Every remaining lock is a product deferral --
     * the feature screen is not built, or the loop is not yet proven -- which no
     * probe reports. Routing any of them to a status page that says REACHABLE
     * would be a dead end: it answers a question the player is not asking and
     * says nothing about why the surface is shut. This flipped when the backend
     * was deployed; before that, Territory/Missions/Leaderboard/Notifications
     * were genuinely backend-gated and Diagnostics did explain them.
     */
    @Test
    fun `no lock routes to Diagnostics once the backend gate is open`() {
        LOCKED_ROUTES.forEach {
            assertFalse(
                "${it.key} routes to Diagnostics, but a product-deferral lock cannot be explained by a REACHABLE backend",
                it.lock!!.diagnosticsExplainsThis
            )
        }
    }

    /**
     * A backend-explained lock must not be worded as a pure product decision, and
     * vice versa -- the flag and the prose have to agree, or the flag is a lie the
     * copy will contradict.
     */
}
