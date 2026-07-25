package com.mapmonarchs.domain.model

/**
 * The player's game history, as far as the server can vouch for it.
 *
 * WHAT THIS IS NOT: a fitness log. There is no distance, no duration, no pace,
 * no calories, no step count. The game is about ground claimed, not exercise
 * performed, and dressing capture history up as a workout would be a different
 * product wearing this one's clothes. Every field here is a fact the capture and
 * ownership systems produce, or it is absent.
 *
 * EVERYTHING IS NULLABLE, AND NULL MEANS UNKNOWN, NOT ZERO. The record store
 * (record_events, see supabase/migrations) is empty until capture is live, so
 * today every field is null and the screen says "nothing recorded yet" per row.
 * A zero would be a claim -- "you have a zero-day streak", "your longest hold was
 * nothing" -- and the app cannot honestly make those claims about a player whose
 * history simply does not exist yet. When the server writes events, these fill
 * from real accepted claims and nothing else.
 */
data class PlayerRecords(
    /** When the player's first accepted claim landed. */
    val firstCaptureEpochMillis: Long? = null,
    /** Consecutive days with at least one accepted claim, up to now. */
    val currentStreakDays: Int? = null,
    /** The best such run the player has ever had. */
    val longestStreakDays: Int? = null,
    /** Total distinct hexes ever held at any point. */
    val totalHexesEverHeld: Int? = null,
    /** The most hexes held at one moment. */
    val peakHexesHeld: Int? = null,
    /** Number of accepted claims all-time. */
    val totalCaptures: Int? = null,
) {
    /**
     * True when the server has produced nothing for this player yet, which is
     * the current state for everyone. Distinguished from "all fields happen to
     * be null" only in intent: this is the signal the screen uses to show its
     * one honest, unified empty state instead of six "unknown" rows.
     */
    val isEmpty: Boolean
        get() = firstCaptureEpochMillis == null &&
            currentStreakDays == null &&
            longestStreakDays == null &&
            totalHexesEverHeld == null &&
            peakHexesHeld == null &&
            totalCaptures == null

    companion object {
        /**
         * The only state this build can honestly produce: nothing recorded.
         *
         * Not a placeholder to be swapped for fake data -- it is what the record
         * store actually returns for a player who has never captured, which is
         * every player, because capture is not live. When record_events is wired,
         * a repository maps rows into a populated instance and this becomes the
         * genuine cold-start case rather than the universal one.
         */
        val NONE = PlayerRecords()
    }
}
