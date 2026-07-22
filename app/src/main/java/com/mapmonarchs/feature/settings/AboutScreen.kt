package com.mapmonarchs.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mapmonarchs.BuildConfig
import com.mapmonarchs.R
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandCard
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.components.StatusRow

/**
 * PROBLEM SOLVED: version reporting for support, and the map attribution Mapbox
 * requires by licence.
 *
 * Deliberately thin. An about screen is where template apps hoard credits,
 * social links, and rate-us prompts; none of those help a player understand,
 * control, or return to this product.
 *
 * FIRST SCREEN MIGRATED TO STRING RESOURCES. It was chosen to go first because
 * it is entirely self-contained: no domain strings, no conditional copy, and no
 * dependency on AppError or Blocker, both of which have to move as one atomic
 * commit later. Proving the pattern on a screen that cannot half-migrate is what
 * makes the remaining screens routine.
 *
 * The BuildConfig values stay as they are. A version number is not language.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    CommandScreen(
        title = stringResource(R.string.about_title),
        onBack = onBack,
        modifier = modifier
    ) {

        CommandCard(title = stringResource(R.string.about_build)) {
            StatusRow(
                label = stringResource(R.string.about_version),
                value = BuildConfig.VERSION_NAME
            )
            StatusRow(
                label = stringResource(R.string.about_build_number),
                value = BuildConfig.VERSION_CODE.toString()
            )
            StatusRow(
                label = stringResource(R.string.about_type),
                value = if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"
            )
        }

        SectionHeader(stringResource(R.string.about_map_data))
        BodyNote(stringResource(R.string.about_map_data_body))

        SectionHeader(stringResource(R.string.about_status))
        BodyNote(stringResource(R.string.about_status_body))

    }
}
