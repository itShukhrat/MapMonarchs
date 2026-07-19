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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "AuthRepo"

class SupabaseAuthRepository(
    private val client: SupabaseClient,
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY
) : AuthRepository {

    override suspend fun restoreSession(): Identity = withContext(Dispatchers.IO) {
        try {
            // Session restore from storage is ASYNC. Without awaiting it,
            // currentUserOrNull() can return null on a cold start even though a
            // valid session exists -- which would sign the player out and, on the
            // next sign-in, hand them a different identity entirely.
            client.auth.awaitInitialization()

            val user = client.auth.currentUserOrNull()
            if (user == null) {
                Log.i(TAG, "no stored session")
                Identity.SignedOut
            } else {
                Log.i(TAG, "restored session")
                Identity.from(user.id)
            }
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
