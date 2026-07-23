package com.mapmonarchs.feature.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.validation.RejectionReason
import com.mapmonarchs.ui.theme.MapMonarchsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The HUD is tested in isolation from MapboxMap on purpose: the GL surface needs
 * a real map context, while the HUD carries the state logic worth asserting.
 *
 * Every state built here is COHERENT. An earlier version of this suite tested a
 * "ready to claim" HUD while location permission was denied -- a combination the
 * app can never produce -- and passed, because the old actionability check never
 * consulted permission at all. A test that only holds in an impossible state
 * proves nothing about the app.
 */
@RunWith(AndroidJUnit4::class)
class MapHudTest {

    @get:Rule
    val rule = createComposeRule()

    private val realUser = "6f2c1e4a-1111-4b2a-9c3d-000000000001"

    /** Defaults to a fully healthy live state: signed in, permission held, fix accepted. */
    private fun state(
        identity: Identity = Identity.from(realUser),
        territory: TerritoryLoad = TerritoryLoad.Loaded(emptyList()),
        capture: CaptureState = CaptureState.Ready,
        permission: Boolean = true,
        previewMode: Boolean = false,
        previewHeld: Int? = null
    ) = MapUiState(
        identity = identity,
        territory = territory,
        capture = capture,
        locationPermissionGranted = permission,
        previewMode = previewMode,
        previewHeld = previewHeld
    )

    @Test
    fun rendersDerivedCallsignForSignedInUser() {
        val id = Identity.from(realUser)
        rule.setContent {
            MapMonarchsTheme { MapHud(state(identity = id), {}, {}, {}) }
        }
        rule.onNodeWithTag(MapHudTags.HANDLE).assertTextEquals(id.callsign)
    }

    @Test
    fun neverShowsAFabricatedHandleWhenSignedOut() {
        rule.setContent {
            MapMonarchsTheme { MapHud(state(identity = Identity.SignedOut), {}, {}, {}) }
        }
        rule.onNodeWithTag(MapHudTags.HANDLE).assertTextEquals("NO IDENTITY")
    }

    @Test
    fun identityUnavailableIsShownExplicitlyWithItsCause() {
        rule.setContent {
            MapMonarchsTheme {
                MapHud(state(identity = Identity.Unavailable(AppError.AuthUnavailable("disabled"))), {}, {}, {})
            }
        }
        rule.onNodeWithTag(MapHudTags.HANDLE).assertTextEquals("IDENTITY UNAVAILABLE")
        rule.onNodeWithTag(MapHudTags.ERROR).assertIsDisplayed()
    }

    @Test
    fun holdingsShowUnknownRatherThanZero() {
        rule.setContent { MapMonarchsTheme { MapHud(state(), {}, {}, {}) } }
        rule.onNodeWithTag(MapHudTags.HELD).assertTextEquals(UNKNOWN_HOLDINGS)
    }

    @Test
    fun failureStripIsHiddenWhenEverythingLoads() {
        rule.setContent { MapMonarchsTheme { MapHud(state(), {}, {}, {}) } }
        rule.onNodeWithTag(MapHudTags.ERROR).assertDoesNotExist()
    }

    @Test
    fun failureStripAppearsAndRetryWorksWhenTerritoryFails() {
        var retries = 0
        rule.setContent {
            MapMonarchsTheme {
                MapHud(
                    state(territory = TerritoryLoad.Failed(AppError.SchemaMissing("42P01"))),
                    {}, { retries++ }, {}
                )
            }
        }
        rule.onNodeWithTag(MapHudTags.ERROR).assertIsDisplayed()
        rule.onNodeWithText("RETRY").performClick()
        assertEquals(1, retries)
    }

    // --- what the button does, per real blocker -----------------------------

    /**
     * The honest current truth of a LIVE session now that the backend is
     * deployed: everything the player controls is correct, so the button reads
     * CLAIM HEX and firing it submits the claim (the server, authoritative,
     * re-checks every gate). It does NOT route to an explanation -- there is no
     * blocker left to explain.
     */
    @Test
    fun fullyHealthyLiveStateIsReadyToClaim() {
        var captures = 0
        var explains = 0
        rule.setContent {
            MapMonarchsTheme { MapHud(state(), { captures++ }, {}, { explains++ }) }
        }
        rule.onNodeWithTag(MapHudTags.CAPTURE)
            .assertTextEquals("CLAIM HEX")
            .performClick()
        assertEquals("a ready claim must fire the capture, not an explanation", 1, captures)
        assertEquals(0, explains)
    }

    /** Preview is the only place a claim actually fires, and it is labelled as simulated. */
    @Test
    fun previewClaimIsActionableAndLabelledAsSimulated() {
        var captures = 0
        var explains = 0
        rule.setContent {
            MapMonarchsTheme {
                MapHud(
                    state(identity = Identity.preview(), previewMode = true, previewHeld = 3),
                    { captures++ }, {}, { explains++ }
                )
            }
        }
        rule.onNodeWithTag(MapHudTags.CAPTURE)
            .assertTextEquals("CLAIM (PREVIEW)")
            .performClick()
        assertEquals(1, captures)
        assertEquals("a ready preview must not route to the explanation", 0, explains)
        rule.onNodeWithTag(MapHudTags.HELD).assertTextEquals("3")
    }

    // --- regression: a blocked button used to swallow the tap entirely ---

    @Test
    fun blockedCaptureRoutesToExplanationInsteadOfSwallowingTheTap() {
        var captures = 0
        var explains = 0
        rule.setContent {
            MapMonarchsTheme {
                MapHud(
                    state(capture = CaptureState.Blocked(RejectionReason.MOCK_LOCATION)),
                    { captures++ }, {}, { explains++ }
                )
            }
        }
        rule.onNodeWithTag(MapHudTags.CAPTURE).assertTextEquals("MOCK LOCATION").performClick()
        assertEquals("must never claim while blocked", 0, captures)
        assertEquals("a dead tap is a dead end", 1, explains)
    }

    /**
     * Regression for the case that used to fall through every branch: signed out,
     * everything else healthy. The HUD names the remedy rather than the symptom.
     */
    @Test
    fun signedOutNamesTheRemedyNotJustTheSymptom() {
        var captures = 0
        var explains = 0
        rule.setContent {
            MapMonarchsTheme {
                MapHud(
                    state(identity = Identity.SignedOut),
                    { captures++ }, {}, { explains++ }
                )
            }
        }
        rule.onNodeWithTag(MapHudTags.CAPTURE).assertTextEquals("SIGN IN TO CLAIM").performClick()
        assertEquals(0, captures)
        assertEquals(1, explains)
    }

    /** Permission outranks identity: the one-tap fix is named before the email round-trip. */
    @Test
    fun deniedPermissionOutranksMissingIdentity() {
        rule.setContent {
            MapMonarchsTheme {
                MapHud(
                    state(
                        identity = Identity.SignedOut,
                        capture = CaptureState.PermissionRequired,
                        permission = false
                    ),
                    {}, {}, {}
                )
            }
        }
        rule.onNodeWithTag(MapHudTags.CAPTURE).assertTextEquals("LOCATION OFF")
    }
}
