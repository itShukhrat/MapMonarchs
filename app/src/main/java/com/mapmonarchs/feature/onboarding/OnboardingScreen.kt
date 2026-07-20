package com.mapmonarchs.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.HexagonShape
import com.mapmonarchs.ui.theme.Sovereign
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.ui.theme.TextPrimary
import com.mapmonarchs.ui.theme.Void

object OnboardingTags {
    const val CONTINUE = "onboarding_continue"
    const val ROOT = "onboarding_root"
}

/**
 * First-run permission priming.
 *
 * PROBLEM SOLVED: a cold OS permission dialog with no context is the single
 * largest top-of-funnel loss in location games. A player who denies "Allow
 * MapMonarchs to access this device's location?" is gone -- Android will not ask
 * twice, and the product is unusable without it.
 *
 * WHY IT IS NOT GENERIC: no carousel, no illustrations, no three-dot pager. One
 * screen, one claim, one action. It states the bargain plainly -- walking is the
 * input, territory is the output -- and it says what we do NOT do, because in a
 * category with a bad privacy reputation, restraint is the differentiator.
 *
 * WHAT CAN GO WRONG: if this reads as a marketing screen it gets skipped and we
 * lose the priming benefit. Hence no hero art and no scroll: it is over in one
 * glance.
 */
@Composable
fun OnboardingScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Void)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp)
            .testTag(OnboardingTags.ROOT),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(HexagonShape)
                .background(Sovereign)
        )

        Text(
            text = "MAPMONARCHS",
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            modifier = Modifier.padding(top = 28.dp)
        )

        Text(
            text = "Ground you walk becomes ground you hold.",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.padding(top = 14.dp)
        )

        Text(
            text = "The map needs your location to know which hex you are standing in. " +
                "Territory is decided on our servers from the route you actually walk, " +
                "which is what keeps it fair.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.padding(top = 20.dp)
        )

        Text(
            text = "We do not track you in the background. Other players can see " +
                "which ground you hold -- never where you are now, and never where " +
                "you have been.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.padding(top = 14.dp)
        )

        CommandAction(
            label = "CONTINUE",
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp)
                .testTag(OnboardingTags.CONTINUE)
        )

        Text(
            text = "Android will ask for permission next.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier
                .padding(top = 16.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
}