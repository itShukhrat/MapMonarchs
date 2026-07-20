package com.mapmonarchs.ui.components

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.theme.Contested
import com.mapmonarchs.ui.theme.TextPrimary
import com.mapmonarchs.ui.theme.Void

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
            .clip(RoundedCornerShape(8.dp))
            .background(Void.copy(alpha = 0.82f))
            .border(1.dp, Contested.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(UiTags.ERROR_STRIP)
            .semantics { contentDescription = "Error: $message" },
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