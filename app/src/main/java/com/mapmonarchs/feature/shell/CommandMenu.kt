package com.mapmonarchs.feature.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
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
        // Three stacked bars, drawn as text-free geometry: a menu mark that does
        // not borrow a stock hamburger icon.
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 1.5.dp)
                        .background(TextPrimary)
                )
            }
        }
    }
}

/**
 * Full-screen command menu.
 *
 * Typographic, not a drawer of icons and cards. Large entries in the display
 * scale read as a strategy game's command list; a Material NavigationDrawer
 * would read as any productivity app. Dismissed by tapping anywhere off the
 * list, which is why the scrim itself is clickable.
 */
@Composable
fun CommandMenu(
    visible: Boolean,
    current: Route,
    onSelect: (Route) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Void.copy(alpha = 0.96f))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onDismiss
                )
                .testTag(UiTags.COMMAND_MENU)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 28.dp, vertical = 32.dp)
            ) {
                Text(
                    text = "COMMAND",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )

                COMMAND_ROUTES.forEach { route ->
                    val isCurrent = route == current
                    Text(
                        text = route.title,
                        style = MaterialTheme.typography.displayMedium,
                        color = if (isCurrent) SovereignGlow else TextPrimary,
                        modifier = Modifier
                            .padding(top = 26.dp)
                            .clickable { onSelect(route) }
                            .testTag("command_${route.title}")
                    )
                }

                Text(
                    text = "MAP",
                    style = MaterialTheme.typography.displayMedium,
                    color = if (current == Route.Map) SovereignGlow else TextPrimary,
                    modifier = Modifier
                        .padding(top = 26.dp)
                        .clickable { onSelect(Route.Map) }
                        .testTag("command_MAP")
                )
            }
        }
    }
}