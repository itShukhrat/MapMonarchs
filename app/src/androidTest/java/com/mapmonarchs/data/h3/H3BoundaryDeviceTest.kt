package com.mapmonarchs.data.h3

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the territory rendering pipeline's native dependency works on REAL
 * hardware, not just the host JVM.
 *
 * h3-java ships android-arm64 but NOT android-x86_64, so H3Boundary cannot load on
 * an x86_64 emulator -- which means the host [H3BoundaryTest] and a device run are
 * proving two different things. The host test proves the conversion is correct;
 * THIS test, run on an arm64 phone, proves the native library actually loads and
 * converts on the device territory will render on. If it passes on the S23+, a
 * server-issued cell id becomes a drawable boundary in production.
 */
@RunWith(AndroidJUnit4::class)
class H3BoundaryDeviceTest {

    @Test
    fun h3NativeLibraryLoadsAndConvertsOnThisDevice() {
        // The whole point: the native .so must actually load on this ABI.
        assertTrue(
            "h3-java native library did not load on this device -- territory cannot render here",
            H3Boundary.available
        )

        // A known-valid res-9 cell id (an H3-documented example). Deliberately a
        // literal, NOT generated via H3Core.newInstance() -- that is the
        // resource-extraction path that fails on Android, and this test must
        // exercise ONLY H3Boundary's device load path (System.loadLibrary +
        // newSystemInstance), exactly what production uses to draw a server cell.
        val ring = H3Boundary.ring("8928308280fffff")

        assertEquals("expected a closed 6-gon ring", 7, ring.size)
        assertEquals("ring must be closed", ring.first().toList(), ring.last().toList())
        ring.forEach { (vlat, vlng) ->
            assertTrue("vertex must be finite", vlat.isFinite() && vlng.isFinite())
            assertTrue("vertex latitude out of range", vlat in -90.0..90.0)
            assertTrue("vertex longitude out of range", vlng in -180.0..180.0)
        }
    }
}
