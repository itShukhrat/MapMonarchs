package com.mapmonarchs.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mapmonarchs.BuildConfig
import com.mapmonarchs.ui.components.BodyNote
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
 */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    CommandScreen(title = "ABOUT", onBack = onBack, modifier = modifier) {

        SectionHeader("BUILD")

        StatusRow(label = "Version", value = BuildConfig.VERSION_NAME)
        StatusRow(label = "Build", value = BuildConfig.VERSION_CODE.toString())
        StatusRow(label = "Type", value = if (BuildConfig.DEBUG) "DEBUG" else "RELEASE")

        SectionHeader("MAP DATA")

        BodyNote(
            "Maps and map data are provided by Mapbox and OpenStreetMap contributors. " +
                "Attribution is shown on the map itself as their licence requires."
        )

        SectionHeader("STATUS")

        BodyNote(
            "This is an early build. Territory claiming is not yet enabled -- the server " +
                "side of the capture system is still being brought up."
        )

        Box(modifier = Modifier.padding(bottom = 40.dp))
    }
}