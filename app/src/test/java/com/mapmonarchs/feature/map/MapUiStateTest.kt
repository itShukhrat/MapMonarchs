package com.mapmonarchs.feature.map

import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.validation.RejectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapUiStateTest {

    private val realUser = "6f2c1e4a-1111-4b2a-9c3d-000000000001"

    /**
     * Permission defaults to GRANTED so every state built here is COHERENT.
     * Previously it defaulted to false while tests asserted on labels for
     * accepted and rejected position fixes -- combinations the app cannot
     * produce. Those tests passed only because the old label logic never
     * consulted permission at all.
     */
    private fun state(
        identity: Identity = Identity.from(realUser),
        territory: TerritoryLoad = TerritoryLoad.Loading,
        capture: CaptureState = CaptureState.AcquiringFix,
        permission: Boolean = true
    ) = MapUiState(
        identity = identity,
        territory = territory,
        capture = capture,
        locationPermissionGranted = permission
    )

    // --- holdings honesty ---

    @Test
    fun `holdings render as unknown rather than a confident zero`() {
        assertEquals(UNKNOWN_HOLDINGS, state().holdingsLabel())
    }

    @Test
    fun `holdings stay unknown when loaded because ownership filtering is not real yet`() {
        assertEquals(UNKNOWN_HOLDINGS, state(territory = TerritoryLoad.Loaded(emptyList())).holdingsLabel())
    }

    // --- identity honesty ---

    @Test
    fun `signed in shows the derived callsign`() {
        val id = Identity.from(realUser)
        assertEquals(id.callsign, state(identity = id).identityLabel())
    }

    @Test
    fun `signed out never renders a fabricated handle`() {
        val label = state(identity = Identity.SignedOut).identityLabel()
        assertEquals("NO IDENTITY", label)
        assertFalse("must not invent a name", label.equals("monarch", ignoreCase = true))
    }

    @Test
    fun `unavailable identity is explicit, not a plausible name`() {
        val s = state(identity = Identity.Unavailable(AppError.AuthUnavailable("disabled")))
        assertEquals("IDENTITY UNAVAILABLE", s.identityLabel())
        assertTrue(s.identityError() is AppError.AuthUnavailable)
    }

    @Test
    fun `resolving is distinguishable from signed out`() {
        assertEquals("IDENTIFYING", state(identity = Identity.Resolving).identityLabel())
    }

    @Test
    fun `territory colour is null without identity so the glyph renders hollow`() {
        assertNull(state(identity = Identity.SignedOut).territoryColor())
        assertNull(state(identity = Identity.Resolving).territoryColor())
        assertNull(state(identity = Identity.Unavailable(AppError.Offline("x"))).territoryColor())
    }

    @Test
    fun `territory colour is derived from identity, never a fixed default`() {
        val a = state(identity = Identity.from("user-a")).territoryColor()
        val b = state(identity = Identity.from("user-b")).territoryColor()
        assertTrue(a != null && b != null)
        // Not asserting inequality of colours (a palette can legitimately collide),
        // but the ARGB must match what derivation produced for that exact user.
        assertEquals(
            androidx.compose.ui.graphics.Color(Identity.from("user-a").territoryColorArgb),
            a
        )
    }


    // --- capture gating on identity ---

    @Test
    fun `capture is not actionable without identity even with a perfect fix`() {
        // The server would reject with not_authenticated, so offering CLAIM HEX
        // would be a lie the player pays for with a failed action.
        val s = state(identity = Identity.SignedOut, capture = CaptureState.Ready)
        assertFalse(s.isCaptureActionable())
        // Names the remedy, not just the symptom.
        assertEquals("SIGN IN TO CLAIM", s.captureLabel())
    }

    /**
     * REVERSED by the backend deploy: a signed-in player on an accepted fix
     * inside the world IS actionable now. attempt_capture() is deployed and
     * server-authoritative, so the button reads CLAIM HEX and fires. This test
     * asserted the opposite while the backend was undeployed.
     */
    @Test
    fun `a ready claim is actionable now that the backend is deployed`() {
        assertTrue(state(capture = CaptureState.Ready).isCaptureActionable())
        assertEquals("CLAIM HEX", state(capture = CaptureState.Ready).captureLabel())
        // Acquiring a fix is still a real block -- you cannot claim a position
        // you do not have yet.
        assertFalse(state(capture = CaptureState.AcquiringFix).isCaptureActionable())
    }

    @Test
    fun `every capture state produces a non empty label when signed in`() {
        val all = listOf(
            CaptureState.PermissionRequired,
            CaptureState.AcquiringFix,
            CaptureState.Ready,
            CaptureState.InFlight,
            CaptureState.Succeeded("captured"),
            CaptureState.Failed(AppError.Offline("x"))
        ) + RejectionReason.entries.map { CaptureState.Blocked(it) }
        all.forEach { c ->
            assertTrue("empty label for $c", state(capture = c).captureLabel().isNotBlank())
        }
    }

    @Test
    fun `blocked states name the specific reason`() {
        assertEquals("MOCK LOCATION", state(capture = CaptureState.Blocked(RejectionReason.MOCK_LOCATION)).captureLabel())
        assertEquals("GPS TOO WEAK", state(capture = CaptureState.Blocked(RejectionReason.LOW_ACCURACY)).captureLabel())
        assertEquals("MOVING TOO FAST", state(capture = CaptureState.Blocked(RejectionReason.TOO_FAST)).captureLabel())
        assertEquals("SIGNAL JUMPED", state(capture = CaptureState.Blocked(RejectionReason.TELEPORT)).captureLabel())
    }

    // --- axis independence ---

    /**
     * Axis independence, asserted as INVARIANCE rather than as a boolean.
     * The old form checked that capture was actionable with territory failing,
     * which conflated "territory does not affect capture" with "capture is
     * available" -- so it broke the moment capture became unavailable for an
     * unrelated reason. What actually matters is that the territory axis does not
     * move the capture answer.
     */
    @Test
    fun `territory failure does not change the capture blocker - independent axes`() {
        val failed = state(territory = TerritoryLoad.Failed(AppError.Offline("x")), capture = CaptureState.Ready)
        val loaded = state(territory = TerritoryLoad.Loaded(emptyList()), capture = CaptureState.Ready)
        assertEquals(resolveBlocker(loaded), resolveBlocker(failed))
        assertEquals(loaded.isCaptureActionable(), failed.isCaptureActionable())
    }

    @Test
    fun `territory failure is exposed with its error`() {
        val s = state(territory = TerritoryLoad.Failed(AppError.SchemaMissing("42P01")))
        assertTrue(s.isTerritoryFailed)
        assertTrue(s.territoryError is AppError.SchemaMissing)
    }

    @Test
    fun `loading is not mistaken for failure`() {
        assertFalse(state(territory = TerritoryLoad.Loading).isTerritoryFailed)
    }
}