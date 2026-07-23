package com.mapmonarchs.feature.diagnostics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mapmonarchs.R
import androidx.compose.ui.unit.dp
import com.mapmonarchs.BuildConfig
import com.mapmonarchs.data.local.TrackingMode
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.CapabilitiesProbe
import com.mapmonarchs.domain.model.DeliveryProof
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.model.PLAYABLE_COUNTRY_NAME
import com.mapmonarchs.domain.model.WorldPosition
import com.mapmonarchs.domain.model.SignInReadiness
import com.mapmonarchs.domain.model.signInReadiness
import com.mapmonarchs.feature.map.CaptureState
import com.mapmonarchs.feature.map.MapUiState
import com.mapmonarchs.feature.map.TerritoryLoad
import com.mapmonarchs.feature.map.UNKNOWN_HOLDINGS
import com.mapmonarchs.feature.map.resolveBlocker
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.CommandCard
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.components.StatusRow
import com.mapmonarchs.ui.theme.Contested
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsTags {
    const val BACKEND = "diag_backend"
    const val SESSION = "diag_session"
    const val PERMISSION = "diag_permission"
    const val FIX = "diag_fix"
    const val BLOCKER = "diag_blocker"
    const val REFRESH = "diag_refresh"
    const val EMAIL_AUTH = "diag_email_auth"
    const val ANON_AUTH = "diag_anon_auth"
    const val DELIVERY = "diag_delivery"
    const val SIGN_IN_POSSIBLE = "diag_signin_possible"
    const val AUTOCONFIRM = "diag_autoconfirm"
    const val WORLD = "diag_world"
}

/**
 * PROBLEM SOLVED: "why can I not claim anything" is the central frustration of a
 * location game, and normally the player has no way to answer it. Every failure
 * looks identical from the outside -- a button that will not respond.
 *
 * WHY IT BELONGS: the whole trust position of this product is that the rules are
 * real and enforced server-side. A player who cannot see WHICH rule stopped them
 * has to take that on faith. This screen is the receipt.
 *
 * WHY NOT GENERIC: it is not a debug dump behind a hidden gesture. It is a
 * first-class destination in the command deck, written for a player rather than an
 * engineer -- every row names a subsystem in plain language and says what its
 * state means for what they can do right now.
 *
 * THE RULE THIS SCREEN LIVES BY: it reports what was observed, or it reports
 * UNKNOWN. It never prints a remembered fact as a current one. Provider status
 * was previously hardcoded here, which meant this screen would have kept
 * insisting anonymous sign-in was disabled long after someone enabled it.
 *
 * PANEL LANGUAGE: the readouts for each subsystem sit in their own CommandCard, so
 * the screen reads as a rack of instruments rather than a wall of loose text. The
 * explanatory notes for a panel sit BELOW it -- a panel holds live values, prose
 * explains them, and keeping prose out of the panels is what stops a dense screen
 * from turning into a busy one.
 */
