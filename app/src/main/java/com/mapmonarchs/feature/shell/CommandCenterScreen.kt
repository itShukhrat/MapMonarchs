package com.mapmonarchs.feature.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.components.StatusRow
import com.mapmonarchs.ui.theme.LockedFg
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted

object CommandCenterTags {
    const val SCREEN = "command_center"
    fun row(title: String) = "command_center_$title"
}

/**
 * The instrument deck.
 *
 * REPLACED THE FLOATING COMMAND MENU, and the reason is worth stating: the menu
 * was an overlay reachable only from the map, so identity, settings, safety and
 * about were two taps deep and invisible until a player found an unlabelled hex
 * button in a corner. A product's own explanation of itself should not be the
 * hardest thing in it to reach.
 *
 * The bar's other four destinations are deliberately NOT repeated here. A list
 * that duplicates the navigation sitting directly beneath it teaches a player
 * that the two are different when they are not.
 *
 * Locked entries shown here are the ones with no seat in the bar. They stay
 * visible rather than hidden: hiding them makes the product look smaller than it
 * is, and a player deciding whether to keep walking deserves to see where this
 * is going.
 */
@Composable
fun CommandCenterScreen(
    onOpen: (Route) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    CommandScreen(title = "COMMAND", onBack = onBack, modifier = modifier.testTag(CommandCenterTags.SCREEN)) {

        SectionHeader("YOUR SESSION")
        COMMAND_ROUTES.forEach { route ->
            StatusRow(
                label = route.title,
                value = "OPEN",
                valueColor = SovereignGlow,
                onClick = { onOpen(route) },
                modifier = Modifier.testTag(CommandCenterTags.row(route.title))
            )
        }

        SectionHeader("REFERENCE")
        REFERENCE_ROUTES.forEach { route ->
            StatusRow(
                label = route.title,
                value = "READ",
                valueColor = TextMuted,
                onClick = { onOpen(route) },
                modifier = Modifier.testTag(CommandCenterTags.row(route.title))
            )
        }

        SectionHeader("NOT OPEN YET")
        BodyNote(
            "These are real systems with real unlock conditions, not placeholders. " +
                "Each one states what has to be true before it works."
        )
        LOCKED_ROUTES.filter { locked -> NAV_DESTINATIONS.none { it.route == locked } }
            .forEach { route ->
                StatusRow(
                    label = route.title,
                    value = "LOCKED",
                    valueColor = LockedFg,
                    onClick = { onOpen(route) },
                    modifier = Modifier.testTag(CommandCenterTags.row(route.title))
                )
            }

    }
}
