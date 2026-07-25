package com.mapmonarchs.feature.badges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mapmonarchs.R
import com.mapmonarchs.domain.model.BadgeCatalog
import com.mapmonarchs.domain.model.BadgeState
import com.mapmonarchs.domain.model.BadgeStatus
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.theme.Contested
import com.mapmonarchs.ui.theme.Neutral
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.Sovereign
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.ui.theme.TextPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object BadgesTags {
    const val ROOT = "badges_root"
    const val SUMMARY = "badges_summary"
}

/**
 * The medal cabinet.
 *
 * It shows the WHOLE catalogue -- every badge the game can award -- with each one
 * marked earned or locked. That is deliberate and honest: a cabinet that hid the
 * locked ones would show the player an empty room and tell them nothing about
 * what the game rewards, while a cabinet that faked a few earned ones would be
 * the exact lie this whole surface is built to avoid. Today the honest count is
 * zero, and the summary says so plainly rather than dressing it up.
 *
 * The badges themselves are real rules (see BadgeCatalog). The earned SET is
 * empty because it comes from the server, which has no capture history to draw
 * on yet -- so this renders the catalogue as a goal, which is what it is.
 */
@Composable
fun BadgesScreen(
    earnedKeys: Map<String, Long>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val byCategory = BadgeCatalog.byCategory(earnedKeys)
    val earnedCount = byCategory.values.flatten().count { it.earned }

    CommandScreen(
        title = stringResource(R.string.route_badges),
        onBack = onBack,
        modifier = modifier.testTag(BadgesTags.ROOT)
    ) {
        // The honest headline number, first. Not a progress bar -- a bar implies
        // motion, and nothing has moved yet.
        Text(
            text = stringResource(R.string.badges_earned_of_total, earnedCount, BadgeCatalog.total),
            style = MaterialTheme.typography.headlineSmall,
            color = if (earnedCount > 0) SovereignGlow else TextMuted,
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(BadgesTags.SUMMARY)
        )
        BodyNote(
            stringResource(R.string.badges_intro),
            modifier = Modifier.padding(top = 8.dp)
        )

        byCategory.forEach { (category, badges) ->
            SectionHeader(stringResource(category.titleRes))
            badges.forEach { BadgeRow(it) }
        }
    }
}

@Composable
private fun BadgeRow(state: BadgeState) {
    val earned = state.status is BadgeStatus.Earned
    val blocked = state.badge.blockedUpstream && !earned
    Row(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .testTag("badge_${state.badge.key}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeGlyph(earned = earned, dim = blocked, modifier = Modifier.size(46.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(state.badge.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = if (earned) TextPrimary else if (blocked) TextMuted else TextPrimary
            )
            Text(
                text = stringResource(state.badge.criteriaRes),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        // State word, right-aligned like an instrument readout. Three distinct
        // truths: earned, not yet earned, or not yet earnable by anyone.
        Text(
            text = stringResource(
                when {
                    earned -> R.string.badge_state_earned
                    blocked -> R.string.badge_state_blocked
                    else -> R.string.badge_state_locked
                }
            ),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                earned -> SovereignGlow
                blocked -> Neutral
                else -> TextMuted
            },
            textAlign = TextAlign.End
        )
    }
}

/**
 * A badge as a crest cell: the same pointy-top hexagon the identity crest uses,
 * so a medal reads as belonging to the same family as the player's own mark.
 * Earned fills with Sovereign; locked is a hollow steel frame; blocked-upstream
 * is dimmed further, because it cannot be earned by anyone yet.
 */
@Composable
private fun BadgeGlyph(earned: Boolean, dim: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(46.dp)) {
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

            val frame = if (dim) SlateEdge.copy(alpha = 0.5f) else SlateEdge
            drawPath(
                hex(unit * 0.92f),
                frame,
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            if (earned) {
                drawCircle(Sovereign.copy(alpha = 0.14f), unit * 0.9f, Offset(cx, cy))
                drawPath(hex(unit * 0.5f), SovereignGlow)
            } else {
                // A hollow centre: the cell exists, the ground is not yet held.
                drawPath(
                    hex(unit * 0.42f),
                    if (dim) Neutral.copy(alpha = 0.4f) else Neutral,
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
