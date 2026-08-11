package com.mapmonarchs.domain.repository

import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.CapabilitiesProbe
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
 * Anonymous sign-in is NOT offered. That is not hardcoded belief -- see
 * [probeCapabilities], which asks the backend rather than asserting the answer.
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

    /**
     * Permanently deletes the signed-in player's account and personal data.
     *
     * The account to delete is taken from the server-verified session, never a
     * client-supplied id. Personal rows (email, records, badges, entitlements,
     * and the precise-location trail) are erased; territory the player held is
     * VACATED -- kept on the shared board, no longer tied to anyone -- rather than
     * destroyed, so deleting an account cannot rewrite other players' map.
     *
     * Returns null on success (the local session is cleared too); an [AppError]
     * if the server refused, so the caller can leave the player signed in to retry.
     */
    suspend fun deleteAccount(): AppError?

    /**
     * Asks the backend which sign-in methods it actually permits.
     *
     * Exists so diagnostics can report observed configuration instead of a
     * literal that was true on the day someone checked by hand.
     */
    suspend fun probeCapabilities(): CapabilitiesProbe
}
