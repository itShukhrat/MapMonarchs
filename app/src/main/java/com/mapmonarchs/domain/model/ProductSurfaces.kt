package com.mapmonarchs.domain.model

/**
 * Whether THIS device, on THIS network, can actually read the deployed
 * product-surfaces schema -- the badge / records / entitlement migration -- right
 * now.
 *
 * WHY IT EXISTS, AND WHY IT IS DEBUG-ONLY. The territory card already proves the
 * database is reachable, but it reads a different table (hex_ownership) behind a
 * different migration. A tester confirming a backend deploy from a phone, with no
 * laptop, otherwise has no in-app signal that the PROFILE migration specifically
 * is live: signed out, the per-player reads short-circuit and prove nothing. This
 * probe reads the world-readable badge catalogue and reports what it found. It is
 * engineer-facing, so it is rendered only under BuildConfig.DEBUG -- the
 * player-facing diagnostics screen stays free of schema internals.
 *
 * THE RULE IT LIVES BY, like the rest of this screen: it reports what it OBSERVED
 * or it reports UNKNOWN. It never prints a remembered value as a current one.
 */
sealed interface ProductSurfaces {

    /** No read attempted yet. */
    data object NotProbed : ProductSurfaces

    /** A read is in flight. */
    data object Probing : ProductSurfaces

    /** The catalogue was read: the migration is live and reachable from here. */
    data class Deployed(val catalogueSize: Int) : ProductSurfaces

    /** The read reached the backend and the table is not there. */
    data object Missing : ProductSurfaces

    /** The read could not be completed -- offline, or an unclassified failure. */
    data class Unavailable(val error: AppError) : ProductSurfaces
}
