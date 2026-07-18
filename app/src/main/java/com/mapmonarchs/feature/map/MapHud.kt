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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * identity (top-left), holdings (top-right), one action (bottom-centre). A fourth
 * element -- the failure strip -- exists but is rendered ONLY on failure, so a
 * healthy screen carries no extra chrome at all.
 */
@Composable
fun MapHud(
    state: MapUiState,
    onCapture: () -> Unit,
    onRetry: () -> Unit,
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
            Identity(
                handle = state.handle,
                territoryColor = state.territoryColor,
                modifier = Modifier.wrapContentWidth()
            )
            state.territoryError?.let { error ->
                FailureStrip(message = error.playerMessage(), onRetry = onRetry)
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
            enabled = state.isCaptureActionable(),
            onClick = onCapture,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

@Composable
private fun Identity(handle: String, territoryColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Void.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The player's colour is a hex, not an avatar circle. The shape itself is
        // the identity language of this game.
        HexGlyph(color = territoryColor, glyphSize = 16.dp)
        Text(
            text = handle.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            modifier = Modifier
                .padding(start = 10.dp)
                .testTag(MapHudTags.HANDLE)
        )
    }
}

/**
 * Shown only when territory failed to load. Deliberately NOT a card, dialog, or
 * full-screen takeover: the map stays usable and the failure stays honest. A
 * modal would imply the app is dead; a silent empty map would imply the player
 * owns nothing, which is a lie we refuse to tell.
 */
@Composable
private fun FailureStrip(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Void.copy(alpha = 0.82f))
            .border(1.dp, Contested.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(MapHudTags.ERROR),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Contested)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 10.dp)
        )
        Text(
            text = "RETRY",
            style = MaterialTheme.typography.labelMedium,
            color = SovereignGlow,
            modifier = Modifier
                .padding(start = 14.dp)
                .clickable(onClick = onRetry)
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
        Text(
            text = "HEXES HELD",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        if (delta != 0) {
            Text(
                text = if (delta > 0) "+$delta" else delta.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (delta > 0) SovereignGlow else Contested,
                modifier = Modifier.testTag(MapHudTags.DELTA)
            )
        }
    }
}

@Composable
private fun CaptureAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) Sovereign else Slate)
            .border(1.dp, if (enabled) SovereignGlow else SlateEdge, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 44.dp, vertical = 18.dp)
            .testTag(MapHudTags.CAPTURE),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Void else TextMuted
        )
    }
}

/** Flat geometric hexagon -- the app's core shape language. */
@Composable
private fun HexGlyph(color: Color, glyphSize: Dp, modifier: Modifier = Modifier) {
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
        drawPath(path, color)
    }
}