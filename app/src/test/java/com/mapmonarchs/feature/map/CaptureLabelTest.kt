package com.mapmonarchs.feature.map

import androidx.compose.ui.graphics.Color
import com.mapmonarchs.domain.validation.RejectionReason
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The capture button must always explain itself. A silent dead button in a game
 * where movement earns territory reads as a broken app, or as the game cheating
 * the player. These assert the label never degrades to a bare disabled state.
 */
class CaptureLabelTest {

    private fun state(
        availability: CaptureAvailability = CaptureAvailability.UNAVAILABLE,
        permission: Boolean = true,
        hasFix: Boolean = true,
        rejection: RejectionReason? = null
    ) = MapUiState(
        handle = "monarch",
        territoryColor = Color(0xFF4C8DFF),
        hexesHeld = 0,
        hexesHeldDelta = 0,
        captureAvailability = availability,
        locationPermissionGranted = permission,
        hasFix = hasFix,
        lastRejection = rejection
    )

    @Test
    fun `missing permission outranks every other reason`() {
        val s = state(
            availability = CaptureAvailability.AVAILABLE,
            permission = false,
            rejection = RejectionReason.TOO_FAST
        )
        assertEquals("LOCATION OFF", s.captureLabel())
    }

    @Test
    fun `available state invites the action`() {
        assertEquals("CLAIM HEX", state(availability = CaptureAvailability.AVAILABLE).captureLabel())
    }

    @Test
    fun `in flight state is shown while the server decides`() {
        assertEquals("CLAIMING", state(availability = CaptureAvailability.IN_FLIGHT).captureLabel())
    }

    @Test
    fun `waiting for a first fix says so rather than blaming the player`() {
        assertEquals("ACQUIRING GPS", state(hasFix = false).captureLabel())
    }

    @Test
    fun `weak gps is named explicitly`() {
        assertEquals("GPS TOO WEAK", state(rejection = RejectionReason.LOW_ACCURACY).captureLabel())
    }

    @Test
    fun `moving too fast is named explicitly`() {
        assertEquals("MOVING TOO FAST", state(rejection = RejectionReason.TOO_FAST).captureLabel())
    }

    @Test
    fun `teleport is named explicitly`() {
        assertEquals("SIGNAL JUMPED", state(rejection = RejectionReason.TELEPORT).captureLabel())
    }

    @Test
    fun `mock location is called out plainly rather than hidden`() {
        // Telling a spoofer the rule was enforced is deliberate: silent failure
        // makes the game feel broken to honest players who share the same state.
        assertEquals("MOCK LOCATION", state(rejection = RejectionReason.MOCK_LOCATION).captureLabel())
    }
}