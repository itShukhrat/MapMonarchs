package com.mapmonarchs.feature.shell

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The locked-feature COPY contract.
 *
 * WHY THIS IS INSTRUMENTED AND NOT A JVM TEST: lock copy used to be raw strings
 * on FeatureLock, so a plain unit test could read it. It is string ids now, and
 * only a Context can resolve an id into text. These assertions did not become
 * less important when localisation moved them -- they became unrunnable where
 * they were, so they moved here rather than being deleted.
 *
 * WHAT THEY GUARD: a locked surface degrading into a "coming soon" screen. That
 * is the single failure mode this whole area exists to prevent, and it is a
 * copy-level failure, which means it can only be caught by reading the copy.
 *
 * These run against the DEFAULT locale, which is the source of truth. A
 * translation that reintroduced a vague phrase would not be caught here; that is
 * a review problem for the translator, not something a device test can decide.
 */
@RunWith(AndroidJUnit4::class)
class FeatureLockCopyTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int): String = context.getString(id)

    @Test
    fun everyLockStatesWhyUnlockAndAlternative() {
        LOCKED_ROUTES.forEach { route ->
            val lock = route.lock!!
            assertTrue("${route.key}: empty why", text(lock.why).isNotBlank())
            assertTrue("${route.key}: empty unlock condition", text(lock.unlocksWhen).isNotBlank())
            assertTrue("${route.key}: nothing offered instead", text(lock.insteadNow).isNotBlank())
        }
    }

    @Test
    fun noLockHidesBehindAVagueTimeline() {
        val banned = listOf("coming soon", "stay tuned", "soon!", "in the future", "tbd")
        LOCKED_ROUTES.forEach { route ->
            val lock = route.lock!!
            val all = (text(lock.why) + text(lock.unlocksWhen) + text(lock.insteadNow)).lowercase()
            banned.forEach { phrase ->
                assertFalse("${route.key} used a vague phrase: '$phrase'", all.contains(phrase))
            }
        }
    }

    @Test
    fun unlockConditionsAreConditionsNotDates() {
        LOCKED_ROUTES.forEach { route ->
            val c = text(route.lock!!.unlocksWhen)
            assertTrue("${route.key}: unlock condition too short to be specific", c.length > 20)
            assertFalse("${route.key}: promised a date", c.contains(Regex("[0-9]{4}")))
        }
    }

    /**
     * The flag and the prose have to agree, or the flag is a lie the copy will
     * contradict: a lock that claims DIAGNOSTICS explains it must name a backend
     * condition a player can actually go and read there.
     */
    @Test
    fun theDiagnosticsFlagMatchesHowTheUnlockIsWorded() {
        LOCKED_ROUTES.forEach { route ->
            val lock = route.lock!!
            val unlock = text(lock.unlocksWhen).lowercase()
            val mentionsBackend = unlock.contains("schema") || unlock.contains("capture") ||
                unlock.contains("backend") || unlock.contains("deployed")
            if (lock.diagnosticsExplainsThis) {
                assertTrue(
                    "${route.key} claims Diagnostics explains it, but its unlock names no backend condition",
                    mentionsBackend
                )
            }
        }
    }

    /** The shop promise is the one piece of copy that constrains future revenue. */
    @Test
    fun theShopCommitmentSurvivedLocalisation() {
        val commitment = Route.Shop.lock!!.commitment
        assertTrue("SHOP lost its monetisation commitment", commitment != null)
        assertTrue(
            "SHOP commitment must still rule out pay-to-win",
            text(commitment!!).lowercase().contains("cosmetics only")
        )
    }
}
