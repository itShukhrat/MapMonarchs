package com.mapmonarchs.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mapmonarchs.R
import com.mapmonarchs.domain.model.BadgeCatalog
import com.mapmonarchs.domain.model.BadgeCategory
import com.mapmonarchs.domain.model.BadgeState
import com.mapmonarchs.domain.model.Entitlement
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.model.isPro
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.CommandCard
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.IdentityCrest
import com.mapmonarchs.ui.components.MetricTile
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.components.StatusRow
import com.mapmonarchs.ui.components.TierBadge
import com.mapmonarchs.ui.theme.Contested
import com.mapmonarchs.ui.theme.EdgeLit
import com.mapmonarchs.ui.theme.LockedEdge
import com.mapmonarchs.ui.theme.MapMonarchsBrush
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The unknown marker, shared with the map holdings readout. */
private const val EM_DASH = "\u2014"

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
 * The identity chamber.
 *
 * WHY IT IS A DESTINATION AND NOT A SETTINGS PAGE: this is the only screen that
 * answers "what have I got". The crest is the hero, the tier sits with it as part
 * of who the player is, standing reads as two instruments, and the medal cabinet
 * is shown as a wall rather than a link. Account actions sit last, because they
 * are maintenance, not identity.
 *
 * THE RULE THIS SCREEN IS BUILT AROUND: it renders no number the backend has not
 * actually produced. Three readouts are UNKNOWN rather than zero, and each says
 * why: held ground (ownership not read back yet), rank (needs the locked
 * leaderboard), and badges/records (no backend yet, so the wall shows the whole
 * earnable cabinet locked and the log states its unlock path).
 */
@Composable
fun ProfileScreen(
    identity: Identity,
    territoryColor: Color?,
    holdings: String,
    entitlement: Entitlement,
    earnedBadgeKeys: Map<String, Long>,
    onBack: () -> Unit,
    onManageIdentity: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenSubscription: () -> Unit,
    modifier: Modifier = Modifier
) {
    val earnedCount = BadgeCatalog.states(earnedBadgeKeys).count { it.earned }
    val pro = entitlement.isPro()
    val heldUnknown = holdings == EM_DASH

    CommandScreen(
        title = stringResource(R.string.route_profile),
        onBack = onBack,
        modifier = modifier.testTag(ProfileTags.ROOT)
    ) {

        // ---- Hero: the crest, with the tier standing beside the identity ----
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
            TierBadge(
                label = stringResource(
                    if (pro) R.string.profile_tier_pro else R.string.profile_tier_free
                ),
                active = pro,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(ProfileTags.TIER)
            )
        }

        // ---- Standing: two instruments, not a list ----
        SectionHeader(stringResource(R.string.profile_standing))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                label = stringResource(R.string.profile_held_ground),
                value = holdings,
                valueColor = if (territoryColor != null) SovereignGlow else TextMuted,
                unknown = heldUnknown,
                modifier = Modifier
                    .weight(1f)
                    .testTag(ProfileTags.HELD)
            )
            MetricTile(
                label = stringResource(R.string.profile_rank),
                value = stringResource(R.string.profile_unknown),
                unknown = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag(ProfileTags.RANK)
            )
        }
        BodyNote(
            stringResource(R.string.profile_standing_note),
            modifier = Modifier.padding(top = 12.dp)
        )

        // ---- Badges: the cabinet as a wall ----
        SectionHeader(stringResource(R.string.profile_badges))
        BadgeWall(earnedKeys = earnedBadgeKeys, modifier = Modifier.padding(top = 4.dp))
        CommandCard {
            StatusRow(
                label = stringResource(R.string.profile_open_badges),
                value = stringResource(R.string.profile_badges_value, earnedCount, BadgeCatalog.total),
                valueColor = if (earnedCount > 0) SovereignGlow else TextMuted,
                onClick = onOpenBadges,
                modifier = Modifier.testTag(ProfileTags.BADGES_LINK)
            )
        }

        // ---- Records ----
        SectionHeader(stringResource(R.string.profile_records))
        CommandCard {
            StatusRow(
                label = stringResource(R.string.profile_open_records),
                value = stringResource(R.string.profile_records_value),
                valueColor = TextMuted,
                onClick = onOpenRecords,
                modifier = Modifier.testTag(ProfileTags.RECORDS_LINK)
            )
        }

        // ---- Account: maintenance, kept last ----
        CommandCard(title = stringResource(R.string.profile_account)) {
            StatusRow(
                label = stringResource(R.string.profile_picture),
                value = stringResource(
                    if (pro) R.string.profile_picture_available else R.string.profile_locked
                ),
                valueColor = if (pro) SovereignGlow else Contested,
                modifier = Modifier.testTag(ProfileTags.PICTURE)
            )
        }

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
 * The medal cabinet as a wall: every catalogued badge, grouped by category, on a
 * raised lit-edged panel. Not interactive itself -- the row beneath is the way in
 * -- it exists so the section reads as a cabinet the player owns rather than a
 * link they follow.
 */
@Composable
private fun BadgeWall(earnedKeys: Map<String, Long>, modifier: Modifier = Modifier) {
    val groups = BadgeCatalog.byCategory(earnedKeys)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MapMonarchsBrush.card)
            .border(1.dp, EdgeLit, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BadgeCategory.entries.forEach { category ->
            val states = groups[category].orEmpty()
            if (states.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        text = stringResource(category.titleRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        states.forEach { BadgeHex(it) }
                    }
                }
            }
        }
    }
}

/**
 * One cell of the wall. Earned fills Sovereign; locked is a hollow ring; a badge
 * whose upstream system is itself locked is a dimmer ring, so "cannot be earned
 * yet" and "not earned yet" are visibly different.
 */
@Composable
private fun BadgeHex(state: BadgeState, modifier: Modifier = Modifier) {
    val earned = state.earned
    val ring = when {
        earned -> SovereignGlow
        state.badge.blockedUpstream -> LockedEdge
        else -> SlateEdge
    }
    Canvas(modifier = modifier.size(34.dp).testTag("profile_badge_" + state.badge.key)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val unit = size.minDimension / 2f
        fun hex(r: Float): Path = Path().apply {
            for (i in 0 until 6) {
                val a = PI.toFloat() / 3f * i + PI.toFloat() / 6f
                val x = cx + r * cos(a)
                val y = cy + r * sin(a)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(hex(unit * 0.9f), ring, style = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round))
        if (earned) drawPath(hex(unit * 0.46f), SovereignGlow)
    }
}