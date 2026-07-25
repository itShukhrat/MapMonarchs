package com.mapmonarchs.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmonarchs.domain.model.Entitlement
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.ui.theme.MapMonarchsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The profile is where fabricated progress would do the most damage, because it
 * is the screen a player reads to find out what is true about themselves. These
 * tests pin the two ways that could go wrong.
 */
@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun render(
        identity: Identity = Identity.SignedOut,
        holdings: String = "—",
        entitlement: Entitlement = Entitlement.Free
    ) {
        rule.setContent {
            MapMonarchsTheme {
                ProfileScreen(
                    identity = identity,
                    territoryColor = null,
                    holdings = holdings,
                    entitlement = entitlement,
                    onBack = {},
                    onManageIdentity = {},
                    onOpenShop = {}
                )
            }
        }
    }

    /**
     * Unknown must not be rendered as zero.
     *
     * "0 hexes held" is a claim ABOUT THE PLAYER -- that they hold nothing.
     * "unknown" is a claim about the DATA. The app cannot currently tell the
     * difference from the server, so it must say the second. A regression here
     * would not crash or look broken; it would quietly start lying, which is
     * precisely why it needs a test rather than a code review.
     */
    @Test
    fun unknownStandingIsNeverRenderedAsZero() {
        render(holdings = "—")
        rule.onNodeWithTag(ProfileTags.HELD).assert(hasText("0", substring = true).not())
        rule.onNodeWithTag(ProfileTags.RANK).assert(hasText("0", substring = true).not())
    }

    /**
     * A free player must be told the truth twice: that the picture is a paid
     * capability, AND that the paid tier cannot currently be bought. Showing
     * only the first turns the screen into an upsell for a product with no
     * purchase path.
     */
    @Test
    fun aFreePlayerSeesTheGateLockedAndTheTierStatedPlainly() {
        render(entitlement = Entitlement.Free)
        // The ACCOUNT card sits below the fold: the profile scrolls, so the
        // rows must be brought into view before "displayed" means anything.
        rule.onNodeWithTag(ProfileTags.TIER).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(ProfileTags.PICTURE).performScrollTo().assertIsDisplayed()
        // The gate is shut for every player in this build; Entitlement.Pro is
        // unreachable, which EntitlementTest pins separately.
        rule.onNodeWithTag(ProfileTags.PICTURE).assert(hasText("—", substring = true).not())
    }

    @Test
    fun theHubRendersForAPlayerWithNoSessionAtAll() {
        // The emptiest possible state still has to be a designed screen rather
        // than a stack of blanks: signed out is the state a brand-new install
        // opens in, so it is the first profile anyone ever sees.
        render(identity = Identity.SignedOut)
        rule.onNodeWithTag(ProfileTags.ROOT).assertIsDisplayed()
        rule.onNodeWithTag(ProfileTags.CREST).assertIsDisplayed()
        // Exists in the composed tree; it may sit just below the fold on shorter
        // screens, and existence is the claim here, not pixel visibility.
        rule.onNodeWithTag(ProfileTags.HELD).assertExists()
    }
}
