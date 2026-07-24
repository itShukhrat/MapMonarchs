package com.mapmonarchs.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmonarchs.ui.theme.MapMonarchsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The launch path's one unrecoverable failure is the intro never handing over:
 * the player taps the icon and sits on a black screen with no control, because
 * this screen is drawn BEFORE onboarding and before the map, and nothing behind
 * it can rescue them. Everything else about the sequence is taste; this is the
 * part that must be nailed down by a test.
 *
 * Deliberately written to pass under BOTH motion settings. BrandIntro returns
 * early and hands over immediately when the system asks for reduced motion, so
 * a test that assumed the animation renders would be green on a normal emulator
 * and silently vacuous on one with animation disabled -- which is exactly the
 * configuration where a regression would strand a motion-sensitive player.
 */
@RunWith(AndroidJUnit4::class)
class BrandIntroTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theIntroAlwaysHandsOverSoTheLaunchPathCannotStrand() {
        var done = false
        rule.setContent { MapMonarchsTheme { BrandIntro(onDone = { done = true }) } }

        // Generous relative to the ~1.75s sequence: this asserts that handover
        // happens at all, not that it happens on a precise schedule.
        rule.waitUntil(timeoutMillis = 6_000) { done }
        assertTrue("BrandIntro never called onDone", done)
    }

    @Test
    fun tappingHandsOverWithoutWaitingOutTheSequence() {
        var done = false
        rule.setContent { MapMonarchsTheme { BrandIntro(onDone = { done = true }) } }

        // Under reduced motion the sequence is skipped entirely, so there is no
        // surface to tap and handover has already happened. Only the animated
        // case can exercise the skip affordance.
        val rendered = rule.onAllNodesWithTagSafe(BrandIntroTags.SCREEN)
        if (!rendered) {
            assertTrue("reduced motion should hand over without a tap", done)
            return
        }

        rule.onNodeWithTag(BrandIntroTags.SCREEN).performClick()
        rule.waitUntil(timeoutMillis = 2_000) { done }
        assertTrue("tapping the intro did not hand over", done)
    }
}

/** True when at least one node carries [tag]. */
private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagSafe(
    tag: String
): Boolean = onAllNodes(
    androidx.compose.ui.test.hasTestTag(tag)
).fetchSemanticsNodes().isNotEmpty()
