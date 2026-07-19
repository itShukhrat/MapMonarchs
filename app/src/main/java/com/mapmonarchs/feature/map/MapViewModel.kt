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
import com.mapmonarchs.domain.repository.TerritoryRepository
import com.mapmonarchs.domain.validation.MovementValidator
import com.mapmonarchs.domain.preview.PreviewClaim
import com.mapmonarchs.domain.preview.PreviewHexGrid
import com.mapmonarchs.domain.preview.PreviewSession
import com.mapmonarchs.domain.validation.MovementVerdict
import com.mapmonarchs.data.local.DeliveryProofStore
import com.mapmonarchs.data.local.TrackingMode
import com.mapmonarchs.domain.model.CapabilitiesProbe
import com.mapmonarchs.domain.model.DeliveryProof
import com.mapmonarchs.domain.model.redactEmail
import com.mapmonarchs.feature.diagnostics.DiagnosticsState
import com.mapmonarchs.location.LocationTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private const val TAG = "MapViewModel"

class MapViewModel(
    private val locationTracker: LocationTracker,
    private val validator: MovementValidator,
    private val repository: TerritoryRepository,
    private val authRepository: AuthRepository,
    private val deliveryProofStore: DeliveryProofStore
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
    private var trackingMode: TrackingMode = TrackingMode.BALANCED

    /**
     * Preview state. Null unless preview is active, and destroyed on exit so
     * simulated territory cannot outlive the mode that created it.
     */
    private var previewGrid: PreviewHexGrid? = null
    private var previewSession: PreviewSession? = null
    private var lastFix: LocationFix? = null

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
            val hint = redactEmail(current.email)
            when (val result = authRepository.requestEmailCode(current.email)) {
                is CodeRequest.Sent -> {
                    // REQUESTED, not DELIVERED. The backend accepted the send; no
                    // one has yet proven a code reached a human being.
                    recordDelivery(DeliveryProof.Requested(hint, System.currentTimeMillis()))
                    _signIn.value = SignInState.EnteringCode(email = current.email.trim())
                }
                is CodeRequest.Failed -> {
                    recordDelivery(
                        DeliveryProof.Failed(hint, result.error, System.currentTimeMillis())
                    )
                    _signIn.value = current.copy(sending = false, error = result.error)
                }
            }
        }
    }

    fun resendSignInCode() {
        val current = _signIn.value as? SignInState.EnteringCode ?: return
        viewModelScope.launch {
            val hint = redactEmail(current.email)
            when (val result = authRepository.requestEmailCode(current.email)) {
                is CodeRequest.Sent -> {
                    recordDelivery(DeliveryProof.Requested(hint, System.currentTimeMillis()))
                    _signIn.value = current.copy(resent = true, error = null)
                }
                is CodeRequest.Failed -> {
                    recordDelivery(
                        DeliveryProof.Failed(hint, result.error, System.currentTimeMillis())
                    )
                    _signIn.value = current.copy(error = result.error)
                }
            }
        }
    }

    fun verifySignInCode() {
        val current = _signIn.value as? SignInState.EnteringCode ?: return
        if (current.verifying) return
        viewModelScope.launch {
            _signIn.value = current.copy(verifying = true, error = null)
            when (val result = authRepository.verifyEmailCode(current.email, current.code)) {
                is CodeVerification.Succeeded -> {
                    // A correct code was typed back, so mail demonstrably arrived.
                    // This is the only transition that may claim delivery.
                    recordDelivery(
                        DeliveryProof.Delivered(
                            redactEmail(current.email), System.currentTimeMillis()
                        )
                    )
                    // Identity comes from the SERVER's user id, derived -- never
                    // from anything typed into this screen.
                    _uiState.value = _uiState.value.copy(identity = result.identity)
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

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            _uiState.value = _uiState.value.copy(
                capture = CaptureState.AcquiringFix,
                locationPermissionGranted = true
            )
            startTracking()
        } else {
            stopTracking()
            _uiState.value = _uiState.value.copy(
                capture = CaptureState.PermissionRequired,
                locationPermissionGranted = false
            )
        }
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

    override fun onCleared() {
        stopTracking()
        super.onCleared()
    }
}