package com.mapmonarchs.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle

/**
 * Milestone 1: prove the map renders with our own dark style.
 *
 * MAPBOX_DARK is a stock Mapbox style for now. It gets replaced by a custom
 * Mapbox Studio style (desaturated terrain, muted labels) so that territory
 * colour is the only saturated thing on screen -- that is what makes the map
 * read as "ours" instead of "a Google Maps clone".
 */
private const val MAPBOX_DARK = "mapbox://styles/mapbox/dark-v11"

// Default camera until real location is wired in Milestone 2.
private val DEFAULT_CENTER: Point = Point.fromLngLat(69.2401, 41.2995)

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = rememberMapViewportState {
                setCameraOptions {
                    center(DEFAULT_CENTER)
                    zoom(14.0)
                    pitch(0.0)
                }
            },
            style = { MapStyle(style = MAPBOX_DARK) }
        )

        // Minimal overlay: confirms the Compose layer composites above the map.
        Text(
            text = "MAPMONARCHS",
            fontSize = 12.sp,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}