package com.mapmonarchs.feature.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.model.LocationFix
import com.mapmonarchs.domain.repository.AuthRepository
import com.mapmonarchs.domain.repository.CodeRequest
import com.mapmonarchs.domain.repository.CodeVerification
import com.mapmonarchs.domain.repository.Outcome
import com.mapmonarchs.feature.auth.SignInState
import com.mapmonarchs.domain.model.WorldPosition
import com.mapmonarchs.domain.repository.TerritoryRepository
import com.mapmonarchs.domain.repository.WorldBoundaryRepository
import com.mapmonarchs.domain.validation.MovementValidator
import com.mapmonarchs.domain.preview.PreviewClaim
import com.mapmonarchs.domain.preview.PreviewHexGrid
import com.mapmonarchs.domain.preview.PreviewSession
import com.mapmonarchs.domain.validation.MovementVerdict
import com.mapmonarchs.data.local.DeliveryProofStore
import com.mapmonarchs.data.local.TrackingMode
import com.mapmonarchs.domain.model.CapabilitiesProbe
import com.mapmonarchs.domain.model.DeliveryProof
import com.mapmonarchs.domain.model.deliveryProofFor
import com.mapmonarchs.feature.diagnostics.DiagnosticsState
import com.mapmonarchs.location.LocationTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "MapViewModel"

/** Re-ask the boundary service only after the player has moved this far. */
private const val CHECK_AFTER_METRES = 500.0

