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
    fun `every mode states its battery trade-off`() {
        // A settings row that does not say what it costs is not a real choice.
        TrackingMode.entries.forEach { mode ->
            assertTrue("no label for $mode", mode.label.isNotBlank())
            assertTrue("no cost detail for $mode", mode.detail.isNotBlank())
        }
    }
}