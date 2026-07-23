package com.mapmonarchs.feature.help

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mapmonarchs.R
import com.mapmonarchs.feature.common.localizedExplanation
import androidx.compose.ui.unit.dp
import com.mapmonarchs.feature.map.resolveBlocker
import com.mapmonarchs.feature.map.MapUiState
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.ActionEmphasis
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
    CommandScreen(
        title = stringResource(R.string.help_title),
        onBack = onBack,
        modifier = modifier
    ) {

        SectionHeader(stringResource(R.string.help_right_now))
        BodyNote(
            text = resolveBlocker(state).localizedExplanation(),
            modifier = Modifier.testTag(HelpTags.CURRENT)
        )
        CommandAction(
            label = stringResource(R.string.help_open_diagnostics),
            onClick = onOpenDiagnostics,
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(HelpTags.DIAGNOSTICS)
        )

        SectionHeader(stringResource(R.string.help_q_no_claim))
        BodyNote(stringResource(R.string.help_a_no_claim))

        SectionHeader(stringResource(R.string.help_q_gps))
        BodyNote(stringResource(R.string.help_a_gps))

        SectionHeader(stringResource(R.string.help_q_fast))
        BodyNote(stringResource(R.string.help_a_fast))

        SectionHeader(stringResource(R.string.help_q_mock))
        BodyNote(stringResource(R.string.help_a_mock))

        SectionHeader(stringResource(R.string.help_q_empty))
        BodyNote(stringResource(R.string.help_a_empty))

        SectionHeader(stringResource(R.string.help_q_battery))
        BodyNote(stringResource(R.string.help_a_battery))
        CommandAction(
            label = stringResource(R.string.help_open_settings),
            onClick = onOpenSettings,
            emphasis = ActionEmphasis.SECONDARY,
            modifier = Modifier.padding(top = 8.dp)
        )

    }
}
