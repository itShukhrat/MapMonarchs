package com.mapmonarchs.feature.diagnostics

import androidx.compose.ui.graphics.Color
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.validation.RejectionReason
import com.mapmonarchs.feature.map.CaptureState
import com.mapmonarchs.feature.map.MapUiState
import com.mapmonarchs.feature.map.TerritoryLoad
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blocker prioritisation.
 *
 * The ordering is the whole value of this screen: telling someone their GPS is
 * imprecise when they never granted location permission is technically true and
 * completely useless. These tests pin the order.
 */
class PrimaryBlockerTest {

    private fun state(
        permission: Boolean = true,
        identity: Identity = Identity.from("user-a"),
        territory: TerritoryLoad = TerritoryLoad.Loaded(emptyList()),
        capture: CaptureState = CaptureState.Ready
    ) = MapUiState(
        identity = identity,
        territory = territory,
        capture = capture,
        locationPermissionGranted = permission
    )

    @Test
    fun `missing permission outranks every other problem`() {
        val s = state(
            permission = false,
            identity = Identity.Unavailable(AppError.AuthUnavailable("x")),
            territory = TerritoryLoad.Failed(AppError.Offline("x")),
            capture = CaptureState.Blocked(RejectionReason.LOW_ACCURACY)
        )
        assertTrue(primaryBlocker(s).contains("permission", ignoreCase = true))
    }

    @Test
    fun `identity outranks backend and fix problems`() {
        val s = state(
            identity = Identity.Unavailable(AppError.AuthUnavailable("x")),
            territory = TerritoryLoad.Failed(AppError.SchemaMissing("x")),
            capture = CaptureState.Blocked(RejectionReason.TOO_FAST)
        )
        assertTrue(primaryBlocker(s).contains("Sign-in", ignoreCase = true))
    }

    @Test
    fun `backend outranks a rejected fix`() {
        val s = state(
            territory = TerritoryLoad.Failed(AppError.SchemaMissing("x")),
            capture = CaptureState.Blocked(RejectionReason.TELEPORT)
        )
        assertTrue(primaryBlocker(s).contains("territory service", ignoreCase = true))
    }

    @Test
    fun `a rejected fix names the specific reason`() {
        val s = state(capture = CaptureState.Blocked(RejectionReason.MOCK_LOCATION))
        assertTrue(primaryBlocker(s).contains("mock location", ignoreCase = true))
    }

    @Test
    fun `healthy state does not invent a problem`() {
        val text = primaryBlocker(state())
        assertTrue(text.contains("Nothing is blocking", ignoreCase = true))
    }

    @Test
    fun `every state produces a non-empty explanation`() {
        val states = listOf(
            state(permission = false),
            state(identity = Identity.Resolving),
            state(identity = Identity.SignedOut),
            state(identity = Identity.Unavailable(AppError.Offline("x"))),
            state(territory = TerritoryLoad.Loading),
            state(territory = TerritoryLoad.Failed(AppError.Unexpected("x"))),
            state(capture = CaptureState.AcquiringFix),
            state(capture = CaptureState.InFlight),
            state()
        )
        states.forEach { s ->
            assertTrue("blank blocker for $s", primaryBlocker(s).isNotBlank())
        }
    }

    @Test
    fun `blocker text never leaks technical detail`() {
        val s = state(territory = TerritoryLoad.Failed(AppError.SchemaMissing("42P01 relation does not exist")))
        val text = primaryBlocker(s)
        assertTrue(!text.contains("42P01"))
        assertTrue(!text.contains("relation"))
    }
}