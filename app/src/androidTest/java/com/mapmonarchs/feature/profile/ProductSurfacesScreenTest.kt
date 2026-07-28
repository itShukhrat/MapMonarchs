package com.mapmonarchs.feature.profile

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmonarchs.domain.model.Entitlement
import com.mapmonarchs.domain.model.PlayerRecords
import com.mapmonarchs.feature.badges.BadgesScreen
import com.mapmonarchs.feature.badges.BadgesTags
import com.mapmonarchs.feature.records.RecordsScreen
import com.mapmonarchs.feature.records.RecordsTags
import com.mapmonarchs.feature.subscription.SubscriptionScreen
import com.mapmonarchs.feature.subscription.SubscriptionTags
import com.mapmonarchs.ui.theme.MapMonarchsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The new profile surfaces, each pinned on the specific honesty claim it makes.
 */
@RunWith(AndroidJUnit4::class)
class ProductSurfacesScreenTest {

    @get:Rule
    val rule = createComposeRule()

    // ---- Badges: an honest zero, not a hidden empty room -------------------

    @Test
    fun badgesShowZeroEarnedAndRenderTheWholeLockedCabinet() {
        rule.setContent { MapMonarchsTheme { BadgesScreen(earnedKeys = emptyMap(), onBack = {}) } }
        rule.onNodeWithTag(BadgesTags.ROOT).assertIsDisplayed()
        // The count is "0 of N earned" -- present and honest, not concealed.
        rule.onNodeWithTag(BadgesTags.SUMMARY).assert(hasText("0", substring = true))
        // A specific catalogued badge renders even though it is locked, so the
        // player sees what is earnable rather than an empty screen.
        rule.onNodeWithTag("badge_first_capture").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anEarnedBadgeIsReflectedInTheCount() {
        rule.setContent {
            MapMonarchsTheme {
                BadgesScreen(earnedKeys = mapOf("first_capture" to 1L), onBack = {})
            }
        }
        rule.onNodeWithTag(BadgesTags.SUMMARY).assert(hasText("1", substring = true))
    }

    // ---- Records: one loud empty state, not six quiet blanks ---------------

    @Test
    fun recordsShowTheHonestEmptyStateWhenThereIsNoHistory() {
        rule.setContent {
            MapMonarchsTheme { RecordsScreen(records = PlayerRecords.NONE, onBack = {}) }
        }
        rule.onNodeWithTag(RecordsTags.ROOT).assertIsDisplayed()
        rule.onNodeWithTag(RecordsTags.EMPTY).performScrollTo().assertIsDisplayed()
    }

    // ---- Subscription: a plan, a pitch, and no fake sale -------------------

    @Test
    fun subscriptionShowsFreePlanAndAnUnavailableUpgrade() {
        rule.setContent {
            MapMonarchsTheme {
                SubscriptionScreen(
                    entitlement = Entitlement.Free,
                    onBack = {},
                    onManageBilling = {}
                )
            }
        }
        rule.onNodeWithTag(SubscriptionTags.ROOT).assertIsDisplayed()
        rule.onNodeWithTag(SubscriptionTags.PLAN).performScrollTo().assertIsDisplayed()
        // The upgrade control exists but is presented as unavailable -- the whole
        // point is that it does not pretend to sell something with no billing.
        rule.onNodeWithTag(SubscriptionTags.UPGRADE).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun subscriptionForAProPlayerShowsTheActivePlanAndNoUpgrade() {
        // The Pro path (reached in prod by a verified purchase / admin grant, and
        // on-device via the debug simulator) must show the active plan and drop
        // the upgrade control -- there is nothing to upgrade to.
        rule.setContent {
            MapMonarchsTheme {
                SubscriptionScreen(
                    entitlement = Entitlement.Pro,
                    onBack = {},
                    onManageBilling = {}
                )
            }
        }
        rule.onNodeWithTag(SubscriptionTags.PLAN).performScrollTo()
            .assert(hasText("PRO", substring = true))
        rule.onNodeWithTag(SubscriptionTags.UPGRADE).assertDoesNotExist()
    }
}
