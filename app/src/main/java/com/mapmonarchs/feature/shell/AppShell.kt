package com.mapmonarchs.feature.shell
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.feature.auth.SignInScreen
import com.mapmonarchs.feature.auth.SignInState
import com.mapmonarchs.feature.diagnostics.DiagnosticsScreen
import com.mapmonarchs.feature.help.HelpScreen
import com.mapmonarchs.feature.identity.IdentityScreen
import com.mapmonarchs.feature.locked.LockedScreen
import com.mapmonarchs.feature.map.MapScreen
import com.mapmonarchs.feature.map.resolveBlocker
import com.mapmonarchs.feature.map.MapViewModel
import com.mapmonarchs.feature.map.MapViewModelFactory
import com.mapmonarchs.feature.onboarding.OnboardingScreen
import com.mapmonarchs.feature.safety.SafetyScreen
import com.mapmonarchs.ui.motion.SCREEN_TRANSITION_MS
import com.mapmonarchs.ui.motion.rememberAnimationsEnabled
import com.mapmonarchs.feature.settings.AboutScreen
import com.mapmonarchs.feature.settings.SettingsScreen

/**
 * The application shell.
 *
 * Composition rules this enforces:
 *  - The map is always the root and is never popped.
 *  - Navigation IS persistent chrome: a floating dock over the map. This reverses
 *    an earlier rule that said navigation must be a launcher so the map kept the
 *    full frame. The launcher won on pixels and lost on comprehension -- one
 *    unlabelled hex button in a corner meant a new player saw no evidence the
 *    game contained territory, missions or an empire at all. The dock floats and
 *    is inset on every side, so the map still runs underneath it rather than
 *    being cut off by it.
 *  - Secondary screens replace the map rather than floating over it. A
 *    half-transparent sheet over a live GL map is expensive and reads as clutter.
 *  - Locked routes are real destinations, not disabled rows. A tap that does
 *    nothing reads as a bug; a tap that explains the boundary reads as design.
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

    BackHandler(enabled = navigator.canGoBack) { navigator.back() }

    val animate = rememberAnimationsEnabled()

    // Location tracking follows the MAP, not the app. SETTINGS promises "location
    // is only read while the map is open"; before this the ViewModel outlived
    // MapScreen and kept the stream running behind Settings, Help and About.
    LaunchedEffect(navigator.current) {
        viewModel.onMapVisible(navigator.current == Route.Map)
    }

    // Location permission is re-read on every ON_RESUME, at the SHELL level so it
    // works from any screen. The map's own prompt handles the first ask; this
    // handles RECOVERY: a player who grants permission in system settings -- from
    // the SETTINGS deep link, from the map's LOCATION OFF button, from anywhere --
    // gets picked up the moment they return, and the SETTINGS permission row and
    // the capture button both refresh. Putting this on the map only would leave
    // the SETTINGS screen -- the very place we send them to grant it -- unable to
    // notice they came back having done so.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                viewModel.onPermissionResult(granted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // A CROSSFADE, and nothing more.
        //
        // Screens here are peers or drill-downs, not layers -- a secondary screen
        // REPLACES the map rather than sliding over it. Directional motion would
        // imply a spatial relationship the shell does not have, and sliding a live
        // GL map off-screen is expensive for a lie.
        //
        // Short on purpose: the player pressed a destination and wants to be there.
        // Anything long enough to notice as an animation is long enough to be in
        // the way on the twentieth use.
        //
        // COST WORTH NAMING: secondary screens replace the map, so the Mapbox
        // surface is torn down on navigation and rebuilt on return. That was
        // already true before this crossfade; the transition briefly overlaps the
        // two rather than introducing the cost.
        AnimatedContent(
            targetState = navigator.current,
            transitionSpec = {
                val spec = tween<Float>(if (animate) SCREEN_TRANSITION_MS else 0)
                fadeIn(spec) togetherWith fadeOut(spec)
            },
            label = "screen"
        ) { route ->
        val lock = route.lock

        when {
            lock != null -> LockedScreen(
                title = route.title,
                lock = lock,
                onBack = { navigator.back() },
                // Only when DIAGNOSTICS can actually explain this lock. Empire and Shop
                // are held back by a product decision no probe reports, so pointing them
                // at a backend status page is the wrong remedy -- see FeatureLock.
                onOpenDiagnostics = if (lock.diagnosticsExplainsThis) {
                    { navigator.navigateTo(Route.Diagnostics) }
                } else null,
                // Preview is offered only from TERRITORY: it demonstrates the
                // claim loop, and nothing else. Offering it from SHOP or EMPIRE
                // would imply it simulates those too.
                onEnterPreview = if (route == Route.Territory && !state.previewMode) {
                    {
                        viewModel.enterPreview()
                        navigator.navigateTo(Route.Map)
                    }
                } else null
            )

            route == Route.Map -> MapScreen(
                viewModel = viewModel,
                // A blocked capture routes to the REMEDY, not just an explanation.
                // If the blocker is a missing session, the useful destination is
                // sign-in; sending the player to a status page they cannot act on
                // would be technically informative and practically useless.
                onExplainBlocker = {
                    // Routes by the SAME blocker the button is labelled from, so
                    // a button reading LOCATION OFF can never open a sign-in form.
                    navigator.navigateTo(remedyRouteFor(resolveBlocker(state)))
                }
            )

            route == Route.Identity -> IdentityScreen(
                identity = state.identity,
                onBack = { navigator.back() },
                onRetry = { viewModel.resolveIdentity() },
                onSignIn = { navigator.navigateTo(Route.SignIn) },
                onSignOut = { viewModel.signOut() }
            )

            route == Route.SignIn -> {
                val signIn by viewModel.signIn.collectAsState()
                // Leaving the flow the moment a real session exists: a success
                // screen the player has to dismiss is a step that earns nothing.
                LaunchedEffect(signIn) {
                    if (signIn is SignInState.Complete) navigator.back()
                }
                SignInScreen(
                    state = signIn,
                    onEmailChanged = viewModel::onEmailChanged,
                    onCodeChanged = viewModel::onCodeChanged,
                    onRequestCode = viewModel::requestSignInCode,
                    onVerifyCode = viewModel::verifySignInCode,
                    onResend = viewModel::resendSignInCode,
                    onChangeEmail = viewModel::restartSignIn,
                    onBack = { navigator.back() }
                )
            }

            route == Route.Diagnostics -> {
                val diagnostics by viewModel.diagnostics.collectAsState()
                DiagnosticsScreen(
                    state = state,
                    diagnostics = diagnostics,
                    trackingMode = trackingMode,
                    onBack = { navigator.back() },
                    onRefresh = {
                        viewModel.resolveIdentity()
                        viewModel.loadTerritory()
                        viewModel.probeCapabilities()
                    }
                )
            }

            route == Route.Settings -> SettingsScreen(
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

            route == Route.Help -> HelpScreen(
                state = state,
                onBack = { navigator.back() },
                onOpenDiagnostics = { navigator.navigateTo(Route.Diagnostics) },
                onOpenSettings = { navigator.navigateTo(Route.Settings) }
            )

            route == Route.Safety -> SafetyScreen(onBack = { navigator.back() })

            route == Route.About -> AboutScreen(onBack = { navigator.back() })

            route == Route.Command -> CommandCenterScreen(
                onOpen = { navigator.navigateTo(it) },
                onBack = { navigator.back() }
            )
        }

        }
        // The bar is hidden during SIGN IN only. That flow is one decision at a
        // time and a persistent navigation invites abandoning it half-finished,
        // which in an email-code flow means an unused code and a second send.
        // Everywhere else it stays, because a navigation that disappears is a
        // navigation the player has to re-find.
        // Reads navigator.current, NOT the crossfading route: the dock must track
        // where the player IS going, not what is still fading out behind it.
        AnimatedVisibility(
            visible = navigator.current != Route.SignIn,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 12.dp)
        ) {
            CommandBar(
                destinations = NAV_DESTINATIONS,
                currentRoute = navigator.current,
                onSelect = { navigator.navigateTo(it.route) }
            )
        }
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