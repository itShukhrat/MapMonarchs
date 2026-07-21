package com.mapmonarchs.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.theme.Sovereign
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.SlateEdge
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The character of a locked destination.
 *
 * WHY THIS EXISTS: every locked surface rendered identically -- the same small
 * hollow hexagon over the same three headings. Six destinations that are
 * genuinely different ideas all felt like one screen shown six times, which made
 * the roadmap read as a single "not yet" rather than as a product with shape.
 *
 * WHY GEOMETRY AND NOT COLOUR: the single-accent rule is not negotiable, and
 * territory colour must stay the most saturated thing anywhere in the app. So
 * each destination is distinguished by FORM -- how many cells, how they are
 * arranged, how tightly they are held -- and every one of them is drawn in the
 * one accent. The shapes are all hexagons because that is the shape language of
 * the whole product; what differs is what the hexagons are DOING.
 *
 * WHY IT IS NOT A PROMISE: a motif describes the idea of a destination. It does
 * not state that the feature exists, when it ships, or what it will contain --
 * that is the job of the three mandatory FeatureLock strings, which stay exactly
 * as blunt as they were.
 */
enum class LockMotif {
    /** A field of cells: the world, seen from above. */
    WORLD,

    /** One cell singled out of the field: an objective. */
    QUEST,

    /** Concentric rings: holdings accumulated, a legacy. */
    PRESTIGE,

    /** A sealed cell within a cell: a vault, closed. */
    VAULT,

    /** Cells ranked by size: a standing order. */
    RANKING,

    /** A cell radiating: something reporting in. */
    SIGNAL;

    /**
     * Whether this destination is withheld rather than merely undeployed.
     *
     * PRESTIGE and VAULT are product decisions -- they are being kept back on
     * purpose -- so they get the warmer wash, which makes "not yet" read as
     * something worth waiting for. The rest are waiting on a backend, which is a
     * status, not an aspiration, and dressing a deployment gap as glamour would
     * be the same dishonesty as a "coming soon" splash.
     */
    val isWithheld: Boolean get() = this == PRESTIGE || this == VAULT
}

/** Draws a pointy-top hexagon path of radius [r] centred at [cx],[cy]. */
private fun hexPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val a = PI.toFloat() / 3f * i + PI.toFloat() / 6f
        val x = cx + r * cos(a)
        val y = cy + r * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.hex(cx: Float, cy: Float, r: Float, color: Color, width: Float) {
    drawPath(
        hexPath(cx, cy, r),
        color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/**
 * The large mark at the head of a locked screen.
 *
 * Outline-only, so it reads as a diagram of an idea rather than as a filled
 * asset. The one exception is the centre of VAULT, which is solid precisely
 * because a vault is the one motif whose meaning is that it is shut.
 */
@Composable
fun LockMotifMark(
    motif: LockMotif,
    modifier: Modifier = Modifier,
    markSize: Dp = 92.dp
) {
    Canvas(modifier = modifier.size(markSize)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val unit = size.minDimension / 2f
        val thin = 1.2.dp.toPx()
        val thick = 2.0.dp.toPx()
        val dim = SlateEdge
        val lit = Sovereign
        val bright = SovereignGlow

        when (motif) {
            // Seven cells: a centre and its full ring. A board, not a token.
            LockMotif.WORLD -> {
                val r = unit * 0.30f
                val step = r * 1.74f
                hex(cx, cy, r, bright, thick)
                for (i in 0 until 6) {
                    val a = PI.toFloat() / 3f * i + PI.toFloat() / 6f
                    hex(cx + step * cos(a), cy + step * sin(a), r, dim, thin)
                }
            }

            // The same field, but one cell is lit and the rest recede: the point
            // of a mission is that it names WHICH ground.
            LockMotif.QUEST -> {
                val r = unit * 0.30f
                val step = r * 1.74f
                hex(cx, cy, r, dim, thin)
                for (i in 0 until 6) {
                    val a = PI.toFloat() / 3f * i + PI.toFloat() / 6f
                    val hx = cx + step * cos(a)
                    val hy = cy + step * sin(a)
                    // The objective sits off-centre. A target in the middle would
                    // read as where you already are.
                    if (i == 5) {
                        hex(hx, hy, r, bright, thick)
                        drawCircle(lit.copy(alpha = 0.18f), r * 1.6f, Offset(hx, hy))
                    } else {
                        hex(hx, hy, r, dim, thin)
                    }
                }
            }

            // Three rings growing outward: ground held, then more, then more.
            LockMotif.PRESTIGE -> {
                hex(cx, cy, unit * 0.30f, bright, thick)
                hex(cx, cy, unit * 0.60f, lit.copy(alpha = 0.70f), thin)
                hex(cx, cy, unit * 0.90f, lit.copy(alpha = 0.35f), thin)
            }

            // A cell inside a cell, with a solid core. Shut, and clearly shut.
            LockMotif.VAULT -> {
                hex(cx, cy, unit * 0.88f, lit.copy(alpha = 0.55f), thin)
                hex(cx, cy, unit * 0.58f, bright, thick)
                drawPath(hexPath(cx, cy, unit * 0.20f), lit)
            }

            // Descending sizes in a row: an order of standing.
            LockMotif.RANKING -> {
                val gap = unit * 0.62f
                hex(cx - gap, cy, unit * 0.34f, bright, thick)
                hex(cx, cy, unit * 0.26f, lit.copy(alpha = 0.70f), thin)
                hex(cx + gap, cy, unit * 0.18f, lit.copy(alpha = 0.40f), thin)
            }

            // One cell with arcs coming off it: an event being reported.
            LockMotif.SIGNAL -> {
                hex(cx, cy, unit * 0.28f, bright, thick)
                for (ring in 1..2) {
                    drawCircle(
                        color = lit.copy(alpha = 0.34f / ring),
                        radius = unit * (0.30f + 0.28f * ring),
                        center = Offset(cx, cy),
                        style = Stroke(width = thin)
                    )
                }
            }
        }
    }
}
