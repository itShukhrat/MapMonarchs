package com.mapmonarchs.feature.map

import androidx.compose.ui.graphics.Color
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.validation.RejectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapUiStateTest {

    private fun state(
        territory: TerritoryLoad = TerritoryLoad.Loading,
        capture: CaptureState = CaptureState.AcquiringFix
    ) = MapUiState(
        handle = "monarch",
        territoryColor = Color(0xFF4C8DFF),
        territory = territory,
        capture = capture
    )

    @Test
    fun `holdings render as unknown rather than a confident zero`() {
        // Showing "0" before asking the server tells the player they own nothing.
        // That is a lie when territory simply has not loaded.
        assertEquals("--", state().holdingsLabel())
    }

    @Test
    fun `holdings stay unknown when loaded because identity is not real yet`() {
        assertEquals("--", state(territory = TerritoryLoad.Loaded(emptyList())).holdingsLabel())
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

    @Test
    fun `only ready is actionable`() {
        assertTrue(state(capture = CaptureState.Ready).isCaptureActionable())
        assertFalse(state(capture = CaptureState.AcquiringFix).isCaptureActionable())
        assertFalse(state(capture = CaptureState.InFlight).isCaptureActionable())
        assertFalse(state(capture = CaptureState.PermissionRequired).isCaptureActionable())
        assertFalse(state(capture = CaptureState.Succeeded("captured")).isCaptureActionable())
        assertFalse(state(capture = CaptureState.Failed(AppError.Offline("x"))).isCaptureActionable())
        assertFalse(state(capture = CaptureState.Blocked(RejectionReason.TOO_FAST)).isCaptureActionable())
    }

    @Test
    fun `every capture state produces a non empty label`() {
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

    @Test
    fun `territory failure does not disable capture - they are independent axes`() {
        // A failed territory read must not stop a player acting on a good GPS fix.
        val s = state(
            territory = TerritoryLoad.Failed(AppError.Offline("x")),
            capture = CaptureState.Ready
        )
        assertTrue(s.isCaptureActionable())
    }
}