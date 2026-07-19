package com.mapmonarchs.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmonarchs.data.remote.SupabaseProvider
import com.mapmonarchs.domain.model.CapabilitiesProbe
import com.mapmonarchs.domain.model.Identity
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real auth repository against the real backend.
 *
 * WHY THIS EXISTS: restoreSession() and signOut() had never been executed by any
 * test. awaitInitialization() -- which several comments in this codebase claim
 * prevents a cold start from handing the player a different identity -- had only
 * ever run inside the app, on the no-session path where the answer is trivially
 * correct either way.
 *
 * WHAT THIS SUITE FOUND: in an instrumented context with no Activity started,
 * awaitInitialization() never returns. The first run of this file hung for over
 * ten minutes and logged nothing from restoreSession() at all -- not the success
 * line, not the catch line -- because execution never left the await. That is
 * why restoreSession() is now bounded by a timeout: an unbounded wait cannot
 * fail loudly, and a stall would have left identity at Resolving forever with
 * the HUD reading IDENTIFYING, no error, and no retry.
 *
 * SO THESE TESTS ASSERT BOUNDEDNESS AND HONESTY, NOT A PARTICULAR IDENTITY.
 * Whether this environment yields SignedOut (init completed, no session) or
 * Unavailable (init stalled, timeout fired) is an environmental fact. What must
 * hold in every environment is that the call RETURNS, and that it never invents
 * a user.
 *
 * WHAT CANNOT BE PROVEN HERE, AND WHY: the restoreSession() -> SignedIn branch.
 * A real session requires a six-digit code to arrive in a real inbox and be
 * typed back. Nothing in this process can do that. It is a genuine external
 * blocker, and the branch is left untested rather than faked with a stub that
 * would prove only that the stub works.
 *
 * NO MAIL IS SENT BY THIS SUITE. requestEmailCode() is deliberately never
 * called: it would consume the project's rate limit and deliver real mail to a
 * real person as a side effect of running tests.
 */
@RunWith(AndroidJUnit4::class)
class SupabaseAuthRepositoryTest {

    private val repository = SupabaseAuthRepository(SupabaseProvider.client)

    /**
     * Comfortably above the production ceiling of 10s. If the call returns within
     * this, the timeout did its job; if it does not, the hang is back.
     */
    private val boundMillis = 25_000L

    private fun <T> timed(block: suspend () -> T): Pair<T, Long> = runBlocking {
        val started = System.currentTimeMillis()
        val result = block()
        result to (System.currentTimeMillis() - started)
    }

    // --- the property the timeout exists to guarantee ------------------------

    /**
     * REGRESSION. Before the timeout, this call never returned in this context
     * and the test run had to be killed by hand.
     */
    @Test
    fun restoringASessionAlwaysReturnsRatherThanHanging() {
        val (identity, elapsed) = timed { repository.restoreSession() }

        assertTrue(
            "restoreSession must be bounded; took ${elapsed}ms and returned $identity",
            elapsed < boundMillis
        )
    }

    /**
     * The contract that holds in EVERY environment: no user is ever invented.
     * SignedOut and Unavailable are both honest here. SignedIn would not be --
     * nothing in this process has signed anyone in.
     */
    @Test
    fun restoringASessionNeverFabricatesAUser() {
        val (identity, _) = timed { repository.restoreSession() }

        assertTrue(
            "nothing has signed in, so an authenticated identity would be invented: $identity",
            identity !is Identity.SignedIn
        )
        assertTrue(
            "must resolve to a stated outcome, got $identity",
            identity is Identity.SignedOut || identity is Identity.Unavailable
        )
    }

    /** An unavailable session must say why, never fail blankly. */
    @Test
    fun anUnavailableSessionCarriesItsCause() {
        val (identity, _) = timed { repository.restoreSession() }

        if (identity is Identity.Unavailable) {
            assertTrue(
                "an unavailable identity must carry a diagnosable cause",
                identity.error.technical.isNotBlank()
            )
            assertTrue(
                "and a message the player can read",
                identity.error.playerMessage().isNotBlank()
            )
        }
    }

