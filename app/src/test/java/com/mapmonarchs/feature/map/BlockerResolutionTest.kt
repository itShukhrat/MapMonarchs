package com.mapmonarchs.feature.map

import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.Blocker
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.validation.RejectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The blocker ordering, pinned.
 *
 * These replace a suite that asserted on the TEXT of a single screen's `when`
 * chain. That suite had a test which fed it a signed-out player and asserted only
 * that the result was non-blank -- which the wrong answer ("nothing is blocking
 * movement tracking") satisfied perfectly. Asserting non-emptiness of a string
 * proves the function returned; it does not prove the function was right.
 *
 * These assert the resolved [Blocker] identity instead, so a wrong answer is a
 * different value rather than a differently-worded sentence.
 */
class BlockerResolutionTest {

    private fun state(
        permission: Boolean = true,
        identity: Identity = Identity.from("user-a"),
        territory: TerritoryLoad = TerritoryLoad.Loaded(emptyList()),
        capture: CaptureState = CaptureState.Ready,
        previewMode: Boolean = false
    ) = MapUiState(
        identity = identity,
        territory = territory,
        capture = capture,
        locationPermissionGranted = permission,
        previewMode = previewMode
    )

    @Test
    fun `missing permission outranks every other problem`() {
        val s = state(
            permission = false,
            identity = Identity.Unavailable(AppError.AuthUnavailable("x")),
            territory = TerritoryLoad.Failed(AppError.Offline("x")),
            capture = CaptureState.Blocked(RejectionReason.LOW_ACCURACY)
        )
        assertEquals(Blocker.LocationPermissionMissing, resolveBlocker(s))
    }

    @Test
    fun `a rejected fix outranks identity`() {
        val s = state(
            identity = Identity.SignedOut,
            capture = CaptureState.Blocked(RejectionReason.MOCK_LOCATION)
        )
        assertEquals(Blocker.FixRejected(RejectionReason.MOCK_LOCATION), resolveBlocker(s))
    }

    /**
     * REGRESSION. Identity.SignedOut previously matched no branch in the
     * diagnostics chain and fell through to a default claiming nothing was wrong,
     * while the capture button was simultaneously disabled.
     */
    @Test
    fun `signed out is a named blocker and never reads as healthy`() {
        val blocker = resolveBlocker(state(identity = Identity.SignedOut))
        assertEquals(Blocker.NotSignedIn, blocker)
        val none: Blocker = Blocker.None
        assertNotEquals(none, blocker)
        assertTrue(
            "must not tell a signed-out player that nothing is blocking them",
            !blocker.explanation().contains("Nothing is blocking", ignoreCase = true)
        )
    }

    @Test
    fun `resolving identity is distinct from being signed out`() {
        assertEquals(Blocker.IdentityResolving, resolveBlocker(state(identity = Identity.Resolving)))
    }

    @Test
    fun `identity unavailable carries its cause`() {
        val error = AppError.AuthUnavailable("provider disabled")
        val s = state(identity = Identity.Unavailable(error))
        assertEquals(Blocker.IdentityUnavailable(error), resolveBlocker(s))
    }

    /**
     * Axis independence, pinned. A failed territory READ must not masquerade as a
     * capture blocker: the map being stale does not stop the server arbitrating a
     * claim. Territory health is reported in its own diagnostics row instead.
     */
    @Test
    fun `a failed territory read is not a capture blocker`() {
        val s = state(territory = TerritoryLoad.Failed(AppError.SchemaMissing("42P01")))
        assertEquals(Blocker.CaptureNotDeployed, resolveBlocker(s))
    }

    /**
     * The current honest truth of a perfect live state: still blocked, by the
     * backend. "Nothing is blocking you" is not representable outside preview and
     * this test exists to keep it that way.
     */
    @Test
    fun `a perfectly healthy live state is still blocked by the undeployed backend`() {
        assertEquals(Blocker.CaptureNotDeployed, resolveBlocker(state()))
    }

    @Test
    fun `preview is genuinely unblocked because it never leaves the device`() {
        val s = state(identity = Identity.preview(), previewMode = true)
        assertEquals(Blocker.None, resolveBlocker(s))
    }

    @Test
    fun `preview still needs a usable position`() {
        val s = state(identity = Identity.preview(), previewMode = true, permission = false)
        assertEquals(Blocker.LocationPermissionMissing, resolveBlocker(s))
    }

    /** Preview identity outside preview mode must never be mistaken for a session. */
    @Test
    fun `preview identity without preview mode is not a session`() {
        val s = state(identity = Identity.preview(), previewMode = false)
        assertEquals(Blocker.NotSignedIn, resolveBlocker(s))
    }

    // --- the two surfaces must never disagree --------------------------------

    private val everyState: List<MapUiState> = listOf(
        state(permission = false),
        state(capture = CaptureState.AcquiringFix),
        state(capture = CaptureState.Blocked(RejectionReason.TELEPORT)),
        state(capture = CaptureState.Blocked(RejectionReason.TOO_FAST)),
        state(capture = CaptureState.Blocked(RejectionReason.LOW_ACCURACY)),
        state(capture = CaptureState.Blocked(RejectionReason.MOCK_LOCATION)),
        state(identity = Identity.Resolving),
        state(identity = Identity.SignedOut),
        state(identity = Identity.Unavailable(AppError.Offline("x"))),
        state(territory = TerritoryLoad.Failed(AppError.Unexpected("x"))),
        state(territory = TerritoryLoad.Loading),
        state(),
        state(identity = Identity.preview(), previewMode = true)
    )

    /**
     * The bug this prevents: the HUD said "NO IDENTITY" while diagnostics said
     * "Location permission is not granted" for the same state, so the player
     * fixed the wrong thing first.
     */
    @Test
    fun `hud label and diagnostics explanation always describe the same blocker`() {
        everyState.forEach { s ->
            val blocker = resolveBlocker(s)
            val expected =
                if (blocker == Blocker.None && s.previewMode) "CLAIM (PREVIEW)"
                else blocker.hudLabel()
            assertEquals("HUD disagreed with the resolver for $s", expected, s.captureLabel())
        }
    }

    @Test
    fun `capture is actionable only when nothing blocks`() {
        everyState.forEach { s ->
            assertEquals(
                "actionability disagreed with the resolver for $s",
                resolveBlocker(s) == Blocker.None,
                s.isCaptureActionable()
            )
        }
    }

    @Test
    fun `every blocker has a distinct, non-empty label and explanation`() {
        val blockers = listOf(
            Blocker.None,
            Blocker.LocationPermissionMissing,
            Blocker.AcquiringFix,
            Blocker.FixRejected(RejectionReason.MOCK_LOCATION),
            Blocker.FixRejected(RejectionReason.LOW_ACCURACY),
            Blocker.FixRejected(RejectionReason.TOO_FAST),
            Blocker.FixRejected(RejectionReason.TELEPORT),
            Blocker.IdentityResolving,
            Blocker.IdentityUnavailable(AppError.AuthUnavailable("x")),
            Blocker.NotSignedIn,
            Blocker.CaptureNotDeployed
        )
        blockers.forEach {
            assertTrue("blank label for $it", it.hudLabel().isNotBlank())
            assertTrue("blank explanation for $it", it.explanation().isNotBlank())
        }
        // Two blockers that read identically are two blockers the player cannot
        // tell apart, which defeats the point of distinguishing them.
        assertEquals(
            "explanations must be distinguishable",
            blockers.size,
            blockers.map { it.explanation() }.toSet().size
        )
    }

    @Test
    fun `explanations never leak technical detail`() {
        val s = state(
            identity = Identity.Unavailable(
                AppError.AuthUnavailable("PGRST202 function public.attempt_capture not found")
            )
        )
        val text = resolveBlocker(s).explanation()
        assertTrue(!text.contains("PGRST202"))
        assertTrue(!text.contains("attempt_capture"))
    }
}
