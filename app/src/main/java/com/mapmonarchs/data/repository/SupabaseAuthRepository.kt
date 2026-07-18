package com.mapmonarchs.data.repository

import android.util.Log
import com.mapmonarchs.data.remote.toAppError
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AuthRepo"

class SupabaseAuthRepository(
    private val client: SupabaseClient
) : AuthRepository {

    override suspend fun ensureSession(): Identity = withContext(Dispatchers.IO) {
        try {
            // Session restore from storage is ASYNC. Without awaiting it,
            // currentUserOrNull() can return null on a cold start even though a
            // valid session exists -- which would sign the player in again and
            // hand them a brand new identity (new callsign, new colour, all their
            // territory apparently gone). This one line is what makes identity
            // survive a restart.
            client.auth.awaitInitialization()

            val existing = client.auth.currentUserOrNull()
            if (existing != null) {
                Log.i(TAG, "reusing existing session")
                return@withContext Identity.from(existing.id)
            }

            client.auth.signInAnonymously()

            val user = client.auth.currentUserOrNull()
            if (user == null) {
                // Sign-in reported no error yet produced no user. Refuse to
                // invent one; say we do not know who this is.
                Log.w(TAG, "anonymous sign-in returned no user")
                return@withContext Identity.Unavailable(
                    com.mapmonarchs.domain.model.AppError.AuthUnavailable(
                        "signInAnonymously produced no user"
                    )
                )
            }
            Log.i(TAG, "anonymous session established")
            Identity.from(user.id)
        } catch (t: Throwable) {
            val error = t.toAppError()
            // Logged with cause, then RETURNED as state. Not swallowed.
            Log.w(TAG, "ensureSession failed: ${error.technical}", t)
            Identity.Unavailable(error)
        }
    }
}