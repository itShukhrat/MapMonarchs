package com.mapmonarchs.data.repository

import android.util.Log
import com.mapmonarchs.data.remote.toAppError
import com.mapmonarchs.domain.model.AppError
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

private const val TAG = "AuthRepo"

class SupabaseAuthRepository(
    private val client: SupabaseClient
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
            Log.i(TAG, "sign-in code requested")
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
}