package com.mapmonarchs.feature.shell

import androidx.annotation.StringRes
import com.mapmonarchs.R
import com.mapmonarchs.domain.model.Blocker
import com.mapmonarchs.ui.components.LockMotif
import com.mapmonarchs.ui.components.NavGlyph

/**
 * A feature that exists in the design but cannot honestly be used yet.
 *
 * Every field is mandatory on purpose. A locked surface that cannot state its
 * unlock condition is just a "coming soon" screen wearing a nicer font, and
 * "coming soon" is the least trustworthy thing a product can say.
 */
data class FeatureLock(
    /** Why it is unavailable, in plain language. */
    @StringRes val why: Int,
    /** The precise condition that unlocks it. No vague timelines. */
    @StringRes val unlocksWhen: Int,
    /** Something useful the player can actually do right now instead. */
    @StringRes val insteadNow: Int,
    /**
     * Whether DIAGNOSTICS can actually explain this lock.
     *
     * True when the blocker is a BACKEND fact the diagnostics screen surfaces --
     * schema not deployed, capture absent. Routing there answers "why is this
     * off and when does it change".
     *
     * False when the lock is a PRODUCT decision -- Empire and Shop are held back
     * until the solo loop is proven fun, which is a judgement no probe reports.
     * Sending those to a backend status page is routing to the wrong remedy: the
     * player would read NOT DEPLOYED and conclude, wrongly, that a deployment is
     * all that stands between them and an empire.
     */
    val diagnosticsExplainsThis: Boolean,
    /**
     * The visual character of this destination.
     *
     * Mandatory like every other field here. Six locked surfaces that all render
     * identically read as one "not yet" repeated, rather than as a product with
     * shape -- and a motif that could be omitted would be omitted.
     */
    val motif: LockMotif,
    /**
     * A promise about money, for the locks where money is the point.
     *
     * NULLABLE, WHICH THE OTHER FIELDS DELIBERATELY ARE NOT. The three strings
     * above are mandatory because a lock that cannot say why it is shut is a
     * "coming soon" screen in a nicer font. This one is different: it only means
     * something on a surface that will one day take money, and inventing a
     * monetisation promise for MISSIONS would be noise.
     *
     * WHY IT EXISTS AT ALL WITH NO STORE TO SHOW. There is no billing
     * integration in this app, no product catalogue, and nothing purchasable --
     * so the honest maximum a shop surface can offer today is the RULE it will
     * be bound by when it opens. Stating that before there is anything to sell
     * is worth more than stating it beside a checkout, because it cannot be
     * read as a sales pitch.
     */
    @StringRes val commitment: Int? = null
)

/**
 * Destinations.
 *
 * Still not androidx.navigation. The count has grown, but there are still no
 * deep links and no nested graphs -- the trigger for adopting the library is a
 * notification opening a specific hex, which cannot exist until notifications
 * do.
 */
sealed interface Route {
    @get:StringRes val titleRes: Int

    /**
     * A stable identifier that is NEVER translated.
     *
     * Test tags and analytics keys were built from the display title. Once that
     * title became a string resource those keys would have changed with the
     * device language, so a Russian device would look like a different app to
     * every selector in the suite. Display text and identity are now separate
     * things, which is what they always were.
     */
    val key: String
    val lock: FeatureLock? get() = null

    data object Map : Route {
        override val titleRes = R.string.route_map
        override val key = "MAP"
    }
    data object Identity : Route {
        override val titleRes = R.string.route_identity
        override val key = "IDENTITY"
    }
    data object Diagnostics : Route {
        override val titleRes = R.string.route_diagnostics
        override val key = "DIAGNOSTICS"
    }
    data object Settings : Route {
        override val titleRes = R.string.route_settings
        override val key = "SETTINGS"
    }
    data object SignIn : Route {
        override val titleRes = R.string.route_signin
        override val key = "SIGN IN"
    }

    /**
     * The instrument deck. Replaced the old floating command MENU, which was an
     * overlay reachable only from the map -- so every reference and settings
     * surface was two taps deep and invisible until you found the button.
     */
    data object Command : Route {
        override val titleRes = R.string.route_command
        override val key = "COMMAND"
    }

    data object Help : Route {
        override val titleRes = R.string.route_help
        override val key = "HELP"
    }
    data object Safety : Route {
        override val titleRes = R.string.route_safety
        override val key = "SAFETY & PRIVACY"
    }
    data object About : Route {
        override val titleRes = R.string.route_about
        override val key = "ABOUT"
    }

