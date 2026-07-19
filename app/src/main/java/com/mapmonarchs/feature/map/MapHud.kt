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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.components.ErrorStrip
import com.mapmonarchs.ui.components.HexagonShape
import com.mapmonarchs.ui.theme.Contested
import com.mapmonarchs.ui.theme.Slate
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.Sovereign
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.ui.theme.TextPrimary
import com.mapmonarchs.ui.theme.Void
import kotlin.math.cos
import kotlin.math.sin

object MapHudTags {
    const val HANDLE = "hud_handle"
    const val HELD = "hud_held"
    const val DELTA = "hud_delta"
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
            delta = state.hexesHeldDelta,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 20.dp, top = 8.dp)
        )

        CaptureAction(
            label = state.captureLabel(),
            ready = state.isCaptureActionable(),
            onReadyClick = onCapture,
            onBlockedClick = onExplainBlocker,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
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
            .clip(RoundedCornerShape(8.dp))
            .background(Void.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
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

@Composable
private fun Holdings(holdings: String, delta: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = holdings,
            style = MaterialTheme.typography.displayLarge,
            color = if (holdings == "--") TextMuted else TextPrimary,
            modifier = Modifier
                .testTag(MapHudTags.HELD)
                .semantics {
                    contentDescription =
                        if (holdings == "--") "Hexes held: unknown" else "Hexes held: $holdings"
                }
        )
        Text(text = "HEXES HELD", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        if (delta != 0) {
            Text(
                text = if (delta > 0) "+$delta" else delta.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (delta > 0) SovereignGlow else Contested,
                modifier = Modifier
                    .testTag(MapHudTags.DELTA)
                    .semantics { contentDescription = "Change since last check: $delta hexes" }
            )
        }
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
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (ready) Sovereign else Slate)
            .border(1.dp, if (ready) SovereignGlow else Contested.copy(alpha = 0.45f), shape)
            .clickable(onClick = if (ready) onReadyClick else onBlockedClick)
            .padding(horizontal = 44.dp, vertical = 18.dp)
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (ready) Void else TextMuted
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