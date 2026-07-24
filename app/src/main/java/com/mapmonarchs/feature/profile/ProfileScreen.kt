package com.mapmonarchs.feature.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mapmonarchs.R
import com.mapmonarchs.domain.model.Entitlement
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.model.isPro
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.CommandCard
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.IdentityCrest
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.components.StatusRow
import com.mapmonarchs.ui.theme.Contested
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted

object ProfileTags {
    const val ROOT = "profile_root"
    const val CREST = "profile_crest"
    const val HELD = "profile_held"
    const val RANK = "profile_rank"
    const val TIER = "profile_tier"
    const val PICTURE = "profile_picture"
}

/**
 * The player hub.
 *
 * WHY IT IS A DESTINATION AND NOT A SETTINGS PAGE: this is the only screen that
 * answers "what have I got". Everything else in the product is about the ground
 * or about the machinery; this is about the player.
 *
 * THE RULE THIS SCREEN IS BUILT AROUND: it renders no number the backend has not
 * actually produced. A profile is exactly the surface where invented progress is
 * most tempting and most damaging -- a fabricated rank or a badge shelf stocked
 * with placeholders would make the whole product's honesty suspect, because this
 * is where a player looks to find out what is true about them.
 *
 * So three of the readouts here are UNKNOWN rather than zero, and each says why:
 *
 *  - HELD GROUND is unknown outside preview because ownership is not yet read
 *    back into the client. Zero would be a claim that the player holds nothing,
 *    which is a different and possibly false statement.
 *  - RANK is unknown because standings need the leaderboard, which is locked.
 *  - BADGES and RECORDS have no backend at all yet, so they state the unlock
 *    path instead of rendering an empty grid that looks like a loading failure.
 *
 * An empty state that explains itself is a designed state. An empty state that
 * merely renders nothing is a bug the player has to diagnose.
 */
@Composable
fun ProfileScreen(
    identity: Identity,
    territoryColor: Color?,
    /** Held-cell count as the map knows it -- may legitimately be unknown. */
    holdings: String,
    entitlement: Entitlement,
    onBack: () -> Unit,
    onManageIdentity: () -> Unit,
    onOpenShop: () -> Unit,
    modifier: Modifier = Modifier
) {
    CommandScreen(
        title = stringResource(R.string.route_profile),
        onBack = onBack,
        modifier = modifier.testTag(ProfileTags.ROOT)
    ) {

        // The crest is the header. Same component the identity surface uses, so
        // the player's mark does not change shape depending on where they look
        // at it from.
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
                is Identity.Preview -> stringResource(R.string.identity_state_preview)
                is Identity.Resolving -> stringResource(R.string.identity_state_resolving)
                is Identity.SignedOut -> stringResource(R.string.identity_state_signed_out)
                is Identity.Unavailable -> stringResource(R.string.identity_state_unavailable)
            },
            territoryColor = territoryColor,
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag(ProfileTags.CREST)
        )

        // ---- Standing -------------------------------------------------------
        CommandCard(title = stringResource(R.string.profile_standing)) {
            StatusRow(
                label = stringResource(R.string.profile_held_ground),
                value = holdings,
                valueColor = if (territoryColor != null) SovereignGlow else TextMuted,
                modifier = Modifier.testTag(ProfileTags.HELD)
            )
            StatusRow(
                label = stringResource(R.string.profile_rank),
                // Not zero, and not "unranked": both would assert a standing the
                // app cannot compute without the leaderboard.
                value = stringResource(R.string.profile_unknown),
                valueColor = TextMuted,
                modifier = Modifier.testTag(ProfileTags.RANK)
            )
        }
        BodyNote(
            stringResource(R.string.profile_standing_note),
            modifier = Modifier.padding(top = 12.dp)
        )

        // ---- Badges ---------------------------------------------------------
        SectionHeader(stringResource(R.string.profile_badges))
        BodyNote(stringResource(R.string.profile_badges_empty))

        // ---- Records --------------------------------------------------------
        SectionHeader(stringResource(R.string.profile_records))
        BodyNote(stringResource(R.string.profile_records_empty))

        // ---- Account --------------------------------------------------------
        CommandCard(title = stringResource(R.string.profile_account)) {
            StatusRow(
                label = stringResource(R.string.profile_tier),
                value = stringResource(
                    if (entitlement.isPro()) R.string.profile_tier_pro
                    else R.string.profile_tier_free
                ),
                valueColor = if (entitlement.isPro()) SovereignGlow else TextMuted,
                modifier = Modifier.testTag(ProfileTags.TIER)
            )
            StatusRow(
                label = stringResource(R.string.profile_picture),
                // Contested, not muted: this is a withheld capability, and the
                // colour the product already uses for "you cannot do this".
                value = stringResource(
                    if (entitlement.isPro()) R.string.profile_picture_available
                    else R.string.profile_locked
                ),
                valueColor = if (entitlement.isPro()) SovereignGlow else Contested,
                modifier = Modifier.testTag(ProfileTags.PICTURE)
            )
        }

        // The two sentences below are different claims and must not be merged.
        // "Pro only" is a product rule; "cannot be bought yet" is a fact about
        // this build. Saying only the first would read as an upsell for
        // something that has no purchase path, which is the dishonest version.
        BodyNote(
            stringResource(R.string.profile_picture_note),
            modifier = Modifier.padding(top = 12.dp)
        )
        if (!Entitlement.PURCHASE_AVAILABLE) {
            BodyNote(
                stringResource(R.string.profile_no_purchase_yet),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        CommandAction(
            label = stringResource(R.string.profile_view_pro),
            onClick = onOpenShop,
            modifier = Modifier.padding(top = 20.dp)
        )

        CommandAction(
            label = stringResource(R.string.profile_manage_identity),
            onClick = onManageIdentity,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
