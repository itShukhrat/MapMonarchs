package com.mapmonarchs.feature.profile

import com.mapmonarchs.domain.model.Entitlement
import com.mapmonarchs.domain.model.PlayerRecords

/**
 * The player's dossier as the shell has loaded it: what the profile, badges,
 * records and subscription surfaces all read from.
 *
 * [EMPTY] is the honest default -- no badges, no history, free tier -- and is
 * what every surface shows while signed out or before a load resolves. It is not
 * a placeholder for real data to be swapped in cosmetically; it is the true
 * state for a player the server knows nothing about, which is everyone until
 * capture and entitlement are live.
 */
data class ProfileDossier(
    val earnedBadges: Map<String, Long>,
    val records: PlayerRecords,
    val entitlement: Entitlement,
) {
    companion object {
        val EMPTY = ProfileDossier(
            earnedBadges = emptyMap(),
            records = PlayerRecords.NONE,
            entitlement = Entitlement.Free,
        )
    }
}
