package com.mapmonarchs.domain.repository

import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.Identity

/** Result of asking the backend to email a sign-in code. */
sealed interface CodeRequest {
    data object Sent : CodeRequest
    data class Failed(val error: AppError) : CodeRequest
}

/** Result of submitting a code. */
sealed interface CodeVerification {
    data class Succeeded(val identity: Identity.SignedIn) : CodeVerification
    data class Failed(val error: AppError) : CodeVerification
}

/**
 * Session source.
 *
 * Email OTP rather than magic link: a link requires deep-link infrastructure, a
 * server-side redirect URL, and App Links verification, and on Android it bounces
 * through a browser that may not return cleanly. A six-digit code never leaves
 * the app. For players outdoors on mobile data, that reliability difference is
 * the whole argument.
 *
 * Anonymous sign-in is NOT offered: the project reports anonymous_users=false,
 * and pretending otherwise would produce a failure we already know is coming.
 */
interface AuthRepository {

    /**
     * Restores a persisted session if one exists. Never creates one -- a session
     * only ever comes from a code the player actually received.
     */
    suspend fun restoreSession(): Identity

    /** Asks the backend to email a sign-in code. */
    suspend fun requestEmailCode(email: String): CodeRequest

    /** Submits a code. Success yields a real, derived identity. */
    suspend fun verifyEmailCode(email: String, code: String): CodeVerification

    /** Ends the session and clears stored credentials. */
    suspend fun signOut(): AppError?
}