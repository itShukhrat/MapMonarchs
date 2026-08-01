package com.mapmonarchs.data.h3

import android.util.Log
import com.uber.h3core.H3Core

/**
 * Converts a server-issued H3 cell id into its boundary polygon, for DRAWING
 * territory only.
 *
 * WHY THIS IS NOT AN ANTI-CHEAT SURFACE. The client never decides which cell it
 * stands in -- that is the Edge Function's job (h3-js), server-side and
 * unforgeable. This class only takes a cell id the SERVER already recorded in
 * hex_ownership and asks "where are its corners", so the map can outline ground
 * the server says is owned. A hacked client could draw a fake hexagon here, but
 * it could draw a fake rectangle with no H3 at all; drawing is not owning.
 *
 * GRACEFUL ON UNSUPPORTED ABIS. h3-java bundles native libraries for
 * android-arm / android-arm64 (real devices) but NOT android-x86_64, so on an
 * x86_64 emulator H3Core.newInstance() throws UnsatisfiedLinkError. That is
 * caught once: [available] reports false and [ring] returns empty, so territory
 * simply does not draw there instead of crashing the map. On a real ARM device
 * the libraries load and territory renders. On the host JVM (windows/linux/mac
 * natives are bundled too) it loads, which is what lets H3BoundaryTest verify the
 * conversion deterministically off-device.
 */
object H3Boundary {

    private const val TAG = "H3Boundary"

    // Lazy + caught once. UnsatisfiedLinkError is an Error, not an Exception, so
    // this catches Throwable deliberately.
    private val core: H3Core? by lazy {
        try {
            H3Core.newInstance()
        } catch (t: Throwable) {
            Log.w(TAG, "H3 native library unavailable on this ABI; territory will not render", t)
            null
        }
    }

    /** True when the native library loaded and boundaries can be produced. */
    val available: Boolean get() = core != null

    /**
     * The boundary of one H3 cell as (lat, lng) vertices, CLOSED by repeating the
     * first vertex so it can be drawn as a ring. Empty when the id is malformed or
     * H3 is unavailable -- callers skip empty rings, so a bad id costs one hex,
     * never a crash.
     */
    fun ring(h3Index: String): List<DoubleArray> {
        val h3 = core ?: return emptyList()
        return try {
            val verts = h3.cellToBoundary(h3Index).map { doubleArrayOf(it.lat, it.lng) }
            if (verts.isEmpty()) verts else verts + listOf(verts.first())
        } catch (t: Throwable) {
            Log.w(TAG, "cellToBoundary failed for '$h3Index'", t)
            emptyList()
        }
    }
}
