package com.mapmonarchs.feature.map

import com.mapmonarchs.domain.model.TerritoryGeometry
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.validation.RejectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PREVIEW IS NOT LIVE.
 *
 * PreviewSessionTest proves the simulation works. These prove it cannot leak --
 * a distinction that matters, because a preview that functions perfectly and
 * quietly submits a real claim would be the single worst defect this product
 * could ship. Every assertion here is about a boundary, not a feature.
 */
class PreviewIsNotLiveTest {

    private val realUser = "6f2c1e4a-1111-4b2a-9c3d-000000000001"

    private fun state(
        identity: Identity = Identity.from(realUser),
        capture: CaptureState = CaptureState.Ready,
        preview: Boolean = false,
        territory: TerritoryLoad = TerritoryLoad.Loaded(TerritoryGeometry.EMPTY)
    ) = MapUiState(
        identity = identity,
        territory = territory,
        capture = capture,
        previewMode = preview,
        locationPermissionGranted = true
    )

    // --- the guard that stops simulation reaching the network ---

    @Test
    fun `preview can never submit a real claim even when everything else is perfect`() {
        // Authenticated identity, accepted fix, backend healthy -- and still no.
        val s = state(preview = true)
        assertFalse(s.canSubmitRealClaim())
    }

    @Test
    fun `preview identity alone can never submit a real claim`() {
        val s = state(identity = Identity.preview(), preview = true)
        assertFalse(s.canSubmitRealClaim())
    }

    @Test
    fun `a real claim requires all three - not preview, signed in, and ready`() {
        assertTrue(state().canSubmitRealClaim())
        assertFalse(state(preview = true).canSubmitRealClaim())
        assertFalse(state(identity = Identity.SignedOut).canSubmitRealClaim())
        assertFalse(state(capture = CaptureState.AcquiringFix).canSubmitRealClaim())
    }

    @Test
    fun `every refusal names its reason rather than failing silently`() {
        assertTrue(state(preview = true).claimRefusalReason().contains("preview"))
        assertTrue(state(identity = Identity.SignedOut).claimRefusalReason().contains("session"))
        assertTrue(state(capture = CaptureState.AcquiringFix).claimRefusalReason().contains("position"))
    }

    // --- preview identity is structurally distinct ---

    @Test
    fun `preview identity is not a SignedIn session`() {
        val preview: Identity = Identity.preview()
        assertFalse("preview must never be typed as an authenticated session", preview is Identity.SignedIn)
        assertTrue(preview is Identity.Preview)
    }

    @Test
    fun `preview callsign is visibly different from a derived callsign`() {
        val real = Identity.from(realUser).callsign
        val previewCallsign = Identity.preview().callsign
        assertNotEquals(real, previewCallsign)
        assertEquals("PREVIEW", previewCallsign)
        // A derived callsign is 6 hex chars; preview is a word. They cannot be
        // confused at a glance, which is the point.
        assertTrue(real.matches(Regex("[0-9A-F]{6}")))
        assertFalse(previewCallsign.matches(Regex("[0-9A-F]{6}")))
    }

    @Test
    fun `preview state is off by default so nothing survives a process restart`() {
        // MapUiState is reconstructed on launch; preview must default to off.
        val fresh = MapUiState(
            identity = Identity.Resolving,
            territory = TerritoryLoad.Loading,
            capture = CaptureState.PermissionRequired
        )
        assertFalse(fresh.previewMode)
        assertTrue(fresh.previewRings.isEmpty())
        assertEquals(null, fresh.previewHeld)
    }

    // --- labelling ---

    @Test
    fun `the capture label in preview never reads as a real claim`() {
        val label = state(preview = true).captureLabel()
        assertTrue("preview action must say so, got '$label'", label.contains("PREVIEW"))
        assertNotEquals("CLAIM HEX", label)
    }

    @Test
    fun `the identity label in preview says PREVIEW`() {
        assertEquals("PREVIEW", state(identity = Identity.preview(), preview = true).identityLabel())
    }

    @Test
    fun `preview does not suppress a real backend failure`() {
        // Preview must not make the app look healthier than it is.
        val s = state(
            preview = true,
            territory = TerritoryLoad.Failed(AppError.SchemaMissing("42P01"))
        )
        assertTrue(s.isTerritoryFailed)
        assertTrue(s.territoryError is AppError.SchemaMissing)
    }

    @Test
    fun `preview holdings are a simulated count, never the unknown dash of live state`() {
        // Live with no data shows "--" (unknown). Preview shows a real local count,
        // because in preview we genuinely do know the number.
        assertEquals(UNKNOWN_HOLDINGS, state().holdingsLabel())
        val previewState = MapUiState(
            identity = Identity.preview(),
            territory = TerritoryLoad.Loading,
            capture = CaptureState.Ready,
            previewMode = true,
            previewHeld = 3
        )
        assertEquals("3", previewState.holdingsLabel())
    }

    /**
     * REVERSED. Preview used to stay actionable on a rejected fix, on the theory
     * that a demo should not dead-end. That was wrong for a specific reason: the
     * preview grid is anchored to the player's REAL position, so claiming on a
     * reading we already judged untrustworthy would place a hex somewhere
     * arbitrary. The demo would then teach a loop the live game does not have.
     *
     * Preview may simplify the loop. It may not misrepresent it.
     */
    @Test
    fun `preview respects position quality because its grid is anchored to real GPS`() {
        val s = state(preview = true, capture = CaptureState.Blocked(RejectionReason.LOW_ACCURACY))
        assertFalse(s.isCaptureActionable())
        assertEquals("GPS TOO WEAK", s.captureLabel())
        assertFalse(s.canSubmitRealClaim())
    }
}