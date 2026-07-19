package com.mapmonarchs.feature.diagnostics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mapmonarchs.BuildConfig
import com.mapmonarchs.data.local.TrackingMode
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.feature.map.CaptureState
import com.mapmonarchs.feature.map.MapUiState
import com.mapmonarchs.feature.map.TerritoryLoad
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.components.StatusRow
import com.mapmonarchs.ui.theme.Contested
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted

object DiagnosticsTags {
    const val BACKEND = "diag_backend"
    const val SESSION = "diag_session"
    const val PERMISSION = "diag_permission"
    const val FIX = "diag_fix"
    const val BLOCKER = "diag_blocker"
    const val REFRESH = "diag_refresh"
}

/**
 * PROBLEM SOLVED: "why can't I claim anything?" is the central frustration of a
 * location game, and normally the player has no way to answer it. Every failure
 * looks identical from the outside -- a button that will not respond.
 *
 * WHY IT BELONGS: this product's whole trust position is that the rules are real
 * and enforced server-side. A player who cannot see WHICH rule stopped them has
 * to take that on faith. This screen is the receipt.
 *
 * WHY NOT GENERIC: it is not a debug dump behind a hidden gesture. It is a
 * first-class readout in the command menu, written for a player rather than an
 * engineer -- every row names a subsystem in plain language and says what its
 * state means for what they can do right now.
 *
 * WHAT CAN GO WRONG: exposing internals invites confusion or scraping. Mitigated
 * by showing STATE, never raw technical strings, tokens, ids, or coordinates.
 */
@Composable
fun DiagnosticsScreen(
    state: MapUiState,
    trackingMode: TrackingMode,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    CommandScreen(title = "DIAGNOSTICS", onBack = onBack, modifier = modifier) {

        // The single most useful line: what is stopping you right now.
        SectionHeader("PRIMARY BLOCKER")
        BodyNote(
            text = primaryBlocker(state),
            modifier = Modifier.testTag(DiagnosticsTags.BLOCKER)
        )

        SectionHeader("TERRITORY SERVICE")
        StatusRow(
            label = "Backend",
            value = when (val t = state.territory) {
                is TerritoryLoad.Loading -> "CHECKING"
                is TerritoryLoad.Loaded -> "REACHABLE"
                is TerritoryLoad.Failed -> when (t.error) {
                    is AppError.Offline -> "NO NETWORK"
                    is AppError.SchemaMissing -> "NOT DEPLOYED"
                    is AppError.Unauthorized -> "REFUSED"
                    else -> "ERROR"
                }
            },
            valueColor = if (state.territory is TerritoryLoad.Loaded) SovereignGlow else Contested,
            modifier = Modifier.testTag(DiagnosticsTags.BACKEND)
        )
        StatusRow(
            label = "Hexes returned",
            value = (state.territory as? TerritoryLoad.Loaded)?.hexes?.size?.toString() ?: "--",
            valueColor = TextMuted
        )
        if (state.territory is TerritoryLoad.Failed) {
            BodyNote((state.territory as TerritoryLoad.Failed).error.playerMessage())
        }

        SectionHeader("SESSION")
        StatusRow(
            label = "Identity",
            value = when (state.identity) {
                is Identity.SignedIn -> "ACTIVE"
                // Named distinctly so a preview run can never be misread as a session.
                is Identity.Preview -> "PREVIEW (NOT SIGNED IN)"
                is Identity.Resolving -> "RESOLVING"
                is Identity.SignedOut -> "NONE"
                is Identity.Unavailable -> "UNAVAILABLE"
            },
            valueColor = if (state.identity is Identity.SignedIn) SovereignGlow else Contested,
            modifier = Modifier.testTag(DiagnosticsTags.SESSION)
        )
        StatusRow(
            label = "Callsign",
            value = (state.identity as? Identity.SignedIn)?.callsign ?: "--",
            valueColor = TextMuted
        )

        SectionHeader("LOCATION")
        StatusRow(
            label = "Permission",
            value = if (state.locationPermissionGranted) "GRANTED" else "DENIED",
            valueColor = if (state.locationPermissionGranted) SovereignGlow else Contested,
            modifier = Modifier.testTag(DiagnosticsTags.PERMISSION)
        )
        StatusRow(
            label = "Position fix",
            value = when (state.capture) {
                is CaptureState.PermissionRequired -> "NO PERMISSION"
                is CaptureState.AcquiringFix -> "ACQUIRING"
                is CaptureState.Blocked -> "REJECTED"
                else -> "ACCEPTED"
            },
            valueColor = when (state.capture) {
                is CaptureState.Ready, is CaptureState.InFlight -> SovereignGlow
                is CaptureState.AcquiringFix -> TextMuted
                else -> Contested
            },
            modifier = Modifier.testTag(DiagnosticsTags.FIX)
        )
        StatusRow(
            label = "Last rejection",
            value = (state.capture as? CaptureState.Blocked)?.reason?.name ?: "NONE",
            valueColor = TextMuted
        )
        StatusRow(label = "Cadence", value = trackingMode.label, valueColor = TextMuted)

        SectionHeader("CAPTURE")
        StatusRow(
            label = "Status",
            value = if (state.previewMode) "PREVIEW ONLY" else "DISABLED",
            valueColor = Contested
        )
        if (state.previewMode) {
            BodyNote(
                "Preview mode is active. Everything you claim is simulated on this device, " +
                    "held in memory, and discarded when you leave. Nothing is sent to the " +
                    "server and none of it is real territory."
            )
        }
        BodyNote(
            "Claiming is switched off in this build. The server function that decides " +
                "ownership has not been deployed, so no client can claim ground -- including " +
                "a modified one."
        )

        SectionHeader("BUILD")
        StatusRow(label = "Version", value = BuildConfig.VERSION_NAME, valueColor = TextMuted)
        StatusRow(
            label = "Type",
            value = if (BuildConfig.DEBUG) "DEBUG" else "RELEASE",
            valueColor = TextMuted
        )

        CommandAction(
            label = "RE-CHECK",
            onClick = onRefresh,
            modifier = Modifier
                .padding(top = 20.dp)
                .testTag(DiagnosticsTags.REFRESH)
        )

        Box(modifier = Modifier.padding(bottom = 40.dp))
    }
}

/**
 * Ordered by what actually blocks the player first.
 *
 * Permission, then identity, then backend, then fix quality. Reporting a GPS
 * accuracy problem to someone who never granted permission would be technically
 * true and completely useless.
 */
internal fun primaryBlocker(state: MapUiState): String = when {
    !state.locationPermissionGranted ->
        "Location permission is not granted, so the app cannot tell which hex you are in. " +
            "Grant it in SETTINGS."

    state.identity is Identity.Unavailable ->
        "Sign-in is unavailable, so the server cannot attribute territory to you. " +
            "Claiming stays disabled until a session exists."

    state.identity is Identity.Resolving ->
        "Still establishing a session."

    state.territory is TerritoryLoad.Failed ->
        "The territory service is not answering, so the map cannot show who holds what."

    state.capture is CaptureState.Blocked ->
        "Your last position reading was rejected: " +
            (state.capture as CaptureState.Blocked).reason.name.lowercase().replace('_', ' ') + "."

    state.capture is CaptureState.AcquiringFix ->
        "Waiting for a position accurate enough to act on."

    else ->
        "Nothing is blocking movement tracking. Claiming itself is disabled in this build."
}