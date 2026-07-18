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

    @Test
    fun `navigating always dismisses the command menu`() {
        val nav = ShellNavigator()
        nav.openCommandMenu()
        nav.navigateTo(Route.Identity)
        assertFalse("menu is a launcher, not a persistent tab bar", nav.commandMenuOpen)
    }

    @Test
    fun `back closes the menu before it pops the stack`() {
        val nav = ShellNavigator()
        nav.navigateTo(Route.Settings)
        nav.openCommandMenu()
        assertTrue(nav.back())
        assertFalse(nav.commandMenuOpen)
        assertEquals("must not also pop", Route.Settings, nav.current)
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
    fun `command routes do not include map - map has its own entry`() {
        assertFalse(COMMAND_ROUTES.contains(Route.Map))
    }
}