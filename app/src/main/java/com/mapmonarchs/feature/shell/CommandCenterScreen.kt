package com.mapmonarchs.feature.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mapmonarchs.R
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandCard
import com.mapmonarchs.ui.components.CommandScreen
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
    CommandScreen(title = stringResource(R.string.command_title), onBack = onBack, modifier = modifier.testTag(CommandCenterTags.SCREEN)) {

        // Panelled like every other dashboard surface. These rows carry a
        // right-aligned state per destination -- OPEN, READ, LOCKED -- which is
        // the same readout shape the panel language exists for. The deck is the
        // hub those screens hang off, so leaving it as loose rows made the hub
        // the least finished thing in its own hierarchy.
        CommandCard(title = stringResource(R.string.command_your_session)) {
            COMMAND_ROUTES.forEach { route ->
                StatusRow(
                    label = stringResource(route.titleRes),
                    value = stringResource(R.string.command_open),
                    valueColor = SovereignGlow,
                    onClick = { onOpen(route) },
                    modifier = Modifier.testTag(CommandCenterTags.row(route.key))
                )
            }
        }

        CommandCard(title = stringResource(R.string.command_reference)) {
            REFERENCE_ROUTES.forEach { route ->
                StatusRow(
                    label = stringResource(route.titleRes),
                    value = stringResource(R.string.command_read),
                    valueColor = TextMuted,
                    onClick = { onOpen(route) },
                    modifier = Modifier.testTag(CommandCenterTags.row(route.key))
                )
            }
        }

        // The caption sits INSIDE this panel because it is what the rows below it
        // mean -- the same call made for the cadence group in SETTINGS. Elsewhere
        // prose stays outside a panel; here it is the label for the group.
        CommandCard(title = stringResource(R.string.command_not_open)) {
            BodyNote(
                stringResource(R.string.command_not_open_body),
                modifier = Modifier.padding(top = 8.dp)
            )
            LOCKED_ROUTES.filter { locked -> NAV_DESTINATIONS.none { it.route == locked } }
                .forEach { route ->
                    StatusRow(
                        label = stringResource(route.titleRes),
                        value = stringResource(R.string.command_locked),
                        valueColor = LockedFg,
                        onClick = { onOpen(route) },
                        modifier = Modifier.testTag(CommandCenterTags.row(route.key))
                    )
                }
        }

    }
}
