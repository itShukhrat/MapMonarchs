package com.mapmonarchs.data.repository

import com.mapmonarchs.data.dto.HexOwnershipDto
import com.mapmonarchs.data.dto.toDomain
import com.mapmonarchs.domain.model.CaptureOutcome
import com.mapmonarchs.domain.model.HexOwnership
import com.mapmonarchs.domain.model.LocationFix
import com.mapmonarchs.domain.repository.TerritoryRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SupabaseTerritoryRepository(
    private val client: SupabaseClient
) : TerritoryRepository {

    override suspend fun ownershipFor(h3Indexes: List<String>): Result<List<HexOwnership>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (h3Indexes.isEmpty()) return@runCatching emptyList()
                client.postgrest["hex_ownership"]
                    .select { filter { isIn("h3_index", h3Indexes) } }
                    .decodeList<HexOwnershipDto>()
                    .map { it.toDomain() }
            }
        }

    /**
     * UNVERIFIED END-TO-END.
     *
     * Calls attempt_capture(), which has not yet been applied to the database, so
     * this path has never executed against a real server. It compiles and the
     * shape is right; that is all that is currently proven. Do not treat it as
     * working until the schema is applied and the H3 sanity check returns
     * 8928308280fffff.
     *
     * Note what is sent: raw lat/lng only. No hex index -- the server derives it.
     */
    override suspend fun attemptCapture(fix: LocationFix): Result<CaptureOutcome> =
        withContext(Dispatchers.IO) {
            runCatching {
                val params = buildJsonObject {
                    put("_lat", fix.latitude)
                    put("_lng", fix.longitude)
                    if (fix.accuracyMeters != null) put("_accuracy", fix.accuracyMeters) else put("_accuracy", JsonNull)
                    if (fix.speedMetersPerSecond != null) put("_speed", fix.speedMetersPerSecond) else put("_speed", JsonNull)
                    put("_is_mock", fix.isMock)
                }

                val response = client.postgrest
                    .rpc(function = "attempt_capture", parameters = params)
                    .decodeAs<JsonObject>()

                val ok = response["ok"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                if (ok) {
                    CaptureOutcome.Applied(
                        h3Index = response["h3"]?.jsonPrimitive?.content.orEmpty(),
                        action = response["action"]?.jsonPrimitive?.content.orEmpty(),
                        ownerId = response["owner"]?.jsonPrimitive?.content.orEmpty()
                    )
                } else {
                    CaptureOutcome.Rejected(
                        reason = response["reason"]?.jsonPrimitive?.content.orEmpty()
                    )
                }
            }
        }
}