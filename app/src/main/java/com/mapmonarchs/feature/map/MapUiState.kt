package com.mapmonarchs.feature.map

import androidx.compose.ui.graphics.Color
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.HexOwnership
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.validation.RejectionReason

/** A drawable vertex. Deliberately not a Mapbox type -- state stays SDK-free. */
data class PreviewPoint(val lat: Double, val lng: Double)

/**
 * Territory data load. Independent of capture readiness and of identity -- the
 * map can be loaded while GPS is acquiring and while identity is still
 * resolving. Collapsing these axes would force the UI to claim one thing while
 * another is untrue.
 */
sealed interface TerritoryLoad {
    data object Loading : TerritoryLoad
    data class Loaded(val hexes: List<HexOwnership>) : TerritoryLoad
    data class Failed(val error: AppError) : TerritoryLoad
}

/** Where the capture action stands. Every state is nameable to the player. */
sealed interface CaptureState {
    data object PermissionRequired : CaptureState
    data object AcquiringFix : CaptureState
    data class Blocked(val reason: RejectionReason) : CaptureState
    data object Ready : CaptureState
    data object InFlight : CaptureState
    data class Succeeded(val action: String) : CaptureState
    data class Failed(val error: AppError) : CaptureState
}

data class MapUiState(
    val identity: Identity,
    val territory: TerritoryLoad,
    val capture: CaptureState,
    val hexesHeldDelta: Int = 0,
    /** Mirrors the OS permission so Settings can report it without re-querying. */
    val locationPermissionGranted: Boolean = false,
    /**
     * True only in preview. Drives the persistent marker. Kept as explicit state
     * rather than inferred from identity so a future identity change cannot
     * silently switch preview off while simulated territory is still on screen.
     */
    val previewMode: Boolean = false,
    /** Simulated holdings. Only ever meaningful in preview. */
    val previewHeld: Int? = null,
    /**
     * Last accepted position. The map centres here once known -- a territory game
     * whose camera sits on a hardcoded city shows the player someone else's
     * ground. Null until a plausible fix arrives; the camera does not guess.
     */
    val playerLat: Double? = null,
    val playerLng: Double? = null,
    /**
     * Preview territory as ready-to-draw rings.
     *
     * These live in STATE rather than being pulled from the ViewModel by the
     * composable. Reading a mutable collection through a plain function meant
     * Compose had nothing to observe, so claiming a hex updated the count but
     * never redrew the map. Derived render data belongs in the state that drives
     * the render.
     */
    val previewRings: List<List<PreviewPoint>> = emptyList()
) {
    /**
     * null means UNKNOWN and the UI must render it as such. Showing 0 before
     * asking the server tells the player they own nothing -- a lie when
     * territory merely failed to load. Stays null until ownership can be
     * filtered by a real authenticated user.
     */
    val hexesHeld: Int? = null

    val isTerritoryFailed: Boolean get() = territory is TerritoryLoad.Failed
    val territoryError: AppError? get() = (territory as? TerritoryLoad.Failed)?.error
}

/** Holdings readout. Never fabricates a number it does not have. */
fun MapUiState.holdingsLabel(): String = when {
    previewMode -> (previewHeld ?: 0).toString()
    else -> hexesHeld?.toString() ?: "--"
}

/**
 * Identity readout.
 *
 * There is no fallback name. If we are not signed in, the HUD says so; it does
 * not print something plausible. A fabricated handle is indistinguishable from a
 * real one, which is precisely why it is forbidden in a game about ownership.
 */
fun MapUiState.identityLabel(): String = when (val id = identity) {
    is Identity.SignedIn -> id.callsign
    is Identity.Preview -> id.callsign
    is Identity.Resolving -> "IDENTIFYING"
    is Identity.SignedOut -> "NO IDENTITY"
    is Identity.Unavailable -> "IDENTITY UNAVAILABLE"
}

/** True only when identity is real. Drives the glyph fill vs. outline. */

/**
 * Territory colour, or null when there is no identity to derive one from.
 * The HUD renders null as a hollow glyph -- an unclaimed shape, which is
 * literally true of a player who owns nothing yet.
 */
fun MapUiState.territoryColor(): Color? = when (val id = identity) {
    is Identity.SignedIn -> Color(id.territoryColorArgb)
    is Identity.Preview -> Color(id.territoryColorArgb)
    else -> null
}

/** Identity failure cause, if any -- shown in the failure strip. */
fun MapUiState.identityError(): AppError? = (identity as? Identity.Unavailable)?.error

/**
 * The capture button always explains itself. A silent dead button in a game
 * where movement earns territory reads as a broken app, or as the game cheating
 * the player. Identity outranks GPS: without a session the server would reject
 * every capture with not_authenticated, so saying "CLAIM HEX" would be a lie.
 */
fun MapUiState.captureLabel(): String {
    // Preview simulates the loop locally, so the action is available -- but it is
    // labelled differently so it can never read as a real claim.
    if (previewMode) {
        return when (capture) {
            is CaptureState.PermissionRequired -> "LOCATION OFF"
            is CaptureState.AcquiringFix -> "ACQUIRING GPS"
            else -> "CLAIM (PREVIEW)"
        }
    }
    if (identity !is Identity.SignedIn) {
        return when (identity) {
            is Identity.Resolving -> "IDENTIFYING"
            else -> "NO IDENTITY"
        }
    }
    return when (val c = capture) {
        is CaptureState.PermissionRequired -> "LOCATION OFF"
        is CaptureState.AcquiringFix -> "ACQUIRING GPS"
        is CaptureState.Ready -> "CLAIM HEX"
        is CaptureState.InFlight -> "CLAIMING"
        is CaptureState.Succeeded -> "CLAIMED"
        is CaptureState.Failed -> "CAPTURE FAILED"
        is CaptureState.Blocked -> when (c.reason) {
            RejectionReason.MOCK_LOCATION -> "MOCK LOCATION"
            RejectionReason.LOW_ACCURACY -> "GPS TOO WEAK"
            RejectionReason.TOO_FAST -> "MOVING TOO FAST"
            RejectionReason.TELEPORT -> "SIGNAL JUMPED"
        }
    }
}

/** Actionable only with a real identity AND a ready capture state. */
fun MapUiState.isCaptureActionable(): Boolean = when {
    previewMode -> capture is CaptureState.Ready || capture is CaptureState.Blocked
    else -> identity is Identity.SignedIn && capture is CaptureState.Ready
}

/**
 * Preview never routes a claim to the server. This is the guard the rest of the
 * app checks before anything could be submitted -- it is false in preview by
 * construction, not by convention.
 */
fun MapUiState.canSubmitRealClaim(): Boolean =
    !previewMode && identity is Identity.SignedIn && capture is CaptureState.Ready

/**
 * Why a real claim was refused. Logged, never shown raw -- the player gets the
 * capture button label instead. Exists so a refusal is always explainable rather
 * than a silent return.
 */
fun MapUiState.claimRefusalReason(): String = when {
    previewMode -> "preview mode is active"
    identity !is Identity.SignedIn -> "no authenticated session"
    capture !is CaptureState.Ready -> "position not accepted (${capture::class.simpleName})"
    else -> "unknown"
}