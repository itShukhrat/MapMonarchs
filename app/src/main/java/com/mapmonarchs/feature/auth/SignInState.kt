package com.mapmonarchs.feature.auth

import com.mapmonarchs.domain.model.AppError

/**
 * The sign-in flow, kept OUT of MapUiState.
 *
 * Email entry and code entry are transient screen concerns; the map does not
 * care about them. Folding them into MapUiState would make every map
 * recomposition depend on keystrokes in a form the player may never open.
 */
sealed interface SignInState {

    /** Collecting an address. */
    data class EnteringEmail(
        val email: String = "",
        val error: AppError? = null,
        val sending: Boolean = false
    ) : SignInState

    /** A code was requested. Waiting for the player to type it. */
    data class EnteringCode(
        val email: String,
        val code: String = "",
        val error: AppError? = null,
        val verifying: Boolean = false,
        val resent: Boolean = false,
        /**
         * Seconds until RESEND is offered again. Counts down from a send/resend to
         * 0. Non-zero means the cooldown is running, so the UI shows a calm timer
         * instead of a live control -- and the ViewModel refuses a resend -- which
         * keeps the player from walking into the backend's rate limit and getting
         * an error where a countdown was the honest thing to show.
         */
        val resendInSeconds: Int = 0
    ) : SignInState

    /** Done. The shell navigates away. */
    data object Complete : SignInState
}

/** A minimal, deliberately permissive check -- the backend is the real authority. */
fun String.looksLikeEmail(): Boolean {
    val trimmed = trim()
    val at = trimmed.indexOf('@')
    val dot = trimmed.lastIndexOf('.')
    return at > 0 && dot > at + 1 && dot < trimmed.length - 1 && !trimmed.contains(' ')
}

/** Codes are six digits. Checked locally only to avoid a pointless round-trip. */
fun String.looksLikeCode(): Boolean = trim().length == 6 && trim().all { it.isDigit() }