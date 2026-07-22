package com.mapmonarchs.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingModeTest {

    @Test
    fun `absent preference resolves to balanced`() {
        // Absence is a valid state -- the player has simply never chosen.
        assertEquals(TrackingMode.BALANCED, TrackingMode.resolve(null))
    }

    @Test
    fun `unknown value from another app version resolves to balanced`() {
        assertEquals(TrackingMode.BALANCED, TrackingMode.resolve("ULTRA_TURBO"))
    }

    @Test
    fun `stored values round trip`() {
        TrackingMode.entries.forEach { mode ->
            assertEquals(mode, TrackingMode.resolve(mode.name))
        }
    }

    @Test
    fun `cadences are ordered from most to least expensive`() {
        assertTrue(TrackingMode.PRECISE.intervalMillis < TrackingMode.BALANCED.intervalMillis)
        assertTrue(TrackingMode.BALANCED.intervalMillis < TrackingMode.CONSERVATIVE.intervalMillis)
    }

    @Test
    fun `every mode carries a label and a cost detail`() {
        // A settings row that does not say what it costs is not a real choice.
        // That the copy is non-blank is asserted in TrackingModeCopyTest
        // (instrumented): these are string ids now, and only a Context resolves
        // them. Here we can still prove no mode was left without one.
        TrackingMode.entries.forEach { mode ->
            assertTrue("no label id for $mode", mode.labelRes != 0)
            assertTrue("no cost detail id for $mode", mode.detailRes != 0)
        }
    }
}