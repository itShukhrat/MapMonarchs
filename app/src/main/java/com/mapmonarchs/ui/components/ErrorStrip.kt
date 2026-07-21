package com.mapmonarchs.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.theme.Contested
import com.mapmonarchs.ui.theme.TextPrimary
import com.mapmonarchs.ui.theme.MapMonarchsBrush

/**
 * The single error treatment for the whole app.
 *
 * Extracted from MapHud so a second surface cannot invent its own. Divergent
 * error styling is how an app starts feeling assembled rather than designed --
 * and an error that looks different in two places makes the player wonder which
 * one is serious.
 *
 * Deliberately not a card, dialog, or snackbar: a modal implies the app is dead,
 * and a snackbar disappears before a walking player has read it.
 */
@Composable
fun ErrorStrip(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "RETRY"
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            // The same glass the other HUD instruments are made of, so a failure
            // reads as part of the panel system rather than as a foreign box
            // pasted over it. Only the EDGE and the status mark carry Contested,
            // so the strip never competes with territory colour for saturation.
            .background(MapMonarchsBrush.hudPanel)
            .border(1.dp, Contested.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(UiTags.ERROR_STRIP)
            .semantics { contentDescription = "Error: $message" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A LIT status mark, not a 6dp dot.
        //
        // The dot was the smallest possible acknowledgement that something had
        // failed -- easy to miss outdoors, and visually the cheapest element on
        // a surface that had just become the most important one. A mark that
        // throws its own light reads as an instrument reporting a fault.
        //
        // Drawn rather than tinted: the glow has to fall off smoothly behind the
        // hexagon, and a background modifier cannot do that without a second
        // composable and a blur this app does not want to pay for.
        Canvas(modifier = Modifier.size(22.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Contested.copy(alpha = 0.22f), size.minDimension * 0.50f, c)
            drawCircle(Contested.copy(alpha = 0.30f), size.minDimension * 0.32f, c)
            drawCircle(Contested, size.minDimension * 0.13f, c)
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 12.dp)
        )
        // The last bare clickable Text in the product. RETRY is the recovery
        // control on a failure strip -- the one a frustrated player jabs at,
        // often walking -- and its target was the width of the word.
        TextCommand(
            label = retryLabel,
            onClick = onRetry,
            modifier = Modifier
                .padding(start = 8.dp)
                .testTag(UiTags.RETRY)
        )
    }
}
