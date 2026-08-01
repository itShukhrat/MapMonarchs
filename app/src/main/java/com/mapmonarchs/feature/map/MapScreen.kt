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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.Void
import androidx.compose.ui.graphics.toArgb
import com.mapmonarchs.domain.identity.IdentityDerivation.TERRITORY_PALETTE
import com.mapmonarchs.ui.theme.Fog
import com.mapmonarchs.ui.theme.Sovereign
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapmonarchs.data.h3.H3Boundary
import com.mapmonarchs.domain.model.HexOwnership
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.ui.theme.Contested
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addPersistentLayer
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.vectorSource
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.style.MapStyle

/**
 * The MapMonarchs tactical basemap: a custom Mapbox Studio style derived from
 * dark-v11 and retuned to this app's palette -- land and water flattened to the
 * cool Void/Obsidian family. Place, road, and geography labels are kept as a
 * deliberate hierarchy rather than stripped: country > capital > regional city >
 * town > district, the full road-class network (muted, widening by importance)
 * with street names at street zoom, and named geography (rivers, seas, deserts).
 * Only POIs are held back -- a command board is not a navigation app. The
 * basemap carries no saturation of its own, so the lit Sovereign border, the
 * player puck, and territory stay the only coloured things on the board.
 *
 * Reversible by design: swap this back to "mapbox://styles/mapbox/dark-v11" and
 * the stock basemap returns. Every layer this file adds is style-independent --
 * it brings its own sources and appends to the top of whatever style loads -- so
 * nothing here depends on the custom style existing.
 */
private const val MAP_STYLE = "mapbox://styles/shukhra-t/cmryd7j1700nf01qt76jvf82q"

/** Layer and source ids, namespaced so a future style cannot collide with them. */
private const val BOUNDARY_SOURCE = "mm-country-boundaries"
private const val FOG_LAYER = "mm-outside-fog"
private const val BORDER_GLOW_LAYER = "mm-uz-border-glow"
private const val BORDER_LAYER = "mm-uz-border"
private const val PLAYER_SOURCE = "mm-player"
private const val PLAYER_HALO_LAYER = "mm-player-halo"
private const val PLAYER_CORE_LAYER = "mm-player-core"

