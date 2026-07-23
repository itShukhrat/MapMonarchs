package com.mapmonarchs.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mapmonarchs.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mapmonarchs.feature.shell.CommandBarHeight
import com.mapmonarchs.ui.components.ErrorStrip
import com.mapmonarchs.ui.components.HexagonShape
import com.mapmonarchs.ui.components.TextCommand
import com.mapmonarchs.ui.theme.Obsidian
import com.mapmonarchs.ui.theme.Contested
import com.mapmonarchs.ui.theme.Slate
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.Sovereign
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.SovereignHalo
import com.mapmonarchs.ui.theme.EdgeLit
import com.mapmonarchs.ui.theme.MapMonarchsBrush
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.ui.theme.TextPrimary
import com.mapmonarchs.ui.theme.Void
import kotlin.math.cos
import kotlin.math.sin

object MapHudTags {
    const val PREVIEW_BANNER = "hud_preview_banner"
    const val HANDLE = "hud_handle"
    const val HELD = "hud_held"
    const val CAPTURE = "hud_capture"
    const val ERROR = "hud_error"
}

/**
 * The HUD is a FRAME, not a panel.
 *
 * The map is the hero, so the centre is never occluded. Content hugs three edges:
 * identity (top-left), holdings (top-right), one action (bottom-centre). The
 * failure strip is rendered ONLY on failure, so a healthy screen carries no extra
 * chrome at all.
 */
@Composable
fun MapHud(
    state: MapUiState,
    onCapture: () -> Unit,
    onRetry: () -> Unit,
    onExplainBlocker: () -> Unit,
    onExitPreview: () -> Unit = {},
    onRecentre: () -> Unit = {},
    onFrameWorld: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        // Width-capped: the failure strip must never grow into the holdings zone
        // on the right. A HUD that overlaps itself stops reading as an instrument
        // panel and starts reading as a bug.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.62f)
                .padding(start = 20.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            IdentityChip(
                label = state.identityLabel(),
                territoryColor = state.territoryColor(),
                modifier = Modifier.wrapContentWidth()
            )
            // Identity failure outranks territory failure: without a session the
            // player is not in the game at all, so that is the fact to surface.
            (state.identityError() ?: state.territoryError)?.let { error ->
                ErrorStrip(
                    message = error.playerMessage(),
                    onRetry = onRetry,
                    modifier = Modifier.testTag(MapHudTags.ERROR)
                )
            }
        }

        Holdings(
            holdings = state.holdingsLabel(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 20.dp, top = 8.dp)
        )

        // Persistent, unmissable, and always present in preview -- not a subtle
        // badge. A player must never be able to forget that what they are
        // looking at is simulated, because the entire trust position of this
        // product depends on never confusing the two.
        if (state.previewMode) {
            PreviewBanner(
                onExit = onExitPreview,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = CommandBarHeight + 98.dp)
            )
        }

        MapControls(
            hasFix = state.playerLat != null && state.playerLng != null,
            onRecentre = onRecentre,
            onFrameWorld = onFrameWorld,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp, bottom = CommandBarHeight + 40.dp)
        )

        CaptureAction(
            label = state.captureLabel(),
            ready = state.isCaptureActionable(),
            onReadyClick = onCapture,
            onBlockedClick = onExplainBlocker,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Clears the floating dock. Without this the primary action of
                // the entire game sits UNDERNEATH the navigation -- which is what
                // shipped for one build, and which no test could see.
                .padding(bottom = CommandBarHeight + 26.dp)
        )
    }
}

/**
 * Identity is a derived callsign beside a hex glyph -- never an avatar circle.
 *
 * A null territoryColor means we have no authenticated session, and the glyph
 * renders HOLLOW rather than picking a default colour. An unclaimed outline is
 * literally accurate for a player who does not exist yet.
 */
@Composable
private fun IdentityChip(label: String, territoryColor: Color?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            // Glass, not a black box. A flat fill read as a rectangle punched out
            // of the map; a lit edge over a gradient reads as an instrument
            // resting on it, which is the whole point of a map-first HUD.
            .background(MapMonarchsBrush.hudPanel)
            .border(1.dp, EdgeLit, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics {
                contentDescription =
                    if (territoryColor != null) "Your callsign: $label" else "Identity: $label"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        HexGlyph(color = territoryColor, glyphSize = 16.dp)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (territoryColor != null) TextPrimary else TextMuted,
            modifier = Modifier
                .padding(start = 10.dp)
                .testTag(MapHudTags.HANDLE)
        )
    }
}

