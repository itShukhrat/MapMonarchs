package com.mapmonarchs.feature.help

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mapmonarchs.feature.map.resolveBlocker
import com.mapmonarchs.feature.map.MapUiState
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.SectionHeader

object HelpTags {
    const val CURRENT = "help_current"
    const val DIAGNOSTICS = "help_open_diagnostics"
}

/**
 * PROBLEM SOLVED: help screens usually answer questions the player is not asking.
 * This one opens with their ACTUAL current blocker, computed from live state,
 * before offering anything general.
 *
 * WHY NOT GENERIC: no search box, no collapsible FAQ accordion, no support-ticket
 * form for a product with no support desk. It is a short list of the only
 * questions this game actually produces, answered plainly -- including the
 * unflattering ones ("why can nobody claim anything yet").
 */
@Composable
fun HelpScreen(
    state: MapUiState,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    CommandScreen(title = "HELP", onBack = onBack, modifier = modifier) {

        SectionHeader("RIGHT NOW")
        BodyNote(
            text = resolveBlocker(state).explanation(),
            modifier = Modifier.testTag(HelpTags.CURRENT)
        )
        CommandAction(
            label = "OPEN DIAGNOSTICS",
            onClick = onOpenDiagnostics,
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(HelpTags.DIAGNOSTICS)
        )

        SectionHeader("WHY CAN'T I CLAIM ANY GROUND?")
        BodyNote(
            "Claiming is disabled in this build. The server side that decides ownership " +
                "has not been deployed yet, so no client can claim a hex -- including a " +
                "modified one. This is a deployment state, not a bug in your app."
        )

        SectionHeader("THE BUTTON SAYS GPS TOO WEAK")
        BodyNote(
            "Your position is known to worse than 30 metres, which is wider than a hex. " +
                "Acting on it could claim ground you are not standing on. Move away from " +
                "tall buildings and give it a few seconds outdoors."
        )

        SectionHeader("IT SAYS I AM MOVING TOO FAST")
        BodyNote(
            "Claiming is limited to walking pace. This is deliberate: the game is not " +
                "playable from a vehicle, and allowing it would be both unfair and unsafe."
        )

        SectionHeader("IT SAYS MOCK LOCATION")
        BodyNote(
            "Android is reporting your position as simulated. Turn off any location-spoofing " +
                "app or developer mock-location setting. Spoofed positions are rejected by the " +
                "server as well, so there is nothing to gain by hiding it."
        )

        SectionHeader("THE MAP LOADS BUT NOTHING IS ON IT")
        BodyNote(
            "That is expected. No territory exists anywhere yet, for any player."
        )

        SectionHeader("BATTERY DRAIN")
        BodyNote(
            "Location is only read while the map is open. If the cost is still too high, " +
                "lower the tracking cadence in settings -- claiming stays available, it just " +
                "updates less often."
        )
        CommandAction(
            label = "OPEN SETTINGS",
            onClick = onOpenSettings,
            modifier = Modifier.padding(top = 8.dp)
        )

    }
}
