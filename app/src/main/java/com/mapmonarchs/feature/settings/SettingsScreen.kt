package com.mapmonarchs.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mapmonarchs.R
import androidx.compose.ui.unit.dp
import com.mapmonarchs.data.local.AppLanguage
import com.mapmonarchs.data.local.TrackingMode
import com.mapmonarchs.BuildConfig
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.ChoiceRow
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.CommandCard
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.components.StatusRow
import com.mapmonarchs.ui.theme.Contested
import com.mapmonarchs.ui.theme.SovereignGlow

object SettingsTags {
    const val PERMISSION_STATE = "settings_permission_state"
    const val OPEN_SYSTEM = "settings_open_system"
    const val DEBUG_FREE = "settings_debug_free"
    const val DEBUG_PRO = "settings_debug_pro"
    const val CRASH_OFF = "settings_crash_off"
    const val CRASH_ON = "settings_crash_on"
}

/**
 * PROBLEM SOLVED: two things the player genuinely controls -- whether we can see
 * their location at all, and how hard we hit their battery doing it.
 *
 * WHY NOT GENERIC: no theme switch (the product is committed to dark), no
 * notification toggles (there are no notifications), no account section (account
 * controls -- sign out and delete account -- live on IDENTITY, where the player
 * expects them). Every row here changes real behaviour. A settings screen padded
 * with inert switches is the clearest tell of a template app.
 */
@Composable
fun SettingsScreen(
    locationGranted: Boolean,
    trackingMode: TrackingMode,
    onTrackingModeChange: (TrackingMode) -> Unit,
    currentLanguageTag: String,
    onLanguageChange: (String) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // Crash-reporting consent (Crashlytics only). Default off; the caller
    // persists it and applies it to Firebase. Behavioural analytics and the
    // advertising id stay off regardless -- see FirebaseTelemetry.
    crashReportingEnabled: Boolean = false,
    onCrashReportingChange: (Boolean) -> Unit = {},
    // QA-only. Rendered as a control only in a debug build; the caller also
    // gates the value behind BuildConfig.DEBUG so release cannot honour it.
    debugSimulatePro: Boolean = false,
    onDebugSimulateProChange: (Boolean) -> Unit = {},
) {
    val activeLanguage = AppLanguage.fromTag(currentLanguageTag)
    CommandScreen(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier
    ) {

        // The permission state is a live readout, so it lives in a panel. The
        // remediation (note + deep link) sits BELOW the panel rather than inside
        // it: the card is the status, the action is the response to it, and the
        // action carries its own accent shadow that a card clip would cut.
        CommandCard(title = stringResource(R.string.settings_location)) {
            StatusRow(
                label = stringResource(R.string.settings_permission),
                value = stringResource(
                    if (locationGranted) R.string.settings_granted else R.string.settings_denied
                ),
                valueColor = if (locationGranted) SovereignGlow else Contested,
                modifier = Modifier.testTag(SettingsTags.PERMISSION_STATE)
            )
        }

        if (!locationGranted) {
            BodyNote(
                stringResource(R.string.settings_denied_note),
                modifier = Modifier.padding(top = 12.dp)
            )
            CommandAction(
                label = stringResource(R.string.settings_open_system),
                onClick = onOpenSystemSettings,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag(SettingsTags.OPEN_SYSTEM)
            )
        }

        // Language sits high because it reframes everything below it. Each row is
        // a peer choice, so it reuses the same radio-style ChoiceRow as cadence.
        CommandCard(title = stringResource(R.string.settings_language)) {
            BodyNote(
                stringResource(R.string.settings_language_note),
                modifier = Modifier.padding(top = 8.dp)
            )
            AppLanguage.entries.forEach { language ->
                ChoiceRow(
                    label = stringResource(language.labelRes),
                    detail = stringResource(language.detailRes),
                    selected = language == activeLanguage,
                    onClick = { onLanguageChange(language.tag) },
                    modifier = Modifier.testTag("language_${language.name}")
                )
            }
        }

        // The cadence choices are a control group, so they share one panel. The
        // caption leads the panel because it is what the choices below it mean.
        CommandCard(title = stringResource(R.string.settings_cadence)) {
            BodyNote(
                stringResource(R.string.settings_cadence_note),
                modifier = Modifier.padding(top = 8.dp)
            )
            TrackingMode.entries.forEach { mode ->
                ChoiceRow(
                    label = stringResource(mode.labelRes),
                    detail = stringResource(mode.detailRes),
                    selected = mode == trackingMode,
                    onClick = { onTrackingModeChange(mode) },
                    modifier = Modifier.testTag("tracking_${mode.name}")
                )
            }
        }

        // Crash reporting is a real, off-by-default choice -- Crashlytics only,
        // which keeps it inside the "no background tracking, no advertising id"
        // promise stated just below. Two peer rows reuse the radio-style
        // ChoiceRow, like cadence and language above.
        CommandCard(title = stringResource(R.string.settings_diagnostics)) {
            BodyNote(
                stringResource(R.string.settings_diagnostics_note),
                modifier = Modifier.padding(top = 8.dp)
            )
            ChoiceRow(
                label = stringResource(R.string.settings_diagnostics_off),
                detail = stringResource(R.string.settings_diagnostics_off_detail),
                selected = !crashReportingEnabled,
                onClick = { onCrashReportingChange(false) },
                modifier = Modifier.testTag(SettingsTags.CRASH_OFF)
            )
            ChoiceRow(
                label = stringResource(R.string.settings_diagnostics_on),
                detail = stringResource(R.string.settings_diagnostics_on_detail),
                selected = crashReportingEnabled,
                onClick = { onCrashReportingChange(true) },
                modifier = Modifier.testTag(SettingsTags.CRASH_ON)
            )
        }

        SectionHeader(stringResource(R.string.settings_not_do))

        BodyNote(stringResource(R.string.settings_not_do_body))

        // ---- QA (debug builds only) ----------------------------------------
        // Compiled out of release by the BuildConfig.DEBUG guard: the whole card
        // never renders, and AppShell also refuses to honour the flag in
        // release. Strings are hardcoded English on purpose -- this is a tester
        // tool, not shipping UI, so it is deliberately outside the localised set.
        if (BuildConfig.DEBUG) {
            SectionHeader("QA · DEBUG ONLY")
            BodyNote(
                "Simulates the Pro DISPLAY state on this device. It never touches " +
                    "the server and is absent from release builds. Real Pro comes " +
                    "only from a verified purchase or an admin grant."
            )
            CommandCard {
                ChoiceRow(
                    label = "FREE",
                    detail = "Server-driven entitlement (the real state).",
                    selected = !debugSimulatePro,
                    onClick = { onDebugSimulateProChange(false) },
                    modifier = Modifier.testTag(SettingsTags.DEBUG_FREE)
                )
                ChoiceRow(
                    label = "PRO (simulated)",
                    detail = "Force the Pro display state for QA.",
                    selected = debugSimulatePro,
                    onClick = { onDebugSimulateProChange(true) },
                    modifier = Modifier.testTag(SettingsTags.DEBUG_PRO)
                )
            }
        }
    }
}
