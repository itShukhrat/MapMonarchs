package com.mapmonarchs.feature.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmonarchs.data.local.FirstRunStore
import com.mapmonarchs.data.local.TrackingPreferences
import com.mapmonarchs.feature.identity.IdentityScreen
import com.mapmonarchs.feature.map.MapScreen
import com.mapmonarchs.feature.map.MapViewModel
import com.mapmonarchs.feature.map.MapViewModelFactory
import com.mapmonarchs.feature.onboarding.OnboardingScreen
import com.mapmonarchs.feature.safety.SafetyScreen
import com.mapmonarchs.feature.settings.AboutScreen
import com.mapmonarchs.feature.settings.SettingsScreen

/**
 * The application shell.
 *
 * Composition rules this enforces:
 *  - The map is always the root; it is never popped and never sits behind a tab.
 *  - Navigation is a launcher (command menu), not persistent chrome, so the map
 *    keeps the full frame.
 *  - Secondary screens replace the map rather than floating over it. A half-
 *    transparent sheet over a live GL map is expensive and reads as clutter.
 */
@Composable
fun AppShell(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val navigator = remember { ShellNavigator() }
    val firstRunStore = remember { FirstRunStore(context) }
    val trackingPrefs = remember { TrackingPreferences(context) }

    var onboardingComplete by remember { mutableStateOf(firstRunStore.hasCompletedOnboarding()) }
    var trackingMode by remember { mutableStateOf(trackingPrefs.mode) }

    val viewModel: MapViewModel = viewModel(factory = MapViewModelFactory(context))
    val state by viewModel.uiState.collectAsState()

    if (!onboardingComplete) {
        OnboardingScreen(
            onContinue = {
                firstRunStore.markOnboardingComplete()
                onboardingComplete = true
            },
            modifier = modifier
        )
        return
    }

    // Back closes the menu, then unwinds the stack, then falls through to the
    // system (exit). Handled explicitly so back never strands the player.
    BackHandler(enabled = navigator.commandMenuOpen || navigator.canGoBack) {
        navigator.back()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (navigator.current) {
            Route.Map -> MapScreen(viewModel = viewModel)

            Route.Identity -> IdentityScreen(
                identity = state.identity,
                onBack = { navigator.back() },
                onRetry = { viewModel.resolveIdentity() }
            )

            Route.Settings -> SettingsScreen(
                locationGranted = state.locationPermissionGranted,
                trackingMode = trackingMode,
                onTrackingModeChange = { mode ->
                    trackingMode = mode
                    trackingPrefs.mode = mode
                    viewModel.onTrackingModeChanged(mode)
                },
                onOpenSystemSettings = { context.openAppSettings() },
                onBack = { navigator.back() }
            )

            Route.Safety -> SafetyScreen(onBack = { navigator.back() })

            Route.About -> AboutScreen(onBack = { navigator.back() })
        }

        // The command button belongs to the map only. On secondary screens the
        // BACK control is the navigation, and two competing affordances would be
        // one too many.
        AnimatedVisibility(
            visible = navigator.current == Route.Map && !navigator.commandMenuOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(end = 20.dp, bottom = 36.dp)
        ) {
            CommandButton(onClick = { navigator.openCommandMenu() })
        }

        CommandMenu(
            visible = navigator.commandMenuOpen,
            current = navigator.current,
            onSelect = { navigator.navigateTo(it) },
            onDismiss = { navigator.closeCommandMenu() }
        )
    }
}

/** Deep-links into this app's system settings page so a denied permission is recoverable. */
private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}