package com.mapmonarchs.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.ui.theme.TextPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The player crest.
 *
 * PROBLEM SOLVED: IDENTITY was reading as an account settings page -- a 40dp
 * swatch beside a name, over a list of rows. In a game about holding ground, the
 * one thing that is unarguably YOURS is your colour, and it was being presented
 * with less weight than a version number.
 *
 * WHY IT IS BUILT FROM RINGS: the same concentric geometry that means accumulated
 * holdings on the locked EMPIRE surface. Identity and empire are the same idea at
 * different scales -- what you are, and what you hold -- so they share a shape
 * rather than each inventing one.
 *
 * ON THE SINGLE-ACCENT RULE. The fill here is TERRITORY colour, not the Sovereign
 * accent, and that is deliberate rather than an exception: Colour.kt requires
 * territory to be the most saturated thing on screen, and this crest is the
 * player's territory colour shown at the one place it means the player
 * themselves. The surrounding rings stay unsaturated so nothing competes with it.
 *
 * WITHOUT A SESSION IT IS HOLLOW. No colour is invented for a player the server
 * does not know. An unclaimed outline is literally accurate, and it is the same
 * hollow-versus-filled language the dock, ChoiceRow and CommandAction all use.
 */
@Composable
fun IdentityCrest(
    callsign: String,
    stateLabel: String,
    territoryColor: Color?,
    modifier: Modifier = Modifier,
    crestSize: Dp = 132.dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(crestSize)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val unit = size.minDimension / 2f
            val thin = 1.2.dp.toPx()
            val thick = 2.4.dp.toPx()

            fun hexPath(r: Float): Path = Path().apply {
                for (i in 0 until 6) {
                    val a = PI.toFloat() / 3f * i + PI.toFloat() / 6f
                    val x = cx + r * cos(a)
                    val y = cy + r * sin(a)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }

            // A held colour throws light. Nothing does when there is no session.
            if (territoryColor != null) {
                drawCircle(territoryColor.copy(alpha = 0.16f), unit * 0.95f, Offset(cx, cy))
            }

            // Outer and mid rings: the frame, always unsaturated so the core wins.
            drawPath(
                hexPath(unit * 0.94f),
                (territoryColor ?: SlateEdge).copy(alpha = 0.30f),
                style = Stroke(width = thin, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                hexPath(unit * 0.70f),
                (territoryColor ?: SlateEdge).copy(alpha = 0.55f),
                style = Stroke(width = thin, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // The core carries the identity itself.
            if (territoryColor != null) {
                drawPath(hexPath(unit * 0.44f), territoryColor)
            } else {
                drawPath(
                    hexPath(unit * 0.44f),
                    SlateEdge,
                    style = Stroke(width = thick, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        Text(
            text = callsign,
            style = MaterialTheme.typography.displaySmall,
            color = if (territoryColor != null) TextPrimary else TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            text = stateLabel,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
