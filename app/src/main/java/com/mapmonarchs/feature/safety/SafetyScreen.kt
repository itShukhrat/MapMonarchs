package com.mapmonarchs.feature.safety

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.SectionHeader

/**
 * PROBLEM SOLVED: this game sends real people walking through real streets, and
 * it reads their location to do it. Both facts carry obligations.
 *
 * WHY IT BELONGS: Google Play requires prominent disclosure for location access,
 * so this is a compliance surface, not decoration. It is also the honest place to
 * state the anti-cheat position -- players are entitled to know their movement is
 * analysed, and saying so plainly is what separates a trustworthy competitive
 * game from one that feels like surveillance.
 *
 * WHAT CAN GO WRONG: safety copy that reads as legal boilerplate gets skipped.
 * Kept short, specific, and in plain language.
 */
@Composable
fun SafetyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    CommandScreen(title = "SAFETY & PRIVACY", onBack = onBack, modifier = modifier) {

        SectionHeader("PLAYING SAFELY")

        BodyNote(
            "Look up. Territory is not worth walking into traffic, and no hex is worth " +
                "entering private property, construction sites, or rail corridors."
        )
        BodyNote(
            "Claiming is disabled above walking speed. This is intentional -- the game is " +
                "not playable from a moving vehicle, and attempting it is dangerous."
        )
        BodyNote(
            "If an area feels unsafe, leave it. The map will still be there tomorrow, and " +
                "territory decays slowly enough that nothing is lost by waiting."
        )

        SectionHeader("WHAT WE COLLECT")

        BodyNote(
            "While the map is open we read your position, its accuracy, your speed, and " +
                "whether Android reports the location as mocked."
        )
        BodyNote(
            "These readings are sent to our servers to decide which hex you are standing in. " +
                "The server makes that decision, not your phone, which is what stops a " +
                "modified app from claiming territory it never walked to."
        )

        SectionHeader("WHY MOVEMENT IS ANALYSED")

        BodyNote(
            "Every claim attempt is recorded with the reason it was accepted or rejected. " +
                "That record is what lets us detect spoofed locations and impossible travel. " +
                "It exists to protect honest players from people who fake their way to the top."
        )

        SectionHeader("WHAT WE DO NOT DO")

        BodyNote(
            "We do not track you in the background. We do not show your live position to " +
                "other players. We do not sell location data, and there is no advertising SDK " +
                "in this app."
        )

    }
}
