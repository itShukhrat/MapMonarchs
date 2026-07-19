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
            assertNotNull("${route.title} is in LOCKED_ROUTES but has no lock", lock)
            assertTrue("${route.title}: empty why", lock!!.why.isNotBlank())
            assertTrue("${route.title}: empty unlock condition", lock.unlocksWhen.isNotBlank())
            assertTrue("${route.title}: nothing offered instead", lock.insteadNow.isNotBlank())
        }
    }

    @Test
    fun `no lock hides behind a vague timeline`() {
        // "Coming soon" and friends are exactly what this surface exists to avoid.
        val banned = listOf("coming soon", "stay tuned", "soon!", "in the future", "tbd")
        LOCKED_ROUTES.forEach { route ->
            val text = (route.lock!!.why + route.lock!!.unlocksWhen + route.lock!!.insteadNow).lowercase()
            banned.forEach { phrase ->
                assertFalse("${route.title} used a vague phrase: '$phrase'", text.contains(phrase))
            }
        }
    }

    @Test
    fun `unlock conditions are stated as conditions, not dates`() {
        LOCKED_ROUTES.forEach { route ->
            val c = route.lock!!.unlocksWhen
            assertTrue("${route.title}: unlock condition too short to be specific", c.length > 20)
            assertFalse("${route.title}: promised a date", c.contains(Regex("\\d{4}")))
        }
    }

    @Test
    fun `active routes are not locked`() {
        (COMMAND_ROUTES + REFERENCE_ROUTES).forEach { route ->
            assertNull("${route.title} is presented as active but carries a lock", route.lock)
        }
    }

    @Test
    fun `locked and active route sets do not overlap`() {
        val active = (COMMAND_ROUTES + REFERENCE_ROUTES).toSet()
        LOCKED_ROUTES.forEach { assertFalse("${it.title} is in both groups", active.contains(it)) }
    }

    @Test
    fun `every route in the app appears in exactly one menu group`() {
        val all = ALL_ROUTES
        assertEquals("a route is listed twice", all.size, all.toSet().size)
    }

    @Test
    fun `map is the only command route without a back destination`() {
        assertTrue(COMMAND_ROUTES.contains(Route.Map))
    }

    @Test
    fun `forbidden systems are locked, not shipped`() {
        // Hard rule 4: no ranks, teams, or shop before the loop is proven.
        listOf(Route.Leaderboard, Route.Empire, Route.Shop).forEach {
            assertNotNull("${it.title} must remain locked", it.lock)
            assertTrue(LOCKED_ROUTES.contains(it))
        }
    }
}