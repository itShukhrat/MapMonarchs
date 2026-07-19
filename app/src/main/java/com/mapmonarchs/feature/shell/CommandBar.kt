package com.mapmonarchs.feature.shell

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.components.NavIconGlyph
import com.mapmonarchs.ui.components.NavIconSize
import com.mapmonarchs.ui.components.NavLockMark
import com.mapmonarchs.ui.theme.FloatCore
import com.mapmonarchs.ui.theme.FloatEdge
import com.mapmonarchs.ui.theme.FloatShadow
import com.mapmonarchs.ui.theme.LockedFg
import com.mapmonarchs.ui.theme.MapMonarchsType
import com.mapmonarchs.ui.theme.Sovereign
import com.mapmonarchs.ui.theme.SovereignDim
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object CommandBarTags {
    const val BAR = "command_bar"
    fun item(title: String) = "command_bar_$title"
}

/**
 * The floating command bar.
 *
 * WHY IT FLOATS RATHER THAN DOCKS: a docked bar owns a permanent horizontal band
 * of the screen, and on a map-first product that band is territory the player
 * came to look at. This one is inset on all sides and casts a shadow onto the
 * map, so the map continues underneath it and stays the subject. That is the one
 * idea worth taking from a floating-pill navigation; the shape itself is not.
 *
 * WHY IT REPLACED THE CORNER BUTTON: the previous shell hid every destination
 * behind a single hex button, which made the product feel smaller than it is and
 * gave a first-time player no sense of what the game contains. Locked
 * destinations shown honestly are a roadmap. A button that reveals them only
 * after a tap is a secret.
 *
 * SELECTION IS A SHAPE, NOT A COLOUR. The indicator is a filled hexagon that
 * springs between positions and the selected glyph solidifies. Colour alone
 * would be invisible to a large minority of players, on the primary control for
 * moving through the app.
 *
 * MOTION: one spring, slightly underdamped so the indicator settles with a
 * single small overshoot -- it should feel like something with mass arriving,
 * not like a tab fading. Honoured only when the system allows animation; see
 * [animationsEnabled].
 */
@Composable
fun CommandBar(
    destinations: List<NavDestination>,
    currentRoute: Route,
    onSelect: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val animate = remember(context) { animationsEnabled(context) }

    val selectedIndex = destinations.indexOfFirst { it.route == currentRoute }
        .let { if (it >= 0) it.toFloat() else -1f }

    // -1 means "no destination in this bar is current" (e.g. Settings, Help).
    // The indicator hides rather than pointing at something that is not open.
    val hasSelection = selectedIndex >= 0f

    val indicator by animateFloatAsState(
        targetValue = if (hasSelection) selectedIndex else 0f,
        animationSpec = if (animate) {
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
        } else {
            tween(durationMillis = 0)
        },
        label = "commandBarIndicator"
    )

    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(elevation = 22.dp, shape = shape, ambientColor = FloatShadow, spotColor = FloatShadow)
            .clip(shape)
            .background(FloatCore)
            .border(1.dp, FloatEdge, shape)
            .testTag(CommandBarTags.BAR)
    ) {
        // The indicator is drawn BEHIND the row in a single canvas rather than as
        // a per-item background. One moving shape reads as one object travelling;
        // five fading backgrounds read as five things blinking.
        if (hasSelection) {
            // matchParentSize, NOT fillMaxSize. Inside a Box that sizes itself to
            // its content, fillMaxSize resolves against the INCOMING constraints
            // -- the whole screen -- so the canvas expanded to full height, took
            // the bar with it, and scaled the indicator hexagon to roughly 800dp.
            // Every test still passed and the build was green; only a screenshot
            // caught it. matchParentSize measures against the siblings instead,
            // which is exactly what a background layer wants.
            Canvas(modifier = Modifier.matchParentSize()) {
                val slot = size.width / destinations.size
                val cx = slot * (indicator + 0.5f)
                val cy = size.height / 2f
                val r = size.height * 0.30f

                fun hex(hr: Float): Path = Path().apply {
                    for (i in 0 until 6) {
                        val a = PI.toFloat() / 3f * i + PI.toFloat() / 6f
                        val x = cx + hr * cos(a)
                        val y = cy + hr * sin(a)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }

                // Layered depth: a soft field, a solid cell, a lit edge. Three
                // cheap draws rather than a blur, which is expensive on the GPU
                // and unsupported on older devices this game must still run on.
                drawCircle(SovereignDim.copy(alpha = 0.30f), r * 1.85f, Offset(cx, cy))
                drawPath(hex(r), SovereignDim)
                drawPath(
                    hex(r),
                    SovereignGlow.copy(alpha = 0.55f),
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { destination ->
                CommandBarItem(
                    destination = destination,
                    selected = destination.route == currentRoute,
                    onClick = {
                        // Haptic on every real navigation, including locked ones:
                        // the tap DID something (it opened the explanation), and
                        // silence would read as a dead control.
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(destination)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CommandBarItem(
    destination: NavDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = when {
        selected -> SovereignGlow
        destination.isLocked -> LockedFg
        else -> TextMuted
    }

    // Locked destinations announce their state rather than relying on a dimmer
    // glyph, which a screen reader cannot see and a player in sunlight may miss.
    val description = buildString {
        append(destination.label)
        if (destination.isLocked) append(", locked")
        if (selected) append(", current screen")
    }

    Column(
        modifier = modifier
            // 48dp is the floor for a touch target. The bar is compact by design,
            // so the target is enlarged beyond the visible glyph rather than the
            // glyph being grown to meet it.
            .sizeIn(minHeight = 48.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics { contentDescription = description }
            .testTag(CommandBarTags.item(destination.route.title))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            NavIconGlyph(
                glyph = destination.glyph,
                color = tint,
                filled = selected,
                modifier = Modifier.size(NavIconSize)
            )
            if (destination.isLocked) {
                NavLockMark(
                    color = LockedFg,
                    modifier = Modifier
                        .size(9.dp)
                        .padding(top = 0.dp)
                )
            }
        }
        Text(
            text = destination.label,
            style = MapMonarchsType.navLabel,
            color = tint,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

/**
 * Whether the system wants animation at all.
 *
 * Reads the platform's own transition scale rather than inventing an in-app
 * toggle. A player who has turned animation off system-wide has already stated a
 * preference -- often for motion sensitivity or for battery -- and asking them to
 * state it again in our settings would be ignoring the answer they gave.
 *
 * Returns true on failure: a missing setting is not evidence that motion is
 * unwanted, and defaulting to "no animation" would silently degrade the product
 * for everyone whose device does not report it.
 */
private fun animationsEnabled(context: android.content.Context): Boolean {
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        1f
    )
    return scale != 0f
}

/** Height reserved so map content can inset above the bar without guessing. */
val CommandBarHeight = 78.dp