    /**
     * Identity must not depend on how many times it was asked. A repository that
     * answered differently on the second call would make a player's identity a
     * function of call order -- the class of bug awaitInitialization() exists to
     * prevent in the first place.
     */
    @Test
    fun restoringTwiceGivesTheSameKindOfAnswer() {
        val (first, _) = timed { repository.restoreSession() }
        val (second, _) = timed { repository.restoreSession() }

        assertTrue(
            "identity must not depend on call count: $first then $second",
            first::class == second::class
        )
    }

    /** Sign-out must also be bounded, and must never leave a session behind. */
    @Test
    fun signingOutIsBoundedAndLeavesNoSession() {
        val (_, signOutElapsed) = timed { repository.signOut() }
        assertTrue("signOut must be bounded; took ${signOutElapsed}ms", signOutElapsed < boundMillis)

        val (identity, _) = timed { repository.restoreSession() }
        assertTrue(
            "a signed-out device must never restore an authenticated user, got $identity",
            identity !is Identity.SignedIn
        )
    }

    // --- the timeout branch, proven deterministically ------------------------

    /**
     * The branch that exists because of the stall this suite found, exercised on
     * purpose rather than waited for.
     *
     * An initialiser that never completes is injected, so this does not race the
     * real one and cannot flake. Everything the branch promises is asserted:
     * it returns, it returns Unavailable, and it says why.
     */
    @Test
    fun aStalledInitialisationBecomesANamedFailureRatherThanAHang() {
        val neverCompletes = SupabaseAuthRepository(
            client = SupabaseProvider.client,
            awaitInitialization = { awaitCancellation() },
            sessionInitTimeoutMs = 300L
        )

        val (identity, elapsed) = timed { neverCompletes.restoreSession() }

        assertTrue(
            "a stalled init must not hang the caller; took ${elapsed}ms",
            elapsed < 5_000L
        )
        assertTrue(
            "a stall is not knowledge that nobody is signed in, got $identity",
            identity is Identity.Unavailable
        )
        assertTrue(
            "must never report SignedOut on a stall -- that would silently drop a " +
                "player who does have a session",
            identity !is Identity.SignedOut
        )
        val cause = (identity as Identity.Unavailable).error.technical
        assertTrue("the cause must name the timeout, got '$cause'", cause.contains("timed out"))
    }

    /**
     * A stall must not be mistaken for a working session either. Belt and braces
     * on the variant that would be worst: inventing an authenticated user while
     * the session layer is visibly broken.
     */
    @Test
    fun aStalledInitialisationNeverFabricatesAUser() {
        val neverCompletes = SupabaseAuthRepository(
            client = SupabaseProvider.client,
            awaitInitialization = { awaitCancellation() },
            sessionInitTimeoutMs = 300L
        )

        val (identity, _) = timed { neverCompletes.restoreSession() }
        assertTrue("no user exists to restore, got $identity", identity !is Identity.SignedIn)
    }

    // --- the probe, end to end ----------------------------------------------

    /**
     * Requires network. Proves the request itself works -- URL construction,
     * apikey header, timeouts, response handling -- which AuthSettingsParserTest
     * cannot cover because it starts from a captured payload.
     *
     * This one passed on the very first run, in 2.8 seconds, while the session
     * test beside it hung. That contrast is what located the defect.
     */
    @Test
    fun theCapabilitiesProbeReachesTheRealBackend() {
        val (probe, _) = timed { repository.probeCapabilities() }

        val observed = probe as? CapabilitiesProbe.Observed
            ?: error(
                "expected Observed from the live project, got $probe. " +
                    "If this machine is offline, that is the cause -- this test " +
                    "deliberately hits the network."
            )

        assertTrue("email auth is enabled on this project", observed.capabilities.emailEnabled)
        assertTrue(
            "anonymous auth is disabled, so email is the only route",
            !observed.capabilities.anonymousEnabled
        )
    }

    /**
     * Whatever the probe returns, it is an observation or an explicit
     * unavailability. There is no third state where it guesses.
     */
    @Test
    fun theProbeIsEitherObservedOrExplicitlyUnavailable() {
        val (probe, _) = timed { repository.probeCapabilities() }

        assertTrue(
            "a probe result must be actionable or honestly unknown, got $probe",
            probe is CapabilitiesProbe.Observed || probe is CapabilitiesProbe.Unavailable
        )
        if (probe is CapabilitiesProbe.Unavailable) {
            assertTrue("an unavailable probe must say why", probe.error.technical.isNotBlank())
        }
    }
}
