package com.mapmonarchs.feature.safety

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mapmonarchs.R
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
 *
 * MIGRATED TO STRING RESOURCES SECOND, and not by accident: a location
 * disclosure that a player cannot read is not a disclosure. Of every screen in
 * the product this is the one where shipping untranslated copy into Uzbekistan
 * would be a compliance problem rather than a polish problem.
 */
@Composable
fun SafetyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    CommandScreen(
        title = stringResource(R.string.safety_title),
        onBack = onBack,
        modifier = modifier
    ) {

        SectionHeader(stringResource(R.string.safety_playing))

        BodyNote(stringResource(R.string.safety_playing_1))
        BodyNote(stringResource(R.string.safety_playing_2))
        BodyNote(stringResource(R.string.safety_playing_3))

        SectionHeader(stringResource(R.string.safety_collect))

        BodyNote(stringResource(R.string.safety_collect_1))
        BodyNote(stringResource(R.string.safety_collect_2))

        SectionHeader(stringResource(R.string.safety_movement))

        BodyNote(stringResource(R.string.safety_movement_body))

        SectionHeader(stringResource(R.string.safety_not_do))

        BodyNote(stringResource(R.string.safety_not_do_body))

    }
}
