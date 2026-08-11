package com.mapmonarchs.feature.map

import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection

/**
 * Splits the server's territory FeatureCollection into (mine, others) by the
 * `mine` flag the SERVER stamped on each feature.
 *
 * The client only READS that flag; it never decides ownership. A feature is mine
 * only when the server said so -- an absent or false flag is someone else's
 * ground, never a client guess. Null/blank/malformed GeoJSON yields two empty
 * collections, so a failed territory read clears the map rather than crashing it.
 *
 * This lives apart from MapScreen deliberately: it is pure and Compose-free so
 * the ownership split -- the one piece of new behaviour the server-geometry
 * migration added to the client -- can be verified off-device. The Mapbox GeoJSON
 * types here are the plain JVM module (Gson-backed), so this runs in host tests.
 */
internal fun splitTerritory(geojson: String?): Pair<FeatureCollection, FeatureCollection> {
    val empty = FeatureCollection.fromFeatures(emptyList<Feature>())
    if (geojson.isNullOrBlank()) return empty to empty
    val fc = runCatching { FeatureCollection.fromJson(geojson) }.getOrNull()
        ?: return empty to empty
    val mine = ArrayList<Feature>()
    val others = ArrayList<Feature>()
    fc.features()?.forEach { f ->
        if (f.getBooleanProperty("mine") == true) mine.add(f) else others.add(f)
    }
    return FeatureCollection.fromFeatures(mine) to FeatureCollection.fromFeatures(others)
}
