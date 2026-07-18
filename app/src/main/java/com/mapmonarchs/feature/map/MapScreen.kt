package com.mapmonarchs.feature.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle

/**
 * MAPBOX_DARK is stock for now. It gets replaced by a custom Mapbox Studio style
 * (labels stripped to arterial roads, terrain flattened to near-monochrome) once
 * hexes render -- you cannot tune "territory is the only saturated thing on
 * screen" without territory on screen, so styling now would mean styling twice.
 */
private const val MAPBOX_DARK = "mapbox://styles/mapbox/dark-v11"

// Default camera until a real fix arrives.
private val DEFAULT_CENTER: Point = Point.fromLngLat(69.2401, 41.2995)

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: MapViewModel = viewModel(factory = MapViewModelFactory(context))
    val state by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            viewModel.onPermissionResult(true)
        } else {
            // Asked immediately because the map is useless without it. A priming
            // screen explaining WHY belongs here before release -- permission
            // denial rate is a top-of-funnel killer in location games.
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Map draws full-bleed, deliberately under the system bars. Only the HUD
        // is inset -- that is what makes the map read as the hero surface.
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = rememberMapViewportState {
                setCameraOptions {
                    center(DEFAULT_CENTER)
                    zoom(15.0)
                    // Flat, top-down. This is a legibility decision, not a
                    // performance one: in a territory game you must read hex
                    // adjacency and ownership across an area, and pitch trades
                    // that away for cinematics. Revisit only with a real reason.
                    pitch(0.0)
                }
            },
            // Stock map furniture removed: it is Mapbox's chrome, not our HUD.
            // Logo and attribution are retained -- licensing requirement.
            scaleBar = {},
            compass = {},
            style = { MapStyle(style = MAPBOX_DARK) }
        )

        MapHud(
            state = state,
            onCapture = viewModel::onCaptureRequested,
            onRetry = viewModel::loadTerritory
        )
    }
}