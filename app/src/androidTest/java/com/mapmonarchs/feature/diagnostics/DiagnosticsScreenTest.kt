package com.mapmonarchs.feature.diagnostics

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmonarchs.data.local.TrackingMode
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.AuthCapabilities
import com.mapmonarchs.domain.model.CapabilitiesProbe
import com.mapmonarchs.domain.model.DeliveryProof
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.model.ProductSurfaces
import com.mapmonarchs.domain.model.WorldPosition
import com.mapmonarchs.feature.map.CaptureState
import com.mapmonarchs.feature.map.MapUiState
import com.mapmonarchs.feature.map.TerritoryLoad
import com.mapmonarchs.ui.theme.MapMonarchsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The screen whose entire job is telling the truth, which until now had no test
 * that it tells the truth.
 *
 * It was verified once by screenshot. A screenshot proves ONE state; this screen
 * renders more than twenty, and the interesting ones are the failures -- a probe
 * that did not answer must read UNKNOWN, never a confident default. That is
 * precisely the bug this screen was rewritten to eliminate, so it is the thing
 * most worth pinning.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val realUser = "6f2c1e4a-1111-4b2a-9c3d-000000000001"

    /** The project's real configuration: email only, no autoconfirm. */
    private val liveCapabilities = AuthCapabilities(
        emailEnabled = true,
        anonymousEnabled = false,
        mailerAutoconfirm = false
    )

    private fun mapState(
        identity: Identity = Identity.from(realUser),
        territory: TerritoryLoad = TerritoryLoad.Failed(AppError.SchemaMissing("42P01")),
        capture: CaptureState = CaptureState.Ready,
        permission: Boolean = true,
        previewMode: Boolean = false,
        worldPosition: WorldPosition = WorldPosition.Inside
    ) = MapUiState(
        identity = identity,
        territory = territory,
        capture = capture,
        locationPermissionGranted = permission,
        previewMode = previewMode,
        worldPosition = worldPosition
    )

    private fun show(
        state: MapUiState = mapState(),
        capabilities: CapabilitiesProbe = CapabilitiesProbe.Observed(liveCapabilities),
        delivery: DeliveryProof = DeliveryProof.NeverAttempted,
        productSurfaces: ProductSurfaces = ProductSurfaces.NotProbed
    ) {
        rule.setContent {
            MapMonarchsTheme {
                DiagnosticsScreen(
                    state = state,
                    diagnostics = DiagnosticsState(
                        capabilities = capabilities,
                        delivery = delivery,
                        productSurfaces = productSurfaces
                    ),
                    trackingMode = TrackingMode.BALANCED,
                    onBack = {},
                    onRefresh = {}
                )
            }
        }
    }

    /**
     * StatusRow now MERGES its label and value into one semantics node, so a
     * screen reader announces "Code delivery: UNCONFIRMED" as a single fact
     * instead of two unrelated fragments. Merging removes the children this used
     * to walk, so the assertion moved onto the merged node itself.
     *
     * This is the production change being correct and the test being stale, not
     * the reverse -- Diagnostics alone was reading out roughly thirty orphaned
     * strings before the merge.
     */
    private fun assertRowValue(tag: String, expected: String) {
        rule.onNodeWithTag(tag).assert(hasText(expected, substring = true))
    }

    // --- observed configuration ---------------------------------------------

    @Test
    fun observedCapabilitiesAreReportedAsObserved() {
        show()
        assertRowValue(DiagnosticsTags.EMAIL_AUTH, "ENABLED")
        assertRowValue(DiagnosticsTags.ANON_AUTH, "DISABLED")
        assertRowValue(DiagnosticsTags.AUTOCONFIRM, "CODE REQUIRED")
    }

    @Test
    fun autoconfirmEnabledIsAlsoReported() {
        // REGRESSION: this fact used to be visible only when it was false. When it
        // flipped true the row did not change -- it disappeared.
        show(capabilities = CapabilitiesProbe.Observed(liveCapabilities.copy(mailerAutoconfirm = true)))
        assertRowValue(DiagnosticsTags.AUTOCONFIRM, "AUTOMATIC")
    }

    // --- the failures are the point -----------------------------------------

    @Test
    fun anUnreachableProbeReadsUnknownAndNeverAConfidentValue() {
        show(capabilities = CapabilitiesProbe.Unavailable(AppError.Offline("dns failure")))
        assertRowValue(DiagnosticsTags.EMAIL_AUTH, "UNKNOWN")
        assertRowValue(DiagnosticsTags.ANON_AUTH, "UNKNOWN")
        assertRowValue(DiagnosticsTags.AUTOCONFIRM, "UNKNOWN")
        assertRowValue(DiagnosticsTags.SIGN_IN_POSSIBLE, "UNKNOWN")
    }

    @Test
    fun anUnprobedScreenSaysSoRatherThanGuessing() {
        show(capabilities = CapabilitiesProbe.NotProbed)
        assertRowValue(DiagnosticsTags.EMAIL_AUTH, "UNKNOWN")
        rule.onNodeWithText("Sign-in configuration has not been checked yet. Press RE-CHECK.")
            .assertExists()
    }

    @Test
    fun probingIsDistinctFromUnknown() {
        // "still asking" and "asked and got nothing" are different facts.
        show(capabilities = CapabilitiesProbe.Probing)
        assertRowValue(DiagnosticsTags.EMAIL_AUTH, "CHECKING")
        assertRowValue(DiagnosticsTags.AUTOCONFIRM, "CHECKING")
    }

    @Test
    fun anUnreachableProbeExplainsWhyTheRowsReadUnknown() {
        val error = AppError.Offline("dns failure")
        show(capabilities = CapabilitiesProbe.Unavailable(error))
        rule.onNodeWithText(
            "The server did not answer when asked how sign-in is configured, so these " +
                "rows read UNKNOWN rather than guessing. " + error.playerMessage()
        ).assertExists()
    }

    // --- delivery proof ------------------------------------------------------

    @Test
    fun anAcceptedSendRequestReadsUnconfirmedNotDelivered() {
        show(delivery = DeliveryProof.Requested("e***@example.com", 1_700_000_000_000))
        assertRowValue(DiagnosticsTags.DELIVERY, "UNCONFIRMED")
        assertRowValue(DiagnosticsTags.SIGN_IN_POSSIBLE, "UNPROVEN")
    }

    @Test
    fun onlyAVerifiedCodeReadsConfirmed() {
        show(delivery = DeliveryProof.Delivered("e***@example.com", 1_700_000_000_000))
        assertRowValue(DiagnosticsTags.DELIVERY, "CONFIRMED")
        assertRowValue(DiagnosticsTags.SIGN_IN_POSSIBLE, "YES")
    }

    @Test
    fun aFreshInstallSaysDeliveryWasNeverAttempted() {
        show(delivery = DeliveryProof.NeverAttempted)
        assertRowValue(DiagnosticsTags.DELIVERY, "NEVER ATTEMPTED")
    }

    @Test
    fun aRefusedSendSurfacesItsSpecificCause() {
        val error = AppError.MailNotDelivered("smtp not configured")
        show(delivery = DeliveryProof.Failed("e***@example.com", error, 1_700_000_000_000))
        assertRowValue(DiagnosticsTags.DELIVERY, "FAILED")
        rule.onNodeWithText(
            "The last send attempt was refused. " + error.playerMessage()
        ).assertExists()
    }

    @Test
    fun anUnreadableRecordIsUnknownNotAFreshInstall() {
        show(delivery = DeliveryProof.Unknown)
        assertRowValue(DiagnosticsTags.DELIVERY, "UNKNOWN")
    }

    @Test
    fun deliveryRowsShowNoRecipientBeforeAnyAttempt() {
        show(delivery = DeliveryProof.NeverAttempted)
        rule.onNodeWithTag(DiagnosticsTags.DELIVERY).assertExists()
        // No address is invented for an attempt that never happened.
        rule.onNodeWithText("Recipient").assertExists()
    }


    // --- the playable world -------------------------------------------------

    @Test
    fun beingInsideTheWorldIsReported() {
        show(state = mapState(worldPosition = WorldPosition.Inside))
        assertRowValue(DiagnosticsTags.WORLD, "INSIDE")
    }

    @Test
    fun beingOutsideNamesTheCountryAndSaysClaimingIsOff() {
        show(state = mapState(worldPosition = WorldPosition.Outside("Kazakhstan")))
        assertRowValue(DiagnosticsTags.WORLD, "OUTSIDE")
        rule.onNodeWithText("You are in Kazakhstan.", substring = true).assertExists()
    }

    /**
     * THE ROW THIS SECTION EXISTS FOR. Unknown deliberately does not block the
     * player, so without an explicit readout a boundary service that had stopped
     * answering would be completely invisible -- the app would simply behave as
     * though it knew where they were.
     */
    @Test
    fun anUnknownWorldPositionIsStatedRatherThanHidden() {
        show(state = mapState(worldPosition = WorldPosition.Unknown("timed out")))
        assertRowValue(DiagnosticsTags.WORLD, "UNKNOWN")
        rule.onNodeWithText("cannot confirm whether you", substring = true).assertExists()
        rule.onNodeWithText("timed out", substring = true).assertExists()
    }

    @Test
    fun anUncheckedPositionIsDistinctFromAnUnknownOne() {
        // "nobody has looked yet" and "we looked and could not tell" are
        // different facts, and a player debugging a dark map needs to tell them
        // apart.
        show(state = mapState(worldPosition = WorldPosition.NotChecked))
        assertRowValue(DiagnosticsTags.WORLD, "NOT CHECKED")
    }
    // --- the rest of the screen still tells the truth ------------------------

    @Test
    fun theSchemaGateIsReportedAsNotDeployed() {
        show(state = mapState(territory = TerritoryLoad.Failed(AppError.SchemaMissing("42P01"))))
        assertRowValue(DiagnosticsTags.BACKEND, "NOT DEPLOYED")
    }

    @Test
    fun aSignedOutPlayerIsNeverToldNothingIsBlockingThem() {
        // REGRESSION: Identity.SignedOut used to fall through every branch of the
        // old blocker chain and land on "Nothing is blocking movement tracking".
        show(state = mapState(identity = Identity.SignedOut))
        assertRowValue(DiagnosticsTags.SESSION, "NONE")
        // The blocker must name the remedy, not just the symptom.
        rule.onNodeWithTag(DiagnosticsTags.BLOCKER)
            .assert(hasText("You are not signed in", substring = true))
    }

    @Test
    fun previewIsReportedAsActiveAndNamedAsSimulated() {
        show(state = mapState(identity = Identity.preview(), previewMode = true))
        assertRowValue(DiagnosticsTags.SESSION, "PREVIEW (NOT SIGNED IN)")
        rule.onNodeWithText(
            "Preview mode is active. Everything you claim is simulated on this device, " +
                "held in memory, and discarded when you leave. Nothing is sent to the " +
                "server and none of it is real territory."
        ).assertExists()
    }

    @Test
    fun reCheckIsWiredToARealCallback() {
        var refreshes = 0
        rule.setContent {
            MapMonarchsTheme {
                DiagnosticsScreen(
                    state = mapState(),
                    diagnostics = DiagnosticsState(),
                    trackingMode = TrackingMode.BALANCED,
                    onBack = {},
                    onRefresh = { refreshes++ }
                )
            }
        }
        // RE-CHECK sits below the fold on this screen, and Compose will not click
        // a node that is not laid out on screen. Scrolling first is what a player
        // does; without it the assertion fails for a reason that has nothing to
        // do with the wiring under test.
        rule.onNodeWithTag(DiagnosticsTags.REFRESH).performScrollTo().performClick()
        assertEquals(1, refreshes)
    }

    // --- product-surfaces deployment probe (debug builds only) ---------------
    // The card renders only under BuildConfig.DEBUG; the androidTest apk is a
    // debug build, so it is present here.

    @Test
    fun aReadableCatalogueReportsTheMigrationDeployedWithItsSize() {
        show(productSurfaces = ProductSurfaces.Deployed(catalogueSize = 9))
        rule.onNodeWithTag(DiagnosticsTags.PRODUCT_SURFACES).performScrollTo()
            .assert(hasText("DEPLOYED", substring = true))
        // The observed count is shown, not a hardcoded expectation.
        rule.onNodeWithText("9", substring = true).assertExists()
    }

    @Test
    fun anAbsentTableReportsTheMigrationNotDeployed() {
        show(productSurfaces = ProductSurfaces.Missing)
        rule.onNodeWithTag(DiagnosticsTags.PRODUCT_SURFACES).performScrollTo()
            .assert(hasText("NOT DEPLOYED", substring = true))
    }

    @Test
    fun anUnprobedCatalogueReadsUnknownRatherThanGuessing() {
        // The default state on a screen that has not run the probe: it must not
        // claim the backend is either present or absent.
        show(productSurfaces = ProductSurfaces.NotProbed)
        rule.onNodeWithTag(DiagnosticsTags.PRODUCT_SURFACES).performScrollTo()
            .assert(hasText("UNKNOWN", substring = true))
    }

    @Test
    fun anUnreachableCatalogueSurfacesItsCause() {
        val error = AppError.Offline("dns failure")
        show(productSurfaces = ProductSurfaces.Unavailable(error))
        rule.onNodeWithTag(DiagnosticsTags.PRODUCT_SURFACES).performScrollTo()
            .assert(hasText("NO NETWORK", substring = true))
        rule.onNodeWithText(error.playerMessage(), substring = true).assertExists()
    }
}
