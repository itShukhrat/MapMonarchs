package com.mapmonarchs.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mapmonarchs.R
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
    CommandScreen(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier
    ) {

        // The permission state is a live readout, so it lives in a panel. The
        // remediation (note + deep link) sits BELOW the panel rather than inside
        // it: the card is the status, the action is the response to it, and the
        // action carries its own accent shadow that a card clip would cut.
        CommandCard(title = stringResource(R.string.settings_location)) {
            StatusRow(
                label = stringResource(R.string.settings_permission),
                value = stringResource(
                    if (locationGranted) R.string.settings_granted else R.string.settings_denied
                ),
                valueColor = if (locationGranted) SovereignGlow else Contested,
                modifier = Modifier.testTag(SettingsTags.PERMISSION_STATE)
            )
        }

        if (!locationGranted) {
            BodyNote(
                stringResource(R.string.settings_denied_note),
                modifier = Modifier.padding(top = 12.dp)
            )
            CommandAction(
                label = stringResource(R.string.settings_open_system),
                onClick = onOpenSystemSettings,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag(SettingsTags.OPEN_SYSTEM)
            )
        }

        // The cadence choices are a control group, so they share one panel. The
        // caption leads the panel because it is what the choices below it mean.
        CommandCard(title = stringResource(R.string.settings_cadence)) {
            BodyNote(
                stringResource(R.string.settings_cadence_note),
                modifier = Modifier.padding(top = 8.dp)
            )
            TrackingMode.entries.forEach { mode ->
                ChoiceRow(
                    label = stringResource(mode.labelRes),
                    detail = stringResource(mode.detailRes),
                    selected = mode == trackingMode,
                    onClick = { onTrackingModeChange(mode) },
                    modifier = Modifier.testTag("tracking_${mode.name}")
                )
            }
        }

        SectionHeader(stringResource(R.string.settings_not_do))

        BodyNote(stringResource(R.string.settings_not_do_body))

    }
}
