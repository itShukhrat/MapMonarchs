package com.mapmonarchs.feature.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
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

/**
 * The ViewModel is supplied by the shell so that Settings and Identity read the
 * same instance. Creating it here as well would give the map and the command
 * screens two disagreeing copies of session and permission state.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onExplainBlocker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    val viewportState = rememberMapViewportState {
        setCameraOptions {
            center(DEFAULT_CENTER)
            zoom(15.0)
            // Flat, top-down. This is a legibility decision, not a performance
            // one: in a territory game you must read hex adjacency and ownership
            // across an area, and pitch trades that away for cinematics.
            pitch(0.0)
        }
    }

    // Recentre once a real position is known. Keyed on the coordinates so it
    // follows the player rather than firing once and stranding the camera.
    LaunchedEffect(state.playerLat, state.playerLng) {
        val lat = state.playerLat
        val lng = state.playerLng
        if (lat != null && lng != null) {
            // flyTo, not setCameraOptions: on an already-attached map the latter
            // does not move an existing camera, so the player stayed looking at
            // the default city while their territory was drawn where they
            // actually stood.
            viewportState.flyTo(
                cameraOptions {
                    center(Point.fromLngLat(lng, lat))
                    zoom(16.0)
                },
                MapAnimationOptions.mapAnimationOptions { duration(1200L) }
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Map draws full-bleed, deliberately under the system bars. Only the HUD
        // is inset -- that is what makes the map read as the hero surface.
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            // The SAME viewport instance the recentre effect drives. Creating a
            // second one here left flyTo animating an orphaned object while the
            // map kept its own camera -- the player stayed looking at a default
            // city while their territory was drawn where they actually stood.
            mapViewportState = viewportState,
            // Stock map furniture removed: it is Mapbox's chrome, not our HUD.
            // Logo and attribution are retained -- licensing requirement.
            scaleBar = {},
            compass = {},
            style = { MapStyle(style = MAPBOX_DARK) }
        ) {
            // Preview territory only. Real territory is drawn from server-owned
            // ownership once the schema is deployed; this draws from an in-memory
            // simulation and never from anything the server said.
            if (state.previewMode) {
                val fill = state.territoryColor() ?: Color(0xFF4C8DFF)
                state.previewRings.forEach { ring ->
                    PolygonAnnotation(points = listOf(ring.map { Point.fromLngLat(it.lng, it.lat) })) {
                        fillColor = fill
                        fillOpacity = 0.45
                        fillOutlineColor = fill
                    }
                }
            }
        }

        MapHud(
            state = state,
            onCapture = viewModel::onCaptureRequested,
            onRetry = viewModel::onRetry,
            onExplainBlocker = onExplainBlocker,
            onExitPreview = viewModel::exitPreview
        )
    }
}