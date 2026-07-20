package com.mapmonarchs.ui.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the system wants animation at all.
 *
 * Reads the platform's own transition scale rather than inventing an in-app
 * toggle. A player who disabled animation system-wide has already stated a
 * preference -- usually for motion sensitivity or battery -- and asking them to
 * state it again in our settings would be ignoring the answer they gave.
 *
 * Returns true on failure: a missing setting is not evidence that motion is
 * unwanted, and defaulting to "no animation" would silently degrade the product
 * for every device that does not report it.
 *
 * EXTRACTED, NOT COPIED. This logic was written for the command dock. Adding
 * screen transitions needed the identical check, and this codebase has paid for
 * duplicated rules four times now -- the blocker chain, the delivery mapping, the
 * remedy route, the unknown-holdings glyph. Two copies of "should we animate"
 * would drift the moment one of them gained a special case.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { animationsEnabled(context) }
}

internal fun animationsEnabled(context: Context): Boolean {
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        1f
    )
    return scale != 0f
}

/**
 * Screen transition timing.
 *
 * Short on purpose. This is navigation, not a reveal: the player pressed a
 * destination and wants to be there. Anything long enough to notice as an
 * animation is long enough to be in the way on the twentieth use.
 */
const val SCREEN_TRANSITION_MS = 180
