package com.mapmonarchs.domain.model

import com.mapmonarchs.domain.identity.IdentityDerivation

/**
 * Who the player is, as far as we can actually prove.
 *
 * The states collapse "signed out", "anonymous disabled", and "auth error" into
 * SignedOut + Unavailable(AppError) on purpose: the reason a session is missing
 * already lives in the AppError taxonomy, and duplicating it into a parallel
 * identity-specific hierarchy would give two places to keep in sync.
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

    /**
     * PREVIEW ONLY. Not a session, not authenticated, not a player.
     *
     * This is a DELIBERATELY SEPARATE variant rather than a fabricated SignedIn.
     * Because Identity is sealed, every `when` in the codebase is forced by the
     * compiler to handle Preview explicitly -- so no code path can drift into
     * treating a preview player as authenticated, and nothing derived from it can
     * ever be submitted to the server as a real claim.
     *
     * The callsign is prefixed so it is unmistakable in the UI and in any log.
     */
    data class Preview(
        val callsign: String,
        val territoryColorArgb: Long
    ) : Identity

    companion object {
        /** The only way to construct a SignedIn: identity is always derived. */
        fun from(userId: String): SignedIn = SignedIn(
            userId = userId,
            callsign = IdentityDerivation.callsign(userId),
            territoryColorArgb = IdentityDerivation.territoryColorArgb(userId)
        )

        /** A preview player. Fixed, obvious, and never mistaken for derived identity. */
        fun preview(): Preview = Preview(
            callsign = "PREVIEW",
            territoryColorArgb = IdentityDerivation.TERRITORY_PALETTE.first()
        )
    }
}