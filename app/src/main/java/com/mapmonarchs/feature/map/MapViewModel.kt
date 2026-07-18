package com.mapmonarchs.feature.map

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapmonarchs.domain.model.LocationFix
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

class MapViewModel(
    private val locationTracker: LocationTracker,
    private val validator: MovementValidator,
    @Suppress("unused") private val repository: TerritoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MapUiState(
            handle = "monarch",
            territoryColor = Color(0xFF4C8DFF),
            hexesHeld = 0,
            hexesHeldDelta = 0,
            captureAvailability = CaptureAvailability.UNAVAILABLE
        )
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    /** Last fix that passed local plausibility -- the baseline for teleport checks. */
    private var lastAcceptedFix: LocationFix? = null
    private var trackingJob: Job? = null

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(locationPermissionGranted = granted)
        if (granted) startTracking() else stopTracking()
    }

    private fun startTracking() {
        if (trackingJob?.isActive == true) return
        trackingJob = viewModelScope.launch {
            locationTracker.fixes()
                .catch { /* Location can fail at any time; never crash the map. */ }
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
                _uiState.value = _uiState.value.copy(
                    hasFix = true,
                    lastRejection = null,
                    captureAvailability = CaptureAvailability.AVAILABLE
                )
            }
            is MovementVerdict.Implausible -> {
                // Deliberately does NOT update lastAcceptedFix: a rejected fix must
                // not become the baseline, or a spoofer could walk the baseline
                // forward one "just barely too fast" step at a time.
                _uiState.value = _uiState.value.copy(
                    hasFix = true,
                    lastRejection = verdict.reason,
                    captureAvailability = CaptureAvailability.UNAVAILABLE
                )
            }
        }
    }

    /**
     * Not wired to the server yet. attempt_capture() exists only as SQL that has
     * not been applied, so firing this would fail against a live project. It is
     * left inert on purpose rather than half-wired -- see N4.
     */
    fun onCaptureRequested() = Unit

    override fun onCleared() {
        stopTracking()
        super.onCleared()
    }
}