package com.mapmonarchs.data.remote

import com.mapmonarchs.domain.model.WorldPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundaryResponseParserTest {

    /**
     * The verbatim response for Tashkent (69.2401, 41.2995), captured from the
     * live Tilequery API. Kept whole rather than trimmed to the fields we read:
     * a hand-minimised fixture would not have shown that `distance` lives nested
     * under `tilequery`, which is the single most important field here.
     */
    private val tashkent = """
        {"type":"FeatureCollection","features":[{"type":"Feature",
        "geometry":{"type":"Point","coordinates":[69.2401,41.2995]},
        "properties":{"subregion":"Central Asia","wikidata_id":"Q265","region":"Asia",
        "name_en":"Uzbekistan","name":"O’zbekiston","iso_3166_1_alpha_3":"UZB",
        "worldview":"all","mapbox_id":"dXJuOm1ieGJuZDo4eERyOnY0","disputed":"false",
        "color_group":2,"iso_3166_1":"UZ",
        "tilequery":{"distance":0,"geometry":"polygon","layer":"country_boundaries"}}}]}
    """.trimIndent().replace("\n", "")

    private fun outside(iso: String, nameEn: String, distance: String) = """
        {"type":"FeatureCollection","features":[{"type":"Feature",
        "geometry":{"type":"Point","coordinates":[0,0]},
        "properties":{"name_en":"$nameEn","iso_3166_1":"$iso",
        "tilequery":{"distance":$distance,"geometry":"polygon","layer":"country_boundaries"}}}]}
    """.trimIndent().replace("\n", "")

    @Test
    fun `a point inside Uzbekistan is inside the playable world`() {
        assertEquals(WorldPosition.Inside, BoundaryResponseParser.parse(tashkent))
    }

    @Test
    fun `a point inside another country names that country`() {
        val result = BoundaryResponseParser.parse(outside("KZ", "Kazakhstan", "0"))
        assertEquals(WorldPosition.Outside("Kazakhstan"), result)
    }

    /**
     * THE ASSERTION THAT EARNS THIS PARSER.
     *
     * Tilequery returns features NEAR the point, not only the containing one. A
     * player standing just over the Kazakh border still gets Uzbekistan back --
     * at a non-zero distance. Accepting mere presence would show them a claim
     * button for ground the server will refuse.
     */
    @Test
    fun `a nearby Uzbekistan polygon does not count as being inside it`() {
        val justOverTheBorder = outside("UZ", "Uzbekistan", "412.7")
        val result = BoundaryResponseParser.parse(justOverTheBorder)
        assertTrue(
            "proximity is not containment; got $result",
            result is WorldPosition.Outside
        )
    }

    @Test
    fun `the containing polygon wins over nearer-listed neighbours`() {
        // Neighbour listed first at distance > 0, container second at 0.
        val body = """
            {"type":"FeatureCollection","features":[
            {"type":"Feature","properties":{"name_en":"Tajikistan","iso_3166_1":"TJ",
            "tilequery":{"distance":1200,"geometry":"polygon","layer":"country_boundaries"}}},
            {"type":"Feature","properties":{"name_en":"Uzbekistan","iso_3166_1":"UZ",
            "tilequery":{"distance":0,"geometry":"polygon","layer":"country_boundaries"}}}]}
        """.trimIndent().replace("\n", "")
        assertEquals(WorldPosition.Inside, BoundaryResponseParser.parse(body))
    }

    @Test
    fun `a point contained by nothing is outside, with no country named`() {
        val body = """{"type":"FeatureCollection","features":[]}"""
        assertEquals(WorldPosition.Outside(null), BoundaryResponseParser.parse(body))
    }

    // --- a failure is never mistaken for an answer ---------------------------

    @Test
    fun `unparseable input is Unknown, never Outside`() {
        val result = BoundaryResponseParser.parse("<html>502 Bad Gateway</html>")
        assertTrue(
            "a broken response must not evict a player from their own country; got $result",
            result is WorldPosition.Unknown
        )
        assertTrue((result as WorldPosition.Unknown).reason.isNotBlank())
    }

    @Test
    fun `a containing feature with no iso is Unknown rather than a guess`() {
        val body = """
            {"type":"FeatureCollection","features":[{"type":"Feature",
            "properties":{"name_en":"Somewhere",
            "tilequery":{"distance":0,"geometry":"polygon","layer":"country_boundaries"}}}]}
        """.trimIndent().replace("\n", "")
        assertTrue(BoundaryResponseParser.parse(body) is WorldPosition.Unknown)
    }

    @Test
    fun `unrelated fields changing does not break the parse`() {
        val body = """
            {"type":"FeatureCollection","brand_new_top_level":{"x":1},"features":[{"type":"Feature",
            "properties":{"name_en":"Uzbekistan","iso_3166_1":"UZ","future_field":true,
            "tilequery":{"distance":0,"geometry":"polygon","layer":"country_boundaries","extra":9}}}]}
        """.trimIndent().replace("\n", "")
        assertEquals(WorldPosition.Inside, BoundaryResponseParser.parse(body))
    }
}
