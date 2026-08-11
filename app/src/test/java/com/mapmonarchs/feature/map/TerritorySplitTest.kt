package com.mapmonarchs.feature.map

import com.mapmonarchs.domain.model.TerritoryGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one piece of new client behaviour the server-geometry migration introduced:
 * partitioning the server's FeatureCollection into (mine, others) by the `mine`
 * flag the SERVER stamped, and degrading safely when the payload is unusable.
 *
 * The recurring theme is that the client READS ownership and never infers it: a
 * feature is mine only when the server said `mine:true`. Absent or false is
 * someone else's ground. These pin that so a future "helpful" default -- e.g.
 * treating an owner_id match on the client as mine -- cannot slip in.
 */
class TerritorySplitTest {

    /** A closed square ring near Tashkent; coordinates are [lng,lat] GeoJSON. */
    private fun feature(mine: Boolean?, id: String): String {
        val mineProp = when (mine) {
            null -> ""
            else -> "\"mine\":$mine,"
        }
        return """
            {"type":"Feature",
             "properties":{$mineProp"h3":"$id","strength":2},
             "geometry":{"type":"Polygon","coordinates":[[
               [69.27,41.31],[69.28,41.31],[69.28,41.32],[69.27,41.32],[69.27,41.31]]]}}
        """.trimIndent()
    }

    private fun collection(vararg features: String): String =
        """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    private fun counts(geojson: String?): Pair<Int, Int> {
        val (mine, others) = splitTerritory(geojson)
        return (mine.features()?.size ?: 0) to (others.features()?.size ?: 0)
    }

    @Test
    fun `null geojson yields two empty collections`() {
        assertEquals(0 to 0, counts(null))
    }

    @Test
    fun `blank geojson yields two empty collections`() {
        assertEquals(0 to 0, counts("   "))
    }

    @Test
    fun `malformed geojson degrades to empty rather than throwing`() {
        assertEquals(0 to 0, counts("{ this is not json"))
        assertEquals(0 to 0, counts("[]"))
    }

    @Test
    fun `a valid but empty FeatureCollection is empty on both sides`() {
        assertEquals(0 to 0, counts(collection()))
    }

    @Test
    fun `the shared EMPTY constant parses to nothing`() {
        assertEquals(0 to 0, counts(TerritoryGeometry.EMPTY.geojson))
    }

    @Test
    fun `a feature the server tagged mine lands in mine`() {
        assertEquals(1 to 0, counts(collection(feature(mine = true, id = "a"))))
    }

    @Test
    fun `a feature the server tagged not-mine lands in others`() {
        assertEquals(0 to 1, counts(collection(feature(mine = false, id = "a"))))
    }

    /**
     * ANTI-CHEAT PIN. A feature with no `mine` flag must NEVER be treated as the
     * player's. Absence is not ownership; only an explicit server `true` is.
     */
    @Test
    fun `a feature with no mine flag is treated as someone else's`() {
        assertEquals(0 to 1, counts(collection(feature(mine = null, id = "a"))))
    }

    @Test
    fun `a mixed collection is partitioned by the server flag alone`() {
        val geojson = collection(
            feature(mine = true, id = "mine1"),
            feature(mine = false, id = "rival1"),
            feature(mine = null, id = "unknown1"),
            feature(mine = true, id = "mine2")
        )
        // 2 explicitly mine; the false and the flag-less both count as others.
        assertEquals(2 to 2, counts(geojson))
    }
}
