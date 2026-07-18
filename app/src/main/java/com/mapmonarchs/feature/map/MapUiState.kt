package com.mapmonarchs.feature.map

import androidx.compose.ui.graphics.Color
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.HexOwnership
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.validation.RejectionReason

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
    val hexesHeldDelta: Int = 0
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
fun MapUiState.holdingsLabel(): String = hexesHeld?.toString() ?: "--"

/**
 * Identity readout.
 *
 * There is no fallback name. If we are not signed in, the HUD says so; it does
 * not print something plausible. A fabricated handle is indistinguishable from a
 * real one, which is precisely why it is forbidden in a game about ownership.
 */
fun MapUiState.identityLabel(): String = when (val id = identity) {
    is Identity.SignedIn -> id.callsign
    is Identity.Resolving -> "IDENTIFYING"
    is Identity.SignedOut -> "NO IDENTITY"
    is Identity.Unavailable -> "IDENTITY UNAVAILABLE"
}

/** True only when identity is real. Drives the glyph fill vs. outline. */
fun MapUiState.hasRealIdentity(): Boolean = identity is Identity.SignedIn

/**
 * Territory colour, or null when there is no identity to derive one from.
 * The HUD renders null as a hollow glyph -- an unclaimed shape, which is
 * literally true of a player who owns nothing yet.
 */
fun MapUiState.territoryColor(): Color? =
    (identity as? Identity.SignedIn)?.let { Color(it.territoryColorArgb) }

/** Identity failure cause, if any -- shown in the failure strip. */
fun MapUiState.identityError(): AppError? = (identity as? Identity.Unavailable)?.error

/**
 * The capture button always explains itself. A silent dead button in a game
 * where movement earns territory reads as a broken app, or as the game cheating
 * the player. Identity outranks GPS: without a session the server would reject
 * every capture with not_authenticated, so saying "CLAIM HEX" would be a lie.
 */
fun MapUiState.captureLabel(): String {
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
fun MapUiState.isCaptureActionable(): Boolean =
    identity is Identity.SignedIn && capture is CaptureState.Ready