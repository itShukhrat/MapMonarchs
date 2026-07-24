package com.mapmonarchs.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmonarchs.ui.theme.MapMonarchsTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The launch path's one unrecoverable failure is the intro never handing over:
 * the player taps the icon and sits on a black screen with no control, because
 * this screen draws BEFORE onboarding and before the map and nothing behind it
 * can rescue them.
 *
 * BOTH MOTION PATHS ARE COVERED EXPLICITLY. The reduced-motion branch returns
 * early and never composes the sequence, so a suite that only exercised the
 * animated path would be green on a normal emulator and silently vacuous on the
 * exact configuration where a motion-sensitive player would be stranded. The
 * `animate` flag is injected rather than read from Settings.Global because an
 * instrumented test cannot change that setting without WRITE_SECURE_SETTINGS.
 */
@RunWith(AndroidJUnit4::class)
class BrandIntroTest {

    @get:Rule
    val rule = createComposeRule()

    // ---- animation enabled -------------------------------------------------

    @Test
    fun withMotionTheIntroHandsOverOnItsOwn() {
        var done = false
        rule.setContent {
            MapMonarchsTheme { BrandIntro(onDone = { done = true }, animate = true) }
        }
        // Generous against the ~1.75s sequence: this asserts handover happens at
        // all, not that it happens on a precise schedule.
        rule.waitUntil(timeoutMillis = 6_000) { done }
        assertTrue("BrandIntro never handed over with motion enabled", done)
    }

    @Test
    fun withMotionTheCrestIsShownAndATapHandsOverEarly() {
        var done = false
        rule.setContent {
            MapMonarchsTheme { BrandIntro(onDone = { done = true }, animate = true) }
        }
        rule.onNodeWithTag(BrandIntroTags.SCREEN).assertIsDisplayed()
        rule.onNodeWithTag(BrandIntroTags.SCREEN).performClick()
        rule.waitUntil(timeoutMillis = 2_000) { done }
        assertTrue("tapping the intro did not hand over", done)
    }

    // ---- reduced motion ----------------------------------------------------

    @Test
    fun withoutMotionTheIntroHandsOverImmediatelyAndDrawsNothing() {
        var done = false
        rule.setContent {
            MapMonarchsTheme { BrandIntro(onDone = { done = true }, animate = false) }
        }
        rule.waitUntil(timeoutMillis = 2_000) { done }
        assertTrue("reduced motion did not hand over", done)

        // Skipped ENTIRELY, not played at zero duration: a player who turned
        // animation off must not be held by a sequence they cannot see.
        val stillDrawn = rule.onAllNodes(
            androidx.compose.ui.test.hasTestTag(BrandIntroTags.SCREEN)
        ).fetchSemanticsNodes().isNotEmpty()
        assertFalse("reduced motion should not compose the sequence", stillDrawn)
    }

    // The invariant "the intro can never strand the user" is exactly the
    // conjunction of withMotionTheIntroHandsOverOnItsOwn and
    // withoutMotionTheIntroHandsOverImmediatelyAndDrawsNothing: those are the
    // only two branches the composable has. A separate looping test over both
    // settings was written and deleted -- it could not drive two independent
    // compositions from one ComposeTestRule, so it degenerated into asserting a
    // literal `true` for the reduced-motion half while appearing to cover it.
    // A test that looks like coverage and is not is worse than no test.
}
