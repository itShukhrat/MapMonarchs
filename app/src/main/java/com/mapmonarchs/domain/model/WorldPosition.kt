package com.mapmonarchs.domain.model

/**
 * Whether the player is standing inside the playable world.
 *
 * MapMonarchs is played in Uzbekistan. That is a game rule, not a rendering
 * choice, so it needs a place in the domain rather than living only as a fog
 * layer on the map.
 *
 * THIS IS UX, NOT SECURITY -- the same stance MovementValidator takes. A client
 * that lies about its coordinates will lie about this too. When attempt_capture()
 * exists it must run its own point-in-polygon check server-side; this exists so a
 * player learns they are out of bounds from the app instead of from a rejected
 * claim.
 *
 * [Unknown] is a first-class outcome and is deliberately NOT merged into
 * [Outside]. "We could not check" and "you are outside" are different facts, and
 * treating the first as the second would lock a player out of their own country
 * because a network call failed.
 */
sealed interface WorldPosition {

    /** Confirmed inside the playable world. */
    data object Inside : WorldPosition

    /**
     * Confirmed outside it. [country] is whatever the boundary data reported, so
     * the app can say "Kazakhstan" rather than "somewhere else" -- null when the
     * position is over water or unclaimed territory that names no country.
     */
    data class Outside(val country: String?) : WorldPosition

    /** Not yet checked, or the check failed. Never treated as either answer. */
    data class Unknown(val reason: String) : WorldPosition

    /** Before any fix has been resolved. */
    data object NotChecked : WorldPosition
}

/** The one playable country, ISO 3166-1 alpha-2 as published by Mapbox. */
const val PLAYABLE_COUNTRY_ISO = "UZ"

/** Used in copy. Kept beside the ISO so the two cannot drift. */
const val PLAYABLE_COUNTRY_NAME = "Uzbekistan"
