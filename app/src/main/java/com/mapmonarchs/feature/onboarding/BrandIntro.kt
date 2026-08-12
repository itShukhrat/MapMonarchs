package com.mapmonarchs.feature.onboarding

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mapmonarchs.R
import com.mapmonarchs.ui.motion.rememberAnimationsEnabled
import com.mapmonarchs.ui.theme.MapMonarchsBrush
import com.mapmonarchs.ui.theme.Sovereign
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlin.math.abs

object BrandIntroTags {
    const val SCREEN = "brand_intro"
}

/** How long the sequence runs before it hands over on its own. */
private const val INTRO_MS = 1750L

/** The reveal itself. The remainder of [INTRO_MS] is a settled hold, not motion. */
private const val REVEAL_MS = 1000

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
 * IT ANIMATES THE ACTUAL LOGO, NOT A REDRAWING OF IT. The painter here is
 * ic_brand_crown -- the same crown artwork the launcher icon and therefore the
 * Android 12+ system splash are built from, keyed out of the supplied tile.
 * Because it is the same pixels rather than a reinterpretation, there is no
 * symbol swap anywhere between tapping the icon and reaching onboarding. A
 * hand-traced approximation was rejected for exactly this reason: it would have
 * been a fourth version of the mark.
 *
 * HOW IT REVEALS: a soft radial mask opens from the CENTRE of the artwork, which
 * is where the logo puts its compass. The motion therefore comes out of the mark
 * rather than being applied on top of it -- the crown resolves outward from its
 * own focal point instead of sliding, spinning, or bouncing in. A single
 * restrained bloom of the Sovereign accent lands as the reveal completes and
 * then settles; it does not pulse, because a pulse reads as a heartbeat, which
 * implies waiting, which would be the loading lie this screen refuses to tell.
 *
 * ON THE FIELD BEHIND IT: the crown is shown on the app's obsidian screen rather
 * than on the logo's own blue-green tile. That tile exists because an app icon
 * must fill a square; reproducing it full-screen would read as a screenshot of
 * an icon pasted onto the app, and would push a second colour across a surface
 * the rest of the product keeps to one accent. The MARK is preserved exactly;
 * only the backdrop it needs at 108dp is not carried onto a 1080px screen.
 *
 * SKIPPABLE, AND HONEST ABOUT MOTION: a tap anywhere hands over immediately, and
 * when the system asks for reduced motion the sequence is skipped entirely
 * rather than played at zero duration. A player who has turned animation off
 * should not be held for two seconds by an animation they cannot see.
 */
@Composable
fun BrandIntro(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether to play the sequence at all. Injected ONLY so both motion paths
     * can be asserted: a test cannot flip Settings.Global without
     * WRITE_SECURE_SETTINGS, so without this seam the reduced-motion branch --
     * the one that strands a motion-sensitive player if it ever regresses --
     * could not be covered. Production always takes the default.
     */
    animate: Boolean = rememberAnimationsEnabled()
) {
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
    // The studio signature settles in last and quietest -- MapMonarchs is a
    // product of Monarchs, said once, subordinate to the wordmark above it. It
    // lands inside the existing hold, so it costs no extra startup time.
    val byline by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 560, delayMillis = 780, easing = LinearOutSlowInEasing),
        label = "introByline"
    )

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

            Box(contentAlignment = Alignment.Center) {

                // Held light behind the mark. One bloom, then rest.
                val bloom = 1f - abs(2f * reveal - 1f)
                Canvas(modifier = Modifier.size(248.dp)) {
                    val r = size.minDimension / 2f
                    val a = 0.05f * reveal + 0.09f * bloom
                    if (a > 0.001f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to Sovereign.copy(alpha = a),
                                1f to Color.Transparent,
                                center = center,
                                radius = r
                            ),
                            radius = r,
                            center = center
                        )
                    }
                }

                Image(
                    painter = painterResource(R.drawable.ic_brand_crown),
                    // Decorative: the whole screen already carries the brand
                    // description, and announcing the mark twice is noise.
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        // 423x344 source; this holds the artwork's aspect exactly
                        // so the crown is never stretched.
                        .size(width = 176.dp, height = 143.dp)
                        .graphicsLayer {
                            val s = 0.94f + 0.06f * reveal
                            scaleX = s
                            scaleY = s
                            // Required for the DstIn mask below to affect only
                            // this image rather than everything drawn beneath it.
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            if (reveal < 1f) {
                                // Opens from the artwork's centre, where the logo
                                // places its compass. Clamped away from zero: a
                                // radial brush with radius 0 has no gradient to
                                // interpolate.
                                val radius = (size.maxDimension * 0.78f)
                                    .times(reveal)
                                    .coerceAtLeast(0.01f)
                                drawRect(
                                    brush = Brush.radialGradient(
                                        0f to Color.Black,
                                        0.72f to Color.Black,
                                        1f to Color.Transparent,
                                        center = center,
                                        radius = radius
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                )
            }

            Text(
                text = stringResource(R.string.brand_wordmark),
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                modifier = Modifier
                    .padding(top = 22.dp)
                    .alpha(wordmark)
                    .scale(0.98f + 0.02f * wordmark)
            )

            // Studio signature. Muted and small so it stays clearly beneath the
            // product wordmark -- an attribution, not a second title.
            Text(
                text = stringResource(R.string.brand_by),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .alpha(byline * 0.85f)
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
