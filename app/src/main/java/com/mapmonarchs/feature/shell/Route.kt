package com.mapmonarchs.feature.shell

import com.mapmonarchs.domain.model.Blocker
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
    val why: String,
    /** The precise condition that unlocks it. No vague timelines. */
    val unlocksWhen: String,
    /** Something useful the player can actually do right now instead. */
    val insteadNow: String
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
    val title: String
    val lock: FeatureLock? get() = null

    data object Map : Route { override val title = "MAP" }
    data object Identity : Route { override val title = "IDENTITY" }
    data object Diagnostics : Route { override val title = "DIAGNOSTICS" }
    data object Settings : Route { override val title = "SETTINGS" }
    data object SignIn : Route { override val title = "SIGN IN" }

    /**
     * The instrument deck. Replaced the old floating command MENU, which was an
     * overlay reachable only from the map -- so every reference and settings
     * surface was two taps deep and invisible until you found the button.
     */
    data object Command : Route { override val title = "COMMAND" }

    data object Help : Route { override val title = "HELP" }
    data object Safety : Route { override val title = "SAFETY & PRIVACY" }
    data object About : Route { override val title = "ABOUT" }

    data object Territory : Route {
        override val title = "TERRITORY"
        override val lock = FeatureLock(
            why = "No territory exists yet. The server function that decides who holds " +
                "a hex has not been deployed, so there is nothing to inspect.",
            unlocksWhen = "The territory schema is applied to the backend.",
            insteadNow = "Open DIAGNOSTICS to see the exact backend status."
        )
    }

    data object Leaderboard : Route {
        override val title = "LEADERBOARD"
        override val lock = FeatureLock(
            why = "A ranking is only meaningful once territory can be won and lost. " +
                "Ranking players on zero captured hexes would rank nobody against nobody.",
            unlocksWhen = "Capture is live and the first season of territory data exists.",
            insteadNow = "Nothing to compare yet. The map is the whole game today."
        )
    }

    data object Notifications : Route {
        override val title = "NOTIFICATIONS"
        override val lock = FeatureLock(
            why = "There are no events to report. Notifications matter when your ground " +
                "is under attack -- until capture exists, an inbox would only ever be empty.",
            unlocksWhen = "Capture and decay are live, so territory can change hands.",
            insteadNow = "Nothing is happening to your territory, because you have none."
        )
    }

    data object Missions : Route {
        override val title = "MISSIONS"
        override val lock = FeatureLock(
            why = "A mission is an instruction to go somewhere and hold it. With no " +
                "capture, every objective would reduce to \"walk around\" -- which the " +
                "map already asks you to do, without pretending it was assigned.",
            unlocksWhen = "Capture is live, so an objective can have a real outcome.",
            insteadNow = "Walk. That is the whole objective today, and it is the one " +
                "being tested."
        )
    }

    data object Empire : Route {
        override val title = "EMPIRE"
        override val lock = FeatureLock(
            why = "Teams and empires are deliberately held back. Alliances built before " +
                "the solo loop is proven fun would hide whether the loop is fun at all.",
            unlocksWhen = "The walk-claim-hold loop is proven on real walks by real players.",
            insteadNow = "Hold ground alone first. That is the part that has to work."
        )
    }

    data object Shop : Route {
        override val title = "SHOP"
        override val lock = FeatureLock(
            why = "There is nothing to sell, and nothing worth buying. A shop opened " +
                "before the game is good is just a way to take money for an unproven product.",
            unlocksWhen = "The loop is proven and cosmetics exist worth owning.",
            insteadNow = "Nothing here will ever affect who wins ground. Cosmetics only."
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
 * FIVE, not six. Five is what a thumb reaches across a 360dp screen without the
 * hand shifting grip, and this bar sits over a map the player is walking with.
 * The sixth tab in the original sketch was PROFILE; identity lives inside
 * COMMAND instead, because it is a place you visit occasionally, not a place you
 * steer from.
 *
 * NAMING: these use each route's own title rather than a second set of nav
 * labels. "CLAIMS" in the bar opening a screen headed "TERRITORY" is the same
 * class of defect as the HUD and diagnostics disagreeing about a blocker -- one
 * concept, two names, drifting independently.
 */
data class NavDestination(
    val route: Route,
    val glyph: NavGlyph,
    /** Short enough to sit under a 22dp glyph without truncating. */
    val label: String
) {
    val isLocked: Boolean get() = route.lock != null
}

val NAV_DESTINATIONS: List<NavDestination> = listOf(
    NavDestination(Route.Map, NavGlyph.MAP, "MAP"),
    NavDestination(Route.Territory, NavGlyph.CLAIMS, "TERRITORY"),
    NavDestination(Route.Missions, NavGlyph.MISSIONS, "MISSIONS"),
    NavDestination(Route.Empire, NavGlyph.SOCIAL, "EMPIRE"),
    NavDestination(Route.Command, NavGlyph.COMMAND, "COMMAND")
)

val ALL_ROUTES: List<Route> = COMMAND_ROUTES + REFERENCE_ROUTES + LOCKED_ROUTES

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
    is Blocker.IdentityResolving,
    is Blocker.AcquiringFix,
    is Blocker.FixRejected,
    is Blocker.CaptureNotDeployed,
    is Blocker.None -> Route.Diagnostics
}
