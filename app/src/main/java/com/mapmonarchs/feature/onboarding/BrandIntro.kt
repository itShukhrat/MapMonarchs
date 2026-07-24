package com.mapmonarchs.feature.onboarding

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.mapmonarchs.R
import com.mapmonarchs.ui.motion.rememberAnimationsEnabled
import com.mapmonarchs.ui.theme.MapMonarchsBrush
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.Sovereign
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object BrandIntroTags {
    const val SCREEN = "brand_intro"
}

/** How long the sequence runs before it hands over on its own. */
private const val INTRO_MS = 1750L

/** The reveal itself. The remainder of [INTRO_MS] is a settled hold, not motion. */
private const val REVEAL_MS = 1150

/**
 * The brand moment.
 *
 * WHY A PRODUCT LIKE THIS EARNS ONE: the app opened straight onto a wall of
 * onboarding prose. A strategy game that asks a player to walk real streets for
 * ground should introduce itself first -- one held breath before the argument
 * starts. That is the whole job, which is why it is under two seconds.
 *
 * WHAT IT IS NOT: a loading screen. Nothing is being fetched behind it and it
 * never claims to be working. Pretending to load would be theatre, and this app
 * does not do theatre about its own state.
 *
 * WHAT IT DRAWS, AND WHY IT MATTERS THAT IT IS EXACTLY THIS: the mark assembles
 * into the crest at IdentityCrest's proportions -- 0.94 / 0.70 / 0.44 of the
 * radius, the same three rings the player then wears as their own identity and
 * sees again on the EMPIRE surface. The intro is not a separate logo animation;
 * it is the player's crest being struck before it is handed to them. Those radii
 * are duplicated deliberately and must move together: if IdentityCrest is
 * retuned, this follows it.
 *
 * HOW IT ASSEMBLES: each ring is TRACED rather than faded. The perimeter is
 * drawn from the top vertex outward through the path, so the crest reads as
 * being struck into the surface -- a seal being cut -- instead of a logo
 * dissolving in. Fading is what a splash screen does; cutting is what a mint
 * does, and this product is about ground that is claimed rather than given.
 *
 * The rings are ordered outside-in: the frame is established, then what it
 * holds, then the held cell ignites once. That single bloom is the only moment
 * of luminance in the sequence, and it settles rather than pulsing -- a pulse
 * would read as a heartbeat, which implies waiting, which would be the loading
 * lie this screen refuses to tell.
 *
 * SKIPPABLE, AND HONEST ABOUT MOTION: a tap anywhere hands over immediately, and
 * when the system asks for reduced motion the sequence is skipped entirely
 * rather than played at zero duration. A player who has turned animation off
 * should not be held for two seconds by an animation they cannot see.
 */
@Composable
fun BrandIntro(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val animate = rememberAnimationsEnabled()

    // Reduced motion means straight through. Not a shortened intro -- none.
    if (!animate) {
        LaunchedEffect(Unit) { onDone() }
        return
    }

    val introLabel = stringResource(R.string.brand_intro_a11y)
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        started = true
        delay(INTRO_MS)
        onDone()
    }

    val reveal by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = REVEAL_MS, easing = LinearOutSlowInEasing),
        label = "introReveal"
    )
    // The wordmark follows the mark rather than arriving with it, so the eye is
    // given one thing at a time.
    val wordmark by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 700, delayMillis = 520, easing = LinearOutSlowInEasing),
        label = "introWordmark"
    )

    // One instance, reused for every ring in every frame. Allocating a measure
    // per ring per frame would be 6 short-lived objects every 16ms for no gain.
    val measure = remember { PathMeasure() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MapMonarchsBrush.screen)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDone
            )
            .testTag(BrandIntroTags.SCREEN)
            .semantics { contentDescription = introLabel },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                modifier = Modifier
                    .size(148.dp)
                    // The whole mark settles the last fraction into place rather
                    // than arriving at final size. Small enough to be felt and
                    // not seen -- anything larger reads as a zoom effect.
                    .scale(0.965f + 0.035f * reveal)
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val unit = size.minDimension / 2f

                // Vertices match IdentityCrest exactly; only the START vertex
                // differs, so the trace begins at the apex instead of mid-edge.
                // Same hexagon, deliberately cut from the top.
                fun hex(r: Float): Path = Path().apply {
                    for (i in 0 until 6) {
                        val a = PI.toFloat() / 3f * i - PI.toFloat() / 2f
                        val x = cx + r * cos(a)
                        val y = cy + r * sin(a)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }

                /** The leading fraction [t] of a ring's perimeter. */
                fun traced(path: Path, t: Float): Path {
                    if (t >= 1f) return path
                    val out = Path()
                    measure.setPath(path, false)
                    measure.getSegment(0f, measure.length * t, out, true)
                    return out
                }

                // Each ring owns a slice of the reveal, so they settle in
                // sequence: the frame arrives, then what it holds.
                fun phase(from: Float, to: Float): Float =
                    ((reveal - from) / (to - from)).coerceIn(0f, 1f)

                val outer = phase(0f, 0.42f)
                val mid = phase(0.22f, 0.66f)
                val core = phase(0.52f, 0.88f)

                // A single bloom at the moment the core lands, easing back to a
                // low resting glow. Triangular, so it has one peak and no beat.
                val bloom = 1f - abs(2f * core - 1f)
                val restingGlow = 0.07f * core

                drawCircle(
                    Sovereign.copy(alpha = restingGlow + 0.11f * bloom),
                    unit * (0.90f + 0.10f * bloom),
                    Offset(cx, cy)
                )

                // Outer frame: the boundary of the crest.
                drawPath(
                    traced(hex(unit * 0.94f), outer),
                    SlateEdge.copy(alpha = 0.85f),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Inner ring: the accent proper, and the only stroke that carries
                // Sovereign.
                drawPath(
                    traced(hex(unit * 0.70f), mid),
                    Sovereign.copy(alpha = 0.80f),
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Held ground: filled, not stroked. It is the one solid thing in
                // the mark, exactly as it is the one solid thing in the game.
                drawPath(
                    hex(unit * 0.44f * (0.86f + 0.14f * core)),
                    SovereignGlow.copy(alpha = core)
                )
            }

            Text(
                text = stringResource(R.string.brand_wordmark),
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                modifier = Modifier
                    .padding(top = 30.dp)
                    .alpha(wordmark)
                    .scale(0.98f + 0.02f * wordmark)
            )

            // A rule that draws outward from the centre, tying the wordmark to
            // the mark above it.
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .width(120.dp * wordmark)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.5f to Sovereign.copy(alpha = 0.65f),
                            1f to Color.Transparent
                        )
                    )
            )

            // The playable world, stated in the first two seconds. This game is
            // Uzbekistan-only and that is a feature of the design, not a
            // limitation to be discovered later at a border.
            Text(
                text = stringResource(R.string.country_name).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .alpha(wordmark)
            )
        }
    }
}
