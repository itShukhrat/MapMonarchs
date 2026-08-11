package com.mapmonarchs.data.repository

import io.ktor.client.call.body

import android.util.Log
import com.mapmonarchs.data.remote.toAppError
import com.mapmonarchs.domain.model.CaptureOutcome
import com.mapmonarchs.domain.model.LocationFix
import com.mapmonarchs.domain.model.TerritoryGeometry
import com.mapmonarchs.domain.repository.Outcome
import com.mapmonarchs.domain.repository.TerritoryRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val TAG = "TerritoryRepo"

class SupabaseTerritoryRepository(
    private val client: SupabaseClient
) : TerritoryRepository {

    override suspend fun territoryGeometry(): Outcome<TerritoryGeometry> =
        withContext(Dispatchers.IO) {
            try {
                // The Edge Function computes the boundaries (h3-js) and tags `mine`
                // for this caller. The Functions plugin attaches the session JWT
                // when signed in, so `mine` is personalised; signed out, all false.
                // Body is unused by the function; sent for a consistent POST.
                val fc = client.functions
                    .invoke(function = "territory_geometry", body = buildJsonObject { })
                    .body<JsonObject>()
                val count = fc["features"]?.jsonArray?.size ?: 0
                Outcome.Success(TerritoryGeometry(geojson = fc.toString(), cellCount = count))
            } catch (t: Throwable) {
                val error = t.toAppError()
                // Logged with the cause, then RETURNED. This is not a silent
                // catch: the failure reaches the UI and is rendered.
                Log.w(TAG, "territoryGeometry failed: ${error.technical}", t)
                Outcome.Failure(error)
            }
        }

    /**
     * LIVE. The `attempt_capture` Edge Function is deployed and verified against
     * project gacptifoxlpribgdqhtz (an unauthenticated probe returns the
     * function's own `{"ok":false,"reason":"not_authenticated"}`, not a platform
     * 404), and `apply_capture()` behind it is applied. This path runs for real
     * whenever a signed-in player submits a fix.
     *
     * Note what is sent: raw lat/lng only. No hex index -- the server derives the
     * H3 cell (h3-js in the Edge Function) so a hacked client cannot name its own.
     */
    override suspend fun attemptCapture(fix: LocationFix): Outcome<CaptureOutcome> =
        withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("_lat", fix.latitude)
                    put("_lng", fix.longitude)
                    if (fix.accuracyMeters != null) put("_accuracy", fix.accuracyMeters) else put("_accuracy", JsonNull)
                    if (fix.speedMetersPerSecond != null) put("_speed", fix.speedMetersPerSecond) else put("_speed", JsonNull)
                    put("_is_mock", fix.isMock)
                }

                // Calls the Edge Function, not a Postgres RPC: the H3 cell is derived
                // there with h3-js because this project's Postgres 17 has no h3
                // extension. The body is identical (raw lat/lng); the server still
                // derives the hex. The Functions plugin attaches the user JWT, which
                // the function verifies before calling apply_capture().
                val response = client.functions
                    .invoke(function = "attempt_capture", body = params)
                    .body<JsonObject>()

                val ok = response["ok"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                val outcome = if (ok) {
                    CaptureOutcome.Applied(
                        h3Index = response["h3"]?.jsonPrimitive?.content.orEmpty(),
                        action = response["action"]?.jsonPrimitive?.content.orEmpty(),
                        ownerId = response["owner"]?.jsonPrimitive?.content.orEmpty()
                    )
                } else {
                    CaptureOutcome.Rejected(response["reason"]?.jsonPrimitive?.content.orEmpty())
                }
                Outcome.Success(outcome)
            } catch (t: Throwable) {
                val error = t.toAppError()
                Log.w(TAG, "attemptCapture failed: ${error.technical}", t)
                Outcome.Failure(error)
            }
        }

    override suspend fun observeTerritoryChanges(onChange: suspend () -> Unit) {
        // Build the change flow BEFORE subscribing (the binding must be
        // registered when the channel joins), then collect until cancelled.
        val realtimeChannel = client.channel("mm-hex-ownership")
        val changes = realtimeChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "hex_ownership"
        }
        try {
            realtimeChannel.subscribe()
            Log.i(TAG, "subscribed to hex_ownership realtime")
            // Ignore the row payload; reload the authoritative snapshot instead so
            // the client stays a reader of server truth, not a truster of the wire.
            changes.collect { onChange() }
        } finally {
            // Cancellation cannot suspend, so leave the server channel under
            // NonCancellable; best-effort, never masking the original cancellation.
            withContext(NonCancellable) {
                runCatching { realtimeChannel.unsubscribe() }
                Log.i(TAG, "unsubscribed from hex_ownership realtime")
            }
        }
    }
}