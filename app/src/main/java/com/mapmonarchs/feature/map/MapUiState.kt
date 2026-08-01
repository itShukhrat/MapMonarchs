package com.mapmonarchs.feature.map

import androidx.compose.ui.graphics.Color
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.CaptureOutcome
import com.mapmonarchs.domain.model.Blocker
import com.mapmonarchs.domain.model.HexOwnership
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.model.WorldPosition
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
     * Whether the last accepted fix is inside the playable world. Defaults to
     * NotChecked rather than Inside -- assuming a player is in bounds until told
     * otherwise would show a claim button to someone standing in Kazakhstan.
     */
    val worldPosition: WorldPosition = WorldPosition.NotChecked,
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
    val isTerritoryFailed: Boolean get() = territory is TerritoryLoad.Failed
    val territoryError: AppError? get() = (territory as? TerritoryLoad.Failed)?.error
}

/**
 * Resolves the one reason the player cannot act. THE ordering, not an ordering:
 * every surface renders [Blocker], none re-derives it. See [Blocker] for the two
 * bugs that duplication caused.
 *
 * PRECEDENCE, and a reversal of an earlier decision: position outranks identity.
 * An older comment on the capture label argued the opposite -- that identity
 * wins because the server rejects unauthenticated captures regardless of GPS.
 * That is true and still the wrong thing to say first. Permission is one tap and
 * the map is visibly broken without it; signing in is an email round-trip. And
 * in preview, position still matters while identity does not, so identity-first
 * ordering cannot even describe preview correctly.
 */
fun resolveBlocker(state: MapUiState): Blocker = when {
    !state.locationPermissionGranted -> Blocker.LocationPermissionMissing

    state.capture is CaptureState.Blocked ->
        Blocker.FixRejected((state.capture as CaptureState.Blocked).reason)

    state.capture is CaptureState.AcquiringFix -> Blocker.AcquiringFix

    // Past this point the position is usable. Identity only gates REAL claims,
    // so preview -- which never leaves the device -- is genuinely unblocked.
    // Preview is checked BEFORE the world boundary because a simulation that
    // never touches the server is legitimate anywhere on earth.
    state.previewMode -> Blocker.None

    // Geography outranks identity: signing in does not move you into the country.
    // A player in Kazakhstan who is also signed out has two problems and only one
    // worth naming -- routing them to an email form would repeat the wrong-remedy
    // bug that once made a LOCATION OFF button open a sign-in screen.
    state.worldPosition is WorldPosition.Outside ->
        Blocker.OutsidePlayableWorld((state.worldPosition as WorldPosition.Outside).country)

    state.identity is Identity.Resolving -> Blocker.IdentityResolving

    state.identity is Identity.Unavailable ->
        Blocker.IdentityUnavailable((state.identity as Identity.Unavailable).error)

    state.identity !is Identity.SignedIn -> Blocker.NotSignedIn

    // Territory load state is deliberately absent: reading ownership and writing
    // a capture are independent axes. A stale map does not stop the server
    // arbitrating a claim, and pretending otherwise would collapse two axes this
    // state model exists to keep apart.

    // Terminal: a signed-in player, on an accepted fix, inside the playable
    // world, not in preview, is genuinely unblocked. The capture backend is
    // deployed and server-authoritative, so the claim button is live. Preview
    // reaches None earlier; this is the real one.
    else -> Blocker.None
}

/**
 * Holdings readout.
 *
 * Outside preview this is ALWAYS unknown, and says so. hex_ownership IS live on
 * the server now, but the map's territory snapshot is a bounded RENDER load, not
 * an authoritative per-player tally -- deriving a holdings count from it could
 * undercount a player whose hexes fall outside the loaded set, and a wrong number
 * is worse than an honest unknown. A real count needs its own
 * `count(*) where owner_id = me` query, which does not exist yet. Until it does,
 * the em dash is the truthful answer.
 */
fun MapUiState.holdingsLabel(): String =
    if (previewMode) (previewHeld ?: 0).toString() else UNKNOWN_HOLDINGS

/**
 * One em dash, not two hyphens.
 *
 * At displayLarge the type scale applies -1.5sp tracking, which pulled "--"
 * apart into two detached strokes that read as a font-loading failure rather
 * than as "unknown". A single glyph cannot come apart.
 */
const val UNKNOWN_HOLDINGS = "—"

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
 * the player.
 *
 * In-flight and settled capture states are progress, not blockers, so they are
 * reported before the blocker is consulted. Everything else is [Blocker]'s
 * answer, verbatim -- this function no longer holds an opinion of its own.
 */
fun MapUiState.captureLabel(): String {
    when (val c = capture) {
        is CaptureState.InFlight -> return "CLAIMING"
        is CaptureState.Succeeded -> return "CLAIMED"
        is CaptureState.Failed -> return "CAPTURE FAILED"
        else -> Unit
    }
    val blocker = resolveBlocker(this)
    if (blocker != Blocker.None) return blocker.hudLabel()
    // None is reachable only in preview, and is labelled so it can never read as
    // a real claim.
    return if (previewMode) "CLAIM (PREVIEW)" else "CLAIM HEX"
}

/** Actionable only when nothing blocks. Blocked buttons stay tappable, but route. */
fun MapUiState.isCaptureActionable(): Boolean = resolveBlocker(this) == Blocker.None

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

/**
 * Server capture outcome -> client capture state. Pure, so the mapping (the one
 * genuinely fiddly part of the capture wiring) is unit-tested on its own without
 * a ViewModel or fakes.
 *
 * The server reason strings are the lower-case of the RejectionReason names by
 * construction (mock_location -> MOCK_LOCATION), so the four movement rejections
 * map straight across. A reason that does NOT map -- not_authenticated, no_cell --
 * is a session or server fault, not a movement verdict, so it surfaces as Failed
 * rather than being forced into a RejectionReason it is not.
 */
fun CaptureOutcome.toCaptureState(): CaptureState = when (this) {
    is CaptureOutcome.Applied -> CaptureState.Succeeded(action)
    is CaptureOutcome.Rejected -> {
        val mapped = runCatching { RejectionReason.valueOf(reason.uppercase()) }.getOrNull()
        if (mapped != null) CaptureState.Blocked(mapped)
        else CaptureState.Failed(AppError.BackendFailure("capture rejected: $reason"))
    }
    is CaptureOutcome.Failed -> CaptureState.Failed(AppError.BackendFailure(message))
}