// Real, server-owned territory. Two collections -- yours and everyone else's --
// so the board reads "mine vs not mine" rather than a rainbow of players the
// client cannot yet name.
private const val TERRITORY_MINE_SOURCE = "mm-territory-mine"
private const val TERRITORY_MINE_FILL = "mm-territory-mine-fill"
private const val TERRITORY_MINE_LINE = "mm-territory-mine-line"
private const val TERRITORY_OTHER_SOURCE = "mm-territory-other"
private const val TERRITORY_OTHER_FILL = "mm-territory-other-fill"
private const val TERRITORY_OTHER_LINE = "mm-territory-other-line"

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

    // MapScreen owns only the FIRST-LAUNCH PROMPT. Recovery -- re-reading the
    // permission after the player grants it in system settings -- lives in
    // AppShell's ON_RESUME handler, which is active on every screen, not just this
    // one. Prompting is the map's job because the map is where location is first
    // needed; recovery is the shell's job because the player might grant it while
    // looking at SETTINGS.
    //
    // Asked once: Android stops showing the dialog after two denials anyway, and
    // the recovery path from there is the system-settings deep link in SETTINGS,
    // not another dialog the OS will silently swallow.
    var hasPrompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted && !hasPrompted) {
            hasPrompted = true
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
            style = { MapStyle(style = MAP_STYLE) }
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
                            // SLOT MATTERS MORE THAN OPACITY HERE. dark-v11 is a
                            // slotted style: addLayer without a slot lands BENEATH
                            // the symbol layers, so the fog was darkening terrain
                            // while every place name punched straight through it.
                            // I first tried raising opacity 0.82 -> 0.93 and the
                            // screenshot was almost identical -- which is what
                            // proved the layer was underneath rather than weak.
                            slot("top")
                            sourceLayer("country_boundaries")
                            filter(
                                Expression.neq(
                                    Expression.get("iso_3166_1"),
                                    Expression.literal(PLAYABLE_ISO)
                                )
                            )
                            fillColor(Fog.toArgb())
                            // Not opaque, now that the slot puts this above labels.
                            // Outside must read as CONTINUING and unreachable rather
                            // than as missing: terrain and place names stay faintly
                            // present, so a player can still orient themselves, but
                            // nothing out there competes with the playable world.
                            fillOpacity(0.86)
                        }
                    )

                    // A WIDE, SOFT UNDERLAY beneath the crisp border line.
                    //
                    // One hairline reads as a cartographic boundary -- the kind
                    // every map has. Two lines, a blurred wide one under a sharp
                    // narrow one, read as an edge that is LIT: the board has a
                    // rim, and the rim is powered. That is the whole difference
                    // between "this map has a country outline on it" and "this is
                    // where the playable world stops".
                    //
                    // Still the one accent. Depth here comes from blur and width,
                    // not from introducing a second hue -- the same move the
                    // command surfaces make with glow instead of colour.
                    style.addLayer(
                        lineLayer(BORDER_GLOW_LAYER, BOUNDARY_SOURCE) {
                            slot("top")
                            sourceLayer("country_boundaries")
                            filter(
                                Expression.eq(
                                    Expression.get("iso_3166_1"),
                                    Expression.literal(PLAYABLE_ISO)
                                )
                            )
                            lineColor(Sovereign.toArgb())
                            lineWidth(7.0)
                            lineBlur(6.0)
                            lineOpacity(0.35)
                        }
                    )

                    style.addLayer(
                        lineLayer(BORDER_LAYER, BOUNDARY_SOURCE) {
                            // Same slot, added after the fog, so the edge stays lit
                            // on top of it rather than being dimmed by it.
                            slot("top")
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
            // Preview territory: an in-memory simulation, drawn ONLY in preview
            // and never from anything the server said. Real server-owned territory
            // is drawn from hex_ownership in the MapEffect just below this block.
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

            // ---- Real, server-owned territory --------------------------------
            //
            // Drawn ONLY outside preview, and ONLY from hex_ownership the server
            // recorded -- never invented. This is the line between preview (a
            // local simulation) and the live board. Territory is the most
            // saturated thing on screen: your ground in your palette colour,
            // rivals' ground in one muted hostile tone, both above the fog and
            // border and below the player puck (this MapEffect runs before the
            // puck's, so the puck's persistent layers land on top).
            //
            // H3-to-boundary runs for real on an ARM device; on an x86_64
            // emulator the h3 native lib cannot load, so H3Boundary returns
            // nothing and this draws an empty collection -- honest, not a crash.
            // Today the collection is empty everywhere because no capture has
            // landed yet; it fills the instant one does.
            MapEffect(state.territory, state.identity, state.previewMode) { mapView ->
                mapView.mapboxMap.getStyle { style ->
                    val hexes = if (state.previewMode) emptyList()
                        else (state.territory as? TerritoryLoad.Loaded)?.hexes ?: emptyList()
                    val myId = (state.identity as? Identity.SignedIn)?.userId
                    val (mine, others) = hexes.partition { myId != null && it.ownerId == myId }

                    val mineColor = (state.territoryColor() ?: Color(TERRITORY_PALETTE.first())).toArgb()
                    upsertTerritory(
                        style, TERRITORY_MINE_SOURCE, TERRITORY_MINE_FILL, TERRITORY_MINE_LINE,
                        territoryFeatures(mine),
                        fill = mineColor, line = SovereignGlow.toArgb(),
                        fillOpacity = 0.55, lineOpacity = 0.95
                    )
                    upsertTerritory(
                        style, TERRITORY_OTHER_SOURCE, TERRITORY_OTHER_FILL, TERRITORY_OTHER_LINE,
                        territoryFeatures(others),
                        fill = Contested.toArgb(), line = Contested.toArgb(),
                        fillOpacity = 0.40, lineOpacity = 0.85
                    )
                }
            }

            // ---- You are here ------------------------------------------------
            //
            // There was NO position marker at all before this. The camera flew to
            // the player and then showed them an anonymous stretch of street with
            // nothing on it, which is why the map read as a basemap with a button
            // rather than as a board someone is standing on.
            //
            // WHY A CIRCLE, IN AN APP MADE OF HEXAGONS. A hexagon here would be
            // read as "the hex you are standing in", and that is a claim this
            // client is not entitled to make: real cells are H3 and the SERVER
            // decides which one contains you. PreviewHexGrid is explicitly not H3
            // and must never be drawn as if it were. A circle cannot be mistaken
            // for a claimable cell, so the shape itself keeps the map honest.
            //
            // WHY A STYLE LAYER RATHER THAN CircleAnnotation. The annotation API
            // was tried first and drew nothing at all -- verified by scanning the
            // rendered frame for accent pixels, of which there were none anywhere
            // on the map even with the marker cranked to a 90px opaque disc. A
            // style layer added here renders correctly, and addPersistentLayer
            // keeps it alive across the style reloads that MapStyle triggers.
            //
            // NOTE ON slot(): dark-v11 is a CLASSIC style and has no slots. The
            // fog and border layers above ask for slot("top") and the SDK logs
            // "Slot 'top' missing" for each of them. It is harmless -- addLayer
            // appends to the end of the layer list, which is already the top --
            // so those layers render correctly despite the warning. The marker
            // therefore does not ask for a slot it cannot get.
            //
            // Radius is in SCREEN pixels, so this stays a puck at every zoom
            // instead of swelling into something that looks like territory.
            MapEffect(state.playerLat, state.playerLng) { mapView ->
                val lat = state.playerLat
                val lng = state.playerLng
                if (lat == null || lng == null) return@MapEffect
                val here = Point.fromLngLat(lng, lat)

                mapView.mapboxMap.getStyle { style ->
                    val existing = style.getSourceAs<GeoJsonSource>(PLAYER_SOURCE)
                    if (existing != null) {
                        existing.geometry(here)
                        return@getStyle
                    }
                    style.addSource(geoJsonSource(PLAYER_SOURCE) { geometry(here) })
                    // Soft halo. Presence, not precision -- deliberately not scaled
                    // to GPS accuracy, because this state carries no accuracy value
                    // and a halo sized from nothing would be a fabricated
                    // confidence reading.
                    style.addPersistentLayer(
                        circleLayer(PLAYER_HALO_LAYER, PLAYER_SOURCE) {
                            circleRadius(26.0)
                            circleColor(Sovereign.toArgb())
                            circleOpacity(0.20)
                            circleBlur(0.9)
                        }
                    )
                    style.addPersistentLayer(
                        circleLayer(PLAYER_CORE_LAYER, PLAYER_SOURCE) {
                            circleRadius(7.0)
                            circleColor(SovereignGlow.toArgb())
                            circleStrokeWidth(2.0)
                            circleStrokeColor(Void.toArgb())
                            circleStrokeOpacity(0.85)
                        }
                    )
                }

            }
        }

        // ---- Vignette --------------------------------------------------------
        //
        // The map is the hero, and a hero surface needs a frame. Stock dark-v11 is
        // lit uniformly corner to corner, so the eye had nothing to settle on and
        // the HUD sat on top of competing road detail.
        //
        // This darkens the edges toward Void and leaves the centre clear: the board
        // reads as lit from the middle, the identity chip and the capture action
        // land on darkness rather than on streets, and the frame stops looking like
        // a basemap screenshot.
        //
        // drawWithCache, not a background brush: a radial gradient needs the real
        // pixel size to place its centre and radius. Built from Offset fractions it
        // would anchor at the top-left corner and shade the whole screen.
        //
        // NO POINTER MODIFIER, deliberately: Compose only routes gestures to nodes
        // that ask for them, so this draws over the map without stealing a single
        // pan or pinch.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val vignette = Brush.radialGradient(
                        0.30f to Color.Transparent,
                        1f to Void.copy(alpha = 0.92f),
                        center = size.center,
                        radius = size.maxDimension * 0.62f
                    )
                    onDrawBehind { drawRect(vignette) }
                }
        )

        MapHud(
            state = state,
            onCapture = viewModel::onCaptureRequested,
            onRetry = viewModel::onRetry,
            onExplainBlocker = onExplainBlocker,
            onExitPreview = viewModel::exitPreview,
            // Camera controls. These steer the view only -- no state, no backend,
            // no gameplay effect. RECENTRE returns to the last accepted fix;
            // FRAME returns to the whole playable world, the same overview the
            // map opens on so a player is always one tap from seeing the board
            // and its edges.
            onRecentre = {
                val lat = state.playerLat
                val lng = state.playerLng
                if (lat != null && lng != null) {
                    viewportState.flyTo(
                        cameraOptions {
                            center(Point.fromLngLat(lng, lat))
                            zoom(16.0)
                        },
                        MapAnimationOptions.mapAnimationOptions { duration(900L) }
                    )
                }
            },
            onFrameWorld = {
                viewportState.flyTo(
                    cameraOptions {
                        center(DEFAULT_CENTER)
                        zoom(5.4)
                        pitch(0.0)
                    },
                    MapAnimationOptions.mapAnimationOptions { duration(1100L) }
                )
            }
        )
    }
}

