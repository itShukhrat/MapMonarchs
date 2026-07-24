package com.mapmonarchs.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mapmonarchs.R
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.components.CommandAction
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.domain.model.PLAYABLE_COUNTRY_NAME
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .testTag(OnboardingTags.ROOT),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        // THE HANDOVER FRAME. BrandIntro finishes on the crown and this screen is
        // the very next thing drawn, so it carries the same mark rather than a
        // different one. This used to be a solid Sovereign hexagon, which meant
        // the brand changed symbol in the gap between the intro ending and
        // onboarding appearing -- the one seam in the launch path the player
        // actually sees.
        //
        // Same asset the icon, the system splash and the intro use, so there is
        // no fourth version of the mark to keep in sync. 64x52 holds the
        // artwork's 423x344 aspect exactly and keeps the previous 52dp height,
        // so the layout below it does not move.
        Image(
            painter = painterResource(R.drawable.ic_brand_crown),
            // Decorative: the wordmark directly beneath already names the brand.
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 64.dp, height = 52.dp)
        )

        Text(
            text = stringResource(R.string.brand_wordmark),
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            modifier = Modifier.padding(top = 28.dp)
        )

        Text(
            text = stringResource(R.string.onboarding_tagline),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.padding(top = 14.dp)
        )

        Text(
            text = stringResource(R.string.onboarding_world, stringResource(R.string.country_name)),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.padding(top = 20.dp)
        )

        Text(
            text = stringResource(R.string.onboarding_location),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.padding(top = 20.dp)
        )

        Text(
            text = stringResource(R.string.onboarding_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.padding(top = 14.dp)
        )

        CommandAction(
            label = stringResource(R.string.onboarding_continue),
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp)
                .testTag(OnboardingTags.CONTINUE)
        )

        Text(
            text = stringResource(R.string.onboarding_permission_next),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier
                .padding(top = 16.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
}