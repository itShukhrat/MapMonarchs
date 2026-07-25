package com.mapmonarchs.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mapmonarchs.R
import com.mapmonarchs.domain.model.BadgeCatalog
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
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object ProfileTags {
    const val ROOT = "profile_root"
    const val CREST = "profile_crest"
    const val HELD = "profile_held"
    const val RANK = "profile_rank"
    const val TIER = "profile_tier"
    const val PICTURE = "profile_picture"
    const val BADGES_LINK = "profile_badges_link"
    const val RECORDS_LINK = "profile_records_link"
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
    /** Earned badge keys -> earned-at millis, from the server. Empty in this build. */
    earnedBadgeKeys: Map<String, Long>,
    onBack: () -> Unit,
    onManageIdentity: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenSubscription: () -> Unit,
    modifier: Modifier = Modifier
) {
    val earnedCount = BadgeCatalog.states(earnedBadgeKeys).count { it.earned }
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
        // A preview strip plus a way in. The strip shows the first cells of the
        // cabinet so the section is not just a text link; the full cabinet, with
        // criteria for each, lives one tap away.
        SectionHeader(stringResource(R.string.profile_badges))
        BadgeStrip(earnedKeys = earnedBadgeKeys, modifier = Modifier.padding(top = 4.dp))
        CommandCard {
            StatusRow(
                label = stringResource(R.string.profile_open_badges),
                value = stringResource(R.string.profile_badges_value, earnedCount, BadgeCatalog.total),
                valueColor = if (earnedCount > 0) SovereignGlow else TextMuted,
                onClick = onOpenBadges,
                modifier = Modifier.testTag(ProfileTags.BADGES_LINK)
            )
        }

        // ---- Records --------------------------------------------------------
        SectionHeader(stringResource(R.string.profile_records))
        CommandCard {
            StatusRow(
                label = stringResource(R.string.profile_open_records),
                // Honest: the log is empty for everyone in this build.
                value = stringResource(R.string.profile_records_value),
                valueColor = TextMuted,
                onClick = onOpenRecords,
                modifier = Modifier.testTag(ProfileTags.RECORDS_LINK)
            )
        }

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
            // The Pro surface, not the shop: the shop is a locked cosmetics vault,
            // while the subscription screen is where Free-vs-Pro actually lives.
            onClick = onOpenSubscription,
            modifier = Modifier.padding(top = 20.dp)
        )

        CommandAction(
            label = stringResource(R.string.profile_manage_identity),
            onClick = onManageIdentity,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/**
 * The first cells of the medal cabinet, shown inline. All hollow today because
 * nothing is earned; an earned one fills Sovereign. Not interactive itself --
 * the row beneath it is the way in -- it exists so the section reads as a cabinet
 * rather than a link.
 */
@Composable
private fun BadgeStrip(earnedKeys: Map<String, Long>, modifier: Modifier = Modifier) {
    val preview = BadgeCatalog.states(earnedKeys).take(6)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        preview.forEach { state ->
            val earned = state.earned
            Canvas(modifier = Modifier.size(34.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val unit = size.minDimension / 2f
                val path = Path().apply {
                    for (i in 0 until 6) {
                        val a = PI.toFloat() / 3f * i + PI.toFloat() / 6f
                        val x = cx + unit * 0.9f * cos(a)
                        val y = cy + unit * 0.9f * sin(a)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(path, SlateEdge, style = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round))
                if (earned) {
                    val inner = Path().apply {
                        for (i in 0 until 6) {
                            val a = PI.toFloat() / 3f * i + PI.toFloat() / 6f
                            val x = cx + unit * 0.46f * cos(a)
                            val y = cy + unit * 0.46f * sin(a)
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawPath(inner, SovereignGlow)
                }
            }
        }
    }
}
