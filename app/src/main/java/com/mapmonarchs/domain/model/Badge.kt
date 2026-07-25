package com.mapmonarchs.domain.model

import androidx.annotation.StringRes
import com.mapmonarchs.R

/**
 * A badge is a RULE, not a decoration.
 *
 * Every entry in [BadgeCatalog] corresponds to a game event the backend can
 * actually produce -- a claim landing, ground surviving into a new day, a streak
 * of active days, a rank threshold. None of them is aspirational flavour: if a
 * badge cannot be described by a condition the server could evaluate, it is not
 * in this file. That is the whole discipline here. A medal cabinet full of
 * achievements the game has no way to award is the most common form of fabricated
 * progress, and it is exactly what this type exists to prevent.
 *
 * EARNED STATE IS SEPARATE AND COMES FROM THE SERVER. This file is the CATALOG --
 * what CAN be earned and how. Whether a given player HAS earned one lives in the
 * user_badges table (see supabase/migrations) and is empty until capture and
 * ownership are live. Showing the catalog with everything locked is honest: it
 * tells the player what the game rewards without claiming they have done any of
 * it.
 */
data class Badge(
    /** Stable key. Matches badge_definitions.key on the server. */
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val criteriaRes: Int,
    val category: BadgeCategory,
    /**
     * True when the earning condition depends on a system that is itself still
     * locked. RANK badges need the leaderboard; surfacing that a badge is not
     * yet EARNABLE (versus merely not yet EARNED) is the honest distinction --
     * "walk to earn this" and "this cannot be earned by anyone yet" are
     * different sentences.
     */
    val blockedUpstream: Boolean = false,
)

/**
 * The axis a badge sits on. Three, deliberately: enough to give the cabinet
 * structure, few enough that a player holds all of them in their head. They map
 * to the three things the game measures -- ground taken, consistency over time,
 * and standing against others.
 */
enum class BadgeCategory(@StringRes val titleRes: Int) {
    FIELD(R.string.badge_cat_field),
    ENDURANCE(R.string.badge_cat_endurance),
    COMMAND(R.string.badge_cat_command),
}

/**
 * Whether a player holds a badge.
 *
 * There is no "in progress with 60%" variant yet, and that absence is honest:
 * partial progress needs a live count from the server (hexes held, days
 * streaked), and until ownership is read back the client cannot compute it
 * without guessing. A fake progress bar is worse than none. When the counts are
 * real, [Earned] gains a sibling and the screen renders a bar; today every
 * unearned badge is simply [Locked].
 */
sealed interface BadgeStatus {
    data object Locked : BadgeStatus
    data class Earned(val atEpochMillis: Long) : BadgeStatus
}

/** A badge paired with this player's status for it. */
data class BadgeState(val badge: Badge, val status: BadgeStatus) {
    val earned: Boolean get() = status is BadgeStatus.Earned
}

/**
 * The catalog of earnable badges.
 *
 * Order within a category is the intended earning order, easiest first, so the
 * cabinet reads as a path rather than a grid.
 */
object BadgeCatalog {

    val all: List<Badge> = listOf(
        // FIELD -- ground taken and held.
        Badge("first_capture", R.string.badge_first_capture_title, R.string.badge_first_capture_criteria, BadgeCategory.FIELD),
        Badge("first_hold", R.string.badge_first_hold_title, R.string.badge_first_hold_criteria, BadgeCategory.FIELD),
        Badge("hold_10", R.string.badge_hold_10_title, R.string.badge_hold_10_criteria, BadgeCategory.FIELD),
        Badge("hold_50", R.string.badge_hold_50_title, R.string.badge_hold_50_criteria, BadgeCategory.FIELD),
        Badge("border_hold", R.string.badge_border_hold_title, R.string.badge_border_hold_criteria, BadgeCategory.FIELD),
        // ENDURANCE -- consistency over time.
        Badge("streak_7", R.string.badge_streak_7_title, R.string.badge_streak_7_criteria, BadgeCategory.ENDURANCE),
        Badge("streak_30", R.string.badge_streak_30_title, R.string.badge_streak_30_criteria, BadgeCategory.ENDURANCE),
        // COMMAND -- standing against others. Gated on the leaderboard, which is locked.
        Badge("rank_top_100", R.string.badge_rank_100_title, R.string.badge_rank_100_criteria, BadgeCategory.COMMAND, blockedUpstream = true),
        Badge("rank_top_10", R.string.badge_rank_10_title, R.string.badge_rank_10_criteria, BadgeCategory.COMMAND, blockedUpstream = true),
    )

    val total: Int get() = all.size

    /**
     * Pair every catalogued badge with this player's status.
     *
     * [earnedKeys] is the set from user_badges. It is empty until the backend is
     * live and wired, so today every badge comes back [BadgeStatus.Locked] --
     * which is the true state, not a placeholder for one.
     */
    fun states(earnedKeys: Map<String, Long> = emptyMap()): List<BadgeState> =
        all.map { badge ->
            val at = earnedKeys[badge.key]
            BadgeState(badge, if (at != null) BadgeStatus.Earned(at) else BadgeStatus.Locked)
        }

    fun byCategory(earnedKeys: Map<String, Long> = emptyMap()): Map<BadgeCategory, List<BadgeState>> =
        states(earnedKeys).groupBy { it.badge.category }
}
