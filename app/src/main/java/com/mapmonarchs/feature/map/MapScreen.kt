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
import com.mapmonarchs.domain.identity.IdentityDerivation.TERRITORY_PALETTE
import com.mapmonarchs.ui.theme.Fog
import com.mapmonarchs.ui.theme.Sovereign
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.vectorSource
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

/** Layer and source ids, namespaced so a future style cannot collide with them. */
private const val BOUNDARY_SOURCE = "mm-country-boundaries"
private const val FOG_LAYER = "mm-outside-fog"
private const val BORDER_LAYER = "mm-uz-border"

/** The one playable country. ISO 3166-1 alpha-2, as published by Mapbox. */
private const val PLAYABLE_ISO = "UZ"

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
            // Country-level until a fix arrives, then flyTo street level.
            //
            // This used to open at zoom 15 -- a street in Tashkent that is not
            // the player's street, chosen because it was the only coordinate on
            // hand. The first thing a new player saw was an anonymous junction.
            // Opening on the whole playable world instead teaches the shape of
            // the board and the fact that it ends, in the one moment before we
            // know anything about where they are.
            zoom(5.4)
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
            // ---- The playable world ------------------------------------------
            //
            // Everything outside Uzbekistan is fogged and the border is drawn as a
            // lit edge. Not decoration: territory can only ever be claimed inside
            // the country, so the map has to say where the board stops. A player
            // who cannot see the edge will walk to it and find out by failing.
            //
            // THE DATA IS MAPBOX'S, NOT INVENTED. mapbox.country-boundaries-v1 is
            // a published vector tileset already licensed under the token this app
            // ships with. Verified against it before writing this: Tashkent and
            // Samarkand return iso_3166_1 = UZ, Almaty returns KZ, Ashgabat
            // returns TM. Hand-drawing a national border from memory would have
            // been fabricated geography.
            //
            // The fog fills every country that is NOT Uzbekistan rather than
            // masking a hole in a full-screen scrim, because Mapbox has no
            // punch-out. Uzbekistan is landlocked and entirely ringed by
            // neighbours, so filling not-UZ covers the whole approach to the
            // border. The technique would leave gaps for a coastal country; it
            // does not here.
            MapEffect(Unit) { mapView ->
                mapView.mapboxMap.getStyle { style ->
                    if (style.styleSourceExists(BOUNDARY_SOURCE)) return@getStyle

                    style.addSource(
                        vectorSource(BOUNDARY_SOURCE) {
                            url("mapbox://mapbox.country-boundaries-v1")
                        }
                    )

                    // Fog first so the border edge draws on top of it.
                    style.addLayer(
                        fillLayer(FOG_LAYER, BOUNDARY_SOURCE) {
                            sourceLayer("country_boundaries")
                            filter(
                                Expression.neq(
                                    Expression.get("iso_3166_1"),
                                    Expression.literal(PLAYABLE_ISO)
                                )
                            )
                            fillColor(Fog.toArgb())
                            // Not opaque. Outside must read as unreachable, not as
                            // missing -- the world visibly continues and simply
                            // cannot be played.
                            fillOpacity(0.82)
                        }
                    )

                    style.addLayer(
                        lineLayer(BORDER_LAYER, BOUNDARY_SOURCE) {
                            sourceLayer("country_boundaries")
                            filter(
                                Expression.eq(
                                    Expression.get("iso_3166_1"),
                                    Expression.literal(PLAYABLE_ISO)
                                )
                            )
                            lineColor(Sovereign.toArgb())
                            lineWidth(1.6)
                            lineOpacity(0.9)
                        }
                    )
                }
            }
            // Preview territory only. Real territory is drawn from server-owned
            // ownership once the schema is deployed; this draws from an in-memory
            // simulation and never from anything the server said.
            if (state.previewMode) {
                // Falls back to the palette, never to a literal. The literal here used to be
                // the UI accent, so preview territory was drawn in the same colour
                // as the buttons sitting on top of it.
                val fill = state.territoryColor() ?: Color(TERRITORY_PALETTE.first())
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