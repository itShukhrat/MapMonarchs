package com.mapmonarchs.domain.model

/**
 * What the player is entitled to.
 *
 * WHY THIS TYPE EXISTS BEFORE THERE IS A STORE. Pro-gated surfaces -- the
 * profile picture today, more later -- need to ask one question: "may this
 * player do this?" Without a type, that question gets answered inline on every
 * screen with a different boolean, and the day billing arrives those booleans
 * have to be found and unified. This is the seam, declared once.
 *
 * WHAT IT DELIBERATELY DOES NOT DO. There is no billing integration in this
 * app: no Play Billing client, no product catalogue, no purchase flow, no
 * receipt verification, and no server that could validate one. So [Pro] is
 * currently UNREACHABLE, and that is the honest state rather than a gap to be
 * papered over with a debug flag that flips it on. A pro tier the app cannot
 * actually sell or verify must not be representable as "granted" -- an
 * entitlement the server never issued is a fabricated entitlement, and the
 * screens that read it would then be lying to the player about what they own.
 *
 * WHEN BILLING LANDS: [Resolver] gains a real implementation backed by Play
 * Billing plus server-side verification, and nothing else in the UI changes,
 * because every gated surface already asks through [current].
 */
sealed interface Entitlement {

    /** Everything the game costs nothing to play. The only reachable state today. */
    data object Free : Entitlement

    /**
     * Paid tier. Not reachable in this build.
     *
     * Kept in the type -- not deleted until billing exists -- because the gated
     * UI has to render the difference between "you do not have this" and "this
     * cannot be bought yet", and the second is a different sentence.
     */
    data object Pro : Entitlement

    companion object {
        /**
         * Whether a paid tier can be obtained AT ALL right now.
         *
         * Separate from "does this player have Pro". A locked surface must say
         * which of the two it means: "upgrade to unlock" is a lie while there is
         * no upgrade to buy, and "unavailable" is wrong once there is.
         */
        const val PURCHASE_AVAILABLE: Boolean = false
    }

    /**
     * The source of truth for entitlement.
     *
     * An interface rather than a static so the eventual Play Billing
     * implementation is a substitution rather than an edit of every caller.
     */
    fun interface Resolver {
        fun current(): Entitlement
    }
}

/**
 * The only resolver that can be honest in this build.
 *
 * It does not read a preference, a debug flag, or a build type. A local flag
 * that granted Pro would produce a player who believes they own something no
 * server has recorded -- the same class of error as drawing territory the
 * backend never confirmed.
 */
object NoBillingEntitlements : Entitlement.Resolver {
    override fun current(): Entitlement = Entitlement.Free
}

/** Convenience for gated UI. False for every player in this build, by design. */
fun Entitlement.isPro(): Boolean = this is Entitlement.Pro
