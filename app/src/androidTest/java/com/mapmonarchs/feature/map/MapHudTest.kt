package com.mapmonarchs.feature.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmonarchs.ui.theme.MapMonarchsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The HUD is tested in isolation from MapboxMap on purpose: the GL surface needs
 * a real map context, while the HUD is the part that carries actual state logic.
 */
@RunWith(AndroidJUnit4::class)
class MapHudTest {

    @get:Rule
    val rule = createComposeRule()

    private fun state(
        handle: String = "monarch",
        hexesHeld: Int = 0,
        delta: Int = 0,
        availability: CaptureAvailability = CaptureAvailability.AVAILABLE
    ) = MapUiState(
        handle = handle,
        territoryColor = Color(0xFF4C8DFF),
        hexesHeld = hexesHeld,
        hexesHeldDelta = delta,
        captureAvailability = availability,
        locationPermissionGranted = true,
        hasFix = true,
        lastRejection = null
    )

    @Test
    fun rendersHandleUppercasedAndHoldingsFromState() {
        rule.setContent {
            MapMonarchsTheme { MapHud(state = state(handle = "shukhrat", hexesHeld = 47), onCapture = {}) }
        }
        rule.onNodeWithTag(MapHudTags.HANDLE).assertTextEquals("SHUKHRAT")
        rule.onNodeWithTag(MapHudTags.HELD).assertTextEquals("47")
    }

    @Test
    fun showsPositiveDeltaWithExplicitSign() {
        rule.setContent {
            MapMonarchsTheme { MapHud(state = state(delta = 3), onCapture = {}) }
        }
        rule.onNodeWithTag(MapHudTags.DELTA).assertTextEquals("+3")
    }

    @Test
    fun hidesDeltaWhenZero() {
        rule.setContent {
            MapMonarchsTheme { MapHud(state = state(delta = 0), onCapture = {}) }
        }
        rule.onNodeWithTag(MapHudTags.DELTA).assertDoesNotExist()
    }

    @Test
    fun captureInvokesCallbackWhenAvailable() {
        var clicks = 0
        rule.setContent {
            MapMonarchsTheme {
                MapHud(state = state(availability = CaptureAvailability.AVAILABLE), onCapture = { clicks++ })
            }
        }
        rule.onNodeWithTag(MapHudTags.CAPTURE).assertIsDisplayed().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun captureDoesNotInvokeCallbackWhenOutOfRange() {
        var clicks = 0
        rule.setContent {
            MapMonarchsTheme {
                MapHud(state = state(availability = CaptureAvailability.UNAVAILABLE), onCapture = { clicks++ })
            }
        }
        rule.onNodeWithTag(MapHudTags.CAPTURE).performClick()
        assertEquals(0, clicks)
    }
}