    data object Territory : Route {
        override val titleRes = R.string.route_territory
        override val key = "TERRITORY"
        override val lock = FeatureLock(
            why = R.string.lock_territory_why,
            unlocksWhen = R.string.lock_territory_when,
            insteadNow = R.string.lock_territory_now,
            diagnosticsExplainsThis = false,
            motif = LockMotif.WORLD
        )
    }

    data object Leaderboard : Route {
        override val titleRes = R.string.route_leaderboard
        override val key = "LEADERBOARD"
        override val lock = FeatureLock(
            why = R.string.lock_leaderboard_why,
            unlocksWhen = R.string.lock_leaderboard_when,
            insteadNow = R.string.lock_leaderboard_now,
            diagnosticsExplainsThis = false,
            motif = LockMotif.RANKING
        )
    }

    data object Notifications : Route {
        override val titleRes = R.string.route_notifications
        override val key = "NOTIFICATIONS"
        override val lock = FeatureLock(
            why = R.string.lock_notifications_why,
            unlocksWhen = R.string.lock_notifications_when,
            insteadNow = R.string.lock_notifications_now,
            diagnosticsExplainsThis = false,
            motif = LockMotif.SIGNAL
        )
    }

    data object Missions : Route {
        override val titleRes = R.string.route_missions
        override val key = "MISSIONS"
        override val lock = FeatureLock(
            why = R.string.lock_missions_why,
            unlocksWhen = R.string.lock_missions_when,
            insteadNow = R.string.lock_missions_now,
            diagnosticsExplainsThis = false,
            motif = LockMotif.QUEST
        )
    }

    data object Empire : Route {
        override val titleRes = R.string.route_empire
        override val key = "EMPIRE"
        override val lock = FeatureLock(
            why = R.string.lock_empire_why,
            unlocksWhen = R.string.lock_empire_when,
            insteadNow = R.string.lock_empire_now,
            diagnosticsExplainsThis = false,
            motif = LockMotif.PRESTIGE
        )
    }

    /**
     * The player hub. Unlocked, because everything it shows is already true.
     *
     * It reads no data the backend has not confirmed: the callsign and session
     * come from identity, held ground comes from ownership, and every surface
     * with no data behind it yet -- rank, badges, records -- says so in its own
     * words rather than rendering a plausible zero.
     */
    data object Profile : Route {
        override val titleRes = R.string.route_profile
        override val key = "PROFILE"
        override val lock = null
    }

    /**
     * Sub-screens of the profile hub. Unlocked, because each is honest about
     * having no data yet rather than being withheld. They are reached FROM
     * profile and are not dock destinations -- the dock stays at five, and these
     * are places you drill into, not places you steer from.
     */
    data object Badges : Route {
        override val titleRes = R.string.route_badges
        override val key = "BADGES"
        override val lock = null
    }

    data object Records : Route {
        override val titleRes = R.string.route_records
        override val key = "RECORDS"
        override val lock = null
    }

    /** The Pro surface. Unlocked so the comparison is visible; purchase is gated honestly inside. */
    data object Subscription : Route {
        override val titleRes = R.string.route_subscription
        override val key = "SUBSCRIPTION"
        override val lock = null
    }

    data object Shop : Route {
        override val titleRes = R.string.route_shop
        override val key = "SHOP"
        override val lock = FeatureLock(
            why = R.string.lock_shop_why,
            unlocksWhen = R.string.lock_shop_when,
            insteadNow = R.string.lock_shop_now,
            diagnosticsExplainsThis = false,
            motif = LockMotif.VAULT,
            commitment = R.string.lock_shop_commitment
        )
    }
}

/** Primary commands: things you actively do. Rendered at full weight. */
val COMMAND_ROUTES: List<Route> = listOf(
    Route.Identity,
    Route.Diagnostics,
    Route.Settings
)

/** Reference material: things you read once. Deliberately secondary. */
val REFERENCE_ROUTES: List<Route> = listOf(
    Route.Help,
    Route.Safety,
    Route.About
)

/**
 * Locked systems, shown rather than hidden.
 *
 * Hiding them would make the product look smaller than it is and give the player
 * no sense of direction; a flat list at equal weight would make it look mostly
 * broken. Shown dimmed and last, they read as roadmap.
 */
val LOCKED_ROUTES: List<Route> = listOf(
    Route.Territory,
    Route.Missions,
    Route.Leaderboard,
    Route.Notifications,
    Route.Empire,
    Route.Shop
)

