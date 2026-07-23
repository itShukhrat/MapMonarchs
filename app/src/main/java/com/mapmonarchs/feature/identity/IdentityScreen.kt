package com.mapmonarchs.feature.identity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mapmonarchs.R
import com.mapmonarchs.feature.common.localizedMessage
import androidx.compose.ui.unit.dp
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.ActionEmphasis
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.CommandCard
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.IdentityCrest
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.components.StatusRow
import com.mapmonarchs.ui.theme.Contested
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.TextMuted

object IdentityTags {
    const val CALLSIGN = "identity_callsign"
    const val STATE = "identity_state"
    const val RETRY = "identity_retry"
    const val SIGN_IN = "identity_sign_in"
    const val SIGN_OUT = "identity_sign_out"
}

/**
 * PROBLEM SOLVED: the player needs to know who they are in this world, and
 * whether the game currently recognises them at all.
 *
 * WHY NOT GENERIC: no avatar, no display-name field, no social profile. Identity
 * here is a derived callsign and a territory colour -- the two things that
 * actually appear on the map. There is nothing to edit, because nothing here is
 * a vanity field; it is a readout of a server-side fact.
 */
@Composable
fun IdentityScreen(
    identity: Identity,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    CommandScreen(
        title = stringResource(R.string.identity_title),
        onBack = onBack,
        modifier = modifier
    ) {

        val colour: Color? = (identity as? Identity.SignedIn)?.let { Color(it.territoryColorArgb) }

        // The crest replaced a 40dp swatch beside a name. In a game about holding
        // ground the player colour is the one thing that is unarguably theirs, and
        // it was carrying less visual weight than the build number on ABOUT.
        IdentityCrest(
            callsign = when (identity) {
                is Identity.SignedIn -> identity.callsign
                is Identity.Preview -> identity.callsign
                is Identity.Resolving -> stringResource(R.string.identity_identifying)
                is Identity.SignedOut -> stringResource(R.string.identity_no_identity)
                is Identity.Unavailable -> stringResource(R.string.identity_unavailable)
            },
            stateLabel = when (identity) {
                is Identity.SignedIn -> stringResource(R.string.identity_state_signed_in)
                // Never dressed up. A preview callsign is a placeholder and the
                // label says so, because the crest makes it look consequential.
                is Identity.Preview -> stringResource(R.string.identity_state_preview)
                is Identity.Resolving -> stringResource(R.string.identity_state_resolving)
                is Identity.SignedOut -> stringResource(R.string.identity_state_signed_out)
                is Identity.Unavailable -> stringResource(R.string.identity_state_unavailable)
            },
            territoryColor = colour,
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag(IdentityTags.CALLSIGN)
        )

        CommandCard(title = stringResource(R.string.identity_session)) {

            StatusRow(
                label = stringResource(R.string.identity_state),
                value = when (identity) {
                    is Identity.SignedIn -> stringResource(R.string.identity_active)
                    is Identity.Preview -> stringResource(R.string.identity_preview)
                    is Identity.Resolving -> stringResource(R.string.identity_resolving)
                    is Identity.SignedOut -> stringResource(R.string.identity_signed_out)
                    is Identity.Unavailable -> stringResource(R.string.identity_unavailable)
                },
                valueColor = if (identity is Identity.Unavailable) Contested else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(IdentityTags.STATE)
            )

            StatusRow(
                label = stringResource(R.string.identity_colour),
                value = stringResource(
                    if (colour != null) R.string.identity_assigned else R.string.identity_none
                ),
                valueColor = colour ?: TextMuted
            )
        }

        when (identity) {
            is Identity.SignedIn -> {
                SectionHeader(stringResource(R.string.identity_derived))
                BodyNote(stringResource(R.string.identity_derived_body))
                CommandAction(
                    label = stringResource(R.string.identity_sign_out),
                    onClick = onSignOut,
                    emphasis = ActionEmphasis.SECONDARY,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .testTag(IdentityTags.SIGN_OUT)
                )
            }
            is Identity.Unavailable -> {
                SectionHeader(stringResource(R.string.identity_why_none))
                BodyNote(identity.error.localizedMessage())
                BodyNote(stringResource(R.string.identity_no_session_effect))
                CommandAction(
                    label = stringResource(R.string.identity_retry),
                    onClick = onRetry,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(IdentityTags.RETRY)
                )
            }
            is Identity.SignedOut -> {
                SectionHeader(stringResource(R.string.identity_no_session))
                BodyNote(stringResource(R.string.identity_no_session_body))
                CommandAction(
                    label = stringResource(R.string.identity_sign_in),
                    onClick = onSignIn,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(IdentityTags.SIGN_IN)
                )
            }
            is Identity.Preview -> {
                SectionHeader(stringResource(R.string.identity_not_account))
                BodyNote(stringResource(R.string.identity_preview_body))
                BodyNote(stringResource(R.string.identity_preview_upgrade))
                CommandAction(
                    label = stringResource(R.string.identity_sign_in),
                    onClick = onSignIn,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .testTag(IdentityTags.SIGN_IN)
                )
            }
            is Identity.Resolving -> {
                SectionHeader(stringResource(R.string.identity_working))
                BodyNote(stringResource(R.string.identity_working_body))
            }
        }

    }
}