/**
 * The holdings readout.
 *
 * WHY UNKNOWN IS NOT SET AT displayLarge. The unknown value is a single em dash,
 * chosen so the type scale tracking could not pull "--" into two detached
 * strokes. That reasoning holds, but at displayLarge one em dash is a 40dp
 * horizontal bar floating over the map, and on screen it reads as a rendering
 * artefact rather than as "we do not know". A number earns the big scale because
 * a number is a quantity; unknown is a STATE, so it is set at the same weight as
 * the label it sits above and stops shouting.
 */
@Composable
private fun Holdings(holdings: String, modifier: Modifier = Modifier) {
    val unknown = holdings == UNKNOWN_HOLDINGS
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MapMonarchsBrush.hudPanel)
            .border(1.dp, EdgeLit, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = holdings,
            style = if (unknown) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.displayLarge
            },
            color = if (unknown) TextMuted else TextPrimary,
            modifier = Modifier
                .testTag(MapHudTags.HELD)
                .semantics {
                    contentDescription =
                        if (unknown) "Hexes held: unknown" else "Hexes held: $holdings"
                }
        )
        Text(
            text = stringResource(R.string.hud_hexes_held),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

/**
 * The single action.
 *
 * When blocked, this used to be clickable(enabled = false): it stated the reason
 * and then swallowed the tap. Telling a player something is wrong and giving them
 * nowhere to go is a dead end -- and a control that ignores touch reads as a
 * broken app, not as a boundary.
 *
 * Now the blocked state stays tappable and routes to the explanation. It is also
 * styled distinctly from "ready": a Contested-tinted edge says "there is a
 * problem here, press to understand it", rather than the flat grey of a disabled
 * control that would invite no interaction at all.
 */
@Composable
private fun CaptureAction(
    label: String,
    ready: Boolean,
    onReadyClick: () -> Unit,
    onBlockedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            // A READY claim lifts off the map on an accent-tinted shadow. This is
            // the single most valuable action in the product and it was rendered
            // as a thin outlined pill sitting flat on the basemap -- lower visual
            // priority than the error strip above it.
            //
            // Depth is what carries the weight, not fill: the button still must
            // not wear a saturated slab, because that colour is TERRITORY_PALETTE
            // and this control sits inches from a player who may own ground drawn
            // in exactly that colour.
            //
            // Blocked stays grounded and flat on purpose. The difference between
            // "press this" and "this will not work yet" is legible before a single
            // word is read.
            .then(
                if (ready) {
                    Modifier.shadow(
                        elevation = 18.dp,
                        shape = shape,
                        ambientColor = SovereignHalo,
                        spotColor = SovereignHalo
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            // THE WORST INSTANCE OF THE PALETTE COLLISION, and the last one fixed.
            // This used to fill with Sovereign -- 0xFF4C8DFF, byte for byte
            // TERRITORY_PALETTE[0]. It sits ON the map, inches from the player's
            // own hexes, so for one player in eight the claim button and their
            // ground were the same colour in the same frame. CommandAction had the
            // same bug and was fixed first, which was backwards: that one lives on
            // a plain dark screen where nothing competes.
            .background(if (ready) MapMonarchsBrush.actionReady else SolidColor(Slate.copy(alpha = 0.94f)))
            .border(
                if (ready) 1.5.dp else 1.dp,
                if (ready) Sovereign else Contested.copy(alpha = 0.45f),
                shape
            )
            .clickable(
                role = Role.Button,
                onClick = {
                    // Blocked taps get feedback too: the press DID something, it
                    // opened the explanation. Silence would read as a dead control.
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (ready) onReadyClick() else onBlockedClick()
                }
            )
            .padding(horizontal = 40.dp, vertical = 18.dp)
            .testTag(MapHudTags.CAPTURE)
            .semantics {
                contentDescription = if (ready) {
                    "Claim this hex"
                } else {
                    "Cannot claim: $label. Press to find out why."
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Same live-state mark as every other command in the product.
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .rotate(90f)
                    .clip(HexagonShape)
                    .background(if (ready) Sovereign else Color.Transparent)
                    .border(1.dp, if (ready) Sovereign else Contested.copy(alpha = 0.6f), HexagonShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (ready) SovereignGlow else TextMuted,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

/** The preview marker. Deliberately loud, and always offers the way out. */
@Composable
private fun PreviewBanner(onExit: () -> Unit, modifier: Modifier = Modifier) {
    val previewLabel = stringResource(R.string.hud_preview_a11y)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Void.copy(alpha = 0.9f))
            .border(1.dp, SovereignGlow.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .testTag(MapHudTags.PREVIEW_BANNER)
            .semantics {
                contentDescription = previewLabel
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.hud_preview_banner),
            style = MaterialTheme.typography.labelSmall,
            color = SovereignGlow
        )
        TextCommand(
            label = stringResource(R.string.hud_exit),
            onClick = onExit,
            color = TextPrimary,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/**
 * Flat geometric hexagon -- the app's core shape language.
 * Filled when identity is real; hollow outline when it is not.
 */
@Composable
private fun HexGlyph(color: Color?, glyphSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(glyphSize)) {
        val radius = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path()
        for (i in 0..5) {
            val angle = Math.toRadians(60.0 * i - 30.0)
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        if (color != null) drawPath(path, color)
        else drawPath(path, TextMuted, style = Stroke(width = 1.5.dp.toPx()))
    }
}
/**
 * Map overlay controls.
 *
 * PROBLEM SOLVED: the map had no controls at all. Once a player panned away from
 * their own position there was no way back except waiting for the next GPS fix to
 * yank the camera. A hero surface that cannot be steered is a screenshot.
 *
 * WHAT THEY ARE, AND ARE NOT: two camera actions, nothing more. RECENTRE returns
 * to the player; FRAME returns to the whole playable world. There is deliberately
 * no zoom rocker, no compass, no layer switcher -- this is a territory board, not
 * a navigation app, and every control that does not serve holding ground is
 * clutter competing with the map.
 *
 * WHY HEXAGONS, NOT MAP ICONS: the brief forbids stock pins and globes, and the
 * product already speaks one shape. RECENTRE is a filled core inside a ring --
 * "you, centred". FRAME is an open ring -- "the board, whole". Same crest
 * geometry as identity and the launcher.
 *
 * RECENTRE DISABLES HONESTLY: with no fix there is no "you" to centre on, so it
 * dims and announces why rather than lying about a position it does not have.
 */
@Composable
private fun MapControls(
    hasFix: Boolean,
    onRecentre: () -> Unit,
    onFrameWorld: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MapMonarchsBrush.hudPanel)
            .border(1.dp, EdgeLit, RoundedCornerShape(16.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MapControlButton(
            filled = true,
            enabled = hasFix,
            onClick = onRecentre,
            contentDescription = if (hasFix) {
                "Recentre on your position"
            } else {
                "Recentre on your position. Unavailable: no position fix yet."
            }
        )
        // Fixed width, not fillMaxWidth: a full-width divider dragged the whole
        // Column to the screen edge and stretched the cluster across the map.
        // The buttons define the width; the rule sits inside them.
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .width(26.dp)
                .height(1.dp)
                .background(SlateEdge)
        )
        MapControlButton(
            filled = false,
            enabled = true,
            onClick = onFrameWorld,
            contentDescription = "Frame the whole playable world"
        )
    }
}

@Composable
private fun MapControlButton(
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val tint = when {
        !enabled -> SlateEdge
        filled -> SovereignGlow
        else -> TextMuted
    }
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2f
            val path = Path()
            for (i in 0..5) {
                val a = Math.toRadians(60.0 * i - 30.0)
                val x = cx + r * kotlin.math.cos(a).toFloat()
                val y = cy + r * kotlin.math.sin(a).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, tint, style = Stroke(width = 1.6.dp.toPx()))
            // The filled core is what makes RECENTRE read as "me", the same way a
            // held identity crest fills and an unclaimed one stays hollow.
            if (filled) {
                drawCircle(tint, r * 0.42f, androidx.compose.ui.geometry.Offset(cx, cy))
            }
        }
    }
}
