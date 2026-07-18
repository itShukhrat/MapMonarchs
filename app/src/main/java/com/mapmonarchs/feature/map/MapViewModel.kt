package com.mapmonarchs.feature.map

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapmonarchs.domain.model.LocationFix
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
    private val repository: TerritoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MapUiState(
            handle = "monarch",
            territoryColor = Color(0xFF4C8DFF),
            territory = TerritoryLoad.Loading,
            capture = CaptureState.PermissionRequired
        )
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    /** Last fix that passed local plausibility -- the baseline for teleport checks. */
    private var lastAcceptedFix: LocationFix? = null
    private var trackingJob: Job? = null

    init {
        loadTerritory()
    }

    /**
     * Runs on every launch. Against the current database this FAILS with
     * SchemaMissing, and that failure is rendered -- which is exactly the point:
     * the read path and its error surface are exercised for real, not mocked.
     */
    fun loadTerritory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(territory = TerritoryLoad.Loading)
            when (val result = repository.territorySnapshot()) {
                is Outcome.Success ->
                    _uiState.value = _uiState.value.copy(
                        territory = TerritoryLoad.Loaded(result.value)
                    )
                is Outcome.Failure ->
                    _uiState.value = _uiState.value.copy(
                        territory = TerritoryLoad.Failed(result.error)
                    )
            }
        }
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
                    // NOT a silent catch: the cause is logged and the failure is
                    // turned into a state the player can see and act on. Losing
                    // location silently would leave "ACQUIRING GPS" forever with
                    // no explanation and no recovery.
                    Log.w(TAG, "location stream failed", t)
                    _uiState.value = _uiState.value.copy(
                        capture = CaptureState.Failed(
                            com.mapmonarchs.domain.model.AppError.Unexpected(
                                "${t::class.simpleName}: ${t.message ?: "location stream failed"}"
                            )
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
                // not become the baseline, or a spoofer could walk the baseline
                // forward one "just barely too fast" step at a time.
                _uiState.value = _uiState.value.copy(
                    capture = CaptureState.Blocked(verdict.reason)
                )
            }
        }
    }

    /**
     * GATED (hard rule 5). attempt_capture() is not applied to the database, so
     * wiring this would fire a request that cannot succeed and would produce a
     * misleading failure state. Left explicitly inert with a log, not silently
     * dead -- pressing it tells us it was pressed.
     */
    fun onCaptureRequested() {
        Log.i(TAG, "capture requested but gated: schema.sql not yet applied")
    }

    override fun onCleared() {
        stopTracking()
        super.onCleared()
    }
}