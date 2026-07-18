package com.mapmonarchs.feature.shell

/**
 * Destinations.
 *
 * Deliberately NOT androidx.navigation: five destinations, no deep links, no
 * nested graphs. The library would add a dependency and its own idioms for no
 * gain here, and it would take control of the transition away from us. Adopt it
 * the moment we need deep links (a notification opening a hex), nested graphs,
 * or roughly eight-plus destinations -- not before.
 */
sealed interface Route {
    val title: String

    data object Map : Route { override val title = "MAP" }
    data object Identity : Route { override val title = "IDENTITY" }
    data object Settings : Route { override val title = "SETTINGS" }
    data object Safety : Route { override val title = "SAFETY & PRIVACY" }
    data object About : Route { override val title = "ABOUT" }
}

/** Command menu entries, in deliberate order: who you are, how it behaves, what it costs you. */
val COMMAND_ROUTES: List<Route> = listOf(
    Route.Identity,
    Route.Settings,
    Route.Safety,
    Route.About
)