/**
 * Boundaries of [hexes] as a Mapbox FeatureCollection, skipping any whose cell id
 * cannot be converted (malformed, or H3 unavailable on this ABI). SDK types only
 * appear at this render boundary; the ownership split that decides which hexes
 * land here is a plain predicate the caller applies. Pure and side-effect free.
 */
private fun territoryFeatures(hexes: List<HexOwnership>): FeatureCollection {
    val features = hexes.mapNotNull { hex ->
        val ring = H3Boundary.ring(hex.h3Index)
        // A drawable polygon needs at least a triangle plus the closing vertex.
        if (ring.size < 4) return@mapNotNull null
        val points = ring.map { (lat, lng) -> Point.fromLngLat(lng, lat) }
        Feature.fromGeometry(Polygon.fromLngLats(listOf(points)))
    }
    return FeatureCollection.fromFeatures(features)
}

/**
 * Adds a territory source with its fill + outline layers the first time, then
 * only swaps the data on later calls. addPersistentLayer, like the player puck,
 * so the layers survive the style reloads MapStyle triggers. Setting an empty
 * collection is how territory clears on sign-out or when a rival loses a hex --
 * the layers stay, the geometry empties, nothing lingers.
 */
private fun upsertTerritory(
    style: Style,
    sourceId: String,
    fillId: String,
    lineId: String,
    features: FeatureCollection,
    fill: Int,
    line: Int,
    fillOpacity: Double,
    lineOpacity: Double,
) {
    val existing = style.getSourceAs<GeoJsonSource>(sourceId)
    if (existing != null) {
        existing.featureCollection(features)
        return
    }
    style.addSource(geoJsonSource(sourceId) { featureCollection(features) })
    style.addPersistentLayer(
        fillLayer(fillId, sourceId) {
            fillColor(fill)
            fillOpacity(fillOpacity)
        }
    )
    style.addPersistentLayer(
        lineLayer(lineId, sourceId) {
            lineColor(line)
            lineWidth(1.5)
            lineOpacity(lineOpacity)
        }
    )
}
