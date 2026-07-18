package com.mapmonarchs.domain.model

import com.mapmonarchs.domain.identity.IdentityDerivation

/**
 * Who the player is, as far as we can actually prove.
 *
 * The states collapse "signed out", "anonymous disabled", and "auth error" into
 * SignedOut + Unavailable(AppError) on purpose: the reason a session is missing
 * already lives in the AppError taxonomy, and duplicating it into a parallel
 * identity-specific hierarchy would give two places to keep in sync and two
 * chances to disagree. Unavailable always names its cause.
 */
sealed interface Identity {

    /** Session resolution in progress. Not a claim either way. */
    data object Resolving : Identity

    /** A real, authenticated session. Handle and colour are derived from userId. */
    data class SignedIn(
        val userId: String,
        val callsign: String,
        val territoryColorArgb: Long
    ) : Identity

    /** No session, and no error -- we simply are not signed in. */
    data object SignedOut : Identity

    /** We could not establish identity, and we can say why. */
    data class Unavailable(val error: AppError) : Identity

    companion object {
        /** The only way to construct a SignedIn: identity is always derived. */
        fun from(userId: String): SignedIn = SignedIn(
            userId = userId,
            callsign = IdentityDerivation.callsign(userId),
            territoryColorArgb = IdentityDerivation.territoryColorArgb(userId)
        )
    }
}