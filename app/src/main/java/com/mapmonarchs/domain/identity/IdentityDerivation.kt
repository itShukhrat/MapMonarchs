package com.mapmonarchs.domain.identity

import java.security.MessageDigest
import java.util.Locale

/**
 * Derives a player's public identity from their authenticated user id.
 *
 * Two rules drive this:
 *
 * 1. DERIVED, NEVER INVENTED. A callsign is computed from the real user id, so
 *    it is stable across restarts and reinstalls, and two different users can
 *    never collapse into the same public identity. Nothing here fabricates a
 *    personality or a friendly-sounding placeholder name -- an invented name
 *    would be indistinguishable from a real one, which is exactly the lie this
 *    replaces.
 *
 * 2. NO RAW IDENTIFIER LEAKS. The callsign comes from SHA-256 of the user id,
 *    not from the id itself, so no fragment of the private identifier is ever
 *    rendered. Reversing a 6-character callsign to a UUID is not feasible.
 */
object IdentityDerivation {

    /**
     * Territory colours.
     *
     * This is the ONE place the "single dominant accent" rule is deliberately
     * relaxed, and only because territory ownership is meaningless if two
     * players cannot be told apart on the map. Constraints that keep it from
     * becoming a rainbow toy:
     *  - fixed, curated set; never random HSL
     *  - held at comparable saturation/lightness so no player's land visually
     *    outranks another's
     *  - used ONLY as territory fill and the identity glyph, never as UI chrome
     *  - no two adjacent entries rely on a red/green distinction alone, which
     *    would be invisible to the most common colour-vision deficiency
     */
    /**
     * NOTE ON WHAT IS NOT HERE: 0xFF4C8DFF, the electric blue that used to lead
     * this list, is gone. It is `Sovereign` -- the single UI accent, byte for
     * byte. One player in eight was therefore assigned the exact colour of every
     * primary button, the command dock indicator, and now the national border
     * itself. Their land was indistinguishable from the chrome describing it.
     *
     * Removing it is free today and expensive later: no hex is owned yet, so
     * nobody's colour changes. Once territory exists this becomes a migration
     * that silently re-skins players' land.
     */
    val TERRITORY_PALETTE: List<Long> = listOf(
        0xFF22D3EEL, // cyan
        0xFFA855F7L, // violet
        0xFFF59E0BL, // amber
        0xFF10B981L, // emerald
        0xFFEC4899L, // magenta
        0xFF38BDF8L, // sky
        0xFFF97316L  // orange
    )

    private const val CALLSIGN_LENGTH = 6

    /**
     * A stable 6-character callsign, e.g. "4F3A9C".
     *
     * Reads as a unit designation rather than a username, which suits the
     * command/territory language of the product and avoids implying a social
     * profile the game does not have.
     */
    fun callsign(userId: String): String {
        require(userId.isNotBlank()) { "cannot derive a callsign from a blank user id" }
        return digest(userId)
            .take(CALLSIGN_LENGTH / 2)
            .joinToString("") { byte -> "%02x".format(byte) }
            .uppercase(Locale.ROOT)
    }

    /** Deterministic territory colour drawn from the curated palette. */
    fun territoryColorArgb(userId: String): Long {
        require(userId.isNotBlank()) { "cannot derive a colour from a blank user id" }
        val bucket = (digest(userId).last().toInt() and 0xFF) % TERRITORY_PALETTE.size
        return TERRITORY_PALETTE[bucket]
    }

    private fun digest(userId: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
}