package com.mapmonarchs.feature.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmonarchs.domain.model.TerritoryGeometry
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.ui.theme.MapMonarchsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Preview must be unmistakable ON SCREEN, not just in the type system.
 *
 * A player never reads the sealed hierarchy. What protects them is the banner,
 * the labels, and an exit they can always find -- so those are asserted here
 * rather than assumed from the unit tests.
 */
@RunWith(AndroidJUnit4::class)
class PreviewHudTest {

    @get:Rule
    val rule = createComposeRule()

    private fun previewState(
        held: Int = 0,
        territory: TerritoryLoad = TerritoryLoad.Loaded(TerritoryGeometry.EMPTY)
    ) = MapUiState(
        identity = Identity.preview(),
        territory = territory,
        capture = CaptureState.Ready,
        // Permission must be GRANTED for `capture = Ready` to be a state the app
        // can actually reach. Without it these fixtures described an impossible
        // device -- an accepted position fix with location switched off.
        locationPermissionGranted = true,
        previewMode = true,
        previewHeld = held
    )

    private fun liveState() = MapUiState(
        identity = Identity.from("6f2c1e4a-1111-4b2a-9c3d-000000000001"),
        territory = TerritoryLoad.Loaded(TerritoryGeometry.EMPTY),
        capture = CaptureState.Ready,
        locationPermissionGranted = true
    )

    @Test
    fun previewBannerIsVisibleWhilePreviewIsActive() {
        rule.setContent { MapMonarchsTheme { MapHud(previewState(), {}, {}, {}, {}) } }
        rule.onNodeWithTag(MapHudTags.PREVIEW_BANNER).assertIsDisplayed()
    }

    @Test
    fun previewBannerIsAbsentInLiveState() {
        rule.setContent { MapMonarchsTheme { MapHud(liveState(), {}, {}, {}, {}) } }
        rule.onNodeWithTag(MapHudTags.PREVIEW_BANNER).assertDoesNotExist()
    }

    @Test
    fun previewBannerSaysItIsNotReal() {
        rule.setContent { MapMonarchsTheme { MapHud(previewState(), {}, {}, {}, {}) } }
        rule.onNodeWithText("PREVIEW - SIMULATED, NOT REAL").assertIsDisplayed()
    }

    @Test
    fun exitIsAlwaysReachableAndInvokesTheCallback() {
        var exits = 0
        rule.setContent {
            MapMonarchsTheme { MapHud(previewState(), {}, {}, {}, { exits++ }) }
        }
        rule.onNodeWithText("EXIT").assertIsDisplayed().performClick()
        assertEquals(1, exits)
    }

    @Test
    fun captureLabelInPreviewNeverReadsAsARealClaim() {
        rule.setContent { MapMonarchsTheme { MapHud(previewState(), {}, {}, {}, {}) } }
        rule.onNodeWithTag(MapHudTags.CAPTURE).assertTextEquals("CLAIM (PREVIEW)")
    }

    @Test
    fun identityChipShowsPreviewNotAFabricatedCallsign() {
        rule.setContent { MapMonarchsTheme { MapHud(previewState(), {}, {}, {}, {}) } }
        rule.onNodeWithTag(MapHudTags.HANDLE).assertTextEquals("PREVIEW")
    }

    @Test
    fun previewShowsItsSimulatedCount() {
        rule.setContent { MapMonarchsTheme { MapHud(previewState(held = 4), {}, {}, {}, {}) } }
        rule.onNodeWithTag(MapHudTags.HELD).assertTextEquals("4")
    }

    @Test
    fun previewDoesNotHideARealBackendFailure() {
        // Preview must never make the app look healthier than it is.
        rule.setContent {
            MapMonarchsTheme {
                MapHud(
                    previewState(territory = TerritoryLoad.Failed(AppError.SchemaMissing("42P01"))),
                    {}, {}, {}, {}
                )
            }
        }
        rule.onNodeWithTag(MapHudTags.PREVIEW_BANNER).assertIsDisplayed()
        rule.onNodeWithTag(MapHudTags.ERROR).assertIsDisplayed()
    }
}