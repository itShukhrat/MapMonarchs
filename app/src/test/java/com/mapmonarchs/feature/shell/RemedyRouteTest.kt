package com.mapmonarchs.feature.shell

import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.Blocker
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.validation.RejectionReason
import com.mapmonarchs.feature.map.CaptureState
import com.mapmonarchs.feature.map.MapUiState
import com.mapmonarchs.feature.map.TerritoryLoad
import com.mapmonarchs.feature.map.resolveBlocker
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a blocked capture sends the player.
 *
 * This was previously decided inline in AppShell by
 * `identity !is SignedIn && !previewMode` -- a second copy of a precedence rule
 * Blocker already owned, with no test. It was the fourth surface of that rule
 * and the one missed when the other three were consolidated, because it was not
 * a `when` chain and so did not look like one.
 *
 * An earlier report of mine ticked "blocked capture routes to the right remedy"
 * on the strength of a screenshot. That screenshot showed the one state where
 * the two rules happened to agree.
 */
class RemedyRouteTest {

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

    /**
     * THE REGRESSION. Signed out AND permission denied: the button reads
     * "LOCATION OFF" because position outranks identity, and the old routing sent
     * the player to a sign-in form that cannot grant location permission.
     */
    @Test
    fun `a denied permission never routes to sign-in even when also signed out`() {
        val s = state(
            permission = false,
            identity = Identity.SignedOut,
            capture = CaptureState.PermissionRequired
        )
        assertEquals(Blocker.LocationPermissionMissing, resolveBlocker(s))
        assertEquals(
            "the button says LOCATION OFF; it must not open a sign-in form",
            Route.Settings,
            remedyRouteFor(resolveBlocker(s))
        )
    }

    @Test
    fun `a missing session routes to sign-in`() {
        val s = state(identity = Identity.SignedOut)
        assertEquals(Route.SignIn, remedyRouteFor(resolveBlocker(s)))
    }

    @Test
    fun `an unavailable identity routes to sign-in`() {
        val s = state(identity = Identity.Unavailable(AppError.AuthUnavailable("disabled")))
        assertEquals(Route.SignIn, remedyRouteFor(resolveBlocker(s)))
    }

    @Test
    fun `a session still resolving routes to status, not to a form`() {
        // Nothing for the player to do -- offering sign-in would imply an action
        // that would not help and might duplicate an in-flight one.
        val s = state(identity = Identity.Resolving)
        assertEquals(Route.Diagnostics, remedyRouteFor(resolveBlocker(s)))
    }

    @Test
    fun `a healthy live state is unblocked and needs no remedy`() {
        // Deploying the backend flipped the terminal from CaptureNotDeployed to
        // None: a signed-in player on a good fix inside the world can just claim.
        // remedyRouteFor is never consulted for None in practice (a ready claim
        // fires onCapture, not onExplainBlocker); its defensive default stays
        // Diagnostics.
        val s = state()
        assertEquals(Blocker.None, resolveBlocker(s))
        assertEquals(Route.Diagnostics, remedyRouteFor(resolveBlocker(s)))
    }

    @Test
    fun `a rejected fix routes to diagnostics where the rejection is named`() {
        RejectionReason.entries.forEach { reason ->
            val s = state(capture = CaptureState.Blocked(reason))
            assertEquals(
                "rejection $reason should explain itself, not ask for a sign-in",
                Route.Diagnostics,
                remedyRouteFor(resolveBlocker(s))
            )
        }
    }

    @Test
    fun `acquiring a fix routes to diagnostics`() {
        val s = state(capture = CaptureState.AcquiringFix)
        assertEquals(Route.Diagnostics, remedyRouteFor(resolveBlocker(s)))
    }

    /** Preview must never be pushed toward a live sign-in it does not need. */
    @Test
    fun `preview never routes to sign-in`() {
        val s = state(identity = Identity.preview(), previewMode = true)
        assertEquals(Blocker.None, resolveBlocker(s))
        assertEquals(Route.Diagnostics, remedyRouteFor(resolveBlocker(s)))
    }

    @Test
    fun `preview with a denied permission still routes to settings`() {
        val s = state(
            identity = Identity.preview(),
            previewMode = true,
            permission = false,
            capture = CaptureState.PermissionRequired
        )
        assertEquals(Route.Settings, remedyRouteFor(resolveBlocker(s)))
    }

    /**
     * Every blocker must have a destination. The `when` in remedyRouteFor is
     * exhaustive so a new variant will not compile without one, but this pins the
     * behaviour for the variants that exist today.
     */
    @Test
    fun `every blocker resolves to a real route`() {
        val blockers = listOf(
            Blocker.None,
            Blocker.LocationPermissionMissing,
            Blocker.AcquiringFix,
            Blocker.FixRejected(RejectionReason.MOCK_LOCATION),
            Blocker.IdentityResolving,
            Blocker.IdentityUnavailable(AppError.Offline("x")),
            Blocker.NotSignedIn,
            Blocker.CaptureNotDeployed
        )
        val allowed = setOf(Route.Settings, Route.SignIn, Route.Diagnostics)
        blockers.forEach { blocker ->
            assertEquals(
                "route for $blocker must be one the player can act on",
                true,
                remedyRouteFor(blocker) in allowed
            )
        }
    }
}
