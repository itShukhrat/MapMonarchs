package com.mapmonarchs.feature.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.model.LocationFix
import com.mapmonarchs.domain.repository.AuthRepository
import com.mapmonarchs.domain.repository.Outcome
import com.mapmonarchs.domain.repository.TerritoryRepository
import com.mapmonarchs.domain.validation.MovementValidator
import com.mapmonarchs.domain.validation.MovementVerdict
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
    private val authRepository: AuthRepository
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

    /** Last fix that passed local plausibility -- the baseline for teleport checks. */
    private var lastAcceptedFix: LocationFix? = null
    private var trackingJob: Job? = null

    init {
        resolveIdentity()
        loadTerritory()
    }

    fun resolveIdentity() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(identity = Identity.Resolving)
            val identity = authRepository.ensureSession()
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

    /** Retry both axes -- the failure strip is a single control. */
    fun onRetry() {
        if (_uiState.value.identity !is Identity.SignedIn) resolveIdentity()
        loadTerritory()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            _uiState.value = _uiState.value.copy(capture = CaptureState.AcquiringFix)
            startTracking()
        } else {
            stopTracking()
            _uiState.value = _uiState.value.copy(capture = CaptureState.PermissionRequired)
        }
    }

    private fun startTracking() {
        if (trackingJob?.isActive == true) return
        trackingJob = viewModelScope.launch {
            locationTracker.fixes()
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
        when (val verdict = validator.validate(fix, lastAcceptedFix)) {
            is MovementVerdict.Plausible -> {
                lastAcceptedFix = fix
                _uiState.value = _uiState.value.copy(capture = CaptureState.Ready)
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
     * GATED (hard rule 5). attempt_capture() is not applied to the database, so
     * wiring this would fire a request that cannot succeed and produce a
     * misleading failure. Explicitly inert with a log, not silently dead.
     */
    fun onCaptureRequested() {
        Log.i(TAG, "capture requested but gated: schema.sql not yet applied")
    }

    override fun onCleared() {
        stopTracking()
        super.onCleared()
    }
}