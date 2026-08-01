package com.mapmonarchs.data.h3

import com.uber.h3core.H3Core
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Proves the H3 -> boundary conversion the map draws territory from, off-device.
 *
 * h3-java bundles the host native library (windows/linux/darwin), so this runs
 * for real on the build machine even though the same library cannot load on an
 * x86_64 emulator. It is the deterministic proof that a server-issued cell id
 * becomes a closed hexagon over the right ground.
 */
class H3BoundaryTest {

    @Test
    fun `a res-9 cell becomes a closed hexagon ring over the same ground`() {
        assumeTrue("H3 native lib not loadable on this host", H3Boundary.available)

        // Make a real res-9 cell for a known point (central Tashkent) with H3
        // itself, then convert it back through the class under test. This mirrors
        // exactly what happens in production: the server stores this id, the
        // client draws its boundary.
        val lat = 41.311
        val lng = 69.279
        // The STRING form, matching what the Edge Function's h3-js writes into
        // hex_ownership.h3_index and what H3Boundary.ring consumes in production.
        val cell = H3Core.newInstance().latLngToCellAddress(lat, lng, 9)

        val ring = H3Boundary.ring(cell)

        // Six hexagon vertices, plus the first repeated to close the ring.
        assertEquals("expected a closed 6-gon ring", 7, ring.size)
        assertEquals("ring must be closed", ring.first().toList(), ring.last().toList())

        // Every vertex is a valid coordinate hugging the requested point (a res-9
        // cell is ~150 m across, so well within a tenth of a degree).
        ring.forEach { (vlat, vlng) ->
            assertTrue("lat in range", vlat in -90.0..90.0)
            assertTrue("lng in range", vlng in -180.0..180.0)
            assertTrue("vertex near the cell", kotlin.math.abs(vlat - lat) < 0.1)
            assertTrue("vertex near the cell", kotlin.math.abs(vlng - lng) < 0.1)
        }
    }

    @Test
    fun `a malformed cell id yields no ring rather than throwing`() {
        // ABI-independent: with no native lib this returns empty because H3 is
        // unavailable; with the lib it returns empty because the id is rejected.
        // Either way the map skips one hex instead of crashing.
        assertTrue(H3Boundary.ring("not-a-real-h3-index").isEmpty())
    }
}
