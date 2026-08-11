package com.mapmonarchs.domain.repository

import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.CaptureOutcome
import com.mapmonarchs.domain.model.LocationFix
import com.mapmonarchs.domain.model.TerritoryGeometry

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
     * Current territory as ready-to-draw GeoJSON, computed by the server.
     *
     * The territory_geometry Edge Function reads the authoritative hex_ownership,
     * converts each cell to a boundary polygon with h3-js, and tags each `mine`
     * for the caller. The client renders the FeatureCollection and computes no
     * geometry -- which is what allowed the client-side H3 native library (and its
     * non-16 KB-aligned `.so`) to be removed entirely. Bounded server-side; a
     * viewport-scoped query is the next step once territory volume warrants it.
     * Empty territory is an empty collection, never an error.
     */
    suspend fun territoryGeometry(): Outcome<TerritoryGeometry>

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
     * off the wire, the caller reloads the authoritative [territoryGeometry],
     * which keeps the client a reader of server truth even for live updates. Backed
     * by the Realtime publication on hex_ownership (added in the foundation
     * migration); a dropped socket ends the flow rather than silently starving it.
     */
    suspend fun observeTerritoryChanges(onChange: suspend () -> Unit)
}