@Composable
fun DiagnosticsScreen(
    state: MapUiState,
    diagnostics: DiagnosticsState,
    trackingMode: TrackingMode,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    CommandScreen(
        title = stringResource(R.string.diag_title),
        onBack = onBack,
        modifier = modifier
    ) {

        // The single most useful line: what is stopping you right now. Derived
        // from the one shared resolver, so it can never contradict the HUD. Prose,
        // not a readout -- so it leads with a section rule, not a panel.
        SectionHeader(stringResource(R.string.diag_primary_blocker))
        BodyNote(
            text = resolveBlocker(state).explanation(),
            modifier = Modifier.testTag(DiagnosticsTags.BLOCKER)
        )

        CommandCard(title = stringResource(R.string.diag_territory_service)) {
            StatusRow(
                label = stringResource(R.string.diag_backend),
                value = when (val t = state.territory) {
                    is TerritoryLoad.Loading -> stringResource(R.string.diag_checking)
                    is TerritoryLoad.Loaded -> stringResource(R.string.diag_reachable)
                    is TerritoryLoad.Failed -> when (t.error) {
                        is AppError.Offline -> stringResource(R.string.diag_no_network)
                        is AppError.SchemaMissing -> stringResource(R.string.diag_not_deployed)
                        is AppError.Unauthorized -> stringResource(R.string.diag_refused)
                        else -> stringResource(R.string.diag_error)
                    }
                },
                valueColor = if (state.territory is TerritoryLoad.Loaded) SovereignGlow else Contested,
                modifier = Modifier.testTag(DiagnosticsTags.BACKEND)
            )
            StatusRow(
                label = stringResource(R.string.diag_hexes_returned),
                value = (state.territory as? TerritoryLoad.Loaded)?.hexes?.size?.toString() ?: UNKNOWN_HOLDINGS,
                valueColor = TextMuted
            )
        }
        if (state.territory is TerritoryLoad.Failed) {
            BodyNote(
                (state.territory as TerritoryLoad.Failed).error.playerMessage(),
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        CommandCard(title = stringResource(R.string.diag_session)) {
            StatusRow(
                label = stringResource(R.string.diag_identity),
                value = when (state.identity) {
                    is Identity.SignedIn -> stringResource(R.string.diag_active)
                    is Identity.Preview -> stringResource(R.string.diag_preview_not_signed_in)
                    is Identity.Resolving -> stringResource(R.string.diag_resolving)
                    is Identity.SignedOut -> stringResource(R.string.diag_none)
                    is Identity.Unavailable -> stringResource(R.string.diag_unavailable)
                },
                valueColor = if (state.identity is Identity.SignedIn) SovereignGlow else Contested,
                modifier = Modifier.testTag(DiagnosticsTags.SESSION)
            )
            StatusRow(
                label = stringResource(R.string.diag_callsign),
                value = (state.identity as? Identity.SignedIn)?.callsign ?: UNKNOWN_HOLDINGS,
                valueColor = TextMuted
            )
        }

        CommandCard(title = stringResource(R.string.diag_signin_availability)) {
            AuthCapabilityRows(diagnostics)
        }
        AuthCapabilityNotes(diagnostics)

        CommandCard(title = stringResource(R.string.diag_email_delivery)) {
            DeliveryRows(diagnostics)
        }
        DeliveryNotes(diagnostics)

        CommandCard(title = stringResource(R.string.diag_playable_world)) {
            StatusRow(
                label = stringResource(R.string.diag_game_world),
                value = PLAYABLE_COUNTRY_NAME.uppercase(),
                valueColor = TextMuted
            )
            StatusRow(
                label = stringResource(R.string.diag_your_position),
                value = when (state.worldPosition) {
                    is WorldPosition.Inside -> stringResource(R.string.diag_inside)
                    is WorldPosition.Outside -> stringResource(R.string.diag_outside)
                    is WorldPosition.Unknown -> stringResource(R.string.diag_unknown)
                    is WorldPosition.NotChecked -> stringResource(R.string.diag_not_checked)
                },
                valueColor = if (state.worldPosition is WorldPosition.Inside) SovereignGlow else Contested,
                modifier = Modifier.testTag(DiagnosticsTags.WORLD)
            )
        }
        when (val w = state.worldPosition) {
            is WorldPosition.Inside -> BodyNote(
                "You are inside the playable world. Ground here can be claimed once the " +
                    "capture service is deployed.",
                modifier = Modifier.padding(top = 12.dp)
            )

            is WorldPosition.Outside -> BodyNote(
                if (w.country != null) {
                    "You are in ${w.country}. Territory can only be claimed inside " +
                        "$PLAYABLE_COUNTRY_NAME, so the map is dimmed here and claiming is off."
                } else {
                    "You are outside $PLAYABLE_COUNTRY_NAME, so the map is dimmed here " +
                        "and claiming is off."
                },
                modifier = Modifier.padding(top = 12.dp)
            )

            // Unknown deliberately does NOT block the player -- a dropped lookup is
            // not evidence they left the country -- which means that without saying
            // so here, a failing boundary service would be completely invisible.
            is WorldPosition.Unknown -> BodyNote(
                "The boundary service did not answer, so we cannot confirm whether you " +
                    "are inside $PLAYABLE_COUNTRY_NAME. Claiming is not blocked by this -- " +
                    "a failed check is not proof you left. " + w.reason,
                modifier = Modifier.padding(top = 12.dp)
            )

            is WorldPosition.NotChecked -> BodyNote(
                "Not checked yet. This resolves once a position fix is accepted.",
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        CommandCard(title = stringResource(R.string.diag_location)) {
            StatusRow(
                label = stringResource(R.string.diag_permission),
                value = if (state.locationPermissionGranted) "GRANTED" else "DENIED",
                valueColor = if (state.locationPermissionGranted) SovereignGlow else Contested,
                modifier = Modifier.testTag(DiagnosticsTags.PERMISSION)
            )
            StatusRow(
                label = stringResource(R.string.diag_position_fix),
                value = when (state.capture) {
                    is CaptureState.PermissionRequired -> stringResource(R.string.diag_no_permission)
                    is CaptureState.AcquiringFix -> stringResource(R.string.diag_acquiring)
                    is CaptureState.Blocked -> stringResource(R.string.diag_rejected)
                    else -> stringResource(R.string.diag_accepted)
                },
                valueColor = when (state.capture) {
                    is CaptureState.Ready, is CaptureState.InFlight -> SovereignGlow
                    is CaptureState.AcquiringFix -> TextMuted
                    else -> Contested
                },
                modifier = Modifier.testTag(DiagnosticsTags.FIX)
            )
            StatusRow(
                label = stringResource(R.string.diag_last_rejection),
                value = (state.capture as? CaptureState.Blocked)?.reason?.name ?: "NONE",
                valueColor = TextMuted
            )
            StatusRow(label = stringResource(R.string.diag_cadence), value = stringResource(trackingMode.labelRes), valueColor = TextMuted)
        }

        CommandCard(title = stringResource(R.string.diag_capture)) {
            StatusRow(
                label = stringResource(R.string.diag_status),
                value = if (state.previewMode) stringResource(R.string.diag_preview_only) else stringResource(R.string.diag_live),
                valueColor = if (state.previewMode) Contested else SovereignGlow
            )
        }
        if (state.previewMode) {
            BodyNote(
                "Preview mode is active. Everything you claim is simulated on this device, " +
                    "held in memory, and discarded when you leave. Nothing is sent to the " +
                    "server and none of it is real territory.",
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        BodyNote(
            "The capture backend is live and server-authoritative. Claiming a hex " +
                "submits your raw position; the server derives the cell, enforces the " +
                "anti-cheat gates, and records the attempt. Everything you claim is real.",
            modifier = Modifier.padding(top = 8.dp)
        )

        CommandCard(title = stringResource(R.string.diag_build)) {
            StatusRow(label = stringResource(R.string.diag_version), value = BuildConfig.VERSION_NAME, valueColor = TextMuted)
            StatusRow(
                label = stringResource(R.string.diag_type),
                value = if (BuildConfig.DEBUG) "DEBUG" else "RELEASE",
                valueColor = TextMuted
            )
        }

        CommandAction(
            label = stringResource(R.string.diag_recheck),
            onClick = onRefresh,
            modifier = Modifier
                .padding(top = 20.dp)
                .testTag(DiagnosticsTags.REFRESH)
        )

    }
}

// The four sign-in readouts. Rows only -- the notes that explain them live in
// AuthCapabilityNotes, so this whole block can sit cleanly inside a panel.
@Composable
private fun ColumnScope.AuthCapabilityRows(diagnostics: DiagnosticsState) {
    val probe = diagnostics.capabilities
    val readiness = signInReadiness(probe, diagnostics.delivery)
    StatusRow(
        label = stringResource(R.string.diag_email_signin),
        value = probe.readBoolean { it.emailEnabled },
        valueColor = probe.colorFor { it.emailEnabled },
        modifier = Modifier.testTag(DiagnosticsTags.EMAIL_AUTH)
    )
    StatusRow(
        label = stringResource(R.string.diag_anonymous_signin),
        value = probe.readBoolean { it.anonymousEnabled },
        valueColor = TextMuted,
        modifier = Modifier.testTag(DiagnosticsTags.ANON_AUTH)
    )
    StatusRow(
        label = stringResource(R.string.diag_signin_possible),
        value = readiness.label,
        valueColor = if (readiness == SignInReadiness.POSSIBLE) SovereignGlow else Contested,
        modifier = Modifier.testTag(DiagnosticsTags.SIGN_IN_POSSIBLE)
    )
    // Always rendered, in every probe state. A fact you are shown only when it
    // holds one particular value is not an observed fact.
    StatusRow(
        label = stringResource(R.string.diag_account_confirmation),
        value = when (probe) {
            is CapabilitiesProbe.Observed ->
                if (probe.capabilities.mailerAutoconfirm) "AUTOMATIC" else "CODE REQUIRED"
            is CapabilitiesProbe.Probing -> stringResource(R.string.diag_checking)
            else -> stringResource(R.string.diag_unknown)
        },
        valueColor = TextMuted,
        modifier = Modifier.testTag(DiagnosticsTags.AUTOCONFIRM)
    )
}

// The prose that explains the sign-in panel. Sits below the panel, never in it.
@Composable
private fun AuthCapabilityNotes(diagnostics: DiagnosticsState) {
    val probe = diagnostics.capabilities
    val readiness = signInReadiness(probe, diagnostics.delivery)
    if (readiness == SignInReadiness.UNPROVEN) {
        BodyNote(
            "Email is the only sign-in route on this project, and no code has been " +
                "confirmed as arriving on this device. So sign-in is UNPROVEN rather " +
                "than available -- an enabled provider is not a working one.",
            modifier = Modifier.padding(top = 12.dp)
        )
    }
    when (probe) {
        is CapabilitiesProbe.NotProbed ->
            BodyNote(
                "Sign-in configuration has not been checked yet. Press RE-CHECK.",
                modifier = Modifier.padding(top = 12.dp)
            )

        is CapabilitiesProbe.Probing ->
            BodyNote(
                "Asking the server which sign-in methods it accepts.",
                modifier = Modifier.padding(top = 12.dp)
            )

        is CapabilitiesProbe.Unavailable ->
            BodyNote(
                "The server did not answer when asked how sign-in is configured, so these " +
                    "rows read UNKNOWN rather than guessing. " + probe.error.playerMessage(),
                modifier = Modifier.padding(top = 12.dp)
            )

        is CapabilitiesProbe.Observed ->
            if (!probe.capabilities.mailerAutoconfirm) {
                BodyNote(
                    "Accounts are not auto-confirmed on this project, so a code must " +
                        "genuinely arrive by email before anyone can sign in. Mail delivery " +
                        "is therefore required, not optional.",
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
    }
}

// The three delivery readouts. Rows only; prose lives in DeliveryNotes.
@Composable
private fun ColumnScope.DeliveryRows(diagnostics: DiagnosticsState) {
    val delivery = diagnostics.delivery
    StatusRow(
        label = stringResource(R.string.diag_code_delivery),
        value = when (delivery) {
            is DeliveryProof.NeverAttempted -> stringResource(R.string.diag_never_attempted)
            is DeliveryProof.Requested -> stringResource(R.string.diag_unconfirmed)
            is DeliveryProof.Delivered -> stringResource(R.string.diag_confirmed)
            is DeliveryProof.Failed -> stringResource(R.string.diag_failed)
            is DeliveryProof.Unknown -> stringResource(R.string.diag_unknown)
        },
        valueColor = if (delivery is DeliveryProof.Delivered) SovereignGlow else Contested,
        modifier = Modifier.testTag(DiagnosticsTags.DELIVERY)
    )
    StatusRow(
        label = stringResource(R.string.diag_recipient),
        value = when (delivery) {
            is DeliveryProof.Requested -> delivery.recipientHint
            is DeliveryProof.Delivered -> delivery.recipientHint
            is DeliveryProof.Failed -> delivery.recipientHint
            else -> UNKNOWN_HOLDINGS
        },
        valueColor = TextMuted
    )
    StatusRow(
        label = stringResource(R.string.diag_last_attempt),
        value = when (delivery) {
            is DeliveryProof.Requested -> formatMoment(delivery.atEpochMillis)
            is DeliveryProof.Delivered -> formatMoment(delivery.atEpochMillis)
            is DeliveryProof.Failed -> formatMoment(delivery.atEpochMillis)
            else -> UNKNOWN_HOLDINGS
        },
        valueColor = TextMuted
    )
}

// The prose that explains the delivery panel. Sits below the panel, never in it.
@Composable
private fun DeliveryNotes(diagnostics: DiagnosticsState) {
    when (val delivery = diagnostics.delivery) {
        is DeliveryProof.NeverAttempted ->
            BodyNote(
                "No sign-in code has ever been requested from this install, so whether " +
                    "email works here is still untested. Requesting one from the SIGN IN " +
                    "screen is the test.",
                modifier = Modifier.padding(top = 12.dp)
            )

        is DeliveryProof.Requested ->
            BodyNote(
                "The server accepted a request to send a code, but no code has been " +
                    "successfully entered yet. That means delivery is UNPROVEN -- an " +
                    "accepted request is not an arrived email. If nothing reached the " +
                    "inbox, the mail service configured for this project is where to look.",
                modifier = Modifier.padding(top = 12.dp)
            )

        is DeliveryProof.Delivered ->
            BodyNote(
                "A code reached this address and was accepted. Email delivery is proven " +
                    "to work on this project.",
                modifier = Modifier.padding(top = 12.dp)
            )

        is DeliveryProof.Failed ->
            BodyNote(
                "The last send attempt was refused. " + delivery.error.playerMessage(),
                modifier = Modifier.padding(top = 12.dp)
            )

        is DeliveryProof.Unknown ->
            BodyNote(
                "A delivery record exists on this device but could not be read -- most " +
                    "likely written by a different version of the app. Treat delivery as " +
                    "unproven and request a fresh code.",
                modifier = Modifier.padding(top = 12.dp)
            )
    }
}

// Reads one boolean off a probe, or says UNKNOWN. Never defaults to a value.
//
// Composable because the answers are now string resources rather than literals.
// It reads state and returns text; it draws nothing.

@Composable
private fun CapabilitiesProbe.readBoolean(
    select: (com.mapmonarchs.domain.model.AuthCapabilities) -> Boolean
): String = when (this) {
    is CapabilitiesProbe.Observed -> stringResource(
        if (select(capabilities)) R.string.diag_enabled else R.string.diag_disabled
    )
    is CapabilitiesProbe.Probing -> stringResource(R.string.diag_checking)
    else -> stringResource(R.string.diag_unknown)
}

private fun CapabilitiesProbe.colorFor(
    select: (com.mapmonarchs.domain.model.AuthCapabilities) -> Boolean
) = when (this) {
    is CapabilitiesProbe.Observed -> if (select(capabilities)) SovereignGlow else Contested
    else -> TextMuted
}

private fun formatMoment(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
