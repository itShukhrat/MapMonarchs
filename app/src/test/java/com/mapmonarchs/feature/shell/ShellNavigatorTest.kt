package com.mapmonarchs.feature.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation rules that must hold, expressed as behaviour rather than
 * implementation: the map is always reachable, back never strands the player,
 * and the command menu is a launcher rather than a tab bar.
 */
class ShellNavigatorTest {

    @Test
    fun `starts on the map`() {
        assertEquals(Route.Map, ShellNavigator().current)
    }

    @Test
    fun `map is the root and cannot be popped`() {
        val nav = ShellNavigator()
        assertFalse(nav.canGoBack)
        assertFalse("back at root must not be consumed, so the system can exit", nav.back())
        assertEquals(Route.Map, nav.current)
    }

    @Test
    fun `navigating pushes a destination`() {
        val nav = ShellNavigator()
        nav.navigateTo(Route.Settings)
        assertEquals(Route.Settings, nav.current)
        assertTrue(nav.canGoBack)
    }

    @Test
    fun `back returns to the map`() {
        val nav = ShellNavigator()
        nav.navigateTo(Route.Settings)
        assertTrue(nav.back())
        assertEquals(Route.Map, nav.current)
    }

    /**
     * Replaces the two tests that covered the old floating menu's open/closed
     * state. That menu is gone -- navigation is a persistent bar now, so "the
     * menu is showing" is not a state this app can be in. These cover what took
     * its place.
     */
    @Test
    fun `bar destinations replace each other rather than deepening the stack`() {
        val nav = ShellNavigator()
        nav.navigateTo(Route.Territory)
        nav.navigateTo(Route.Missions)
        nav.navigateTo(Route.Empire)
        nav.navigateTo(Route.Territory)

        assertEquals(Route.Territory, nav.current)
        assertEquals(
            "bouncing between tabs must not make BACK unpredictable",
            2,
            nav.depth()
        )
        assertTrue(nav.back())
        assertEquals("one back press returns to the map", Route.Map, nav.current)
    }

    @Test
    fun `drilling into a screen from a tab still pushes onto it`() {
        val nav = ShellNavigator()
        nav.navigateTo(Route.Command)
        nav.navigateTo(Route.Settings)

        assertEquals(3, nav.depth())
        assertTrue(nav.back())
        assertEquals(
            "back from a drill-down returns to the tab it was opened from",
            Route.Command,
            nav.current
        )
    }

    @Test
    fun `selecting map from a secondary screen returns to root rather than stacking`() {
        val nav = ShellNavigator()
        nav.navigateTo(Route.Settings)
        nav.navigateTo(Route.Safety)
        nav.navigateTo(Route.Map)
        assertEquals(Route.Map, nav.current)
        assertEquals("stack must unwind, not grow", 1, nav.depth())
    }

    @Test
    fun `re-selecting the current destination does not stack duplicates`() {
        val nav = ShellNavigator()
        nav.navigateTo(Route.Settings)
        nav.navigateTo(Route.Settings)
        assertEquals(2, nav.depth())
    }

    @Test
    fun `deep stacks unwind one screen at a time`() {
        val nav = ShellNavigator()
        nav.navigateTo(Route.Identity)
        nav.navigateTo(Route.Settings)
        assertEquals(3, nav.depth())
        nav.back()
        assertEquals(Route.Identity, nav.current)
        nav.back()
        assertEquals(Route.Map, nav.current)
        assertFalse(nav.back())
    }

    @Test
    fun `every command route has a title`() {
        COMMAND_ROUTES.forEach { assertTrue("blank title for $it", it.title.isNotBlank()) }
    }

    @Test
    /**
     * Stronger than the test it replaces. That one pinned Map specifically; this
     * pins the rule Map was an instance of -- a destination gets exactly one seat
     * across every navigation surface that renders simultaneously.
     *
     * LOCKED_ROUTES is deliberately allowed to overlap the bar: TERRITORY,
     * MISSIONS and EMPIRE are locked AND have seats. CommandCenterScreen filters
     * those out at render, so the player never sees the same entry twice.
     */
    fun `no destination has a seat in two navigation surfaces at once`() {
        val barRoutes = NAV_DESTINATIONS.map { it.route }.toSet()

        assertEquals(
            "a bar destination must not also be listed under COMMAND",
            emptySet<Route>(),
            barRoutes intersect COMMAND_ROUTES.toSet()
        )
        assertEquals(
            "a bar destination must not also be listed under REFERENCE",
            emptySet<Route>(),
            barRoutes intersect REFERENCE_ROUTES.toSet()
        )
        assertEquals(
            "the bar itself must not list anything twice",
            NAV_DESTINATIONS.size,
            barRoutes.size
        )
    }
}