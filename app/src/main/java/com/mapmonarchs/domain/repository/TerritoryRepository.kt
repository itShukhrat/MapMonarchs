package com.mapmonarchs.domain.repository

import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.CaptureOutcome
import com.mapmonarchs.domain.model.HexOwnership
import com.mapmonarchs.domain.model.LocationFix

/** Either a value or an explained failure. There is no third, silent option. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

/**
 * Territory access.
 *
 * Note the asymmetry and keep it: reads return territory, and there is NO
 * setOwner()/saveHex(). There must never be one. The only mutating call submits
 * movement evidence and returns whatever the server decided.
 */
interface TerritoryRepository {

    /**
     * Current territory snapshot, bounded.
     *
     * Deliberately not viewport-scoped yet: scoping requires computing H3 cells
     * for the visible region, which needs a client H3 library we have not added.
     * A bounded snapshot is honest for v0 data volumes and -- unlike the previous
     * ownershipFor(indexes) -- it can actually be called, so the read path and
     * its failure modes are exercised on every launch instead of sitting dead.
     */
    suspend fun territorySnapshot(limit: Int = 200): Outcome<List<HexOwnership>>

    /**
     * Submits movement evidence. The SERVER derives the hex, runs the anti-cheat
     * gates, and decides. The client renders the result and never predicts it.
     *
     * GATED: attempt_capture() is not yet applied to the database, so this has
     * never executed against a real server. Not called from anywhere yet -- by
     * design, not by neglect. See hard rule 5.
     */
    suspend fun attemptCapture(fix: LocationFix): Outcome<CaptureOutcome>
}