package com.mapmonarchs.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.theme.Slate
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.Sovereign
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.ui.theme.TextPrimary
import com.mapmonarchs.ui.theme.Void

/**
 * Every non-map screen uses this.
 *
 * There is no TopAppBar: Material's bar brings its own elevation, its own title
 * scale, and its own back affordance, all of which read as "generic Android".
 * Instead the title is set in the instrument-panel label style and the back
 * control is a word, not a chevron -- consistent with a product whose entire
 * hierarchy is typographic.
 */
@Composable
fun CommandScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Void)
            .safeDrawingPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BACK",
                style = MaterialTheme.typography.labelMedium,
                color = SovereignGlow,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .testTag(UiTags.BACK)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier
                    .padding(start = 20.dp)
                    .testTag(UiTags.SCREEN_TITLE)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            content()
        }
    }
}

object UiTags {
    const val BACK = "ui_back"
    const val SCREEN_TITLE = "ui_screen_title"
    const val COMMAND_BUTTON = "ui_command_button"
    const val COMMAND_MENU = "ui_command_menu"
    const val ERROR_STRIP = "ui_error_strip"
    const val RETRY = "ui_retry"
}

/** Section divider. A tracked label over a hairline -- no card, no elevation. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 28.dp, bottom = 12.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(SlateEdge)
        )
    }
}

/**
 * A labelled value. The workhorse of every settings/identity surface.
 * Value is right-aligned so a column of them scans like an instrument readout.
 */
@Composable
fun StatusRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = valueColor)
    }
}

/** Explanatory prose. Muted, never competing with the readouts above it. */
@Composable
fun BodyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextMuted,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

/** Primary action. Same geometry as the capture button so the language is one system. */
@Composable
fun CommandAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (enabled) Sovereign else Slate)
            .border(1.dp, if (enabled) SovereignGlow else SlateEdge, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Void else TextMuted
        )
    }
}

/**
 * Selectable option. Used for tracking mode.
 * Selection is shown by a filled hex mark, not a radio button -- the hexagon is
 * this product's selection language everywhere else, so it is here too.
 */
@Composable
fun ChoiceRow(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
            .semantics { contentDescription = "$label. $detail" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .rotate(90f)
                .clip(HexagonShape)
                .background(if (selected) Sovereign else Color.Transparent)
                .border(1.dp, if (selected) Sovereign else SlateEdge, HexagonShape)
        )
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) TextPrimary else TextMuted
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}