/**
 * A destination in the floating command bar.
 *
 * STILL FIVE. Five is what a thumb reaches across a 360dp screen without the
 * hand shifting grip, and this bar sits over a map the player is walking with.
 * That constraint has not changed, so PROFILE was added by REPLACING a tab
 * rather than by growing the bar to six.
 *
 * MISSIONS gave up the slot, not one of the working surfaces. It is locked, and
 * CommandCenterScreen already lists every locked route that is NOT in this bar,
 * so it lost no discoverability by moving -- it simply appears under the
 * roadmap where the other unopened systems are. PROFILE, by contrast, is a
 * surface the player uses continuously and can act on today.
 *
 * An earlier revision of this comment argued profile belonged inside COMMAND
 * because it is "a place you visit occasionally". That was true when it held a
 * callsign and a session row; it stopped being true once it carried the crest,
 * held ground, account state and the pro gate.
 *
 * NAMING: these use each route's own title rather than a second set of nav
 * labels. "CLAIMS" in the bar opening a screen headed "TERRITORY" is the same
 * class of defect as the HUD and diagnostics disagreeing about a blocker -- one
 * concept, two names, drifting independently.
 */
data class NavDestination(
    val route: Route,
    val glyph: NavGlyph
) {
    /**
     * The label IS the route title, not a copy of it.
     *
     * It used to be a separate constructor argument holding the same words. The
     * comment above already said these must not diverge, but nothing enforced
     * it -- two literals for one concept is precisely the drift this file warns
     * about, and localisation would have doubled the number of places to keep
     * in sync. Now there is one.
     */
    val labelRes: Int get() = route.titleRes

    val isLocked: Boolean get() = route.lock != null
}

val NAV_DESTINATIONS: List<NavDestination> = listOf(
    NavDestination(Route.Map, NavGlyph.MAP),
    NavDestination(Route.Territory, NavGlyph.CLAIMS),
    NavDestination(Route.Profile, NavGlyph.PROFILE),
    NavDestination(Route.Empire, NavGlyph.SOCIAL),
    NavDestination(Route.Command, NavGlyph.COMMAND)
)

val ALL_ROUTES: List<Route> = COMMAND_ROUTES + REFERENCE_ROUTES + LOCKED_ROUTES

/**
 * Screens reached by drilling INTO another screen rather than by steering the
 * dock -- Profile's sub-screens and the sign-in flow. The shell gives ENTERING
 * one of these a forward "deeper" transition and LEAVING it the reverse, so the
 * hierarchy is legible in motion. Everything else -- dock tabs, the command deck,
 * the reference screens, and the map -- transitions as a peer.
 *
 * The map is deliberately NOT here: it must only ever get the gentle peer scale,
 * because a heavier scale on a live GL surface is a cost the board should not pay.
 */
val DRILL_ROUTES: Set<Route> = setOf(
    Route.Badges,
    Route.Records,
    Route.Subscription,
    Route.SignIn,
)

/**
 * Where a blocked action should send the player.
 *
 * WHY THIS IS A PROJECTION OF [Blocker] AND NOT ITS OWN RULE: AppShell used to
 * decide this inline with `identity !is SignedIn && !previewMode`, which was a
 * second, simpler copy of a precedence rule Blocker already owned. The two
 * disagreed in a reachable state:
 *
 *   signed out AND location permission denied
 *     -> Blocker says LocationPermissionMissing (position outranks identity)
 *     -> the capture button reads "LOCATION OFF"
 *     -> the old routing saw "no session" and sent the player to SIGN IN
 *
 * A button that says LOCATION OFF and opens a sign-in form is the same defect as
 * the HUD and diagnostics naming different blockers -- one rule, two
 * hand-maintained copies. This was the fourth surface of that rule and the one
 * missed when the other three were consolidated, because it was not a `when`
 * chain and so did not look like one.
 *
 * Exhaustive over Blocker on purpose: a new variant will not compile until it
 * has been given a destination.
 */
fun remedyRouteFor(blocker: Blocker): Route = when (blocker) {
    // The blocker's own explanation says "Grant it in SETTINGS", and SETTINGS is
    // where permission state is shown along with the fact that Android will not
    // ask again once denied. Prose and destination must not drift apart.
    is Blocker.LocationPermissionMissing -> Route.Settings

    // Identity is the blocker and signing in is the action that clears it.
    is Blocker.NotSignedIn,
    is Blocker.IdentityUnavailable -> Route.SignIn

    // Nothing here is actionable by the player right now -- a session being
    // established, a fix being acquired, a reading rejected, or a backend
    // function that was never deployed. Status is the honest destination; a
    // sign-in form would imply an action that would not help.
    // Nothing to act on and nothing to sign into -- the explanation IS the
    // remedy, and diagnostics is where it lives alongside the rest of the truth.
    is Blocker.OutsidePlayableWorld,
    is Blocker.IdentityResolving,
    is Blocker.AcquiringFix,
    is Blocker.FixRejected,
    is Blocker.None -> Route.Diagnostics
}
