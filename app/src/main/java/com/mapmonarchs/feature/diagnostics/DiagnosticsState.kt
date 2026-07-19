package com.mapmonarchs.feature.diagnostics

import com.mapmonarchs.domain.model.CapabilitiesProbe
import com.mapmonarchs.domain.model.DeliveryProof

/**
 * Facts diagnostics reports that are not map state.
 *
 * Kept separate from MapUiState because these change on a completely different
 * clock -- a backend configuration probe and a persisted delivery record have
 * nothing to do with a location fix, and folding them together would recompose
 * the map every time either one settled.
 */
data class DiagnosticsState(
    val capabilities: CapabilitiesProbe = CapabilitiesProbe.NotProbed,
    val delivery: DeliveryProof = DeliveryProof.NeverAttempted
)