class MapViewModel(
    private val locationTracker: LocationTracker,
    private val validator: MovementValidator,
    private val repository: TerritoryRepository,
    private val authRepository: AuthRepository,
    private val deliveryProofStore: DeliveryProofStore,
    private val worldBoundaryRepository: WorldBoundaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MapUiState(
            // No fabricated handle, no fixed colour. We start by admitting we do
            // not yet know who this is.
            identity = Identity.Resolving,
            territory = TerritoryLoad.Loading,
            capture = CaptureState.PermissionRequired
        )
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _signIn = MutableStateFlow<SignInState>(SignInState.EnteringEmail())
    val signIn: StateFlow<SignInState> = _signIn.asStateFlow()

    private val _diagnostics = MutableStateFlow(DiagnosticsState())
    val diagnostics: StateFlow<DiagnosticsState> = _diagnostics.asStateFlow()

    /** Last fix that passed local plausibility -- the baseline for teleport checks. */
    private var lastAcceptedFix: LocationFix? = null
    private var trackingJob: Job? = null

    /** True only while MAP is the visible screen. Half of the tracking condition. */
    private var mapVisible: Boolean = false
    private var trackingMode: TrackingMode = TrackingMode.BALANCED

    /**
     * Preview state. Null unless preview is active, and destroyed on exit so
     * simulated territory cannot outlive the mode that created it.
     */
    private var previewGrid: PreviewHexGrid? = null
    private var previewSession: PreviewSession? = null
    private var lastFix: LocationFix? = null

    /** Last position a boundary lookup was made for. Drives the distance throttle. */
    private var lastBoundaryCheck: LocationFix? = null

    init {
        resolveIdentity()
        loadTerritory()
        // Read first: the persisted delivery record is what this install already
        // knows, and it must be visible even if the network probe never answers.
        _diagnostics.value = _diagnostics.value.copy(delivery = deliveryProofStore.read())
        probeCapabilities()
    }

    /** Asks the backend what it permits, rather than assuming. */
    fun probeCapabilities() {
        viewModelScope.launch {
            _diagnostics.value = _diagnostics.value.copy(capabilities = CapabilitiesProbe.Probing)
            val probe = authRepository.probeCapabilities()
            when (probe) {
                is CapabilitiesProbe.Observed -> Log.i(
                    TAG,
                    "capabilities: email=${probe.capabilities.emailEnabled} " +
                        "anonymous=${probe.capabilities.anonymousEnabled} " +
                        "autoconfirm=${probe.capabilities.mailerAutoconfirm}"
                )
                is CapabilitiesProbe.Unavailable ->
                    Log.w(TAG, "capabilities unknown: ${probe.error.technical}")
                else -> Unit
            }
            _diagnostics.value = _diagnostics.value.copy(capabilities = probe)
        }
    }

    private fun recordDelivery(proof: DeliveryProof) {
        deliveryProofStore.write(proof)
        _diagnostics.value = _diagnostics.value.copy(delivery = proof)
    }

    fun resolveIdentity() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(identity = Identity.Resolving)
            val identity = authRepository.restoreSession()
            when (identity) {
                is Identity.SignedIn -> Log.i(TAG, "identity resolved: ${identity.callsign}")
                is Identity.Unavailable -> Log.w(TAG, "identity unavailable: ${identity.error.technical}")
                else -> Log.i(TAG, "identity state: $identity")
            }
            _uiState.value = _uiState.value.copy(identity = identity)
        }
    }

    /**
     * Runs on every launch. Against the current database this FAILS with
     * SchemaMissing, and that failure is rendered -- the read path and its error
     * surface are exercised for real rather than mocked.
     */
    fun loadTerritory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(territory = TerritoryLoad.Loading)
            when (val result = repository.territorySnapshot()) {
                is Outcome.Success ->
                    _uiState.value = _uiState.value.copy(territory = TerritoryLoad.Loaded(result.value))
                is Outcome.Failure ->
                    _uiState.value = _uiState.value.copy(territory = TerritoryLoad.Failed(result.error))
            }
        }
    }

    // --- email sign-in -------------------------------------------------------

    fun onEmailChanged(value: String) {
        val current = _signIn.value
        if (current is SignInState.EnteringEmail) {
            _signIn.value = current.copy(email = value, error = null)
        }
    }

    fun onCodeChanged(value: String) {
        val current = _signIn.value
        if (current is SignInState.EnteringCode) {
            _signIn.value = current.copy(code = value.filter { it.isDigit() }.take(6), error = null)
        }
    }

    fun requestSignInCode() {
        val current = _signIn.value as? SignInState.EnteringEmail ?: return
        if (current.sending) return
        viewModelScope.launch {
            _signIn.value = current.copy(sending = true, error = null)
            val result = authRepository.requestEmailCode(current.email)
            // One place decides what a result means for delivery. This call site
            // cannot accidentally claim a 200 was an arrived email.
            recordDelivery(deliveryProofFor(result, current.email, System.currentTimeMillis()))
            _signIn.value = when (result) {
                is CodeRequest.Sent -> SignInState.EnteringCode(email = current.email.trim())
                is CodeRequest.Failed -> current.copy(sending = false, error = result.error)
            }
        }
    }

    fun resendSignInCode() {
        val current = _signIn.value as? SignInState.EnteringCode ?: return
        viewModelScope.launch {
            val result = authRepository.requestEmailCode(current.email)
            recordDelivery(deliveryProofFor(result, current.email, System.currentTimeMillis()))
            _signIn.value = when (result) {
                is CodeRequest.Sent -> current.copy(resent = true, error = null)
                is CodeRequest.Failed -> current.copy(error = result.error)
            }
        }
    }

    fun verifySignInCode() {
        val current = _signIn.value as? SignInState.EnteringCode ?: return
        if (current.verifying) return
        viewModelScope.launch {
            _signIn.value = current.copy(verifying = true, error = null)
            val result = authRepository.verifyEmailCode(current.email, current.code)
            // A verified code is the only proof that mail arrived. A rejected one
            // is ambiguous -- most often a typo -- so the mapping returns null and
            // the prior record stands rather than being overwritten with a guess.
            deliveryProofFor(result, current.email, System.currentTimeMillis())
                ?.let { recordDelivery(it) }
            when (result) {
                is CodeVerification.Succeeded -> {
                    // A real session supersedes any preview. Signing in from
                    // preview mode (the Identity screen offers exactly that) must
                    // not leave the simulation running underneath a real identity --
                    // that is the preview/live confusion the whole design forbids.
                    previewSession?.clear()
                    previewSession = null
                    previewGrid = null
                    // Identity comes from the SERVER's user id, derived -- never
                    // from anything typed into this screen.
                    _uiState.value = _uiState.value.copy(
                        identity = result.identity,
                        previewMode = false,
                        previewHeld = null,
                        previewRings = emptyList()
                    )
                    _signIn.value = SignInState.Complete
                    Log.i(TAG, "signed in as ${result.identity.callsign}")
                }
                is CodeVerification.Failed ->
                    _signIn.value = current.copy(verifying = false, error = result.error)
            }
        }
    }

    /** Back to the address step, e.g. wrong address entered. */
    fun restartSignIn() {
        _signIn.value = SignInState.EnteringEmail()
    }

    fun signOut() {
        viewModelScope.launch {
            val error = authRepository.signOut()
            if (error != null) Log.w(TAG, "sign-out failed: ${error.technical}")
            _signIn.value = SignInState.EnteringEmail()
            _uiState.value = _uiState.value.copy(
                identity = if (error != null) Identity.Unavailable(error) else Identity.SignedOut
            )
        }
    }

    /** Retry both axes -- the failure strip is a single control. */
    fun onRetry() {
        if (_uiState.value.identity !is Identity.SignedIn) resolveIdentity()
        loadTerritory()
        probeCapabilities()
    }

    /**
     * The OS permission state, reported from wherever it was observed -- the map's
     * first-launch prompt, or AppShell re-reading it on resume after the player
     * granted it in system settings from ANY screen.
     *
     * Idempotent, so it is safe on every resume. It updates the flag and lets
     * syncTracking decide whether the stream should run, so reporting a grant
     * while on SETTINGS refreshes the permission row without starting tracking
     * behind it.
     */
    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(
            locationPermissionGranted = granted,
            capture = when {
                !granted -> CaptureState.PermissionRequired
                // Do not knock an already-accepted fix back to AcquiringFix.
                _uiState.value.capture is CaptureState.PermissionRequired -> CaptureState.AcquiringFix
                else -> _uiState.value.capture
            }
        )
        syncTracking()
    }


    /**
     * Tracking follows the MAP, not the app.
     *
     * SETTINGS states "location is only read while the map is open", and until
     * now that was simply untrue: MapViewModel is scoped to the shell rather than
     * to MapScreen, so navigating to Settings, Help or About left the location
     * stream running behind them. A privacy claim the code contradicts is worse
     * than no claim at all, so the code changed rather than the sentence.
     *
     * It is also correct on its own merits. This is a walking game read with the
     * screen on; collecting fixes while somebody reads the About page spends
     * battery to learn nothing.
     */
    fun onMapVisible(visible: Boolean) {
        mapVisible = visible
        syncTracking()
    }

    /**
     * The single owner of whether the location stream runs. Both inputs --
     * permission and map visibility -- funnel through here, so the two callers
     * can never leave the stream in a state neither of them intended. It is what
     * lets AppShell report a permission grant from SETTINGS without that grant
     * starting tracking on a screen that is not the map.
     */
    private fun syncTracking() {
        if (mapVisible && _uiState.value.locationPermissionGranted) startTracking() else stopTracking()
    }
    /**
     * Applies a new cadence immediately by restarting the stream. Deferring it
     * until the next app launch would make the setting feel broken -- the player
     * changed it to fix a battery problem they are having right now.
     */
    fun onTrackingModeChanged(mode: TrackingMode) {
        if (mode == trackingMode) return
        trackingMode = mode
        Log.i(TAG, "tracking cadence -> ${mode.name} (${mode.intervalMillis}ms)")
        if (trackingJob?.isActive == true) {
            stopTracking()
            startTracking()
        }
    }

    private fun startTracking() {
        if (trackingJob?.isActive == true) return
        trackingJob = viewModelScope.launch {
            locationTracker.fixes(intervalMillis = trackingMode.intervalMillis)
                .catch { t ->
                    // NOT a silent catch: cause logged, failure surfaced as state.
                    // Losing location silently would leave "ACQUIRING GPS" forever
                    // with no explanation and no recovery.
                    Log.w(TAG, "location stream failed", t)
                    _uiState.value = _uiState.value.copy(
                        capture = CaptureState.Failed(
                            AppError.Unexpected("${t::class.simpleName}: ${t.message ?: "location stream failed"}")
                        )
                    )
                }
                .collect { fix -> onFix(fix) }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    private fun onFix(fix: LocationFix) {
        lastFix = fix
        // Preview anchors its grid to wherever the player actually is, so the
        // simulation is about their real surroundings rather than a fixed city.
        if (previewGrid == null && _uiState.value.previewMode) {
            previewGrid = PreviewHexGrid(fix.latitude, fix.longitude)
            previewSession = PreviewSession(previewGrid!!)
        }
        when (val verdict = validator.validate(fix, lastAcceptedFix)) {
            is MovementVerdict.Plausible -> {
                lastAcceptedFix = fix
                if (_uiState.value.playerLat == null) {
                    Log.i(TAG, "first accepted fix at ${fix.latitude}, ${fix.longitude}")
                }
                _uiState.value = _uiState.value.copy(
                    capture = CaptureState.Ready,
                    playerLat = fix.latitude,
                    playerLng = fix.longitude
                )
                checkWorldPosition(fix)
            }
            is MovementVerdict.Implausible -> {
                // Deliberately does NOT update lastAcceptedFix: a rejected fix must
                // not become the baseline, or a spoofer could walk it forward one
                // "just barely too fast" step at a time.
                _uiState.value = _uiState.value.copy(capture = CaptureState.Blocked(verdict.reason))
            }
        }
    }

    /**
     * In preview: simulates a claim locally.
     * Otherwise: GATED (hard rule 5) -- attempt_capture() is not deployed, so
     * firing it would produce a misleading failure. Explicitly inert with a log.
     *
     * The separation is enforced by canSubmitRealClaim(), which is checked below
     * rather than merely asserted in a comment. An earlier version documented
     * that guard without calling it, and placed a check() after an early return
     * where it could never execute -- a safety claim with nothing behind it.
     */
    fun onCaptureRequested() {
        val state = _uiState.value
        if (state.previewMode) {
            val fix = lastFix
            val session = previewSession
            if (fix == null || session == null) {
                Log.i(TAG, "preview claim ignored: no position yet")
                return
            }
            when (val claim = session.claimAt(fix.latitude, fix.longitude)) {
                is PreviewClaim.Captured ->
                    Log.i(TAG, "preview: captured ${claim.cell.id} (${claim.totalHeld} held)")
                is PreviewClaim.AlreadyHeld ->
                    Log.i(TAG, "preview: already holding ${claim.cell.id}")
            }
            _uiState.value = _uiState.value.copy(
                previewHeld = session.heldCount,
                previewRings = renderRings(session)
            )
            return
        }
        // The real gate. Reached only outside preview, and it must hold before
        // anything could ever be submitted. It is false in preview by
        // construction, so this is belt and braces -- deliberately, because the
        // cost of being wrong here is a client minting territory.
        if (!state.canSubmitRealClaim()) {
            Log.i(TAG, "real claim refused: ${state.claimRefusalReason()}")
            return
        }
        Log.i(TAG, "capture requested but gated: schema.sql not yet applied")
    }

    /**
     * Enters the local simulation.
     *
     * Identity becomes Identity.Preview -- a distinct sealed variant, never a
     * fabricated session. Territory load is left exactly as it is: preview does
     * not pretend the backend is reachable.
     */
    fun enterPreview() {
        Log.i(TAG, "entering preview mode (local simulation, no network)")
        val fix = lastFix
        if (fix != null) {
            previewGrid = PreviewHexGrid(fix.latitude, fix.longitude)
            previewSession = PreviewSession(previewGrid!!)
        }
        _uiState.value = _uiState.value.copy(
            previewMode = true,
            previewHeld = 0,
            identity = Identity.preview()
        )
    }

    /** Leaves preview and destroys everything it created. */
    fun exitPreview() {
        Log.i(TAG, "exiting preview mode; discarding simulated territory")
        previewSession?.clear()
        previewSession = null
        previewGrid = null
        _uiState.value = _uiState.value.copy(
            previewMode = false,
            previewHeld = null,
            previewRings = emptyList(),
            identity = Identity.Resolving
        )
        resolveIdentity()
    }

    /** Converts held cells into drawable rings. Closed by repeating the first vertex. */
    private fun renderRings(session: PreviewSession): List<List<PreviewPoint>> {
        val grid = previewGrid ?: return emptyList()
        return session.heldCells().map { cell ->
            val ring = grid.boundaryOf(cell).map { PreviewPoint(it.lat, it.lng) }
            ring + ring.first()
        }
    }


    /**
     * Where the player is relative to the playable world.
     *
     * Throttled by distance rather than by time. A walking player produces a fix
     * every few seconds and the answer only changes when they cross a border, so
     * re-asking on every fix would be hundreds of identical lookups per walk. It
     * re-checks after CHECK_AFTER_METRES, which near a border is a couple of
     * minutes on foot and everywhere else is free.
     *
     * ADVISORY ONLY. attempt_capture() must run its own containment check when it
     * exists; a client that lies about coordinates will lie about this too.
     */
    private fun checkWorldPosition(fix: LocationFix) {
        val last = lastBoundaryCheck
        if (last != null && metresBetween(last, fix) < CHECK_AFTER_METRES) return
        lastBoundaryCheck = fix

        viewModelScope.launch {
            val position = worldBoundaryRepository.positionOf(fix.latitude, fix.longitude)
            // Unknown never overwrites a known answer. A dropped call while walking
            // must not blank out a position we already established.
            if (position is WorldPosition.Unknown &&
                _uiState.value.worldPosition !is WorldPosition.NotChecked
            ) {
                Log.w(TAG, "boundary unknown, keeping previous: ${position.reason}")
                return@launch
            }
            _uiState.value = _uiState.value.copy(worldPosition = position)
        }
    }

    /** Haversine, metres. Only used to decide whether to re-ask. */
    private fun metresBetween(a: LocationFix, b: LocationFix): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
            sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }
    override fun onCleared() {
        stopTracking()
        super.onCleared()
    }
}
