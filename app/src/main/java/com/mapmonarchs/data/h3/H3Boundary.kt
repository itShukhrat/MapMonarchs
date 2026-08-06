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
 * HOW THE NATIVE LIB LOADS, AND WHY IT IS NOT h3-java's default. h3-java's
 * `newInstance()` extracts its `.so` from a CLASSPATH RESOURCE (e.g.
 * `/android-arm64/libh3-java.so`). AGP does not package jar resources at those
 * paths into an APK, so on a real device that fails at runtime with
 * `UnsatisfiedLinkError: No native resource found` -- even on arm64 (proven on an
 * S23+). So the arm64/armeabi-v7a `.so` is vendored into `src/main/jniLibs/`, AGP
 * packages it into the APK's `lib/<abi>/`, and [loadH3] loads it the normal
 * Android way via `System.loadLibrary` + `newSystemInstance()`.
 *
 * The host JVM (unit tests) has no `libh3-java` on `java.library.path`, so there
 * the system path fails and [loadH3] falls back to `newInstance()`, whose
 * resource extraction DOES work off-device (windows/linux/mac natives are in the
 * jar). That fallback is what lets H3BoundaryTest verify the conversion on the
 * host. There is no android-x86_64 `.so` at all, so both paths fail on an x86_64
 * emulator: [available] reports false and [ring] returns empty, and territory
 * simply does not draw there instead of crashing the map.
 */
object H3Boundary {

    private const val TAG = "H3Boundary"

    private val core: H3Core? by lazy { loadH3() }

    // UnsatisfiedLinkError is an Error, not an Exception, so these catch Throwable.
    private fun loadH3(): H3Core? {
        // Android: load the jniLibs-packaged .so from the APK's lib/<abi>/.
        try {
            System.loadLibrary("h3-java")
            return H3Core.newSystemInstance()
        } catch (t: Throwable) {
            // Expected on the host JVM (no libh3-java on java.library.path).
            Log.i(TAG, "system H3 load unavailable (${t.message}); trying the resource loader")
        }
        // Host fallback: h3-java's own extraction path (works off-device).
        return try {
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
