package com.mapmonarchs.feature.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.validation.RejectionReason
import com.mapmonarchs.ui.theme.MapMonarchsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The HUD is tested in isolation from MapboxMap on purpose: the GL surface needs
 * a real map context, while the HUD carries the state logic worth asserting.
 */
@RunWith(AndroidJUnit4::class)
class MapHudTest {

    @get:Rule
    val rule = createComposeRule()

    private fun state(
        handle: String = "monarch",
        territory: TerritoryLoad = TerritoryLoad.Loaded(emptyList()),
        capture: CaptureState = CaptureState.Ready,
        delta: Int = 0
    ) = MapUiState(
        handle = handle,
        territoryColor = Color(0xFF4C8DFF),
        territory = territory,
        capture = capture,
        hexesHeldDelta = delta
    )

    @Test
    fun rendersHandleUppercased() {
        rule.setContent {
            MapMonarchsTheme { MapHud(state(handle = "shukhrat"), onCapture = {}, onRetry = {}) }
        }
        rule.onNodeWithTag(MapHudTags.HANDLE).assertTextEquals("SHUKHRAT")
    }

    @Test
    fun holdingsShowUnknownRatherThanZero() {
        rule.setContent {
            MapMonarchsTheme { MapHud(state(), onCapture = {}, onRetry = {}) }
        }
        rule.onNodeWithTag(MapHudTags.HELD).assertTextEquals("--")
    }

    @Test
    fun failureStripIsHiddenWhenTerritoryLoads() {
        rule.setContent {
            MapMonarchsTheme { MapHud(state(), onCapture = {}, onRetry = {}) }
        }
        rule.onNodeWithTag(MapHudTags.ERROR).assertDoesNotExist()
    }

    @Test
    fun failureStripAppearsAndRetryWorksWhenTerritoryFails() {
        var retries = 0
        rule.setContent {
            MapMonarchsTheme {
                MapHud(
                    state(territory = TerritoryLoad.Failed(AppError.SchemaMissing("42P01"))),
                    onCapture = {},
                    onRetry = { retries++ }
                )
            }
        }
        rule.onNodeWithTag(MapHudTags.ERROR).assertIsDisplayed()
        rule.onNodeWithText("RETRY").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun captureInvokesCallbackWhenReady() {
        var clicks = 0
        rule.setContent {
            MapMonarchsTheme {
                MapHud(state(capture = CaptureState.Ready), onCapture = { clicks++ }, onRetry = {})
            }
        }
        rule.onNodeWithTag(MapHudTags.CAPTURE).assertIsDisplayed().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun captureIsInertWhenBlocked() {
        var clicks = 0
        rule.setContent {
            MapMonarchsTheme {
                MapHud(
                    state(capture = CaptureState.Blocked(RejectionReason.MOCK_LOCATION)),
                    onCapture = { clicks++ },
                    onRetry = {}
                )
            }
        }
        rule.onNodeWithTag(MapHudTags.CAPTURE).assertTextEquals("MOCK LOCATION").performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun deltaHiddenWhenZero() {
        rule.setContent {
            MapMonarchsTheme { MapHud(state(delta = 0), onCapture = {}, onRetry = {}) }
        }
        rule.onNodeWithTag(MapHudTags.DELTA).assertDoesNotExist()
    }
}