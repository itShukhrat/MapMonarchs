package com.mapmonarchs.feature.subscription

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mapmonarchs.R
import com.mapmonarchs.domain.model.Entitlement
import com.mapmonarchs.domain.model.ProBenefit
import com.mapmonarchs.domain.model.ProBenefits
import com.mapmonarchs.domain.model.isPro
import com.mapmonarchs.ui.components.ActionState
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.CommandCard
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.components.StatusRow
import com.mapmonarchs.ui.theme.Contested
import com.mapmonarchs.ui.theme.Neutral
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.ui.theme.TextPrimary

object SubscriptionTags {
    const val ROOT = "subscription_root"
    const val PLAN = "subscription_plan"
    const val UPGRADE = "subscription_upgrade"
}

/**
 * The Pro surface.
 *
 * It does two things and refuses a third. It states the current plan, and it
 * lays out exactly what Pro is. It does NOT pretend to sell it: there is no
 * billing client, no price, and no server that could verify a receipt, so the
 * upgrade control is present but disabled, and it says why. An enabled "Upgrade"
 * button with no purchase behind it is the precise dishonesty this screen is
 * arranged to avoid -- a button that cannot do what it says is worse than no
 * button.
 *
 * THE BENEFITS ARE HONEST TWICE OVER. First, every one is fairness-neutral --
 * cosmetic, an account convenience, or a richer view of the player's own history
 * (see ProBenefits) -- so Pro never buys an advantage in taking ground. Second,
 * each benefit is marked with whether this build can actually deliver it; today
 * none can, because avatar storage and the cosmetic catalogue do not exist yet,
 * and a promised capability the app cannot honour is a fabricated one.
 */
@Composable
fun SubscriptionScreen(
    entitlement: Entitlement,
    onBack: () -> Unit,
    onManageBilling: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPro = entitlement.isPro()

    CommandScreen(
        title = stringResource(R.string.route_subscription),
        onBack = onBack,
        modifier = modifier.testTag(SubscriptionTags.ROOT)
    ) {
        // Current plan, first. The player's own state before the pitch.
        CommandCard(title = stringResource(R.string.subscription_plan)) {
            StatusRow(
                label = stringResource(R.string.subscription_current),
                value = stringResource(
                    if (isPro) R.string.profile_tier_pro else R.string.profile_tier_free
                ),
                valueColor = if (isPro) SovereignGlow else TextMuted,
                modifier = Modifier.testTag(SubscriptionTags.PLAN)
            )
        }

        // What Pro is. Two columns of truth: the benefit, and whether it is live.
        SectionHeader(stringResource(R.string.subscription_what_is_pro))
        ProBenefits.all.forEach { BenefitRow(it) }

        BodyNote(
            stringResource(R.string.subscription_fairness),
            modifier = Modifier.padding(top = 16.dp)
        )

        // The upgrade control. Disabled, because there is nothing to charge
        // against, and honest about it rather than dressed up as "coming soon".
        if (!isPro) {
            CommandAction(
                label = stringResource(R.string.subscription_upgrade_unavailable),
                onClick = {},
                // Unavailable, not merely styled-off: the control announces to a
                // screen reader why it will not respond, and cannot be pressed.
                state = ActionState.Unavailable(stringResource(R.string.subscription_upgrade_reason)),
                modifier = Modifier
                    .padding(top = 20.dp)
                    .testTag(SubscriptionTags.UPGRADE)
            )
            BodyNote(
                stringResource(R.string.subscription_no_billing_note),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Restore / manage appears ONLY when a store account could back it.
        // Showing "Restore purchases" with no billing implies there is something
        // to restore, which is its own small lie -- so it is hidden until
        // PURCHASE_AVAILABLE, at which point it opens the Play subscriptions page.
        if (Entitlement.PURCHASE_AVAILABLE) {
            CommandAction(
                label = stringResource(R.string.subscription_manage),
                onClick = onManageBilling,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun BenefitRow(benefit: ProBenefit) {
    Row(
        modifier = Modifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(benefit.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = stringResource(benefit.detailRes),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        // Honest per-benefit state: even inside Pro, a benefit the build cannot
        // deliver yet is marked, so the list never over-promises.
        Text(
            text = stringResource(
                if (benefit.live) R.string.subscription_benefit_live
                else R.string.subscription_benefit_soon
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (benefit.live) SovereignGlow else Neutral
        )
    }
}
