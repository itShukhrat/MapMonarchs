package com.mapmonarchs.feature.shell

import androidx.compose.runtime.mutableStateListOf

/**
 * A back stack, and nothing more.
 *
 * Map is the root and can never be popped -- there is no state in which this
 * product has no map. Kept as a plain class so it is testable on the JVM without
 * a Compose or Android runtime.
 *
 * The command-menu open/closed flag that used to live here is gone. It existed
 * because navigation was an overlay launched from a floating button; navigation
 * is now a persistent bar, so "the menu is showing" is no longer a state this app
 * can be in. Keeping the flag as a no-op would have been dead wiring.
 */
class ShellNavigator {

    private val stack = mutableStateListOf<Route>(Route.Map)

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /**
     * Bar destinations REPLACE rather than deepen; anything else pushes.
     *
     * A persistent bar makes bouncing between tabs trivial, and a stack that grew
     * on every bounce would turn one back press into an unpredictable number of
     * them -- tap MAP, TERRITORY, MAP, TERRITORY and back would walk you through
     * all four. Tabs are peers. Drilling into a screen FROM a tab still pushes,
     * so BACK from Settings returns to the tab you opened it from.
     */
    fun navigateTo(route: Route) {
        if (route == Route.Map) {
            popToRoot()
            return
        }
        if (stack.last() == route) return

        if (NAV_DESTINATIONS.any { it.route == route }) {
            popToRoot()
        }
        stack.add(route)
    }

    /** Returns true if it consumed the back press. */
    fun back(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        return false
    }

    fun popToRoot() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Test/debug visibility into the stack depth. */
    fun depth(): Int = stack.size
}
