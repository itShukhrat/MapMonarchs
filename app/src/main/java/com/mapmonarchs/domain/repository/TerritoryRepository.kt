package com.mapmonarchs.domain.repository

import com.mapmonarchs.domain.model.CaptureOutcome
import com.mapmonarchs.domain.model.HexOwnership
import com.mapmonarchs.domain.model.LocationFix

/**
 * Territory access.
 *
 * Note the asymmetry, and keep it: reads return territory, but there is NO
 * method to write ownership. The only mutating call is attemptCapture(), which
 * submits movement evidence and returns whatever the server decided. There is
 * deliberately no setOwner()/saveHex() and there must never be one.
 */
interface TerritoryRepository {

    /** Ownership for the given H3 cells. Read-only; safe. */
    suspend fun ownershipFor(h3Indexes: List<String>): Result<List<HexOwnership>>

    /**
     * Submits movement evidence. The SERVER derives the hex, runs the anti-cheat
     * gates, and decides the outcome. The client renders the result and never
     * predicts it authoritatively.
     */
    suspend fun attemptCapture(fix: LocationFix): Result<CaptureOutcome>
}