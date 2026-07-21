package com.mapmonarchs.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mapmonarchs.data.local.TrackingMode
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.ChoiceRow
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.CommandCard
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.components.StatusRow
import com.mapmonarchs.ui.theme.Contested
import com.mapmonarchs.ui.theme.SovereignGlow

object SettingsTags {
    const val PERMISSION_STATE = "settings_permission_state"
    const val OPEN_SYSTEM = "settings_open_system"
}

/**
 * PROBLEM SOLVED: two things the player genuinely controls -- whether we can see
 * their location at all, and how hard we hit their battery doing it.
 *
 * WHY NOT GENERIC: no theme switch (the product is committed to dark), no
 * notification toggles (there are no notifications), no account section (there is
 * no account to manage). Every row here changes real behaviour. A settings screen
 * padded with inert switches is the clearest tell of a template app.
 */
@Composable
fun SettingsScreen(
    locationGranted: Boolean,
    trackingMode: TrackingMode,
    onTrackingModeChange: (TrackingMode) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    CommandScreen(title = "SETTINGS", onBack = onBack, modifier = modifier) {

        // The permission state is a live readout, so it lives in a panel. The
        // remediation (note + deep link) sits BELOW the panel rather than inside
        // it: the card is the status, the action is the response to it, and the
        // action carries its own accent shadow that a card clip would cut.
        CommandCard(title = "LOCATION") {
            StatusRow(
                label = "Permission",
                value = if (locationGranted) "GRANTED" else "DENIED",
                valueColor = if (locationGranted) SovereignGlow else Contested,
                modifier = Modifier.testTag(SettingsTags.PERMISSION_STATE)
            )
        }

        if (!locationGranted) {
            BodyNote(
                "Android will not ask again once permission has been denied. It has to be " +
                    "granted from system settings, or the map cannot tell which hex you are in.",
                modifier = Modifier.padding(top = 12.dp)
            )
            CommandAction(
                label = "OPEN SYSTEM SETTINGS",
                onClick = onOpenSystemSettings,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag(SettingsTags.OPEN_SYSTEM)
            )
        }

        // The cadence choices are a control group, so they share one panel. The
        // caption leads the panel because it is what the choices below it mean.
        CommandCard(title = "TRACKING CADENCE") {
            BodyNote(
                "How often the app asks for a position while the map is open. Faster updates " +
                    "make claiming feel more responsive and cost more battery.",
                modifier = Modifier.padding(top = 8.dp)
            )
            TrackingMode.entries.forEach { mode ->
                ChoiceRow(
                    label = mode.label,
                    detail = mode.detail,
                    selected = mode == trackingMode,
                    onClick = { onTrackingModeChange(mode) },
                    modifier = Modifier.testTag("tracking_${mode.name}")
                )
            }
        }

        SectionHeader("WHAT WE DO NOT DO")

        BodyNote(
            "Location is only read while the map is open. There is no background tracking, " +
                "and no advertising identifier. Other players can see which ground you hold -- " +
                "never your live position, and never the route you walked to reach it."
        )

    }
}
