package com.mapmonarchs.domain.repository

import com.mapmonarchs.domain.model.Entitlement
import com.mapmonarchs.domain.model.PlayerRecords

/**
 * Read access to the player's own dossier: badges, history, entitlement.
 *
 * READ-ONLY BY DESIGN, exactly like [TerritoryRepository]. There is no
 * awardBadge(), no writeRecord(), no grantPro(). The tables behind these reads
 * are written only by the server (service_role) -- the capture backend awards
 * badges and logs events, and a verified purchase writes entitlement. A client
 * method that could write any of them would make all three forgeable, which is
 * the whole thing the product refuses. The client receives this state; it never
 * constructs it and pushes it up.
 *
 * WHAT EACH RETURNS WHEN THERE IS NO DATA. Signed out, every method returns the
 * honest empty default WITHOUT a network call -- badges/records/entitlement are
 * per-player, and there is no player. Signed in with an empty backend, the reads
 * return empty because the tables are empty, which is the true cold-start state.
 * A failed read (offline, or the migration not yet applied) is a [Outcome.Failure]
 * the caller degrades from, not a fabricated value.
 */
interface ProfileRepository {

    /** Earned badge keys -> earned-at epoch millis, for the signed-in player. */
    suspend fun earnedBadges(): Outcome<Map<String, Long>>

    /** The player's record ledger, aggregated by the server. */
    suspend fun playerRecords(): Outcome<PlayerRecords>

    /**
     * The player's entitlement, as the SERVER records it.
     *
     * This is the real tier resolution: a row in the entitlements table (written
     * only by verified purchase or an admin grant) means Pro; its absence means
     * Free. It is deliberately independent of whether a purchase can be MADE in
     * this build -- an admin can grant Pro server-side today, and the app will
     * honour it, without any billing client existing.
     */
    suspend fun entitlement(): Outcome<Entitlement>
}
