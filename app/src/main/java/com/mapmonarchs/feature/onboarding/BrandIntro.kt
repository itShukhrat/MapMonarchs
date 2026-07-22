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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.mapmonarchs.R
import com.mapmonarchs.domain.model.PLAYABLE_COUNTRY_NAME
import com.mapmonarchs.ui.motion.rememberAnimationsEnabled
import com.mapmonarchs.ui.theme.MapMonarchsBrush
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.Sovereign
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object BrandIntroTags {
    const val SCREEN = "brand_intro"
}

/** How long the sequence runs before it hands over on its own. */
private const val INTRO_MS = 1750L

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
 * WHAT IT DRAWS: the crest geometry the rest of the product already speaks --
 * concentric hexagons, the same shape used for identity and for accumulated
 * holdings. The rings settle outward-in, so the mark assembles rather than
 * appears.
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
        animationSpec = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
        label = "introReveal"
    )
    // The wordmark follows the mark rather than arriving with it, so the eye is
    // given one thing at a time.
    val wordmark by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 700, delayMillis = 420, easing = LinearOutSlowInEasing),
        label = "introWordmark"
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
            Canvas(modifier = Modifier.size(148.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val unit = size.minDimension / 2f

                fun hex(r: Float): Path = Path().apply {
                    for (i in 0 until 6) {
                        val a = PI.toFloat() / 3f * i + PI.toFloat() / 6f
                        val x = cx + r * cos(a)
                        val y = cy + r * sin(a)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }

                // Each ring owns a slice of the reveal, so they settle in
                // sequence: the frame arrives, then what it holds.
                fun phase(from: Float, to: Float): Float =
                    ((reveal - from) / (to - from)).coerceIn(0f, 1f)

                val outer = phase(0f, 0.55f)
                val mid = phase(0.25f, 0.80f)
                val core = phase(0.50f, 1f)

                drawCircle(Sovereign.copy(alpha = 0.10f * core), unit * 0.95f, Offset(cx, cy))
                drawPath(
                    hex(unit * (0.70f + 0.24f * outer)),
                    SlateEdge.copy(alpha = 0.85f * outer),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    hex(unit * (0.50f + 0.14f * mid)),
                    Sovereign.copy(alpha = 0.75f * mid),
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(hex(unit * 0.32f * core), SovereignGlow.copy(alpha = core))
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
                text = PLAYABLE_COUNTRY_NAME.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .alpha(wordmark)
            )
        }
    }
}
