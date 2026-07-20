package com.mapmonarchs.domain.repository

import com.mapmonarchs.domain.model.WorldPosition

/**
 * Answers whether a coordinate is inside the playable world.
 *
 * An interface because the answer will eventually come from two places: the
 * client, so a player learns they are out of bounds before they try to claim,
 * and attempt_capture() server-side, which is the one that decides. Keeping the
 * client behind a contract makes it obvious that this is the advisory copy.
 */
interface WorldBoundaryRepository {

    /**
     * Never throws. A failure returns [WorldPosition.Unknown] so a lost network
     * call cannot evict a player from their own country.
     */
    suspend fun positionOf(latitude: Double, longitude: Double): WorldPosition
}
