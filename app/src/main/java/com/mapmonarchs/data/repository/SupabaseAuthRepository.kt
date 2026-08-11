package com.mapmonarchs.data.repository

import android.util.Log
import com.mapmonarchs.BuildConfig
import com.mapmonarchs.data.remote.AuthSettingsParser
import com.mapmonarchs.data.remote.toAppError
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.CapabilitiesProbe
import com.mapmonarchs.domain.model.Identity
import com.mapmonarchs.domain.repository.AuthRepository
import com.mapmonarchs.domain.repository.CodeRequest
import com.mapmonarchs.domain.repository.CodeVerification
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "AuthRepo"

/** Generous ceiling: a local session read is milliseconds, never seconds. */
private const val SESSION_INIT_TIMEOUT_MS = 10_000L

class SupabaseAuthRepository(
    private val client: SupabaseClient,
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    /**
     * Seams, present ONLY so the timeout branch below can be proven.
     *
     * That branch is the app's entire response to a stall that was actually
     * observed once and never reproduced. If it were wrong -- returning
     * SignedOut, or losing the cause -- a player with a valid session would be
     * silently signed out at the exact moment the app was already misbehaving.
     * Asserting it by reading the code was not good enough for a path that only
     * runs when something has already gone wrong.
     *
     * A test could instead have passed a 1ms timeout and raced the real
     * initialiser, but initialisation measured ~4ms on this emulator, so that
     * test would pass or fail on timing. A flaky test claiming coverage is worse
     * than an honest gap.
     */
    private val awaitInitialization: suspend () -> Unit = { client.auth.awaitInitialization() },
    private val sessionInitTimeoutMs: Long = SESSION_INIT_TIMEOUT_MS
) : AuthRepository {

    override suspend fun restoreSession(): Identity = withContext(Dispatchers.IO) {
        try {
            // Session restore from storage is ASYNC. Without awaiting it,
            // currentUserOrNull() can return null on a cold start even though a
            // valid session exists -- which would sign the player out and, on the
            // next sign-in, hand them a different identity entirely.
            //
            // BOUNDED, because an unbounded await is a silent hang. Proven, not
            // hypothesised: in an instrumented test with no Activity started,
            // awaitInitialization() never returns. The test logged nothing from
            // this method at all -- not the success line, not the catch line --
            // because execution never left the await. Whether the same stall can
            // occur in production is UNPROVEN; the app has always completed it on
            // device. But a wait with no ceiling cannot fail loudly: identity
            // would sit at Resolving forever, the HUD would read IDENTIFYING with
            // no error and no retry, and nothing would be caught or logged. That
            // is the one failure mode this codebase refuses to ship.
            //
            // Ten seconds is far beyond any legitimate local-storage read, so a
            // timeout here means something is genuinely wrong rather than slow.
            withTimeout(sessionInitTimeoutMs) {
                awaitInitialization()
            }

            val user = client.auth.currentUserOrNull()
            if (user == null) {
                Log.i(TAG, "no stored session")
                Identity.SignedOut
            } else {
                Log.i(TAG, "restored session")
                Identity.from(user.id)
            }
        } catch (t: TimeoutCancellationException) {
            // A named, retryable failure instead of an invisible stall. Unavailable
            // (not SignedOut) because we do not know whether a session exists --
            // reporting SignedOut would be a claim we cannot support, and would
            // silently drop a player who is in fact signed in.
            Log.w(TAG, "session init did not complete within ${sessionInitTimeoutMs}ms", t)
            Identity.Unavailable(
                AppError.AuthUnavailable(
                    "session initialisation timed out after ${sessionInitTimeoutMs}ms"
                )
            )
        } catch (t: CancellationException) {
            // Not ours to swallow. This is the caller cancelling us -- a ViewModel
            // being cleared, a scope shutting down -- and converting it into a
            // domain error would report a failure to a screen that no longer
            // exists, and would break structured concurrency.
            throw t
        } catch (t: Throwable) {
            val error = t.toAppError()
            Log.w(TAG, "restoreSession failed: ${error.technical}", t)
            Identity.Unavailable(error)
        }
    }

    override suspend fun requestEmailCode(email: String): CodeRequest = withContext(Dispatchers.IO) {
        try {
            client.auth.signInWith(OTP) {
                this.email = email.trim()
                // No redirect: this is a code flow, not a magic link. Setting one
                // would make Supabase send a link instead.
                createUser = true
            }
            // Deliberately worded as "accepted", not "sent". This return value
            // proves the API call succeeded and nothing about whether mail was
            // delivered -- see DeliveryProof.
            Log.i(TAG, "send request accepted by backend")
            CodeRequest.Sent
        } catch (t: Throwable) {
            val error = t.toAppError()
            Log.w(TAG, "requestEmailCode failed: ${error.technical}", t)
            CodeRequest.Failed(error)
        }
    }

    override suspend fun verifyEmailCode(email: String, code: String): CodeVerification =
        withContext(Dispatchers.IO) {
            try {
                client.auth.verifyEmailOtp(
                    type = OtpType.Email.EMAIL,
                    email = email.trim(),
                    token = code.trim()
                )
                val user = client.auth.currentUserOrNull()
                if (user == null) {
                    // Verified with no user is not a success we can act on, and we
                    // will not invent one to make the screen advance.
                    Log.w(TAG, "verification returned no user")
                    CodeVerification.Failed(
                        AppError.AuthUnavailable("verifyEmailOtp produced no user")
                    )
                } else {
                    Log.i(TAG, "session established")
                    CodeVerification.Succeeded(Identity.from(user.id))
                }
            } catch (t: Throwable) {
                val error = t.toAppError()
                Log.w(TAG, "verifyEmailCode failed: ${error.technical}", t)
                CodeVerification.Failed(error)
            }
        }

    override suspend fun signOut(): AppError? = withContext(Dispatchers.IO) {
        try {
            client.auth.signOut()
            Log.i(TAG, "signed out")
            null
        } catch (t: Throwable) {
            val error = t.toAppError()
            Log.w(TAG, "signOut failed: ${error.technical}", t)
            error
        }
    }

    override suspend fun deleteAccount(): AppError? = withContext(Dispatchers.IO) {
        try {
            // The Edge Function deletes the account behind the caller's verified
            // JWT (attached by the Functions plugin). The body is empty on
            // purpose: the id to delete comes from the session, never the client.
            val response = client.functions
                .invoke(function = "delete_account", body = buildJsonObject { })
                .body<JsonObject>()
            val ok = response["ok"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            if (!ok) {
                // A refusal is RETURNED, not swallowed: the caller keeps the player
                // signed in so a transient failure is retryable rather than leaving
                // them stranded between two states.
                val reason = response["reason"]?.jsonPrimitive?.content.orEmpty()
                Log.w(TAG, "deleteAccount refused: $reason")
                return@withContext AppError.Unexpected("delete_account refused: $reason")
            }
            // The server account is gone; drop the local session so the app lands
            // in a clean signed-out state rather than holding a token for a user
            // that no longer exists. Best-effort: the deletion already succeeded.
            runCatching { client.auth.signOut() }
            Log.i(TAG, "account deleted")
            null
        } catch (t: Throwable) {
            val error = t.toAppError()
            Log.w(TAG, "deleteAccount failed: ${error.technical}", t)
            error
        }
    }

    /**
     * GET /auth/v1/settings -- the backend's own account of what it permits.
     *
     * Uses HttpURLConnection rather than the Supabase client's transport on
     * purpose: this endpoint is outside the typed surface supabase-kt exposes,
     * and a hand-rolled GET with an explicit parser is easier to reason about
     * than reaching into the SDK's internals for one unusual call.
     */
    override suspend fun probeCapabilities(): CapabilitiesProbe = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("${supabaseUrl.trimEnd('/')}/auth/v1/settings").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", anonKey)
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                // The body of an error response is the most useful thing we have
                // and is preserved rather than replaced with a generic message.
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "no response body"
                Log.w(TAG, "capabilities probe HTTP $status: $detail")
                return@withContext CapabilitiesProbe.Unavailable(
                    AppError.BackendFailure("auth settings returned HTTP $status: $detail")
                )
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val probe = AuthSettingsParser.parse(body)
            if (probe is CapabilitiesProbe.Unavailable) {
                Log.w(TAG, "capabilities response unparseable: ${probe.error.technical}")
            }
            probe
        } catch (t: Throwable) {
            val error = t.toAppError()
            Log.w(TAG, "capabilities probe failed: ${error.technical}", t)
            CapabilitiesProbe.Unavailable(error)
        } finally {
            connection?.disconnect()
        }
    }

}
