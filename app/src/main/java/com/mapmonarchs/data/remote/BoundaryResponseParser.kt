package com.mapmonarchs.data.remote

import com.mapmonarchs.domain.model.PLAYABLE_COUNTRY_ISO
import com.mapmonarchs.domain.model.WorldPosition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses a Mapbox Tilequery response against mapbox.country-boundaries-v1.
 *
 * Android-free so it can be unit-tested without `returnDefaultValues`, which
 * globally stubs the framework and would quietly weaken every other assertion in
 * the suite. Same reasoning as AuthSettingsParser.
 *
 * THE RULE THAT MATTERS: Tilequery returns features NEAR the point, not only the
 * one containing it. Each carries `tilequery.distance`, and only a distance of 0
 * means the point actually falls inside that polygon. Treating mere presence as
 * containment would place a player standing just over the Kazakh border "inside"
 * Uzbekistan -- they would see a claim button, press it, and be rejected by a
 * server that measured properly. That is precisely the kind of lie this codebase
 * exists to avoid, and it is one comparison away.
 */
object BoundaryResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): WorldPosition = try {
        val features = json.parseToJsonElement(body).jsonObject["features"]?.jsonArray
            ?: error("missing 'features'")

        // The containing polygon, if the point is inside one at all.
        val containing = features.firstOrNull { feature ->
            val props = feature.jsonObject["properties"]?.jsonObject ?: return@firstOrNull false
            val distance = props["tilequery"]?.jsonObject?.get("distance")
                ?.jsonPrimitive?.content?.toDoubleOrNull()
            distance == 0.0
        }

        when {
            containing == null ->
                // Over water, or outside every mapped boundary. Not "unknown" --
                // the service answered, and the answer is that no country contains
                // this point, which is definitively not Uzbekistan.
                WorldPosition.Outside(country = null)

            else -> {
                val props = containing.jsonObject["properties"]!!.jsonObject
                val iso = props["iso_3166_1"]?.jsonPrimitive?.content
                    ?: error("containing feature has no iso_3166_1")

                if (iso == PLAYABLE_COUNTRY_ISO) {
                    WorldPosition.Inside
                } else {
                    // name_en so the message reads "you are in Kazakhstan" rather
                    // than "you are in KZ".
                    WorldPosition.Outside(props["name_en"]?.jsonPrimitive?.content)
                }
            }
        }
    } catch (t: Throwable) {
        // Unknown, never Outside. A parse failure is not evidence that the player
        // left the country, and treating it as such would lock someone out of
        // their own game because a response shape changed.
        WorldPosition.Unknown("boundary response unparseable: ${t.message ?: t::class.simpleName}")
    }
}
