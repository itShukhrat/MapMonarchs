package com.mapmonarchs.ui.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

// ---------------------------------------------------------------------------
// A small shared motion vocabulary.
//
// WHY IT EXISTS: every screen was reaching for its own duration and its own
// idea of "settle", which is how a product ends up with six slightly different
// animations that read as inconsistency rather than as one hand. These are the
// named beats the whole app animates to -- the same reason Colour.kt names its
// palette instead of letting each screen pick a blue. Every one of them is a no-op
// when the player has turned system animation off (rememberAnimationsEnabled),
// so the vocabulary never overrides that answer.
// ---------------------------------------------------------------------------

/** A content entrance -- a card rising into place. Longer than a nav, it IS a reveal. */
const val ENTRANCE_MS = 260

/** Per-item delay when a group enters, so a panel assembles rather than snapping in. */
const val ENTRANCE_STAGGER_MS = 45

/** A confirmation or emphasis beat -- a success pulse, a mark landing. */
const val EMPHASIS_MS = 340

/**
 * The house easing for things ARRIVING: quick to leave, long to settle, so an
 * entrance decelerates into place instead of stopping dead. One curve, used
 * everywhere, is what makes independently-authored motion feel like one system.
 */
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * A one-shot "rise and fade in" for content as it first appears.
 *
 * Draw-time only (graphicsLayer alpha + translationY), so it never touches
 * layout -- a staggered column of these does not reflow, it just resolves. Pass
 * an increasing [index] to stagger a group; leave it 0 and everything rises
 * together. A no-op under reduced motion: the content is simply already there,
 * which is the correct end state, not a degraded one.
 *
 * WHY A MODIFIER, NOT AnimatedVisibility: the content is always present and never
 * leaves; only its ARRIVAL is animated. AnimatedVisibility models appear/disappear
 * and would add a node and a visibility flag this never needs.
 */
@Composable
fun Modifier.riseIn(index: Int = 0, distance: Dp = 10.dp): Modifier {
    if (!rememberAnimationsEnabled()) return this
    var appeared by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(
            durationMillis = ENTRANCE_MS,
            delayMillis = index * ENTRANCE_STAGGER_MS,
            easing = EmphasizedDecelerate
        ),
        label = "riseIn"
    )
    LaunchedEffect(Unit) { appeared = true }
    val dyPx = with(LocalDensity.current) { distance.toPx() }
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * dyPx
    }
}

/**
 * A slow, endless breath in [0,1] for elements that should read as LIVE rather
 * than painted -- the player puck, the hero action's glow. Deliberately calm
 * (multi-second period): a fast pulse reads as an alert, and nothing here is
 * alerting. Fixed at its bright end under reduced motion, so a still frame still
 * looks intentional rather than dimmed.
 */
/** Which way through the shell a navigation is going, for [screenTransform]. */
enum class NavDirection { FORWARD, BACKWARD, PEER }

/**
 * The transform between full screens in the shell.
 *
 * A crossfade with DEPTH along z, expressed by scale rather than a slide -- the
 * shell's screens are peers and drill-downs, not a horizontal filmstrip, and
 * sliding a live GL map off-screen is expensive for a lie. [direction] shapes it:
 *
 *  - FORWARD (drilling in) pushes the new screen up from behind while the old one
 *    recedes toward the viewer and fades -- "going deeper".
 *  - BACKWARD (drilling out) returns the new screen from in front while the old
 *    falls away -- the exact reverse, so leaving undoes entering.
 *  - PEER (a tab switch, or anything touching the map) barely scales at all: a
 *    gentle settle with no direction, and the only variant the map ever gets.
 *
 * All modest -- this is navigation, not a set-piece -- on the house
 * EmphasizedDecelerate curve. A hard cut when the system has disabled motion.
 */
fun screenTransform(
    animationsEnabled: Boolean,
    direction: NavDirection = NavDirection.PEER,
): ContentTransform {
    val d = if (animationsEnabled) SCREEN_TRANSITION_MS else 0
    val spec = tween<Float>(durationMillis = d, easing = EmphasizedDecelerate)
    val enterScale: Float
    val exitScale: Float
    when (direction) {
        NavDirection.FORWARD -> { enterScale = 0.94f; exitScale = 1.03f }
        NavDirection.BACKWARD -> { enterScale = 1.04f; exitScale = 0.97f }
        NavDirection.PEER -> { enterScale = 0.985f; exitScale = 0.99f }
    }
    return (fadeIn(spec) + scaleIn(spec, initialScale = enterScale)) togetherWith
        (fadeOut(spec) + scaleOut(spec, targetScale = exitScale))
}

@Composable
fun rememberBreath(periodMillis: Int = 2600): Float {
    if (!rememberAnimationsEnabled()) return 1f
    val transition = rememberInfiniteTransition(label = "breath")
    val v by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = EmphasizedDecelerate),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathV"
    )
    return v
}
