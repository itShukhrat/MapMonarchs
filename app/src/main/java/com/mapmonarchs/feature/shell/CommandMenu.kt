package com.mapmonarchs.feature.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.components.HexagonShape
import com.mapmonarchs.ui.components.UiTags
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.ui.theme.TextPrimary
import com.mapmonarchs.ui.theme.Void

/**
 * The command button: a single hexagon, quiet, bottom-right.
 *
 * This exists instead of a bottom navigation bar. A nav bar would run the full
 * width, permanently occupy the bottom of the screen, and force the map -- the
 * hero -- to share the frame with chrome the player looks at maybe twice a
 * session. One hex costs 44dp and disappears into the composition.
 */
@Composable
fun CommandButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(HexagonShape)
            .background(Void.copy(alpha = 0.82f))
            .border(1.dp, SlateEdge, HexagonShape)
            .clickable(onClick = onClick)
            .testTag(UiTags.COMMAND_BUTTON)
            .semantics { contentDescription = "Open command menu" },
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(modifier = Modifier.size(width = 14.dp, height = 1.5.dp).background(TextPrimary))
            }
        }
    }
}

/**
 * Full-screen command menu, tiered.
 *
 * Three weights, deliberately: COMMAND is what you do, REFERENCE is what you read
 * once, LOCKED is what does not exist yet. A flat list at equal weight would make
 * a product that is mostly locked look mostly broken; hiding the locked entries
 * would leave the player with no sense of where this is going. Dimmed and last is
 * the honest middle.
 */
@Composable
fun CommandMenu(
    visible: Boolean,
    current: Route,
    onSelect: (Route) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Void.copy(alpha = 0.97f))
                .clickable(interactionSource = interaction, indication = null, onClick = onDismiss)
                .testTag(UiTags.COMMAND_MENU)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 28.dp)
            ) {
                MenuGroupLabel("COMMAND")
                COMMAND_ROUTES.forEach { route ->
                    PrimaryEntry(route = route, isCurrent = route == current, onSelect = onSelect)
                }

                MenuGroupLabel("REFERENCE", topPadding = 34.dp)
                REFERENCE_ROUTES.forEach { route ->
                    SecondaryEntry(route = route, isCurrent = route == current, locked = false, onSelect = onSelect)
                }

                MenuGroupLabel("LOCKED", topPadding = 34.dp)
                LOCKED_ROUTES.forEach { route ->
                    SecondaryEntry(route = route, isCurrent = route == current, locked = true, onSelect = onSelect)
                }

                Box(modifier = Modifier.padding(bottom = 40.dp))
            }
        }
    }
}

@Composable
private fun MenuGroupLabel(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        modifier = Modifier.padding(top = topPadding, bottom = 4.dp)
    )
}

@Composable
private fun PrimaryEntry(route: Route, isCurrent: Boolean, onSelect: (Route) -> Unit) {
    Text(
        text = route.title,
        style = MaterialTheme.typography.displayMedium,
        color = if (isCurrent) SovereignGlow else TextPrimary,
        modifier = Modifier
            .padding(top = 18.dp)
            .clickable { onSelect(route) }
            .testTag("command_${route.title}")
    )
}

/**
 * Locked entries stay tappable. Tapping through to a surface that explains the
 * lock is far more useful than an inert row that ignores the tap -- an unresponsive
 * entry reads as a bug, not as a boundary.
 */
@Composable
private fun SecondaryEntry(route: Route, isCurrent: Boolean, locked: Boolean, onSelect: (Route) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clickable { onSelect(route) }
            .testTag("command_${route.title}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (locked) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(HexagonShape)
                    .border(1.dp, SlateEdge, HexagonShape)
            )
        }
        Text(
            text = route.title,
            style = MaterialTheme.typography.titleLarge,
            color = when {
                isCurrent -> SovereignGlow
                locked -> TextMuted
                else -> TextPrimary
            },
            modifier = Modifier.padding(start = if (locked) 12.dp else 0.dp)
        )
    }
}