package com.mapmonarchs.feature.map

import androidx.compose.ui.graphics.Color
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.HexOwnership
import com.mapmonarchs.domain.validation.RejectionReason

/**
 * Territory data load. Independent of capture readiness -- the map can be loaded
 * while GPS is still acquiring, and vice versa. Collapsing both into one enum
 * would force the UI to claim one thing while the other is untrue.
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
    val handle: String,
    val territoryColor: Color,
    val territory: TerritoryLoad,
    val capture: CaptureState,
    val hexesHeldDelta: Int = 0
) {
    /**
     * null means UNKNOWN, and the UI must render it as such.
     *
     * The previous version hardcoded 0, which asserted "you own zero hexes"
     * before we had ever asked the server. A confident wrong number is worse
     * than an honest blank: in a game about ownership, showing 0 when territory
     * simply failed to load tells the player they lost everything.
     *
     * Still unknown even when loaded, because ownership must be filtered by the
     * signed-in user and there is no auth yet. It stays null until identity is real.
     */
    val hexesHeld: Int? = null

    val isTerritoryFailed: Boolean get() = territory is TerritoryLoad.Failed
    val territoryError: AppError? get() = (territory as? TerritoryLoad.Failed)?.error
}

/** Holdings readout. Never fabricates a number it does not have. */
fun MapUiState.holdingsLabel(): String = hexesHeld?.toString() ?: "--"

/**
 * The capture button always explains itself.
 *
 * A silent dead button in a game where movement earns territory reads as a
 * broken app, or as the game cheating the player. Naming the blocker -- even
 * telling a spoofer plainly that mock location fired -- makes the rules feel
 * enforced rather than arbitrary.
 */
fun MapUiState.captureLabel(): String = when (val c = capture) {
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

/** Only Ready is actionable. Success/failure are terminal readouts, not buttons. */
fun MapUiState.isCaptureActionable(): Boolean = capture is CaptureState.Ready