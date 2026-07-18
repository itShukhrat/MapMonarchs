package com.mapmonarchs.feature.map

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State holder for the map screen.
 *
 * N1 scope: state is hoisted out of the composable and seeded with placeholder
 * values. There is no network, no Supabase, and no territory logic here yet --
 * those arrive in N2/N3, and capture only after the schema gate is cleared.
 */
class MapViewModel : ViewModel() {

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

    /**
     * Placeholder. The real implementation submits movement evidence to
     * attempt_capture() and renders whatever the server returns -- it never
     * decides ownership locally.
     */
    fun onCaptureRequested() = Unit
}