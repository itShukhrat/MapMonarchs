package com.mapmonarchs.feature.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A back stack, and nothing more.
 *
 * Map is the root and can never be popped -- there is no state in which this
 * product has no map. Kept as a plain class so it is testable on the JVM without
 * a Compose or Android runtime.
 */
class ShellNavigator {

    private val stack = mutableStateListOf<Route>(Route.Map)

    var commandMenuOpen by mutableStateOf(false)
        private set

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun openCommandMenu() { commandMenuOpen = true }
    fun closeCommandMenu() { commandMenuOpen = false }

    /** Navigating always dismisses the command menu -- it is a launcher, not a tab bar. */
    fun navigateTo(route: Route) {
        commandMenuOpen = false
        if (route == Route.Map) {
            popToRoot()
            return
        }
        if (stack.last() != route) stack.add(route)
    }

    /** Returns true if it consumed the back press. */
    fun back(): Boolean {
        if (commandMenuOpen) { commandMenuOpen = false; return true }
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        return false
    }

    fun popToRoot() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Test/debug visibility into the stack depth. */
    fun depth(): Int = stack.size
}