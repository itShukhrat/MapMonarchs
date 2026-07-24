package com.mapmonarchs.feature.settings

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmonarchs.data.local.TrackingMode
import com.mapmonarchs.ui.theme.MapMonarchsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings must VISUALLY recover when the permission flag flips.
 *
 * The recovery wiring itself lives in AppShell (ON_RESUME re-reads the OS
 * permission from any screen). This test proves the other half: that when that
 * flag flips from denied to granted, this screen actually changes -- the DENIED
 * readout and the OPEN SYSTEM SETTINGS action must disappear, and the row must
 * read GRANTED.
 *
 * WHY AN INSTRUMENTED SCREEN TEST RATHER THAN A NAVIGATION WALKTHROUGH: a runtime
 * permission change kills the app process and bounces it to onboarding, and dock
 * taps miss on the unstable emulator, so the end-to-end uiautomator path could
 * not be captured cleanly. Rendering the screen at each flag value is the
 * reliable proof that the screen reflects the state the shell feeds it.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun render(granted: Boolean) {
        rule.setContent {
            MapMonarchsTheme {
                SettingsScreen(
                    locationGranted = granted,
                    trackingMode = TrackingMode.BALANCED,
                    onTrackingModeChange = {},
                    currentLanguageTag = "",
                    onLanguageChange = {},
                    onOpenSystemSettings = {},
                    onBack = {}
                )
            }
        }
    }

    @Test
    fun deniedShowsTheDeniedReadoutAndTheRecoveryAction() {
        render(granted = false)
        rule.onNodeWithTag(SettingsTags.PERMISSION_STATE).assert(hasText("DENIED", substring = true))
        // The only way out of a permanent denial is the system-settings deep link,
        // so the action must be present precisely when permission is denied.
        rule.onNodeWithTag(SettingsTags.OPEN_SYSTEM).assertIsDisplayed()
    }

    @Test
    fun grantedShowsGrantedAndRemovesTheNowPointlessAction() {
        render(granted = true)
        rule.onNodeWithTag(SettingsTags.PERMISSION_STATE).assert(hasText("GRANTED", substring = true))
        // Once granted, OPEN SYSTEM SETTINGS would send the player somewhere with
        // nothing left to do -- an action that is not useful is worse than none,
        // so it must be gone.
        rule.onNodeWithTag(SettingsTags.OPEN_SYSTEM).assertDoesNotExist()
    }

    @Test
    fun everyTrackingCadenceIsOfferedAsARealChoice() {
        render(granted = true)
        TrackingMode.entries.forEach { mode ->
            rule.onNodeWithTag("tracking_${mode.name}").assertIsDisplayed()
        }
    }
}
