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
import androidx.compose.ui.unit.dp
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.HexagonShape
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.components.StatusRow
import com.mapmonarchs.ui.theme.Contested
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.TextMuted

object IdentityTags {
    const val CALLSIGN = "identity_callsign"
    const val STATE = "identity_state"
    const val RETRY = "identity_retry"
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
    modifier: Modifier = Modifier
) {
    CommandScreen(title = "IDENTITY", onBack = onBack, modifier = modifier) {

        val colour: Color? = (identity as? Identity.SignedIn)?.let { Color(it.territoryColorArgb) }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(HexagonShape)
                    .background(colour ?: Color.Transparent)
                    .border(1.dp, colour ?: SlateEdge, HexagonShape)
            )
            Text(
                text = when (identity) {
                    is Identity.SignedIn -> identity.callsign
                    is Identity.Preview -> identity.callsign
                    is Identity.Resolving -> "IDENTIFYING"
                    is Identity.SignedOut -> "NO IDENTITY"
                    is Identity.Unavailable -> "UNAVAILABLE"
                },
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(start = 18.dp)
                    .testTag(IdentityTags.CALLSIGN)
            )
        }

        SectionHeader("SESSION")

        StatusRow(
            label = "State",
            value = when (identity) {
                is Identity.SignedIn -> "ACTIVE"
                is Identity.Preview -> "PREVIEW"
                is Identity.Resolving -> "RESOLVING"
                is Identity.SignedOut -> "SIGNED OUT"
                is Identity.Unavailable -> "UNAVAILABLE"
            },
            valueColor = if (identity is Identity.Unavailable) Contested else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(IdentityTags.STATE)
        )

        StatusRow(
            label = "Territory colour",
            value = if (colour != null) "ASSIGNED" else "NONE",
            valueColor = colour ?: TextMuted
        )

        when (identity) {
            is Identity.SignedIn -> {
                SectionHeader("HOW THIS IS DERIVED")
                BodyNote(
                    "Your callsign and colour are computed from your account id, so they " +
                        "are the same on every device and after every reinstall. They are " +
                        "not stored as a profile and cannot be changed -- which also means " +
                        "nobody can impersonate your colour on the map."
                )
            }
            is Identity.Unavailable -> {
                SectionHeader("WHY YOU HAVE NO IDENTITY")
                BodyNote(identity.error.playerMessage())
                BodyNote(
                    "Without a session the server cannot attribute territory to you, so " +
                        "claiming is disabled. The map stays readable in the meantime."
                )
                CommandAction(
                    label = "RETRY SIGN-IN",
                    onClick = onRetry,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(IdentityTags.RETRY)
                )
            }
            is Identity.SignedOut -> {
                SectionHeader("NO SESSION")
                BodyNote("You are not signed in. Claiming is disabled until you are.")
                CommandAction(
                    label = "SIGN IN",
                    onClick = onRetry,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(IdentityTags.RETRY)
                )
            }
            is Identity.Preview -> {
                SectionHeader("THIS IS NOT AN ACCOUNT")
                BodyNote(
                    "Preview mode gives you a placeholder callsign so the map has something " +
                        "to colour. It is not a session, it is not stored anywhere, and it " +
                        "cannot hold real territory."
                )
                BodyNote(
                    "When sign-in becomes available you will get a real callsign derived " +
                        "from your account -- stable across devices and reinstalls."
                )
            }
            is Identity.Resolving -> {
                SectionHeader("WORKING")
                BodyNote("Establishing a session.")
            }
        }

        Box(modifier = Modifier.padding(bottom = 40.dp))
    }
}