package com.mapmonarchs.domain.model

/**
 * Whether a sign-in code has ever actually reached a human being.
 *
 * THE DISTINCTION THIS TYPE PROTECTS: Supabase returning 200 to
 * `signInWith(OTP)` means the request was accepted for sending. It does not mean
 * mail was delivered, and with `mailer_autoconfirm = false` delivery is the only
 * thing that makes sign-in possible at all. If the project has no working SMTP,
 * every screen in the auth flow works perfectly and no player can ever sign in.
 *
 * So [Requested] is deliberately NOT a success state, and there is no path from
 * [Requested] to [Delivered] that does not go through a code a person typed
 * back in. That round-trip is the only evidence that mail arrived.
 */
sealed interface DeliveryProof {

    /** No code has ever been requested from this install. */
    data object NeverAttempted : DeliveryProof

    /**
     * The backend accepted a send request. Proves the API call worked and
     * nothing else. Sitting in this state is itself informative: it means a code
     * was asked for and never confirmed.
     */
    data class Requested(val recipientHint: String, val atEpochMillis: Long) : DeliveryProof

    /** A code was received and verified. This is the only proof of delivery. */
    data class Delivered(val recipientHint: String, val atEpochMillis: Long) : DeliveryProof

    /** The backend refused, with the specific reason preserved. */
    data class Failed(
        val recipientHint: String,
        val error: AppError,
        val atEpochMillis: Long
    ) : DeliveryProof

    /**
     * A persisted value could not be understood -- written by a different app
     * version, or corrupted.
     *
     * This exists so a parse failure is never quietly rendered as
     * [NeverAttempted]. Those mean opposite things: one says "nobody has tried",
     * the other says "something happened and we lost the record of it".
     */
    data object Unknown : DeliveryProof
}

/**
 * Redacts an address for display. Diagnostics is a screen a player may hand to
 * someone else or screenshot for support, so it shows enough to confirm WHICH
 * address was used without publishing the address itself.
 */
fun redactEmail(email: String): String {
    val trimmed = email.trim()
    val at = trimmed.indexOf('@')
    if (at <= 0) return "***"
    val local = trimmed.substring(0, at)
    val domain = trimmed.substring(at)
    return if (local.length == 1) "*$domain" else "${local.first()}***$domain"
}
