package com.mapmonarchs.domain.model

/**
 * Server-computed territory, ready to draw.
 *
 * The client receives a finished GeoJSON FeatureCollection of owned-hex polygons
 * -- each already converted from its H3 cell by the server (territory_geometry
 * Edge Function, h3-js) and tagged `mine` for the signed-in caller. The client
 * renders this answer and decides nothing: not the geometry, not ownership, not
 * whether a cell is the player's. That is what let the client-side H3 native
 * library be removed -- it was only ever a rendering dependency, and its `.so`
 * could not meet Android's 16 KB page-size requirement.
 *
 * [geojson] is the raw FeatureCollection JSON handed straight to Mapbox.
 * [cellCount] is how many hexes it holds, kept alongside so honest readouts (the
 * diagnostics count) need not re-parse. [EMPTY] is the true state before any
 * capture -- an empty collection, never invented cells.
 */
data class TerritoryGeometry(
    val geojson: String,
    val cellCount: Int,
) {
    companion object {
        val EMPTY = TerritoryGeometry(
            geojson = """{"type":"FeatureCollection","features":[]}""",
            cellCount = 0,
        )
    }
}
