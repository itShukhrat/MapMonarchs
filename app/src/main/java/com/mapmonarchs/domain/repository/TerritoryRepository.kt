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
     * Deliberately not viewport-scoped yet: scoping means polyfilling the visible
     * region into H3 cells and querying those. h3-java is now on the client (for
     * drawing ownership boundaries), so this is no longer blocked on a library --
     * it is simply future work. A bounded snapshot stays honest for v0 data
     * volumes and -- unlike the previous ownershipFor(indexes) -- it can actually
     * be called, so the read path and its failure modes are exercised on launch.
     */
    suspend fun territorySnapshot(limit: Int = 200): Outcome<List<HexOwnership>>

    /**
     * Submits movement evidence. The SERVER derives the hex, runs the anti-cheat
     * gates, and decides. The client renders the result and never predicts it.
     *
     * LIVE: the attempt_capture Edge Function is deployed and verified, and this
     * is called from MapViewModel.onCaptureRequested for a signed-in player on an
     * accepted fix. The server re-checks every gate regardless of the client.
     */
    suspend fun attemptCapture(fix: LocationFix): Outcome<CaptureOutcome>

    /**
     * Invokes [onChange] whenever server territory changes, so the map can
     * live-refresh instead of only reloading on launch or manual recheck.
     *
     * Suspends until the collector is cancelled, then unsubscribes cleanly. The
     * change PAYLOAD is deliberately ignored: rather than trust an incremental row
     * off the wire, the caller reloads the authoritative [territorySnapshot],
     * which keeps the client a reader of server truth even for live updates. Backed
     * by the Realtime publication on hex_ownership (added in the foundation
     * migration); a dropped socket ends the flow rather than silently starving it.
     */
    suspend fun observeTerritoryChanges(onChange: suspend () -> Unit)
}