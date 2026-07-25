package com.mapmonarchs.data.repository

import android.util.Log
import com.mapmonarchs.data.dto.EntitlementDto
import com.mapmonarchs.data.dto.PlayerRecordsDto
import com.mapmonarchs.data.dto.UserBadgeDto
import com.mapmonarchs.data.dto.toDomain
import com.mapmonarchs.data.dto.toEarnedMap
import com.mapmonarchs.data.dto.toEntitlement
import com.mapmonarchs.data.remote.toAppError
import com.mapmonarchs.domain.model.Entitlement
import com.mapmonarchs.domain.model.PlayerRecords
import com.mapmonarchs.domain.repository.Outcome
import com.mapmonarchs.domain.repository.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "ProfileRepo"

/**
 * Reads the player's dossier from Supabase.
 *
 * SIGNED OUT IS A SHORT-CIRCUIT, NOT AN ERROR. Every method returns the honest
 * empty default the moment there is no session, and makes no network call: these
 * are per-player facts, and there is no player. That also means the app makes no
 * failing requests against the profile tables while sign-in is the universal
 * state -- the reads only reach the network once someone is actually signed in,
 * by which point the migration is expected to be applied.
 *
 * A read that DOES reach the network and fails -- offline, or the tables not yet
 * created -- returns Outcome.Failure with the cause logged. The caller degrades
 * to the same honest empty state rather than crashing; it does not fabricate a
 * value to paper over the failure.
 */
class SupabaseProfileRepository(
    private val client: SupabaseClient
) : ProfileRepository {

    override suspend fun earnedBadges(): Outcome<Map<String, Long>> =
        withContext(Dispatchers.IO) {
            val uid = client.auth.currentUserOrNull()?.id
                ?: return@withContext Outcome.Success(emptyMap())
            try {
                val rows = client.postgrest["user_badges"]
                    .select { filter { eq("user_id", uid) } }
                    .decodeList<UserBadgeDto>()
                Outcome.Success(rows.toEarnedMap())
            } catch (t: Throwable) {
                val error = t.toAppError()
                Log.w(TAG, "earnedBadges failed: ${error.technical}", t)
                Outcome.Failure(error)
            }
        }

    override suspend fun playerRecords(): Outcome<PlayerRecords> =
        withContext(Dispatchers.IO) {
            val uid = client.auth.currentUserOrNull()?.id
                ?: return@withContext Outcome.Success(PlayerRecords.NONE)
            try {
                val row = client.postgrest
                    .rpc("get_player_records", buildJsonObject { put("uid", uid) })
                    .decodeList<PlayerRecordsDto>()
                    .firstOrNull()
                Outcome.Success(row?.toDomain() ?: PlayerRecords.NONE)
            } catch (t: Throwable) {
                val error = t.toAppError()
                Log.w(TAG, "playerRecords failed: ${error.technical}", t)
                Outcome.Failure(error)
            }
        }

    override suspend fun entitlement(): Outcome<Entitlement> =
        withContext(Dispatchers.IO) {
            val uid = client.auth.currentUserOrNull()?.id
                ?: return@withContext Outcome.Success(Entitlement.Free)
            try {
                val row = client.postgrest["entitlements"]
                    .select { filter { eq("user_id", uid) } }
                    .decodeList<EntitlementDto>()
                    .firstOrNull()
                Outcome.Success(row?.toEntitlement() ?: Entitlement.Free)
            } catch (t: Throwable) {
                val error = t.toAppError()
                Log.w(TAG, "entitlement failed: ${error.technical}", t)
                Outcome.Failure(error)
            }
        }
}
